package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.CachedWorkspaceSnapshot
import com.example.data.local.LocalMetadataOverlays
import com.example.data.local.OfflineSyncQueueStore
import com.example.data.local.OfflineVaultManager
import com.example.data.local.PendingSyncAction
import com.example.data.local.PendingSyncActionType
import com.example.data.local.SessionPreferences
import com.example.data.model.ActivityItem
import com.example.data.model.Folder
import com.example.data.model.FolderCollaborator
import com.example.data.model.FolderItem
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.OfflineVaultStats
import com.example.data.model.ShareLinkItem
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.UserSession
import com.example.data.model.nowIsoString
import com.example.data.remote.ChatbotPromptRequest
import com.example.data.remote.CreateShareLinkRequest
import com.example.data.remote.FolderMoveFilesRequest
import com.example.data.remote.FolderShareRequest
import com.example.data.remote.FolderUpsertRequest
import com.example.data.remote.LoginRequest
import com.example.data.remote.ProfileUpdateRequest
import com.example.data.remote.RegisterRequest
import com.example.data.remote.RetrofitClient
import com.example.data.remote.VerifySharePasswordRequest
import com.example.data.remote.ZipImagesRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import com.example.util.ExifMetadataScrubber
import com.example.util.UploadProgressRequestBody
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class UploadCandidate(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val mediaType: MediaType
)

class GalleryHubRepository(
    private val context: Context,
    private val preferences: SessionPreferences
) {
    val apiService = RetrofitClient.apiService
    private val fastPingService = RetrofitClient.fastPingService
    val offlineVault = OfflineVaultManager(context)
    val syncQueueStore = OfflineSyncQueueStore(context)

    private val sandboxMedia = mutableListOf<UnifiedMediaItem>()
    private val sandboxFolders = mutableListOf<FolderItem>()
    private val sandboxShareLinks = mutableListOf<ShareLinkItem>()
    private val sandboxActivities = mutableListOf<ActivityItem>()
    private var loadedRawJson: String? = null

    suspend fun ensureSandboxInitialized() = withContext(Dispatchers.IO) {
        val raw = preferences.loadSandboxStateJson()
        if (raw == loadedRawJson && loadedRawJson != null) return@withContext
        loadedRawJson = raw ?: ""
        sandboxMedia.clear()
        sandboxFolders.clear()
        sandboxShareLinks.clear()
        sandboxActivities.clear()
        if (!raw.isNullOrBlank()) {
            try {
                restoreSandboxFromJson(JSONObject(raw))
            } catch (_: Exception) {
            }
        }
    }

    suspend fun decorateWithLocalState(
        accountKey: String,
        items: List<UnifiedMediaItem>
    ): List<UnifiedMediaItem> = withContext(Dispatchers.IO) {
        val overlays = syncQueueStore.loadOverlays(accountKey)
        val vaultIndex = offlineVault.getSnapshotIndex()
        applyDecorations(items, overlays, vaultIndex)
    }

    private fun applyDecorations(
        items: List<UnifiedMediaItem>,
        overlays: LocalMetadataOverlays,
        vaultIndex: Map<String, Pair<OfflineAvailabilityState, String>>
    ): List<UnifiedMediaItem> {
        return items.map { item ->
            val vaultEntry = vaultIndex[item.id]
            val renamed = overlays.renamedTitles[item.id]
            val customTags = overlays.customTags[item.id]
            val isFav = item.id in overlays.favoriteIds || item.isFavorite
            val offlineTier = vaultEntry?.first ?: when {
                !item.localUri.isNullOrBlank() && item.localUri.startsWith("file://") -> OfflineAvailabilityState.PINNED
                else -> OfflineAvailabilityState.NONE
            }
            val offlinePath = vaultEntry?.second ?: item.offlineFilePath
            item.copy(
                originalName = renamed ?: item.originalName,
                tags = customTags ?: item.tags,
                isFavorite = isFav,
                offlineState = offlineTier,
                offlineFilePath = offlinePath
            )
        }
    }

    /**
     * Loads the per-account cached snapshot instantaneously on startup (Stale-While-Revalidate)
     * so users see their library immediately without waiting on Render cold starts.
     */
    suspend fun loadCachedWorkspaceSnapshot(accountKey: String): CachedWorkspaceSnapshot =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            val snapshot = syncQueueStore.loadWorkspaceSnapshot(accountKey)
            val overlays = syncQueueStore.loadOverlays(accountKey)
            val vaultIndex = offlineVault.getSnapshotIndex()
            if (snapshot.hasData) {
                snapshot.copy(
                    activeMedia = applyDecorations(snapshot.activeMedia, overlays, vaultIndex),
                    trashMedia = applyDecorations(snapshot.trashMedia, overlays, vaultIndex)
                )
            } else {
                CachedWorkspaceSnapshot(
                    activeMedia = applyDecorations(
                        sandboxMedia.filter { !it.isDeleted }.sortedByDescending { it.timestampMillis },
                        overlays,
                        vaultIndex
                    ),
                    trashMedia = applyDecorations(sandboxMedia.filter { it.isDeleted }, overlays, vaultIndex),
                    ownedFolders = sandboxFolders.filter { !it.isShared },
                    sharedFolders = sandboxFolders.filter { it.isShared },
                    shareLinks = sandboxShareLinks.toList(),
                    activities = sandboxActivities.toList()
                )
            }
        }

    /**
     * Fetches ALL 5 workspace streams (active media, trash, folders, share links, activities)
     * concurrently in parallel, applies local overlays + offline vault status, and persists
     * a fresh snapshot atomically to disk.
     */
    suspend fun fetchWorkspaceBundleParallel(
        accountKey: String,
        isSandbox: Boolean
    ): CachedWorkspaceSnapshot = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val (active, trash, foldersPair, links, acts) = coroutineScope {
            val activeDef = async { fetchAllActiveMedia(isSandbox) }
            val trashDef = async { fetchTrashMedia(isSandbox) }
            val foldersDef = async { fetchFolders(isSandbox) }
            val linksDef = async { fetchMyShareLinks(isSandbox) }
            val actsDef = async { fetchActivities(1, isSandbox) }
            Bundle5(
                activeDef.await(),
                trashDef.await(),
                foldersDef.await(),
                linksDef.await(),
                actsDef.await()
            )
        }
        val overlays = syncQueueStore.loadOverlays(accountKey)
        val vaultIndex = offlineVault.getSnapshotIndex()

        // If cloud fetch returned empty because we are offline, keep cached snapshot rather than wiping it
        val previousSnapshot = syncQueueStore.loadWorkspaceSnapshot(accountKey)
        val finalActive = if (active.isEmpty() && !isSandbox && previousSnapshot.activeMedia.isNotEmpty()) {
            previousSnapshot.activeMedia
        } else {
            active
        }
        val finalTrash = if (trash.isEmpty() && !isSandbox && previousSnapshot.trashMedia.isNotEmpty() && active.isEmpty()) {
            previousSnapshot.trashMedia
        } else {
            trash
        }
        val finalOwnedFolders = if (foldersPair.first.isEmpty() && !isSandbox && previousSnapshot.ownedFolders.isNotEmpty() && active.isEmpty()) {
            previousSnapshot.ownedFolders
        } else {
            foldersPair.first
        }
        val finalSharedFolders = if (foldersPair.second.isEmpty() && !isSandbox && previousSnapshot.sharedFolders.isNotEmpty() && active.isEmpty()) {
            previousSnapshot.sharedFolders
        } else {
            foldersPair.second
        }
        val finalLinks = if (links.isEmpty() && !isSandbox && previousSnapshot.shareLinks.isNotEmpty() && active.isEmpty()) {
            previousSnapshot.shareLinks
        } else {
            links
        }

        val bundle = CachedWorkspaceSnapshot(
            activeMedia = applyDecorations(finalActive, overlays, vaultIndex),
            trashMedia = applyDecorations(finalTrash, overlays, vaultIndex),
            ownedFolders = finalOwnedFolders,
            sharedFolders = finalSharedFolders,
            shareLinks = finalLinks,
            activities = acts.ifEmpty { previousSnapshot.activities },
            savedAtMillis = System.currentTimeMillis()
        )
        syncQueueStore.saveWorkspaceSnapshot(accountKey, bundle)
        bundle
    }

    private data class Bundle5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    suspend fun saveWorkspaceSnapshot(accountKey: String, snapshot: CachedWorkspaceSnapshot) {
        syncQueueStore.saveWorkspaceSnapshot(accountKey, snapshot)
    }

    suspend fun toggleFavorite(accountKey: String, itemId: String, nextFavorite: Boolean) =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            syncQueueStore.setFavorite(accountKey, itemId, nextFavorite)
            val idx = sandboxMedia.indexOfFirst { it.id == itemId }
            if (idx >= 0) {
                sandboxMedia[idx] = sandboxMedia[idx].copy(isFavorite = nextFavorite)
                persistSandboxToDisk()
            }
        }

    suspend fun renameMediaItem(
        accountKey: String,
        item: UnifiedMediaItem,
        rawNewName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val trimmed = rawNewName.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("File name cannot be empty"))
        }
        val existingExt = item.extension
        val finalName = if (existingExt.isNotBlank() && !trimmed.contains('.')) {
            "$trimmed.$existingExt"
        } else {
            trimmed
        }
        syncQueueStore.setRenamedTitle(accountKey, item.id, finalName)
        val idx = sandboxMedia.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            sandboxMedia[idx] = sandboxMedia[idx].copy(originalName = finalName)
            logSandboxActivity("rename", finalName, item.mediaType.key)
            persistSandboxToDisk()
        }
        Result.success(finalName)
    }

    suspend fun updateMediaTags(
        itemId: String,
        newTags: List<String>,
        accountKey: String = "default"
    ) = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val cleaned = newTags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct()
        syncQueueStore.setCustomTags(accountKey, itemId, cleaned)
        val idx = sandboxMedia.indexOfFirst { it.id == itemId }
        if (idx >= 0) {
            sandboxMedia[idx] = sandboxMedia[idx].copy(tags = cleaned)
            persistSandboxToDisk()
        }
    }

    // --- Offline Vault Helpers ---
    suspend fun pinItemOffline(item: UnifiedMediaItem, serverBaseUrl: String): Result<File> =
        offlineVault.pinItemOffline(item, serverBaseUrl)

    suspend fun pinLocalFileOffline(item: UnifiedMediaItem, sourceFile: File): Result<File> =
        offlineVault.pinLocalFileOffline(item, sourceFile)

    suspend fun unpinItemOffline(mediaId: String): Boolean =
        offlineVault.unpinItemOffline(mediaId)

    suspend fun autoCacheViewedItem(item: UnifiedMediaItem, serverBaseUrl: String): File? =
        offlineVault.autoCacheViewedItem(item, serverBaseUrl)

    suspend fun getOfflineVaultStats(): OfflineVaultStats =
        offlineVault.computeStats()

    suspend fun setAutoCacheEnabled(enabled: Boolean): OfflineVaultStats =
        offlineVault.setAutoCacheEnabled(enabled)

    suspend fun clearAutoCache(): Int =
        offlineVault.clearAutoCache()

    suspend fun clearAllOfflineFiles(): Int =
        offlineVault.clearAllOfflineFiles()

    // --- Offline Mutation Queue & Auto-Sync Draining ---
    suspend fun getPendingSyncCount(accountKey: String): Int =
        syncQueueStore.loadPendingQueue(accountKey).size

    suspend fun drainPendingSyncQueue(accountKey: String, isSandbox: Boolean): Int =
        withContext(Dispatchers.IO) {
            if (isSandbox) return@withContext 0
            val pending = syncQueueStore.loadPendingQueue(accountKey)
            if (pending.isEmpty()) return@withContext 0
            var syncedCount = 0
            for (action in pending) {
                val ok = try {
                    when (action.type) {
                        PendingSyncActionType.MOVE_FOLDER -> {
                            val req = FolderMoveFilesRequest(listOf(action.targetId), action.mediaTypeKey)
                            val res = if (action.folderId.isNullOrBlank()) {
                                apiService.removeFilesFromFolder(req)
                            } else {
                                apiService.moveFilesToFolder(action.folderId, req)
                            }
                            res.isSuccessful || res.code() in 400..404
                        }
                        PendingSyncActionType.SOFT_DELETE -> {
                            val res = when (MediaType.fromKey(action.mediaTypeKey)) {
                                MediaType.IMAGE -> apiService.softDeleteImage(action.targetId)
                                MediaType.VIDEO -> apiService.softDeleteVideo(action.targetId)
                                MediaType.DOCUMENT -> apiService.softDeleteDocument(action.targetId)
                            }
                            res.isSuccessful || res.code() == 404
                        }
                        PendingSyncActionType.RESTORE_TRASH -> {
                            val res = when (MediaType.fromKey(action.mediaTypeKey)) {
                                MediaType.IMAGE -> apiService.restoreImage(action.targetId)
                                MediaType.VIDEO -> apiService.restoreVideo(action.targetId)
                                MediaType.DOCUMENT -> apiService.restoreDocument(action.targetId)
                            }
                            res.isSuccessful || res.code() == 404
                        }
                        PendingSyncActionType.PERMANENT_DELETE -> {
                            val res = when (MediaType.fromKey(action.mediaTypeKey)) {
                                MediaType.IMAGE -> apiService.permanentDeleteImage(action.targetId)
                                MediaType.VIDEO -> apiService.permanentDeleteVideo(action.targetId)
                                MediaType.DOCUMENT -> apiService.permanentDeleteDocument(action.targetId)
                            }
                            res.isSuccessful || res.code() == 404
                        }
                        PendingSyncActionType.CREATE_FOLDER -> {
                            val res = apiService.createFolder(
                                FolderUpsertRequest(
                                    name = action.textValue.orEmpty(),
                                    color = action.colorHex ?: "#4285F4"
                                )
                            )
                            res.isSuccessful || res.code() == 400
                        }
                        PendingSyncActionType.UPDATE_FOLDER -> {
                            val res = apiService.updateFolder(
                                action.targetId,
                                FolderUpsertRequest(
                                    name = action.textValue.orEmpty(),
                                    color = action.colorHex ?: "#4285F4"
                                )
                            )
                            res.isSuccessful || res.code() == 404
                        }
                        PendingSyncActionType.DELETE_FOLDER -> {
                            val res = apiService.deleteFolder(action.targetId)
                            res.isSuccessful || res.code() == 404
                        }
                        PendingSyncActionType.RENAME_ITEM,
                        PendingSyncActionType.UPDATE_TAGS,
                        PendingSyncActionType.TOGGLE_FAVORITE -> true
                    }
                } catch (_: Exception) {
                    false
                }
                if (ok) {
                    syncQueueStore.removeAction(accountKey, action.id)
                    syncedCount++
                }
            }
            syncedCount
        }

    // --- 1. Server Ping & Connection Check (Fast Fail) ---
    suspend fun checkServerConnection(customUrl: String? = null): Triple<Boolean, Long, String> =
        withContext(Dispatchers.IO) {
            if (!customUrl.isNullOrBlank()) {
                RetrofitClient.baseUrl = customUrl
                preferences.saveServerUrl(RetrofitClient.baseUrl)
            }
            val start = System.currentTimeMillis()
            try {
                val pingRes = fastPingService.pingBackend()
                val elapsed = System.currentTimeMillis() - start
                if (pingRes.isSuccessful) {
                    return@withContext Triple(true, elapsed, "Cloud sync connected (${elapsed}ms)")
                }
                val testRes = fastPingService.testBackend()
                val elapsedTest = System.currentTimeMillis() - start
                if (testRes.isSuccessful) {
                    return@withContext Triple(true, elapsedTest, "Cloud sync connected (${elapsedTest}ms)")
                }
                Triple(false, elapsedTest, "Server responded with HTTP ${pingRes.code()}")
            } catch (_: Exception) {
                val elapsed = System.currentTimeMillis() - start
                Triple(false, elapsed, "Cloud server is waking up or offline")
            }
        }

    // --- 2. Authentication (Register, Login, Guest Workspace) ---
    suspend fun login(email: String, password: String): Result<UserSession> =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            val normalizedEmail = email.trim().lowercase()
            try {
                val res = apiService.login(LoginRequest(normalizedEmail, password))
                val user = if (res.isSuccessful) res.body() else null
                if (user != null) {
                    val session = UserSession(
                        id = user.id.ifBlank { UUID.randomUUID().toString() },
                        name = user.name.ifBlank { normalizedEmail.substringBefore('@') },
                        email = user.email.ifBlank { normalizedEmail },
                        profilePicture = user.profilePicture,
                        token = user.token?.takeIf { it.isNotBlank() }
                            ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.${UUID.randomUUID()}",
                        isOfflineSandbox = false
                    )
                    RetrofitClient.jwtToken = session.token
                    preferences.saveUserSession(session)
                    preferences.saveVerifiedAccount(session, password)
                    return@withContext Result.success(session)
                } else {
                    val msg = RetrofitClient.parseErrorMessage(
                        res.errorBody(),
                        "Invalid email or password. Please try again."
                    )
                    val isServerSideFailure = res.code() >= 500 ||
                        res.code() == 404 ||
                        msg.contains("server error", ignoreCase = true) ||
                        msg.contains("internal", ignoreCase = true) ||
                        msg.contains("buffering timed out", ignoreCase = true) ||
                        msg.contains("mongo", ignoreCase = true)

                    // Check local account store (and auto-provision if the remote server returned 500 Server Error)
                    val localAuth = preferences.authenticateOrProvisionAccountLocally(
                        email = normalizedEmail,
                        plainPassword = password,
                        allowAutoProvisionOnServerError = isServerSideFailure
                    )
                    if (localAuth.isSuccess) {
                        val session = localAuth.getOrThrow()
                        RetrofitClient.jwtToken = session.token
                        return@withContext Result.success(session)
                    }
                    return@withContext if (isServerSideFailure) {
                        localAuth
                    } else {
                        Result.failure(IllegalStateException(msg))
                    }
                }
            } catch (_: Exception) {
                val localAuth = preferences.authenticateOrProvisionAccountLocally(
                    email = normalizedEmail,
                    plainPassword = password,
                    allowAutoProvisionOnServerError = true
                )
                if (localAuth.isSuccess) {
                    RetrofitClient.jwtToken = localAuth.getOrThrow().token
                }
                return@withContext localAuth
            }
        }

    suspend fun register(name: String, email: String, password: String): Result<UserSession> =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            val normalizedEmail = email.trim().lowercase()
            try {
                val res = apiService.register(
                    RegisterRequest(
                        name = name.trim(),
                        email = normalizedEmail,
                        password = password
                    )
                )
                val user = if (res.isSuccessful) res.body() else null
                if (user != null) {
                    val session = UserSession(
                        id = user.id.ifBlank { UUID.randomUUID().toString() },
                        name = user.name.ifBlank { name.trim() },
                        email = user.email.ifBlank { normalizedEmail },
                        profilePicture = user.profilePicture,
                        token = user.token?.takeIf { it.isNotBlank() }
                            ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.${UUID.randomUUID()}",
                        isOfflineSandbox = false
                    )
                    RetrofitClient.jwtToken = session.token
                    preferences.saveUserSession(session)
                    preferences.saveVerifiedAccount(session, password)
                    return@withContext Result.success(session)
                } else {
                    val msg = RetrofitClient.parseErrorMessage(
                        res.errorBody(),
                        "Could not create account. Email may already be in use."
                    )
                    val isServerSideFailure = res.code() >= 500 ||
                        res.code() == 404 ||
                        msg.contains("server error", ignoreCase = true) ||
                        msg.contains("internal", ignoreCase = true) ||
                        msg.contains("buffering timed out", ignoreCase = true) ||
                        msg.contains("mongo", ignoreCase = true)

                    if (isServerSideFailure) {
                        val localReg = preferences.registerAccountLocally(
                            name = name.trim(),
                            email = normalizedEmail,
                            plainPassword = password
                        )
                        if (localReg.isSuccess) {
                            RetrofitClient.jwtToken = localReg.getOrThrow().token
                        }
                        return@withContext localReg
                    }
                    return@withContext Result.failure(IllegalStateException(msg))
                }
            } catch (_: Exception) {
                val localReg = preferences.registerAccountLocally(
                    name = name.trim(),
                    email = normalizedEmail,
                    plainPassword = password
                )
                if (localReg.isSuccess) {
                    RetrofitClient.jwtToken = localReg.getOrThrow().token
                }
                return@withContext localReg
            }
        }

    suspend fun loginInteractiveSandbox(name: String, email: String): UserSession =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            val cleanEmail = email.trim().lowercase().ifBlank { "akshat890jain@gmail.com" }
            val cleanName = name.trim().ifBlank {
                cleanEmail.substringBefore('@').replaceFirstChar { it.uppercase() }
            }
            val session = UserSession(
                id = "usr_sandbox_owner",
                name = cleanName,
                email = cleanEmail,
                profilePicture = null,
                token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.guest.${UUID.randomUUID()}",
                isOfflineSandbox = true
            )
            RetrofitClient.jwtToken = session.token
            preferences.saveUserSession(session)
            session
        }

    suspend fun logout() {
        RetrofitClient.jwtToken = null
        preferences.saveUserSession(null)
    }

    // --- 3. Fetch Unified Gallery & Trash ---
    suspend fun fetchAllActiveMedia(isSandbox: Boolean): List<UnifiedMediaItem> =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            if (!isSandbox) {
                try {
                    val (imgRes, vidRes, docRes) = coroutineScope {
                        val i = async { apiService.getImages() }
                        val v = async { apiService.getVideos() }
                        val d = async { apiService.getDocuments() }
                        Triple(i.await(), v.await(), d.await())
                    }
                    if (imgRes.isSuccessful || vidRes.isSuccessful || docRes.isSuccessful) {
                        // Hoist directory listing ONCE instead of O(N) disk scans inside toUnified()
                        val localUploadFiles = File(context.filesDir, "uploaded_media")
                            .listFiles()
                            ?.filter { it.isFile && it.length() > 0L }
                            .orEmpty()
                        val images = imgRes.body().orEmpty().map { it.toUnified(MediaType.IMAGE, localUploadFiles = localUploadFiles) }
                        val videos = vidRes.body().orEmpty().map { it.toUnified(MediaType.VIDEO, localUploadFiles = localUploadFiles) }
                        val docs = docRes.body().orEmpty().map { it.toUnified(MediaType.DOCUMENT, localUploadFiles = localUploadFiles) }
                        val remoteItems = (images + videos + docs).filter { !it.isDeleted }
                        if (remoteItems.isNotEmpty()) {
                            val remoteIds = remoteItems.map { it.id }.toSet()
                            val localExtras = sandboxMedia.filter { !it.isDeleted && it.id !in remoteIds }
                            return@withContext (remoteItems + localExtras)
                                .sortedByDescending { it.timestampMillis }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            sandboxMedia.filter { !it.isDeleted }.sortedByDescending { it.timestampMillis }
        }

    suspend fun fetchTrashMedia(isSandbox: Boolean): List<UnifiedMediaItem> =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            if (!isSandbox) {
                try {
                    val (imgRes, vidRes, docRes) = coroutineScope {
                        val i = async { apiService.getTrashedImages() }
                        val v = async { apiService.getTrashedVideos() }
                        val d = async { apiService.getTrashedDocuments() }
                        Triple(i.await(), v.await(), d.await())
                    }
                    if (imgRes.isSuccessful || vidRes.isSuccessful || docRes.isSuccessful) {
                        val localUploadFiles = File(context.filesDir, "uploaded_media")
                            .listFiles()
                            ?.filter { it.isFile && it.length() > 0L }
                            .orEmpty()
                        val images = imgRes.body().orEmpty().map { it.toUnified(MediaType.IMAGE, forceDeleted = true, localUploadFiles = localUploadFiles) }
                        val videos = vidRes.body().orEmpty().map { it.toUnified(MediaType.VIDEO, forceDeleted = true, localUploadFiles = localUploadFiles) }
                        val docs = docRes.body().orEmpty().map { it.toUnified(MediaType.DOCUMENT, forceDeleted = true, localUploadFiles = localUploadFiles) }
                        return@withContext (images + videos + docs)
                    }
                } catch (_: Exception) {
                }
            }
            sandboxMedia.filter { it.isDeleted }
        }

    // --- 4. Inspect & Upload Multi-Format Media ---
    fun inspectUriForUpload(uri: Uri): Result<UploadCandidate> {
        val resolver = context.contentResolver
        var displayName = "file_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }

        val mimeType = resolver.getType(uri) ?: guessMimeFromName(displayName)
        val maxBytes = 50L * 1024L * 1024L
        if (sizeBytes > maxBytes) {
            return Result.failure(
                IllegalArgumentException("\"$displayName\" exceeds the 50 MB file size limit.")
            )
        }

        val mediaType = when {
            mimeType.startsWith("image/") -> MediaType.IMAGE
            mimeType.startsWith("video/") -> MediaType.VIDEO
            else -> MediaType.DOCUMENT
        }

        return Result.success(
            UploadCandidate(
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes.coerceAtLeast(1024L),
                mediaType = mediaType
            )
        )
    }

    suspend fun uploadBatch(
        candidates: List<UploadCandidate>,
        targetFolderId: String?,
        isSandbox: Boolean,
        onFileProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val uploadDir = File(context.filesDir, "uploaded_media").apply { mkdirs() }
        val localCopies = mutableListOf<Pair<UploadCandidate, File>>()
        val candidateIndexByFile = mutableMapOf<File, Int>()

        candidates.forEachIndexed { idx, candidate ->
            onFileProgress(idx, 5)
            val safeName = "${System.currentTimeMillis()}-${candidate.displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}"
            val destFile = File(uploadDir, safeName)
            context.contentResolver.openInputStream(candidate.uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists()) {
                localCopies.add(candidate to destFile)
                candidateIndexByFile[destFile] = idx
                onFileProgress(idx, 18)
            }
        }

        if (!isSandbox && localCopies.isNotEmpty()) {
            try {
                fun buildTrackedPart(fieldName: String, c: UploadCandidate, file: File): MultipartBody.Part {
                    val idx = candidateIndexByFile[file] ?: 0
                    val body = UploadProgressRequestBody(
                        file = file,
                        contentType = c.mimeType.toMediaTypeOrNull(),
                        onProgress = { pct ->
                            val scaled = 20 + (pct * 0.78f).toInt()
                            onFileProgress(idx, scaled.coerceIn(20, 98))
                        }
                    )
                    return MultipartBody.Part.createFormData(fieldName, c.displayName, body)
                }

                val imageCopies = localCopies.filter { it.first.mediaType == MediaType.IMAGE }
                val videoCopies = localCopies.filter { it.first.mediaType == MediaType.VIDEO }
                val docCopies = localCopies.filter { it.first.mediaType == MediaType.DOCUMENT }

                val imageParts = imageCopies.map { (c, file) -> buildTrackedPart("images", c, file) }
                val videoParts = videoCopies.map { (c, file) -> buildTrackedPart("videos", c, file) }
                val docParts = docCopies.map { (c, file) -> buildTrackedPart("documents", c, file) }

                var anyRemoteSuccess = false

                if (imageParts.isNotEmpty()) {
                    val r = if (imageParts.size == 1) {
                        val singleFile = imageCopies.first()
                        val singlePart = buildTrackedPart("image", singleFile.first, singleFile.second)
                        val singleRes = apiService.uploadSingleImage(singlePart)
                        if (singleRes.isSuccessful) singleRes else apiService.uploadMultipleImages(imageParts)
                    } else {
                        apiService.uploadMultipleImages(imageParts)
                    }
                    if (r.isSuccessful) {
                        anyRemoteSuccess = true
                        val ids = extractIdsFromUploadResponse(r.body()?.string())
                        autoPinUploadedCopies(ids, imageCopies, targetFolderId)
                        if (!targetFolderId.isNullOrBlank() && ids.isNotEmpty()) {
                            apiService.moveFilesToFolder(
                                targetFolderId,
                                FolderMoveFilesRequest(ids, "image")
                            )
                        }
                        imageCopies.forEach { (_, f) -> candidateIndexByFile[f]?.let { onFileProgress(it, 100) } }
                    }
                }
                if (videoParts.isNotEmpty()) {
                    val r = apiService.uploadMultipleVideos(videoParts)
                    if (r.isSuccessful) {
                        anyRemoteSuccess = true
                        val ids = extractIdsFromUploadResponse(r.body()?.string())
                        autoPinUploadedCopies(ids, videoCopies, targetFolderId)
                        if (!targetFolderId.isNullOrBlank() && ids.isNotEmpty()) {
                            apiService.moveFilesToFolder(
                                targetFolderId,
                                FolderMoveFilesRequest(ids, "video")
                            )
                        }
                        videoCopies.forEach { (_, f) -> candidateIndexByFile[f]?.let { onFileProgress(it, 100) } }
                    }
                }
                if (docParts.isNotEmpty()) {
                    val r = apiService.uploadMultipleDocuments(docParts)
                    if (r.isSuccessful) {
                        anyRemoteSuccess = true
                        val ids = extractIdsFromUploadResponse(r.body()?.string())
                        autoPinUploadedCopies(ids, docCopies, targetFolderId)
                        if (!targetFolderId.isNullOrBlank() && ids.isNotEmpty()) {
                            apiService.moveFilesToFolder(
                                targetFolderId,
                                FolderMoveFilesRequest(ids, "document")
                            )
                        }
                        docCopies.forEach { (_, f) -> candidateIndexByFile[f]?.let { onFileProgress(it, 100) } }
                    }
                }
                if (anyRemoteSuccess) {
                    return@withContext Result.success(localCopies.size)
                }
            } catch (_: Exception) {
            }
        }

        for ((candidate, file) in localCopies) {
            val newId = "med_${UUID.randomUUID().toString().take(8)}"
            val ext = candidate.displayName.substringAfterLast('.', "").lowercase()
            val autoTags = buildList {
                add(candidate.mediaType.label.lowercase())
                if (ext.isNotBlank()) add(ext)
                candidate.displayName.substringBeforeLast('.')
                    .split('_', '-', ' ', '.')
                    .map { it.trim().lowercase() }
                    .filter { it.length >= 3 && !it.all { ch -> ch.isDigit() } }
                    .take(3)
                    .forEach { add(it) }
            }.distinct()
            val baseItem = UnifiedMediaItem(
                id = newId,
                filename = file.name,
                originalName = candidate.displayName,
                path = file.absolutePath,
                size = file.length().coerceAtLeast(candidate.sizeBytes),
                folderId = targetFolderId,
                uploadDate = nowIsoString(),
                mediaType = candidate.mediaType,
                duration = if (candidate.mediaType == MediaType.VIDEO) 42.0 else null,
                docType = if (candidate.mediaType == MediaType.DOCUMENT) ext else null,
                localUri = Uri.fromFile(file).toString(),
                aspectRatio = if (candidate.mediaType == MediaType.IMAGE) 1.2f else 1.33f,
                tags = autoTags
            )
            // Auto-pin every newly uploaded file into the protected Offline Vault immediately
            val pinnedFile = offlineVault.pinLocalFileOffline(baseItem, file).getOrNull()
            val item = if (pinnedFile != null) {
                baseItem.copy(
                    offlineState = OfflineAvailabilityState.PINNED,
                    offlineFilePath = pinnedFile.absolutePath
                )
            } else {
                baseItem
            }
            sandboxMedia.add(0, item)
            logSandboxActivity("upload", candidate.displayName, candidate.mediaType.key)
        }
        recomputeSandboxFolderCounts()
        persistSandboxToDisk()
        Result.success(localCopies.size)
    }

    private suspend fun autoPinUploadedCopies(
        returnedServerIds: List<String>,
        uploadedCopies: List<Pair<UploadCandidate, File>>,
        folderId: String?
    ) {
        if (returnedServerIds.isEmpty()) return
        uploadedCopies.forEachIndexed { idx, (candidate, file) ->
            val serverId = returnedServerIds.getOrNull(idx) ?: return@forEachIndexed
            val ext = candidate.displayName.substringAfterLast('.', "").lowercase()
            val placeholderItem = UnifiedMediaItem(
                id = serverId,
                filename = file.name,
                originalName = candidate.displayName,
                path = file.absolutePath,
                size = file.length().coerceAtLeast(candidate.sizeBytes),
                folderId = folderId,
                uploadDate = nowIsoString(),
                mediaType = candidate.mediaType,
                docType = if (candidate.mediaType == MediaType.DOCUMENT) ext else null,
                localUri = Uri.fromFile(file).toString()
            )
            offlineVault.pinLocalFileOffline(placeholderItem, file)
        }
    }

    /**
     * Re-links a file that is missing on the server disk (HTTP 404) using a replacement picked
     * from the user's device via Android's zero-permission Photo/Document Picker.
     * Immediately pins the restored file in the Offline Vault and re-uploads it to the cloud
     * (preserving folder placement and cleaning up the old unreachable server record if replaced).
     */
    suspend fun relinkMissingMediaFromDevice(
        missingItem: UnifiedMediaItem,
        pickedUri: Uri,
        isSandbox: Boolean,
        accountKey: String = "default"
    ): Result<UnifiedMediaItem> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val inspected = inspectUriForUpload(pickedUri).getOrElse { err ->
            return@withContext Result.failure(err)
        }

        val uploadDir = File(context.filesDir, "uploaded_media").apply { mkdirs() }
        val targetDisplayName = missingItem.originalName.ifBlank { inspected.displayName }
        val safeName = "${System.currentTimeMillis()}-${targetDisplayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}"
        val localCopy = File(uploadDir, safeName)

        val copied = runCatching {
            context.contentResolver.openInputStream(pickedUri)?.use { input ->
                FileOutputStream(localCopy).use { output -> input.copyTo(output) }
            }
            localCopy.exists() && localCopy.length() > 0L
        }.getOrDefault(false)

        if (!copied) {
            return@withContext Result.failure(IllegalStateException("Could not read selected file from device"))
        }

        // Step 1: Immediately pin locally under the existing item ID so the user gets instant offline recovery
        val immediatePinnedFile = offlineVault.pinLocalFileOffline(missingItem, localCopy).getOrNull()
        var restoredItem = missingItem.copy(
            size = localCopy.length(),
            localUri = Uri.fromFile(localCopy).toString(),
            offlineState = if (immediatePinnedFile != null) OfflineAvailabilityState.PINNED else missingItem.offlineState,
            offlineFilePath = immediatePinnedFile?.absolutePath ?: localCopy.absolutePath
        )

        // Step 2: Re-upload the restored bytes back to the cloud server so share links & other devices recover too
        if (!isSandbox) {
            try {
                val uploadRes = when (missingItem.mediaType) {
                    MediaType.IMAGE -> {
                        val singlePart = MultipartBody.Part.createFormData(
                            "image",
                            targetDisplayName,
                            localCopy.asRequestBody(inspected.mimeType.toMediaTypeOrNull())
                        )
                        val singleRes = apiService.uploadSingleImage(singlePart)
                        if (singleRes.isSuccessful) singleRes else {
                            val multiPart = MultipartBody.Part.createFormData(
                                "images",
                                targetDisplayName,
                                localCopy.asRequestBody(inspected.mimeType.toMediaTypeOrNull())
                            )
                            apiService.uploadMultipleImages(listOf(multiPart))
                        }
                    }
                    MediaType.VIDEO -> {
                        val part = MultipartBody.Part.createFormData(
                            "videos",
                            targetDisplayName,
                            localCopy.asRequestBody(inspected.mimeType.toMediaTypeOrNull())
                        )
                        apiService.uploadMultipleVideos(listOf(part))
                    }
                    MediaType.DOCUMENT -> {
                        val part = MultipartBody.Part.createFormData(
                            "documents",
                            targetDisplayName,
                            localCopy.asRequestBody(inspected.mimeType.toMediaTypeOrNull())
                        )
                        apiService.uploadMultipleDocuments(listOf(part))
                    }
                }

                if (uploadRes.isSuccessful) {
                    val newServerId = extractIdsFromUploadResponse(uploadRes.body()?.string()).firstOrNull()
                    if (!newServerId.isNullOrBlank() && newServerId != missingItem.id) {
                        if (!missingItem.folderId.isNullOrBlank()) {
                            runCatching {
                                apiService.moveFilesToFolder(
                                    missingItem.folderId,
                                    FolderMoveFilesRequest(listOf(newServerId), missingItem.mediaType.key)
                                )
                            }
                        }
                        val newPinnedItem = restoredItem.copy(id = newServerId)
                        val newVaultFile = offlineVault.pinLocalFileOffline(newPinnedItem, localCopy).getOrNull()
                        restoredItem = newPinnedItem.copy(
                            offlineState = OfflineAvailabilityState.PINNED,
                            offlineFilePath = newVaultFile?.absolutePath ?: restoredItem.offlineFilePath
                        )
                        // Remove old broken 404 server entry now that the restored replacement is live
                        removeMissingServerMedia(missingItem, isSandbox = false, accountKey = accountKey)
                    }
                }
            } catch (_: Exception) {
                // Local Offline Vault pin already succeeded even if cloud re-upload failed
            }
        }

        sandboxMedia.removeAll { it.id == missingItem.id || it.id == restoredItem.id }
        sandboxMedia.add(0, restoredItem)
        persistSandboxToDisk()
        Result.success(restoredItem)
    }

    /**
     * Cleanly deletes an unreachable 404 server entry from both the backend and local state.
     */
    suspend fun removeMissingServerMedia(
        item: UnifiedMediaItem,
        isSandbox: Boolean,
        accountKey: String = "default"
    ): Boolean = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        if (!isSandbox && !item.id.startsWith("med_") && !item.id.startsWith("edt_")) {
            runCatching {
                when (item.mediaType) {
                    MediaType.IMAGE -> {
                        val perm = apiService.permanentDeleteImage(item.id)
                        if (!perm.isSuccessful && perm.code() != 404) {
                            apiService.softDeleteImage(item.id)
                            apiService.permanentDeleteImage(item.id)
                        }
                    }
                    MediaType.VIDEO -> {
                        val perm = apiService.permanentDeleteVideo(item.id)
                        if (!perm.isSuccessful && perm.code() != 404) {
                            apiService.softDeleteVideo(item.id)
                            apiService.permanentDeleteVideo(item.id)
                        }
                    }
                    MediaType.DOCUMENT -> {
                        val perm = apiService.permanentDeleteDocument(item.id)
                        if (!perm.isSuccessful && perm.code() != 404) {
                            apiService.softDeleteDocument(item.id)
                            apiService.permanentDeleteDocument(item.id)
                        }
                    }
                }
            }
        }
        offlineVault.unpinItemOffline(item.id)
        sandboxMedia.removeAll { it.id == item.id }
        recomputeSandboxFolderCounts()
        persistSandboxToDisk()
        true
    }

    // --- 5. Save Edited Image Copy from Canvas ImageEditor ---
    suspend fun uploadEditedImageBitmap(
        bitmap: Bitmap,
        originalItem: UnifiedMediaItem,
        isSandbox: Boolean
    ): Result<UnifiedMediaItem> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val editedDir = File(context.filesDir, "edited_images").apply { mkdirs() }
        val baseOriginal = originalItem.originalName.substringBeforeLast('.')
        val editedName = "edited-$baseOriginal.png"
        val file = File(editedDir, "${System.currentTimeMillis()}-$editedName")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        if (!isSandbox) {
            try {
                val part = MultipartBody.Part.createFormData(
                    "image",
                    editedName,
                    file.asRequestBody("image/png".toMediaTypeOrNull())
                )
                val res = apiService.uploadSingleImage(part)
                if (res.isSuccessful) {
                    val serverId = extractIdsFromUploadResponse(res.body()?.string()).firstOrNull()
                        ?: "edt_${UUID.randomUUID().toString().take(8)}"
                    val baseCreated = UnifiedMediaItem(
                        id = serverId,
                        filename = file.name,
                        originalName = editedName,
                        path = file.absolutePath,
                        size = file.length(),
                        folderId = originalItem.folderId,
                        uploadDate = nowIsoString(),
                        mediaType = MediaType.IMAGE,
                        localUri = Uri.fromFile(file).toString(),
                        aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
                    )
                    val pinned = offlineVault.pinLocalFileOffline(baseCreated, file).getOrNull()
                    return@withContext Result.success(
                        if (pinned != null) {
                            baseCreated.copy(
                                offlineState = OfflineAvailabilityState.PINNED,
                                offlineFilePath = pinned.absolutePath
                            )
                        } else {
                            baseCreated
                        }
                    )
                }
            } catch (_: Exception) {
            }
        }

        val baseCreated = UnifiedMediaItem(
            id = "edt_${UUID.randomUUID().toString().take(8)}",
            filename = file.name,
            originalName = editedName,
            path = file.absolutePath,
            size = file.length(),
            folderId = originalItem.folderId,
            uploadDate = nowIsoString(),
            mediaType = MediaType.IMAGE,
            localUri = Uri.fromFile(file).toString(),
            aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
        )
        val pinned = offlineVault.pinLocalFileOffline(baseCreated, file).getOrNull()
        val createdItem = if (pinned != null) {
            baseCreated.copy(
                offlineState = OfflineAvailabilityState.PINNED,
                offlineFilePath = pinned.absolutePath
            )
        } else {
            baseCreated
        }
        sandboxMedia.add(0, createdItem)
        logSandboxActivity("edit_image", editedName, "image")
        recomputeSandboxFolderCounts()
        persistSandboxToDisk()
        Result.success(createdItem)
    }

    // --- 6. Soft Delete, Restore, Permanent Delete & Delete All (Offline Queue Aware) ---
    suspend fun softDeleteMedia(
        items: List<UnifiedMediaItem>,
        isSandbox: Boolean,
        accountKey: String = "default"
    ): Boolean = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        var queuedOffline = false
        if (!isSandbox) {
            coroutineScope {
                items.map { item ->
                    async {
                        val succeeded = try {
                            val res = when (item.mediaType) {
                                MediaType.IMAGE -> apiService.softDeleteImage(item.id)
                                MediaType.VIDEO -> apiService.softDeleteVideo(item.id)
                                MediaType.DOCUMENT -> apiService.softDeleteDocument(item.id)
                            }
                            res.isSuccessful || res.code() == 404
                        } catch (_: Exception) {
                            false
                        }
                        if (!succeeded) {
                            syncQueueStore.enqueueAction(
                                accountKey,
                                PendingSyncAction(
                                    type = PendingSyncActionType.SOFT_DELETE,
                                    targetId = item.id,
                                    mediaTypeKey = item.mediaType.key
                                )
                            )
                            queuedOffline = true
                        }
                    }
                }.forEach { it.await() }
            }
        }
        val targetIds = items.map { it.id }.toSet()
        val now = nowIsoString()
        for (i in sandboxMedia.indices) {
            val current = sandboxMedia[i]
            if (current.id in targetIds) {
                sandboxMedia[i] = current.copy(
                    isDeleted = true,
                    deletedAt = now,
                    folderId = null
                )
                logSandboxActivity("delete", current.originalName, current.mediaType.key)
            }
        }
        recomputeSandboxFolderCounts()
        persistSandboxToDisk()
        queuedOffline
    }

    suspend fun restoreFromTrash(
        item: UnifiedMediaItem,
        isSandbox: Boolean,
        accountKey: String = "default"
    ): Boolean = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        var queuedOffline = false
        if (!isSandbox) {
            val succeeded = try {
                val res = when (item.mediaType) {
                    MediaType.IMAGE -> apiService.restoreImage(item.id)
                    MediaType.VIDEO -> apiService.restoreVideo(item.id)
                    MediaType.DOCUMENT -> apiService.restoreDocument(item.id)
                }
                res.isSuccessful || res.code() == 404
            } catch (_: Exception) {
                false
            }
            if (!succeeded) {
                syncQueueStore.enqueueAction(
                    accountKey,
                    PendingSyncAction(
                        type = PendingSyncActionType.RESTORE_TRASH,
                        targetId = item.id,
                        mediaTypeKey = item.mediaType.key
                    )
                )
                queuedOffline = true
            }
        }
        val index = sandboxMedia.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            sandboxMedia[index] = sandboxMedia[index].copy(
                isDeleted = false,
                deletedAt = null
            )
            logSandboxActivity("restore", item.originalName, item.mediaType.key)
            persistSandboxToDisk()
        }
        queuedOffline
    }

    suspend fun permanentDeleteMedia(
        item: UnifiedMediaItem,
        isSandbox: Boolean,
        accountKey: String = "default"
    ): Boolean = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        var queuedOffline = false
        if (!isSandbox) {
            val succeeded = try {
                val res = when (item.mediaType) {
                    MediaType.IMAGE -> apiService.permanentDeleteImage(item.id)
                    MediaType.VIDEO -> apiService.permanentDeleteVideo(item.id)
                    MediaType.DOCUMENT -> apiService.permanentDeleteDocument(item.id)
                }
                res.isSuccessful || res.code() == 404
            } catch (_: Exception) {
                false
            }
            if (!succeeded) {
                syncQueueStore.enqueueAction(
                    accountKey,
                    PendingSyncAction(
                        type = PendingSyncActionType.PERMANENT_DELETE,
                        targetId = item.id,
                        mediaTypeKey = item.mediaType.key
                    )
                )
                queuedOffline = true
            }
        }
        offlineVault.unpinItemOffline(item.id)
        sandboxMedia.removeAll { it.id == item.id }
        logSandboxActivity("delete", item.originalName, item.mediaType.key, permanent = true)
        recomputeSandboxFolderCounts()
        persistSandboxToDisk()
        queuedOffline
    }

    suspend fun dangerZoneDeleteAllFiles(allActiveAndTrashed: List<UnifiedMediaItem>, isSandbox: Boolean) =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            if (!isSandbox) {
                coroutineScope {
                    allActiveAndTrashed.map { item ->
                        async {
                            try {
                                when (item.mediaType) {
                                    MediaType.IMAGE -> apiService.permanentDeleteImage(item.id)
                                    MediaType.VIDEO -> apiService.permanentDeleteVideo(item.id)
                                    MediaType.DOCUMENT -> apiService.permanentDeleteDocument(item.id)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }.forEach { it.await() }
                }
            }
            sandboxMedia.clear()
            recomputeSandboxFolderCounts()
            logSandboxActivity("delete", "All Media Files", "folder", permanent = true)
            persistSandboxToDisk()
        }

    // --- 7. Streaming ZIP Archive Creation & Download ---
    suspend fun buildOrDownloadZipArchive(
        selectedItems: List<UnifiedMediaItem>,
        isSandbox: Boolean
    ): Result<File> = withContext(Dispatchers.IO) {
        val zipDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outZipFile = File(zipDir, "GalleryHub_Files_${System.currentTimeMillis()}.zip")

        if (!isSandbox && selectedItems.isNotEmpty()) {
            try {
                val imageIds = selectedItems.filter { it.mediaType == MediaType.IMAGE }.map { it.id }
                if (imageIds.isNotEmpty() && selectedItems.size == imageIds.size) {
                    val res = apiService.downloadImagesZip(ZipImagesRequest(imageIds))
                    val zipBody = if (res.isSuccessful) res.body() else null
                    if (zipBody != null) {
                        zipBody.byteStream().use { input ->
                            FileOutputStream(outZipFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (outZipFile.exists() && outZipFile.length() > 0) {
                            return@withContext Result.success(outZipFile)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        try {
            ZipOutputStream(FileOutputStream(outZipFile)).use { zos ->
                zos.setLevel(Deflater.BEST_COMPRESSION)
                val usedNames = mutableSetOf<String>()
                for (item in selectedItems) {
                    var entryName = item.originalName.ifBlank { "${item.id}.dat" }
                    if (!usedNames.add(entryName)) {
                        entryName = "${item.id}_$entryName"
                        usedNames.add(entryName)
                    }
                    zos.putNextEntry(ZipEntry(entryName))
                    val vaultFile = item.offlineFilePath?.takeIf { it.isNotBlank() }?.let { File(it) }
                        ?.takeIf { it.exists() && it.isFile && it.length() > 0L }
                    val localFile = vaultFile ?: File(item.path).takeIf { it.exists() && it.isFile && it.length() > 0L }
                    if (localFile != null) {
                        localFile.inputStream().use { it.copyTo(zos) }
                    } else {
                        val metadataSummary = "GalleryHub File: ${item.originalName}\nType: ${item.mediaType.label}\nSize: ${item.formattedSize}\nUploaded: ${item.uploadDate}\n"
                        zos.write(metadataSummary.toByteArray())
                    }
                    zos.closeEntry()
                }
            }
            Result.success(outZipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 8. Folders & Collaboration ---
    suspend fun fetchFolders(isSandbox: Boolean): Pair<List<FolderItem>, List<FolderItem>> =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            if (!isSandbox) {
                try {
                    val (ownRes, sharedRes) = coroutineScope {
                        val o = async { apiService.getFolders() }
                        val s = async { apiService.getSharedFolders() }
                        o.await() to s.await()
                    }
                    if (ownRes.isSuccessful || sharedRes.isSuccessful) {
                        val myFolders = ownRes.body().orEmpty().map { it.toFolderItem(isSharedDefault = false) }
                        val sharedFolders = sharedRes.body().orEmpty().map { it.toFolderItem(isSharedDefault = true) }
                        return@withContext myFolders to sharedFolders
                    }
                } catch (_: Exception) {
                }
            }
            recomputeSandboxFolderCounts()
            val owned = sandboxFolders.filter { !it.isShared }
            val shared = sandboxFolders.filter { it.isShared }
            owned to shared
        }

    suspend fun createFolder(
        name: String,
        colorHex: String,
        isSandbox: Boolean,
        accountKey: String = "default"
    ): Result<FolderItem> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a folder name"))
        }
        if (!isSandbox) {
            try {
                val res = apiService.createFolder(FolderUpsertRequest(trimmed, colorHex))
                val folderBody = if (res.isSuccessful) res.body() else null
                if (folderBody != null) {
                    return@withContext Result.success(folderBody.toFolderItem(false))
                } else if (res.code() == 400) {
                    val msg = RetrofitClient.parseErrorMessage(
                        res.errorBody(),
                        "A folder with this name already exists"
                    )
                    return@withContext Result.failure(IllegalStateException(msg))
                }
            } catch (_: Exception) {
                // Queue folder creation for auto-sync when reconnected
                syncQueueStore.enqueueAction(
                    accountKey,
                    PendingSyncAction(
                        type = PendingSyncActionType.CREATE_FOLDER,
                        targetId = "fld_${UUID.randomUUID().toString().take(8)}",
                        textValue = trimmed,
                        colorHex = colorHex
                    )
                )
            }
        }

        if (sandboxFolders.any { !it.isShared && it.name.equals(trimmed, ignoreCase = true) }) {
            return@withContext Result.failure(
                IllegalStateException("A folder with this name already exists")
            )
        }
        val created = FolderItem(
            id = "fld_${UUID.randomUUID().toString().take(8)}",
            name = trimmed,
            color = colorHex,
            ownerName = "Me",
            createdAt = nowIsoString()
        )
        sandboxFolders.add(0, created)
        logSandboxActivity("create_folder", trimmed, "folder")
        persistSandboxToDisk()
        Result.success(created)
    }

    suspend fun updateFolder(
        folderId: String,
        name: String,
        colorHex: String,
        isSandbox: Boolean,
        accountKey: String = "default"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val trimmed = name.trim()
        if (!isSandbox) {
            val ok = try {
                apiService.updateFolder(folderId, FolderUpsertRequest(trimmed, colorHex)).isSuccessful
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                syncQueueStore.enqueueAction(
                    accountKey,
                    PendingSyncAction(
                        type = PendingSyncActionType.UPDATE_FOLDER,
                        targetId = folderId,
                        textValue = trimmed,
                        colorHex = colorHex
                    )
                )
            }
        }
        val idx = sandboxFolders.indexOfFirst { it.id == folderId }
        if (idx >= 0) {
            sandboxFolders[idx] = sandboxFolders[idx].copy(name = trimmed, color = colorHex)
            persistSandboxToDisk()
        }
        Result.success(Unit)
    }

    suspend fun deleteFolder(
        folderId: String,
        isSandbox: Boolean,
        accountKey: String = "default"
    ) = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        if (!isSandbox) {
            val ok = try {
                val res = apiService.deleteFolder(folderId)
                res.isSuccessful || res.code() == 404
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                syncQueueStore.enqueueAction(
                    accountKey,
                    PendingSyncAction(
                        type = PendingSyncActionType.DELETE_FOLDER,
                        targetId = folderId
                    )
                )
            }
        }
        val targetFolder = sandboxFolders.firstOrNull { it.id == folderId }
        sandboxFolders.removeAll { it.id == folderId }
        for (i in sandboxMedia.indices) {
            if (sandboxMedia[i].folderId == folderId) {
                sandboxMedia[i] = sandboxMedia[i].copy(folderId = null)
            }
        }
        if (targetFolder != null) {
            logSandboxActivity("delete_folder", targetFolder.name, "folder")
        }
        persistSandboxToDisk()
    }

    suspend fun moveFilesToFolder(
        folderId: String?,
        items: List<UnifiedMediaItem>,
        isSandbox: Boolean,
        accountKey: String = "default"
    ): Boolean = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        var queuedOffline = false
        if (!isSandbox) {
            val byType = items.groupBy { it.mediaType }
            for ((type, group) in byType) {
                val ids = group.map { it.id }
                val succeeded = try {
                    val res = if (folderId == null) {
                        apiService.removeFilesFromFolder(FolderMoveFilesRequest(ids, type.key))
                    } else {
                        apiService.moveFilesToFolder(folderId, FolderMoveFilesRequest(ids, type.key))
                    }
                    res.isSuccessful
                } catch (_: Exception) {
                    false
                }
                if (!succeeded) {
                    group.forEach { item ->
                        syncQueueStore.enqueueAction(
                            accountKey,
                            PendingSyncAction(
                                type = PendingSyncActionType.MOVE_FOLDER,
                                targetId = item.id,
                                mediaTypeKey = item.mediaType.key,
                                folderId = folderId
                            )
                        )
                    }
                    queuedOffline = true
                }
            }
        }
        val targetIds = items.map { it.id }.toSet()
        for (i in sandboxMedia.indices) {
            val current = sandboxMedia[i]
            if (current.id in targetIds) {
                sandboxMedia[i] = current.copy(folderId = folderId)
                logSandboxActivity("move", current.originalName, current.mediaType.key)
            }
        }
        recomputeSandboxFolderCounts()
        persistSandboxToDisk()
        queuedOffline
    }

    suspend fun shareFolderWithCollaborator(
        folderId: String,
        email: String,
        permission: String,
        currentUserEmail: String,
        isSandbox: Boolean
    ): Result<FolderItem> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val normalized = email.trim().lowercase()
        if (normalized.equals(currentUserEmail.trim(), ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("You already own this folder"))
        }
        if (!isSandbox) {
            try {
                val res = apiService.shareFolder(folderId, FolderShareRequest(normalized, permission))
                val folderBody = if (res.isSuccessful) res.body() else null
                if (folderBody != null) {
                    return@withContext Result.success(folderBody.toFolderItem(false))
                } else if (res.code() == 400 || res.code() == 404) {
                    val msg = RetrofitClient.parseErrorMessage(
                        res.errorBody(),
                        "Could not share folder with $normalized"
                    )
                    return@withContext Result.failure(IllegalStateException(msg))
                }
            } catch (_: Exception) {
            }
        }

        val idx = sandboxFolders.indexOfFirst { it.id == folderId }
        if (idx < 0) return@withContext Result.failure(IllegalStateException("Folder not found"))
        val folder = sandboxFolders[idx]
        val existingList = folder.sharedWith.toMutableList()
        val existingIdx = existingList.indexOfFirst { it.email.equals(normalized, ignoreCase = true) }
        val displayName = normalized.substringBefore('@')
            .split('.', '_', '-')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        if (existingIdx >= 0) {
            existingList[existingIdx] = existingList[existingIdx].copy(permission = permission)
        } else {
            existingList.add(
                FolderCollaborator(
                    userId = "usr_${UUID.randomUUID().toString().take(6)}",
                    name = displayName.ifBlank { "Member" },
                    email = normalized,
                    permission = permission,
                    addedAt = nowIsoString()
                )
            )
        }
        val updated = folder.copy(sharedWith = existingList)
        sandboxFolders[idx] = updated
        logSandboxActivity("share", folder.name, "folder")
        persistSandboxToDisk()
        Result.success(updated)
    }

    suspend fun removeFolderCollaborator(
        folderId: String,
        collaboratorUserId: String,
        isSandbox: Boolean
    ): Result<FolderItem> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        if (!isSandbox) {
            try {
                val res = apiService.unshareFolder(folderId, collaboratorUserId)
                val folderBody = if (res.isSuccessful) res.body() else null
                if (folderBody != null) {
                    return@withContext Result.success(folderBody.toFolderItem(false))
                }
            } catch (_: Exception) {
            }
        }
        val idx = sandboxFolders.indexOfFirst { it.id == folderId }
        if (idx < 0) return@withContext Result.failure(IllegalStateException("Folder not found"))
        val folder = sandboxFolders[idx]
        val updated = folder.copy(
            sharedWith = folder.sharedWith.filterNot { it.userId == collaboratorUserId }
        )
        sandboxFolders[idx] = updated
        persistSandboxToDisk()
        Result.success(updated)
    }

    // --- 9. Public File Sharing (/api/share) ---
    suspend fun fetchMyShareLinks(isSandbox: Boolean): List<ShareLinkItem> =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            if (!isSandbox) {
                try {
                    val res = apiService.getMyShareLinks()
                    val shareDtos = if (res.isSuccessful) res.body() else null
                    if (shareDtos != null) {
                        return@withContext shareDtos.map { dto ->
                            ShareLinkItem(
                                id = dto.id.ifBlank { UUID.randomUUID().toString() },
                                fileId = dto.fileId?.toString() ?: "",
                                fileName = dto.fileName ?: dto.originalName ?: "Shared File",
                                fileType = MediaType.fromKey(dto.fileType),
                                token = dto.token,
                                hasPassword = dto.hasPassword == true || dto.password == true || (dto.password is String && dto.password.isNotBlank()),
                                expiresAt = dto.expiresAt,
                                viewCount = dto.viewCount,
                                createdAt = dto.createdAt ?: nowIsoString()
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
            sandboxShareLinks.toList()
        }

    suspend fun createPublicShareLink(
        item: UnifiedMediaItem,
        passwordPlain: String?,
        expiresInDays: Int?,
        isSandbox: Boolean
    ): Result<ShareLinkItem> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val cleanPassword = passwordPlain?.trim()?.takeIf { it.isNotEmpty() }
        if (!isSandbox) {
            try {
                val res = apiService.createShareLink(
                    CreateShareLinkRequest(
                        fileId = item.id,
                        fileType = item.mediaType.key,
                        password = cleanPassword,
                        expiresInDays = expiresInDays
                    )
                )
                val dto = if (res.isSuccessful) res.body() else null
                if (dto != null) {
                    val created = ShareLinkItem(
                        id = dto.id.ifBlank { UUID.randomUUID().toString() },
                        fileId = item.id,
                        fileName = item.originalName,
                        fileType = item.mediaType,
                        token = dto.token.ifBlank { generateHexToken32Bytes() },
                        hasPassword = cleanPassword != null || dto.hasPassword == true,
                        expiresAt = dto.expiresAt,
                        viewCount = dto.viewCount,
                        createdAt = dto.createdAt ?: nowIsoString(),
                        localPasswordPlain = cleanPassword
                    )
                    sandboxShareLinks.add(0, created)
                    return@withContext Result.success(created)
                }
            } catch (_: Exception) {
            }
        }

        val expiresIso = expiresInDays?.let { days ->
            val futureMillis = System.currentTimeMillis() + days * 86_400_000L
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.format(java.util.Date(futureMillis))
        }
        val link = ShareLinkItem(
            id = "shr_${UUID.randomUUID().toString().take(8)}",
            fileId = item.id,
            fileName = item.originalName,
            fileType = item.mediaType,
            token = generateHexToken32Bytes(),
            hasPassword = cleanPassword != null,
            expiresAt = expiresIso,
            viewCount = 0,
            createdAt = nowIsoString(),
            localPasswordPlain = cleanPassword
        )
        sandboxShareLinks.add(0, link)
        logSandboxActivity("share", item.originalName, item.mediaType.key)
        persistSandboxToDisk()
        Result.success(link)
    }

    suspend fun revokeShareLink(linkId: String, isSandbox: Boolean) = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        if (!isSandbox) {
            try {
                apiService.deleteShareLink(linkId)
            } catch (_: Exception) {
            }
        }
        sandboxShareLinks.removeAll { it.id == linkId }
        persistSandboxToDisk()
    }

    suspend fun inspectAndUnlockPublicToken(
        tokenInput: String,
        passwordInput: String?
    ): Result<Pair<ShareLinkItem, UnifiedMediaItem?>> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        val parsed = com.example.util.ShareLinkCodec.parseShareInput(tokenInput)
        val cleanToken = parsed?.rawToken ?: com.example.util.ShareLinkCodec.unmaskToken(
            tokenInput.trim()
                .substringAfterLast("/api/share/")
                .substringAfterLast("/share/")
                .substringAfterLast("/s/")
                .substringBefore("?")
                .trim()
        )
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid or empty share link."))
        }
        val effectivePassword = passwordInput?.takeIf { it.isNotBlank() } ?: parsed?.embeddedPassword

        // 1. Check local sandbox links first (for offline/sandbox links)
        val localIdx = sandboxShareLinks.indexOfFirst { it.token.equals(cleanToken, ignoreCase = true) }
        if (localIdx >= 0) {
            val link = sandboxShareLinks[localIdx]
            if (link.isExpired) {
                return@withContext Result.failure(IllegalStateException("This shared link has expired."))
            }
            if (link.hasPassword && !link.localPasswordPlain.isNullOrEmpty()) {
                if (effectivePassword.isNullOrEmpty()) {
                    return@withContext Result.failure(SecurityException("PASSWORD_REQUIRED"))
                }
                if (effectivePassword != link.localPasswordPlain) {
                    return@withContext Result.failure(IllegalArgumentException("401: Incorrect password."))
                }
            }
            val updated = link.copy(viewCount = link.viewCount + 1)
            sandboxShareLinks[localIdx] = updated
            persistSandboxToDisk()
            val media = sandboxMedia.firstOrNull { it.id == link.fileId }
            if (media != null) {
                return@withContext Result.success(updated to media)
            }
        }

        // 2. Fetch directly from the target backend host (works even when signed out!)
        val effectiveBase = (parsed?.hostBaseUrl ?: RetrofitClient.baseUrl).trimEnd('/')
        try {
            if (!effectivePassword.isNullOrBlank()) {
                val verifyBody = okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    JSONObject().put("password", effectivePassword).toString()
                )
                val verifyReq = okhttp3.Request.Builder()
                    .url("$effectiveBase/api/share/$cleanToken/verify")
                    .post(verifyBody)
                    .build()
                RetrofitClient.okHttpClient.newCall(verifyReq).execute().use { verifyRes ->
                    if (!verifyRes.isSuccessful && verifyRes.code == 401) {
                        return@withContext Result.failure(
                            IllegalArgumentException("401: Incorrect password for this link.")
                        )
                    }
                }
            }

            val streamUrl = if (!effectivePassword.isNullOrBlank()) {
                val encodedPw = java.net.URLEncoder.encode(effectivePassword, "UTF-8")
                "$effectiveBase/api/share/$cleanToken?password=$encodedPw"
            } else {
                "$effectiveBase/api/share/$cleanToken"
            }

            val streamReq = okhttp3.Request.Builder()
                .url(streamUrl)
                .get()
                .build()

            RetrofitClient.okHttpClient.newCall(streamReq).execute().use { response ->
                when (response.code) {
                    in 200..299 -> {
                        val contentType = response.header("Content-Type").orEmpty().lowercase()
                        val contentDisposition = response.header("Content-Disposition").orEmpty()
                        val body = response.body
                            ?: return@withContext Result.failure(IllegalStateException("Shared file response was empty."))

                        if (contentType.contains("application/json")) {
                            val rawJson = body.string()
                            return@withContext parsePublicShareJsonResponse(
                                rawJson = rawJson,
                                cleanToken = cleanToken,
                                effectiveBase = effectiveBase,
                                effectivePassword = effectivePassword
                            )
                        }

                        if (contentType.contains("text/html")) {
                            val htmlBody = body.string()
                            if (htmlBody.contains("password", ignoreCase = true) &&
                                (htmlBody.contains("required", ignoreCase = true) || htmlBody.contains("protected", ignoreCase = true))
                            ) {
                                return@withContext Result.failure(SecurityException("PASSWORD_REQUIRED"))
                            }
                            val remoteLink = ShareLinkItem(
                                id = "remote_$cleanToken",
                                fileId = "shared_${cleanToken.take(12)}",
                                fileName = "Shared Web Link",
                                fileType = MediaType.DOCUMENT,
                                token = cleanToken,
                                hasPassword = !effectivePassword.isNullOrBlank(),
                                viewCount = 1,
                                createdAt = nowIsoString()
                            )
                            return@withContext Result.success(remoteLink to null)
                        }

                        // Binary file stream (image / video / pdf / document)
                        val extractedName = extractFilenameFromDisposition(contentDisposition)
                            ?: "shared_${cleanToken.take(8)}.${extensionFromMimeType(contentType)}"
                        val mediaType = mediaTypeFromMimeAndName(contentType, extractedName)
                        val shareCacheDir = File(context.cacheDir, "shared_links").apply { mkdirs() }
                        val safeName = extractedName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                        val cachedFile = File(shareCacheDir, "share_${cleanToken.take(12)}_$safeName")

                        body.byteStream().use { input ->
                            FileOutputStream(cachedFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        val ext = extractedName.substringAfterLast('.', "").lowercase()
                        val mediaItem = UnifiedMediaItem(
                            id = "shared_${cleanToken.take(16)}",
                            filename = safeName,
                            originalName = extractedName,
                            path = cachedFile.absolutePath,
                            size = cachedFile.length().coerceAtLeast(1L),
                            uploadDate = nowIsoString(),
                            mediaType = mediaType,
                            docType = if (mediaType == MediaType.DOCUMENT) ext else null,
                            localUri = Uri.fromFile(cachedFile).toString(),
                            tags = listOfNotNull("shared", mediaType.label.lowercase(), ext.takeIf { it.isNotBlank() }).distinct()
                        )
                        val remoteLink = ShareLinkItem(
                            id = "remote_$cleanToken",
                            fileId = mediaItem.id,
                            fileName = extractedName,
                            fileType = mediaType,
                            token = cleanToken,
                            hasPassword = !effectivePassword.isNullOrBlank(),
                            viewCount = 1,
                            createdAt = nowIsoString()
                        )
                        return@withContext Result.success(remoteLink to mediaItem)
                    }
                    401, 403 -> {
                        if (effectivePassword.isNullOrBlank()) {
                            return@withContext Result.failure(SecurityException("PASSWORD_REQUIRED"))
                        } else {
                            return@withContext Result.failure(
                                IllegalArgumentException("401: Incorrect password for this link.")
                            )
                        }
                    }
                    410 -> return@withContext Result.failure(IllegalStateException("This shared link has expired."))
                    404 -> return@withContext Result.failure(
                        IllegalStateException("Shared file not found or this link was revoked (HTTP 404).")
                    )
                    else -> return@withContext Result.failure(
                        IllegalStateException("Unable to open shared link (HTTP ${response.code}).")
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalStateException("Could not reach the server to open this link. ${e.localizedMessage ?: ""}".trim())
            )
        }
    }

    private fun parsePublicShareJsonResponse(
        rawJson: String,
        cleanToken: String,
        effectiveBase: String,
        effectivePassword: String?
    ): Result<Pair<ShareLinkItem, UnifiedMediaItem?>> {
        val root = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return Result.failure(IllegalStateException("Unexpected response from share server."))

        if (root.optBoolean("requiresPassword", false) ||
            root.optBoolean("passwordRequired", false) ||
            (root.optBoolean("hasPassword", false) && root.optJSONObject("file") == null && effectivePassword.isNullOrBlank())
        ) {
            return Result.failure(SecurityException("PASSWORD_REQUIRED"))
        }

        val fileObj = root.optJSONObject("file") ?: root.optJSONObject("data") ?: root
        val rawName = fileObj.optString("originalName")
            .ifBlank { fileObj.optString("fileName") }
            .ifBlank { fileObj.optString("filename") }
            .ifBlank { root.optString("fileName") }
            .ifBlank { "Shared File" }
        val rawPath = fileObj.optString("path")
            .ifBlank { fileObj.optString("url") }
            .ifBlank { fileObj.optString("downloadUrl") }
        val rawFilename = fileObj.optString("filename").ifBlank { rawName }
        val fileTypeStr = root.optString("fileType")
            .ifBlank { fileObj.optString("fileType") }
            .ifBlank { fileObj.optString("mediaType") }
        val mediaType = if (fileTypeStr.isNotBlank()) {
            MediaType.fromKey(fileTypeStr)
        } else {
            mediaTypeFromMimeAndName(fileObj.optString("mimeType"), rawName)
        }
        val size = fileObj.optLong("size", 0L)
        val viewCount = root.optInt("viewCount", 1)
        val expiresAt = root.optString("expiresAt").takeIf { it.isNotBlank() && it != "null" }
        val ext = rawName.substringAfterLast('.', "").lowercase()

        var mediaItem = UnifiedMediaItem(
            id = fileObj.optString("_id").ifBlank { "shared_${cleanToken.take(16)}" },
            filename = rawFilename,
            originalName = rawName,
            path = rawPath.ifBlank { rawFilename },
            size = size,
            uploadDate = fileObj.optString("uploadDate")
                .ifBlank { fileObj.optString("createdAt") }
                .ifBlank { nowIsoString() },
            mediaType = mediaType,
            thumbnail = fileObj.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" },
            duration = fileObj.optDouble("duration", 0.0).takeIf { it > 0.0 },
            docType = if (mediaType == MediaType.DOCUMENT) ext else null,
            tags = listOfNotNull("shared", mediaType.label.lowercase(), ext.takeIf { it.isNotBlank() }).distinct()
        )

        // Opportunistically download file into shared_links cache so PDF/Video/Offline/Export work immediately
        val shareCacheDir = File(context.cacheDir, "shared_links").apply { mkdirs() }
        val safeName = rawName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val cachedFile = File(shareCacheDir, "share_${cleanToken.take(12)}_$safeName")
        val candidates = mediaItem.resolveRemoteCandidateUrls(effectiveBase)
        for (url in candidates) {
            val downloaded = runCatching {
                val req = okhttp3.Request.Builder().url(url).get().build()
                RetrofitClient.okHttpClient.newCall(req).execute().use { r ->
                    val ct = r.header("Content-Type").orEmpty().lowercase()
                    val responseBody = r.body
                    if (r.isSuccessful && !ct.contains("text/html") && responseBody != null) {
                        responseBody.byteStream().use { input ->
                            FileOutputStream(cachedFile).use { output -> input.copyTo(output) }
                        }
                        cachedFile.exists() && cachedFile.length() > 0L
                    } else false
                }
            }.getOrDefault(false)
            if (downloaded) {
                mediaItem = mediaItem.copy(
                    localUri = Uri.fromFile(cachedFile).toString(),
                    size = if (mediaItem.size <= 0L) cachedFile.length() else mediaItem.size
                )
                break
            }
        }

        val link = ShareLinkItem(
            id = root.optString("_id").ifBlank { "remote_$cleanToken" },
            fileId = mediaItem.id,
            fileName = rawName,
            fileType = mediaType,
            token = cleanToken,
            hasPassword = root.optBoolean("hasPassword", !effectivePassword.isNullOrBlank()),
            expiresAt = expiresAt,
            viewCount = viewCount,
            createdAt = root.optString("createdAt").ifBlank { nowIsoString() }
        )
        return Result.success(link to mediaItem)
    }

    private fun extractFilenameFromDisposition(disposition: String): String? {
        if (disposition.isBlank()) return null
        val utf8Match = Regex("""filename\*\s*=\s*(?:UTF-8''|utf-8'')([^;\r\n]+)""").find(disposition)
        if (utf8Match != null) {
            val raw = utf8Match.groupValues[1].trim().trim('"')
            return runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                .takeIf { it.isNotBlank() }
        }
        val plainMatch = Regex("""filename\s*=\s*"?([^";\r\n]+)"?""").find(disposition)
        return plainMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extensionFromMimeType(contentType: String): String {
        val base = contentType.substringBefore(';').trim().lowercase()
        return when (base) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/csv" -> "csv"
            "application/zip" -> "zip"
            else -> "bin"
        }
    }

    private fun mediaTypeFromMimeAndName(contentType: String, fileName: String): MediaType {
        val ct = contentType.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ct.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic") -> MediaType.IMAGE
            ct.startsWith("video/") || ext in setOf("mp4", "mov", "mkv", "webm", "avi", "m4v") -> MediaType.VIDEO
            else -> MediaType.DOCUMENT
        }
    }

    // --- 10. Activity Feed & AI Assistant ---
    suspend fun fetchActivities(page: Int = 1, isSandbox: Boolean): List<ActivityItem> =
        withContext(Dispatchers.IO) {
            ensureSandboxInitialized()
            if (!isSandbox) {
                try {
                    val res = apiService.getActivities(page = page, limit = 25)
                    val activityList = if (res.isSuccessful) res.body()?.activities else null
                    if (activityList != null) {
                        return@withContext activityList.map { dto ->
                            ActivityItem(
                                id = dto.id.ifBlank { UUID.randomUUID().toString() },
                                action = dto.action,
                                targetName = dto.targetName,
                                targetType = dto.targetType,
                                permanent = dto.metadata?.get("permanent") == true,
                                createdAt = dto.createdAt ?: nowIsoString()
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
            sandboxActivities.toList()
        }

    suspend fun askChatbot(
        message: String,
        mediaCount: Int,
        folderCount: Int,
        usedBytes: Long,
        isSandbox: Boolean
    ): Triple<String, List<String>, Boolean> = withContext(Dispatchers.IO) {
        if (!isSandbox) {
            try {
                val res = apiService.sendChatbotMessage(ChatbotPromptRequest(message))
                val body = if (res.isSuccessful) res.body() else null
                val responseText = body?.response
                if (body != null && responseText != null) {
                    return@withContext Triple(
                        responseText,
                        body.suggestions ?: defaultChatbotSuggestions(),
                        body.isAI ?: true
                    )
                }
            } catch (_: Exception) {
            }
        }
        val lower = message.lowercase()
        val reply = when {
            "upload" in lower || "size" in lower || "type" in lower ->
                "Tap the '+ Upload' button at the bottom right to add photos, videos, PDFs, or documents up to 50 MB each. If you're viewing a folder, new uploads are automatically placed inside that folder!"
            "zip" in lower || "multiple" in lower || "bulk" in lower || "select" in lower ->
                "Long-press any file (or tap the checklist icon at the top) to select multiple items at once. Then tap 'ZIP' in the bottom bar to download or share them as a single compressed archive."
            "storage" in lower || "quota" in lower || "space" in lower ->
                "You currently have $mediaCount files across $folderCount folders, using ${com.example.data.model.formatBytes(usedBytes)} of your 500 MB cloud storage."
            "share" in lower || "link" in lower || "password" in lower || "collaborat" in lower ->
                "You can share any individual file via a link (with an optional password and expiration date) or invite friends and teammates to collaborate on entire folders in the Folders tab."
            "edit" in lower || "crop" in lower || "filter" in lower ->
                "Tap any photo to open it full-screen, then tap 'Edit Photo' to crop, rotate, flip, or apply studio filters like Vivid, Warm, Vintage, and B&W. Saving creates a new copy so your original photo stays untouched."
            "trash" in lower || "delete" in lower || "restore" in lower ->
                "Deleted files stay in your Trash for 30 days before they are permanently removed. You can restore them anytime from the Trash tab."
            else ->
                "Hi! I'm your GalleryHub Assistant. You have $mediaCount files across $folderCount folders (${com.example.data.model.formatBytes(usedBytes)} of 500 MB used). How can I help you organize, edit, or share your media today?"
        }
        Triple(reply, defaultChatbotSuggestions(), false)
    }

    // --- 11. Profile & Password Updates ---
    suspend fun updateProfile(
        currentSession: UserSession,
        newName: String,
        newEmail: String,
        currentPassword: String?,
        newPassword: String?
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        if (!currentSession.isOfflineSandbox) {
            try {
                val res = apiService.updateProfile(
                    ProfileUpdateRequest(
                        name = newName.trim(),
                        email = newEmail.trim().lowercase(),
                        currentPassword = currentPassword?.takeIf { it.isNotBlank() },
                        newPassword = newPassword?.takeIf { it.isNotBlank() }
                    )
                )
                val body = if (res.isSuccessful) res.body() else null
                if (body != null) {
                    val updated = currentSession.copy(
                        name = body.name.ifBlank { newName.trim() },
                        email = body.email.ifBlank { newEmail.trim().lowercase() },
                        profilePicture = body.profilePicture ?: currentSession.profilePicture
                    )
                    preferences.saveUserSession(updated)
                    return@withContext Result.success(updated)
                } else {
                    val msg = RetrofitClient.parseErrorMessage(
                        res.errorBody(),
                        "Could not update your profile."
                    )
                    return@withContext Result.failure(IllegalStateException(msg))
                }
            } catch (_: Exception) {
            }
        }
        val updated = currentSession.copy(
            name = newName.trim(),
            email = newEmail.trim().lowercase()
        )
        preferences.saveUserSession(updated)
        Result.success(updated)
    }

    suspend fun updateProfileAvatar(
        currentSession: UserSession,
        avatarUri: Uri
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
        val destFile = File(avatarDir, "profile-${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(avatarUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        if (!currentSession.isOfflineSandbox && destFile.exists()) {
            try {
                val part = MultipartBody.Part.createFormData(
                    "profilePicture",
                    destFile.name,
                    destFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                val res = apiService.uploadProfilePicture(part)
                val avatarBody = if (res.isSuccessful) res.body() else null
                if (avatarBody != null) {
                    val updated = currentSession.copy(
                        profilePicture = avatarBody.profilePicture ?: Uri.fromFile(destFile).toString()
                    )
                    preferences.saveUserSession(updated)
                    return@withContext Result.success(updated)
                }
            } catch (_: Exception) {
            }
        }
        val updated = currentSession.copy(
            profilePicture = Uri.fromFile(destFile).toString()
        )
        preferences.saveUserSession(updated)
        Result.success(updated)
    }

    private fun MediaItem.toUnified(
        mediaType: MediaType,
        forceDeleted: Boolean = false,
        localUploadFiles: List<File> = emptyList()
    ): UnifiedMediaItem {
        val resolvedName = originalName.ifBlank { filename.ifBlank { "Untitled" } }
        val ext = type?.removePrefix(".") ?: resolvedName.substringAfterLast('.', "").lowercase()
        val resolvedTags = tags?.filter { it.isNotBlank() }?.ifEmpty { null } ?: buildList {
            add(mediaType.label.lowercase())
            if (ext.isNotBlank()) add(ext)
            resolvedName.substringBeforeLast('.')
                .split('_', '-', ' ', '.')
                .map { it.trim().lowercase() }
                .filter { it.length >= 3 && !it.all { ch -> ch.isDigit() } }
                .take(3)
                .forEach { add(it) }
        }.distinct()

        // Match against pre-hoisted local uploaded_media files (O(1) directory scan per fetch)
        val sanitizedOriginal = resolvedName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val matchedLocalFile = localUploadFiles.firstOrNull { f ->
            f.name.equals(resolvedName, ignoreCase = true) ||
                f.name.equals(filename, ignoreCase = true) ||
                f.name.endsWith("-$sanitizedOriginal", ignoreCase = true) ||
                (size > 0L && f.length() == size && f.extension.equals(ext, ignoreCase = true))
        }

        return UnifiedMediaItem(
            id = id.ifBlank { UUID.randomUUID().toString() },
            filename = filename.ifBlank { resolvedName },
            originalName = resolvedName,
            path = resolveBestRemotePath(),
            size = size,
            folderId = extractFolderId(),
            isDeleted = forceDeleted || isDeleted,
            deletedAt = deletedAt,
            uploadDate = uploadDate ?: createdAt ?: nowIsoString(),
            mediaType = mediaType,
            thumbnail = thumbnail,
            duration = duration,
            docType = type,
            localUri = matchedLocalFile?.let { Uri.fromFile(it).toString() },
            tags = resolvedTags
        )
    }

    private fun Folder.toFolderItem(isSharedDefault: Boolean): FolderItem {
        var ownerIdStr = ""
        var ownerNameStr = "Owner"
        var ownerEmailStr = ""
        when (val u = user) {
            is String -> ownerIdStr = u
            is Map<*, *> -> {
                ownerIdStr = (u["_id"] ?: u["id"])?.toString().orEmpty()
                ownerNameStr = u["name"]?.toString() ?: "Owner"
                ownerEmailStr = u["email"]?.toString().orEmpty()
            }
        }

        val roster = sharedWith.mapNotNull { entry ->
            val uObj = entry["user"]
            var uId = ""
            var uName = "Collaborator"
            var uEmail = ""
            when (uObj) {
                is String -> uId = uObj
                is Map<*, *> -> {
                    uId = (uObj["_id"] ?: uObj["id"])?.toString().orEmpty()
                    uName = uObj["name"]?.toString() ?: "Collaborator"
                    uEmail = uObj["email"]?.toString().orEmpty()
                }
            }
            if (uId.isBlank() && uEmail.isBlank()) null
            else FolderCollaborator(
                userId = uId.ifBlank { UUID.randomUUID().toString() },
                name = uName,
                email = uEmail,
                permission = entry["permission"]?.toString() ?: "view",
                addedAt = entry["addedAt"]?.toString()
            )
        }

        return FolderItem(
            id = id.ifBlank { UUID.randomUUID().toString() },
            name = name.ifBlank { "Untitled Folder" },
            color = color.ifBlank { "#4285F4" },
            ownerId = ownerIdStr,
            ownerName = ownerNameStr,
            ownerEmail = ownerEmailStr,
            fileCount = if (fileCount > 0) fileCount else (imageCount + videoCount + docCount),
            imageCount = imageCount,
            videoCount = videoCount,
            docCount = docCount,
            sharedWith = roster,
            isShared = isShared || isSharedDefault,
            myPermission = myPermission ?: "edit",
            createdAt = createdAt ?: nowIsoString()
        )
    }

    private fun recomputeSandboxFolderCounts() {
        val active = sandboxMedia.filter { !it.isDeleted }
        for (i in sandboxFolders.indices) {
            val f = sandboxFolders[i]
            if (f.isShared) continue
            val inFolder = active.filter { it.folderId == f.id }
            val imgC = inFolder.count { it.mediaType == MediaType.IMAGE }
            val vidC = inFolder.count { it.mediaType == MediaType.VIDEO }
            val docC = inFolder.count { it.mediaType == MediaType.DOCUMENT }
            sandboxFolders[i] = f.copy(
                fileCount = inFolder.size,
                imageCount = imgC,
                videoCount = vidC,
                docCount = docC
            )
        }
    }

    private fun logSandboxActivity(
        action: String,
        targetName: String,
        targetType: String,
        permanent: Boolean = false
    ) {
        sandboxActivities.add(
            0,
            ActivityItem(
                id = "act_${UUID.randomUUID().toString().take(8)}",
                action = action,
                targetName = targetName,
                targetType = targetType,
                permanent = permanent,
                createdAt = nowIsoString()
            )
        )
    }

    private fun generateHexToken32Bytes(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun extractIdsFromUploadResponse(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) return emptyList()
        val ids = mutableListOf<String>()
        try {
            val trimmed = rawJson.trim()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    val id = obj?.optString("_id")
                    if (!id.isNullOrBlank()) ids.add(id)
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val singleId = root.optString("_id")
                if (singleId.isNotBlank()) ids.add(singleId)
                for (key in listOf("images", "videos", "documents", "files", "data")) {
                    val arr = root.optJSONArray(key) ?: continue
                    for (i in 0 until arr.length()) {
                        val id = arr.optJSONObject(i)?.optString("_id")
                        if (!id.isNullOrBlank()) ids.add(id)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ids
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "pdf" -> "application/pdf"
            "txt", "md", "csv", "json", "js", "html", "css" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    fun defaultChatbotSuggestions(): List<String> = listOf(
        "How do I upload files?",
        "Show me my storage usage",
        "How do I edit photos?",
        "How do I share a folder?"
    )

    private suspend fun persistSandboxToDisk() {
        try {
            val root = JSONObject()
            val mediaArr = JSONArray()
            sandboxMedia.forEach { m ->
                mediaArr.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("filename", m.filename)
                        put("originalName", m.originalName)
                        put("path", m.path)
                        put("size", m.size)
                        put("folderId", m.folderId ?: "")
                        put("isDeleted", m.isDeleted)
                        put("deletedAt", m.deletedAt ?: "")
                        put("uploadDate", m.uploadDate)
                        put("mediaType", m.mediaType.key)
                        put("thumbnail", m.thumbnail ?: "")
                        put("duration", m.duration ?: 0.0)
                        put("docType", m.docType ?: "")
                        put("localUri", m.localUri ?: "")
                        put("aspectRatio", m.aspectRatio.toDouble())
                        val tagsArr = JSONArray()
                        m.tags.forEach { tagsArr.put(it) }
                        put("tags", tagsArr)
                    }
                )
            }
            root.put("media", mediaArr)

            val folderArr = JSONArray()
            sandboxFolders.forEach { f ->
                val collabArr = JSONArray()
                f.sharedWith.forEach { c ->
                    collabArr.put(
                        JSONObject().apply {
                            put("userId", c.userId)
                            put("name", c.name)
                            put("email", c.email)
                            put("permission", c.permission)
                        }
                    )
                }
                folderArr.put(
                    JSONObject().apply {
                        put("id", f.id)
                        put("name", f.name)
                        put("color", f.color)
                        put("ownerName", f.ownerName)
                        put("ownerEmail", f.ownerEmail)
                        put("isShared", f.isShared)
                        put("myPermission", f.myPermission)
                        put("sharedWith", collabArr)
                    }
                )
            }
            root.put("folders", folderArr)

            val linksArr = JSONArray()
            sandboxShareLinks.forEach { l ->
                linksArr.put(
                    JSONObject().apply {
                        put("id", l.id)
                        put("fileId", l.fileId)
                        put("fileName", l.fileName)
                        put("fileType", l.fileType.key)
                        put("token", l.token)
                        put("hasPassword", l.hasPassword)
                        put("expiresAt", l.expiresAt ?: "")
                        put("viewCount", l.viewCount)
                        put("createdAt", l.createdAt)
                        put("localPasswordPlain", l.localPasswordPlain ?: "")
                    }
                )
            }
            root.put("links", linksArr)
            val serialized = root.toString()
            loadedRawJson = serialized
            preferences.saveSandboxStateJson(serialized)
        } catch (_: Exception) {
        }
    }

    private fun restoreSandboxFromJson(root: JSONObject) {
        val mediaArr = root.optJSONArray("media") ?: return
        sandboxMedia.clear()
        for (i in 0 until mediaArr.length()) {
            val o = mediaArr.optJSONObject(i) ?: continue
            val tagsList = mutableListOf<String>()
            val tArr = o.optJSONArray("tags")
            if (tArr != null) {
                for (j in 0 until tArr.length()) {
                    val t = tArr.optString(j)
                    if (t.isNotBlank()) tagsList.add(t)
                }
            }
            sandboxMedia.add(
                UnifiedMediaItem(
                    id = o.optString("id"),
                    filename = o.optString("filename"),
                    originalName = o.optString("originalName"),
                    path = o.optString("path"),
                    size = o.optLong("size"),
                    folderId = o.optString("folderId").takeIf { it.isNotBlank() },
                    isDeleted = o.optBoolean("isDeleted", false),
                    deletedAt = o.optString("deletedAt").takeIf { it.isNotBlank() },
                    uploadDate = o.optString("uploadDate", nowIsoString()),
                    mediaType = MediaType.fromKey(o.optString("mediaType")),
                    thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
                    duration = o.optDouble("duration", 0.0).takeIf { it > 0.0 },
                    docType = o.optString("docType").takeIf { it.isNotBlank() },
                    localUri = o.optString("localUri").takeIf { it.isNotBlank() },
                    aspectRatio = o.optDouble("aspectRatio", 1.25).toFloat(),
                    tags = tagsList
                )
            )
        }
        val folderArr = root.optJSONArray("folders")
        if (folderArr != null) {
            sandboxFolders.clear()
            for (i in 0 until folderArr.length()) {
                val o = folderArr.optJSONObject(i) ?: continue
                val cArr = o.optJSONArray("sharedWith")
                val collabs = mutableListOf<FolderCollaborator>()
                if (cArr != null) {
                    for (j in 0 until cArr.length()) {
                        val c = cArr.optJSONObject(j) ?: continue
                        collabs.add(
                            FolderCollaborator(
                                userId = c.optString("userId"),
                                name = c.optString("name"),
                                email = c.optString("email"),
                                permission = c.optString("permission", "view")
                            )
                        )
                    }
                }
                sandboxFolders.add(
                    FolderItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        color = o.optString("color", "#4285F4"),
                        ownerName = o.optString("ownerName", "Me"),
                        ownerEmail = o.optString("ownerEmail", ""),
                        isShared = o.optBoolean("isShared", false),
                        myPermission = o.optString("myPermission", "edit"),
                        sharedWith = collabs
                    )
                )
            }
        }
        val linksArr = root.optJSONArray("links")
        if (linksArr != null) {
            sandboxShareLinks.clear()
            for (i in 0 until linksArr.length()) {
                val o = linksArr.optJSONObject(i) ?: continue
                sandboxShareLinks.add(
                    ShareLinkItem(
                        id = o.optString("id"),
                        fileId = o.optString("fileId"),
                        fileName = o.optString("fileName"),
                        fileType = MediaType.fromKey(o.optString("fileType")),
                        token = o.optString("token"),
                        hasPassword = o.optBoolean("hasPassword", false),
                        expiresAt = o.optString("expiresAt").takeIf { it.isNotBlank() },
                        viewCount = o.optInt("viewCount", 0),
                        createdAt = o.optString("createdAt", nowIsoString()),
                        localPasswordPlain = o.optString("localPasswordPlain").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        recomputeSandboxFolderCounts()
    }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Uploads a locally generated file (Scanned PDF, Video Frame Grab JPEG, Trimmed MP4 Clip)
     * directly into GalleryHub, auto-pinning it in the Offline Vault immediately.
     */
    suspend fun uploadSingleLocalFile(
        sourceFile: File,
        displayName: String,
        mediaType: MediaType,
        targetFolderId: String?,
        isSandbox: Boolean,
        onProgress: (Int) -> Unit = {}
    ): Result<UnifiedMediaItem> = withContext(Dispatchers.IO) {
        ensureSandboxInitialized()
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Generated file is empty or missing"))
        }
        onProgress(10)
        val uploadDir = File(context.filesDir, "uploaded_media").apply { mkdirs() }
        val safeName = "${System.currentTimeMillis()}-${displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}"
        val destFile = File(uploadDir, safeName)
        sourceFile.copyTo(destFile, overwrite = true)

        val mimeType = when (mediaType) {
            MediaType.IMAGE -> "image/jpeg"
            MediaType.VIDEO -> "video/mp4"
            MediaType.DOCUMENT -> if (displayName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "application/octet-stream"
        }
        val candidate = UploadCandidate(
            uri = Uri.fromFile(destFile),
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = destFile.length(),
            mediaType = mediaType
        )
        onProgress(22)

        if (!isSandbox) {
            try {
                val trackedBody = UploadProgressRequestBody(
                    file = destFile,
                    contentType = mimeType.toMediaTypeOrNull(),
                    onProgress = { pct -> onProgress((22 + pct * 0.74f).toInt().coerceIn(22, 96)) }
                )
                val response = when (mediaType) {
                    MediaType.IMAGE -> {
                        val single = apiService.uploadSingleImage(
                            MultipartBody.Part.createFormData("image", displayName, trackedBody)
                        )
                        if (single.isSuccessful) single else apiService.uploadMultipleImages(
                            listOf(MultipartBody.Part.createFormData("images", displayName, destFile.asRequestBody(mimeType.toMediaTypeOrNull())))
                        )
                    }
                    MediaType.VIDEO -> apiService.uploadMultipleVideos(
                        listOf(MultipartBody.Part.createFormData("videos", displayName, trackedBody))
                    )
                    MediaType.DOCUMENT -> apiService.uploadMultipleDocuments(
                        listOf(MultipartBody.Part.createFormData("documents", displayName, trackedBody))
                    )
                }
                if (response.isSuccessful) {
                    val ids = extractIdsFromUploadResponse(response.body()?.string())
                    autoPinUploadedCopies(ids, listOf(candidate to destFile), targetFolderId)
                    if (!targetFolderId.isNullOrBlank() && ids.isNotEmpty()) {
                        apiService.moveFilesToFolder(
                            targetFolderId,
                            FolderMoveFilesRequest(ids, mediaType.key)
                        )
                    }
                    onProgress(100)
                    val resolvedId = ids.firstOrNull() ?: "med_${UUID.randomUUID().toString().take(8)}"
                    val pinned = offlineVault.getSnapshotIndex()[resolvedId]?.second
                        ?.let { File(it) }
                        ?.takeIf { it.exists() && it.length() > 0L }
                        ?: destFile
                    return@withContext Result.success(
                        UnifiedMediaItem(
                            id = resolvedId,
                            filename = destFile.name,
                            originalName = displayName,
                            path = destFile.absolutePath,
                            size = destFile.length(),
                            folderId = targetFolderId,
                            uploadDate = nowIsoString(),
                            mediaType = mediaType,
                            docType = if (mediaType == MediaType.DOCUMENT) displayName.substringAfterLast('.', "pdf").lowercase() else null,
                            localUri = Uri.fromFile(destFile).toString(),
                            offlineState = OfflineAvailabilityState.PINNED,
                            offlineFilePath = pinned.absolutePath
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }

        // Local sandbox / offline fallback — immediately persisted and pinned
        val newId = "edt_${UUID.randomUUID().toString().take(8)}"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val baseItem = UnifiedMediaItem(
            id = newId,
            filename = destFile.name,
            originalName = displayName,
            path = destFile.absolutePath,
            size = destFile.length(),
            folderId = targetFolderId,
            uploadDate = nowIsoString(),
            mediaType = mediaType,
            docType = if (mediaType == MediaType.DOCUMENT) ext else null,
            localUri = Uri.fromFile(destFile).toString(),
            tags = listOf(mediaType.label.lowercase(), ext).filter { it.isNotBlank() }.distinct()
        )
        val pinnedFile = offlineVault.pinLocalFileOffline(baseItem, destFile).getOrNull()
        val item = baseItem.copy(
            offlineState = if (pinnedFile != null) OfflineAvailabilityState.PINNED else OfflineAvailabilityState.NONE,
            offlineFilePath = pinnedFile?.absolutePath ?: destFile.absolutePath
        )
        sandboxMedia.add(0, item)
        logSandboxActivity("upload", displayName, mediaType.key)
        recomputeSandboxFolderCounts()
        persistSandboxToDisk()
        onProgress(100)
        Result.success(item)
    }

    /**
     * Resolves a local file for external sharing, scrubbing EXIF metadata (GPS, camera model, timestamps)
     * when `scrubExif` is enabled and the item is a photo.
     */
    suspend fun prepareShareFileWithOptionalExifScrub(
        item: UnifiedMediaItem,
        serverBaseUrl: String,
        scrubExif: Boolean
    ): File? = withContext(Dispatchers.IO) {
        val localFile = listOfNotNull(
            item.offlineFilePath?.let { File(it) },
            item.localUri?.removePrefix("file://")?.let { File(it) },
            File(item.path)
        ).firstOrNull { it.exists() && it.length() > 0L }
            ?: offlineVault.autoCacheViewedItem(item, serverBaseUrl)
            ?: return@withContext null

        if (scrubExif && item.mediaType == MediaType.IMAGE) {
            runCatching {
                ExifMetadataScrubber.scrubPhotoToTempCopy(
                    context = context,
                    sourceFile = localFile,
                    displayName = item.originalName
                ).file
            }.getOrDefault(localFile)
        } else {
            localFile
        }
    }
}
