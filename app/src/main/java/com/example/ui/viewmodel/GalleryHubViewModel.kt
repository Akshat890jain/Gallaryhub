package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CachedWorkspaceSnapshot
import com.example.data.local.MissingServerFileException
import com.example.data.local.SessionPreferences
import com.example.data.model.ActivityItem
import com.example.data.model.CategoryFilter
import com.example.data.model.ChatMessage
import com.example.data.model.FolderItem
import com.example.data.model.GalleryViewMode
import com.example.data.model.MediaStorageBreakdown
import com.example.data.model.MediaType
import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.OfflineVaultStats
import com.example.data.model.ServerHealthState
import com.example.data.model.ShareLinkItem
import com.example.data.model.SmartCollectionFilter
import com.example.data.model.SortField
import com.example.data.model.SortOrder
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.UploadQueueItem
import com.example.data.model.UploadQueueStatus
import com.example.data.model.UserSession
import com.example.data.model.formatBytes
import com.example.data.model.nowIsoString
import com.example.data.remote.CompanionCloudRepository
import com.example.data.remote.GeminiStudioAssistant
import com.example.data.remote.MediaOverrideDoc
import com.example.data.remote.StudioStateDoc
import com.example.data.repository.GalleryHubRepository
import com.example.data.repository.UploadCandidate
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import com.example.util.DynamicAppIconManager
import com.example.util.LauncherIconVariant
import com.example.util.OcrTextIndexer
import com.example.util.StorageAuditReport
import com.example.util.StorageCleanerEngine
import com.example.widget.GalleryHubDashboardWidget
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class MainNavTab(val key: String, val label: String) {
    GALLERY("gallery", "Gallery"),
    FOLDERS("folders", "Folders"),
    LINKS("links", "Share Links"),
    TRASH("trash", "Trash"),
    SETTINGS("settings", "Settings")
}

sealed class PublicShareViewState {
    data object Idle : PublicShareViewState()
    data object Loading : PublicShareViewState()
    data class PasswordRequired(val token: String, val errorMessage: String? = null) : PublicShareViewState()
    data class Unlocked(val link: ShareLinkItem, val mediaItem: UnifiedMediaItem?) : PublicShareViewState()
    data class Error(val message: String) : PublicShareViewState()
}

data class GalleryUiState(
    val isInitialized: Boolean = false,
    val userSession: UserSession? = null,
    val serverHealth: ServerHealthState = ServerHealthState(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentHex: String = AccentPreset.TERRACOTTA.hex,
    val dynamicAppIconEnabled: Boolean = true,
    val hasPendingAppIconChange: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val isTourActive: Boolean = false,
    val tourStepIndex: Int = 0,

    // Navigation & Loading
    val currentTab: MainNavTab = MainNavTab.GALLERY,
    val isLoadingMedia: Boolean = false,
    val isSyncing: Boolean = false,
    val isUploading: Boolean = false,
    val authLoading: Boolean = false,
    val authError: String? = null,
    val toastMessage: String? = null,

    // Media & Filtering
    val allMedia: List<UnifiedMediaItem> = emptyList(),
    val trashMedia: List<UnifiedMediaItem> = emptyList(),
    val activeCategory: CategoryFilter = CategoryFilter.ALL,
    val searchQuery: String = "",
    val sortField: SortField = SortField.DATE,
    val sortOrder: SortOrder = SortOrder.DESC,
    val viewMode: GalleryViewMode = GalleryViewMode.GRID,
    val activeFolder: FolderItem? = null,

    // Multi-Select & Bulk Operations
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isZipExporting: Boolean = false,

    // Folders & RBAC
    val myFolders: List<FolderItem> = emptyList(),
    val sharedFolders: List<FolderItem> = emptyList(),

    // Share Links & Public Token Explorer
    val myShareLinks: List<ShareLinkItem> = emptyList(),
    val publicShareViewState: PublicShareViewState = PublicShareViewState.Idle,

    // Incoming Deep-Link Full-Screen Public Share Viewer (works signed-in or signed-out)
    val activeDeepLinkUrl: String? = null,
    val deepLinkShareState: PublicShareViewState = PublicShareViewState.Idle,
    val isSavingDeepLinkOffline: Boolean = false,
    val isDeepLinkSavedOffline: Boolean = false,

    // Modals
    val isUploadModalOpen: Boolean = false,
    val uploadCandidates: List<UploadCandidate> = emptyList(),
    val previewItem: UnifiedMediaItem? = null,
    val isSlideshowPlaying: Boolean = false,
    val editingImageItem: UnifiedMediaItem? = null,
    val sharingMediaItem: UnifiedMediaItem? = null,
    val lastCreatedShareLink: ShareLinkItem? = null,
    val managingCollaboratorsFolder: FolderItem? = null,
    val isActivityDrawerOpen: Boolean = false,
    val isChatbotOpen: Boolean = false,
    val isServerConfigDialogOpen: Boolean = false,
    val isGuestRestrictionDialogOpen: Boolean = false,

    // Offline Vault & Pending Auto-Sync Queue
    val offlineVaultStats: OfflineVaultStats = OfflineVaultStats(),
    val downloadingOfflineIds: Set<String> = emptySet(),
    val missingServerMediaIds: Set<String> = emptySet(),
    val recoveringMissingMediaItem: UnifiedMediaItem? = null,
    val isRelinkingMissingFile: Boolean = false,
    val pendingSyncCount: Int = 0,

    // Activity & AI Assistant
    val activities: List<ActivityItem> = emptyList(),
    val seenActivityIds: Set<String> = emptySet(),
    val clearedActivityIds: Set<String> = emptySet(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val isChatSending: Boolean = false,

    // Companion Cloud Metadata Sync (Firebase Firestore via Google Sign-In)
    val cloudSyncUserUid: String? = null,
    val cloudSyncUserEmail: String? = null,
    val cloudSyncStatusMessage: String? = null,
    val isCloudSyncingMetadata: Boolean = false,

    // Privacy & Security (Biometric Vault, Hidden Items, EXIF Scrubber)
    val hiddenMediaIds: Set<String> = emptySet(),
    val isVaultSheetOpen: Boolean = false,
    val isVaultUnlocked: Boolean = false,
    val vaultPinHash: String? = null,
    val vaultBiometricEnabled: Boolean = true,
    val exifScrubDefaultOn: Boolean = true,

    // Organization & Discovery (Smart Auto-Collections, Cleaner, On-Device OCR)
    val smartCollectionFilter: SmartCollectionFilter = SmartCollectionFilter.ALL,
    val isCleanerSheetOpen: Boolean = false,
    val storageAuditReport: StorageAuditReport = StorageAuditReport(),
    val ocrTextByMediaId: Map<String, String> = emptyMap(),
    val isOcrIndexing: Boolean = false,
    val ocrExtractedDialogContent: Pair<String, String>? = null,

    // Scanners & Upload Queue
    val isDocumentScannerOpen: Boolean = false,
    val isQrScannerOpen: Boolean = false,
    val uploadQueue: List<UploadQueueItem> = emptyList(),
    val wifiOnlyUploads: Boolean = false
) {
    companion object {
        const val STORAGE_QUOTA_BYTES: Long = 500L * 1024L * 1024L // 500 MB
    }

    val hasVaultPin: Boolean
        get() = !vaultPinHash.isNullOrBlank()

    val hiddenMediaItems: List<UnifiedMediaItem>
        get() = allMedia.filter { !it.isDeleted && it.id in hiddenMediaIds }

    val onThisDayMemories: List<UnifiedMediaItem>
        get() {
            val visible = allMedia.filter { !it.isDeleted && it.id !in hiddenMediaIds && it.timestampMillis > 0L }
            if (visible.isEmpty()) return emptyList()
            val todayCal = Calendar.getInstance()
            val todayMonth = todayCal.get(Calendar.MONTH)
            val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
            val todayYear = todayCal.get(Calendar.YEAR)
            val itemCal = Calendar.getInstance()
            val exactOnThisDay = visible.filter { item ->
                itemCal.timeInMillis = item.timestampMillis
                itemCal.get(Calendar.MONTH) == todayMonth &&
                    itemCal.get(Calendar.DAY_OF_MONTH) == todayDay &&
                    itemCal.get(Calendar.YEAR) < todayYear
            }
            if (exactOnThisDay.isNotEmpty()) return exactOnThisDay.take(10)
            // Fallback highlight: favorite or older photo memories so the Memories strip stays useful
            return visible
                .filter { it.mediaType == MediaType.IMAGE }
                .sortedWith(compareByDescending<UnifiedMediaItem> { it.isFavorite }.thenBy { it.timestampMillis })
                .take(6)
        }

    val totalUsedBytes: Long
        get() = allMedia.sumOf { it.size }

    val usagePercent: Float
        get() = ((totalUsedBytes.toFloat() / STORAGE_QUOTA_BYTES.toFloat()) * 100f).coerceIn(0f, 100f)

    val storageBreakdown: MediaStorageBreakdown
        get() {
            var imgC = 0
            var imgB = 0L
            var vidC = 0
            var vidB = 0L
            var docC = 0
            var docB = 0L
            for (item in allMedia) {
                if (item.isDeleted) continue
                when (item.mediaType) {
                    MediaType.IMAGE -> { imgC++; imgB += item.size }
                    MediaType.VIDEO -> { vidC++; vidB += item.size }
                    MediaType.DOCUMENT -> { docC++; docB += item.size }
                }
            }
            return MediaStorageBreakdown(
                imageCount = imgC,
                imageBytes = imgB,
                videoCount = vidC,
                videoBytes = vidB,
                documentCount = docC,
                documentBytes = docB
            )
        }

    val isCloudConnected: Boolean
        get() = userSession != null && !userSession.isOfflineSandbox && serverHealth.isReachable

    val unreadActivityCount: Int
        get() = activities.count { it.id !in seenActivityIds }
}

private data class GalleryFilterInput(
    val allMedia: List<UnifiedMediaItem>,
    val hiddenMediaIds: Set<String>,
    val activeFolderId: String?,
    val activeCategory: CategoryFilter,
    val smartCollectionFilter: SmartCollectionFilter,
    val searchQuery: String,
    val ocrTextByMediaId: Map<String, String>,
    val sortField: SortField,
    val sortOrder: SortOrder,
    val folderNamesById: Map<String, String>
)

class GalleryHubViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SessionPreferences(application)
    val repository = GalleryHubRepository(application, preferences)
    private val companionCloudRepo by lazy { CompanionCloudRepository(application) }
    private val geminiAssistant = GeminiStudioAssistant()
    private val ocrIndexer = OcrTextIndexer(application)
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var mediaOverridesObserverJob: Job? = null
    private var studioStateObserverJob: Job? = null
    private var hasBootstrappedCloudUid: String? = null

    private val _uiState = MutableStateFlow(
        GalleryUiState(
            chatMessages = listOf(
                ChatMessage(
                    id = "welcome_bot",
                    text = "Hi! I'm your GalleryHub AI Assistant. Ask me how to batch-upload media, pin files for offline viewing, edit photos with filters, or generate password-protected share links!",
                    isUser = false,
                    isAiBadge = true,
                    suggestions = repository.defaultChatbotSuggestions()
                )
            )
        )
    )
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    /**
     * Off-main-thread reactive filter & sort pipeline.
     * Excludes Private Vault hidden items, applies Smart Auto-Collections, and searches ML Kit OCR text.
     */
    val filteredMedia: StateFlow<List<UnifiedMediaItem>> = _uiState
        .map { state ->
            val folderMap = buildMap<String, String>(state.myFolders.size + state.sharedFolders.size) {
                state.myFolders.forEach { put(it.id, it.name) }
                state.sharedFolders.forEach { put(it.id, it.name) }
            }
            GalleryFilterInput(
                allMedia = state.allMedia,
                hiddenMediaIds = state.hiddenMediaIds,
                activeFolderId = state.activeFolder?.id,
                activeCategory = state.activeCategory,
                smartCollectionFilter = state.smartCollectionFilter,
                searchQuery = state.searchQuery,
                ocrTextByMediaId = state.ocrTextByMediaId,
                sortField = state.sortField,
                sortOrder = state.sortOrder,
                folderNamesById = folderMap
            )
        }
        .distinctUntilChanged()
        .map { input ->
            var list = input.allMedia.filter { !it.isDeleted && it.id !in input.hiddenMediaIds }
            if (input.activeFolderId != null) {
                list = list.filter { it.folderId == input.activeFolderId }
            }
            list = when (input.activeCategory) {
                CategoryFilter.ALL -> list
                CategoryFilter.FAVORITES -> list.filter { it.isFavorite }
                CategoryFilter.OFFLINE -> list.filter { it.isAvailableOffline }
                CategoryFilter.IMAGES -> list.filter { it.mediaType == MediaType.IMAGE }
                CategoryFilter.VIDEOS -> list.filter { it.mediaType == MediaType.VIDEO }
                CategoryFilter.DOCUMENTS -> list.filter { it.mediaType == MediaType.DOCUMENT }
            }
            if (input.smartCollectionFilter != SmartCollectionFilter.ALL) {
                list = list.filter { input.smartCollectionFilter.matches(it) }
            }
            val query = input.searchQuery.trim()
            if (query.isNotEmpty()) {
                list = list.filter { item ->
                    val folderName = item.folderId?.let { input.folderNamesById[it] }
                    val ocrMatch = input.ocrTextByMediaId[item.id]
                        ?.contains(query, ignoreCase = true) == true
                    item.matchesSearchQuery(query, folderName) || ocrMatch
                }
            }
            val comparator = when (input.sortField) {
                SortField.DATE -> compareBy<UnifiedMediaItem> { it.timestampMillis }
                SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.originalName }
                SortField.SIZE -> compareBy { it.size }
                SortField.TYPE -> compareBy { it.extension.ifBlank { it.mediaType.key } }
            }
            if (input.sortOrder == SortOrder.DESC) {
                list.sortedWith(comparator.reversed())
            } else {
                list.sortedWith(comparator)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun currentAccountKey(): String {
        val session = _uiState.value.userSession
        return session?.email?.ifBlank { session.id } ?: "default"
    }

    init {
        registerConnectivityAutoSyncObserver()
        viewModelScope.launch {
            val stats = repository.getOfflineVaultStats()
            val savedOcr = ocrIndexer.loadIndex()
            _uiState.update { it.copy(offlineVaultStats = stats, ocrTextByMediaId = savedOcr) }
        }
        viewModelScope.launch {
            preferences.appStateFlow.collect { persisted ->
                com.example.data.remote.RetrofitClient.baseUrl = persisted.serverBaseUrl
                com.example.data.remote.RetrofitClient.jwtToken = persisted.userSession?.token
                val wasInitialized = _uiState.value.isInitialized
                val previousSession = _uiState.value.userSession
                val appContext = getApplication<Application>()
                val targetVariant = DynamicAppIconManager.resolveVariant(
                    accentHex = persisted.accentHex,
                    themeMode = persisted.themeMode,
                    isSystemDark = DynamicAppIconManager.isSystemDarkMode(appContext),
                    dynamicSyncEnabled = persisted.dynamicAppIconEnabled
                )
                _uiState.update { current ->
                    current.copy(
                        isInitialized = true,
                        userSession = persisted.userSession,
                        themeMode = persisted.themeMode,
                        accentHex = persisted.accentHex,
                        dynamicAppIconEnabled = persisted.dynamicAppIconEnabled,
                        hasPendingAppIconChange = false,
                        onboardingCompleted = persisted.onboardingCompleted,
                        isTourActive = if (!wasInitialized && persisted.userSession != null && !persisted.onboardingCompleted) true else current.isTourActive,
                        serverHealth = current.serverHealth.copy(baseUrl = persisted.serverBaseUrl),
                        hiddenMediaIds = persisted.hiddenMediaIds,
                        vaultPinHash = persisted.vaultPinHash,
                        vaultBiometricEnabled = persisted.vaultBiometricEnabled,
                        exifScrubDefaultOn = persisted.exifScrubDefaultOn,
                        wifiOnlyUploads = persisted.wifiOnlyUploads,
                        clearedActivityIds = persisted.clearedActivityIds
                    )
                }
                refreshHomeScreenWidget()
                if (!wasInitialized) {
                    viewModelScope.launch(Dispatchers.IO) {
                        DynamicAppIconManager.applyVariantIfChanged(appContext, targetVariant)
                    }
                    pingServer(persisted.serverBaseUrl)
                }
                if (persisted.userSession != null && previousSession?.id != persisted.userSession.id) {
                    hydrateWorkspaceFast()
                }
            }
        }
    }

    private fun registerConnectivityAutoSyncObserver() {
        val cm = connectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val state = _uiState.value
                val session = state.userSession ?: return
                if (session.isOfflineSandbox) return
                viewModelScope.launch {
                    val accountKey = currentAccountKey()
                    val (ok, latency, msg) = repository.checkServerConnection(state.serverHealth.baseUrl)
                    _uiState.update {
                        it.copy(
                            serverHealth = ServerHealthState(
                                baseUrl = com.example.data.remote.RetrofitClient.baseUrl,
                                isChecking = false,
                                isReachable = ok,
                                latencyMs = latency,
                                statusMessage = msg,
                                lastCheckedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    if (ok) {
                        val synced = repository.drainPendingSyncQueue(accountKey, isSandbox = false)
                        val remaining = repository.getPendingSyncCount(accountKey)
                        _uiState.update {
                            it.copy(
                                pendingSyncCount = remaining,
                                toastMessage = if (synced > 0) {
                                    "Back online · Auto-synced $synced offline change(s)"
                                } else {
                                    it.toastMessage
                                }
                            )
                        }
                        if (synced > 0) {
                            refreshWorkspaceData(silent = true)
                        }
                        // Automatically retry queued or Wi-Fi-waiting uploads when connectivity returns
                        val currentQueue = _uiState.value.uploadQueue
                        val hasPendingUploads = currentQueue.any {
                            it.status == UploadQueueStatus.FAILED || it.status == UploadQueueStatus.WAITING_WIFI
                        }
                        if (hasPendingUploads && (!_uiState.value.wifiOnlyUploads || repository.isWifiConnected())) {
                            retryFailedOrQueuedUploads()
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                val activeCaps = cm.getNetworkCapabilities(cm.activeNetwork)
                val stillConnected = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (!stillConnected) {
                    _uiState.update {
                        it.copy(
                            serverHealth = it.serverHealth.copy(
                                isReachable = false,
                                isChecking = false,
                                statusMessage = "Offline Mode · Using cached archive"
                            )
                        )
                    }
                }
            }
        }
        connectivityCallback = callback
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelCloudSyncListeners()
        connectivityCallback?.let { cb ->
            runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
        }
        connectivityCallback = null
    }

    // =========================================================================
    // Companion Cloud Metadata Sync (Firebase Firestore via Google Sign-In)
    // Preserves Render/MongoDB backend untouched while syncing Favorites, Tags,
    // Renames, Private Vault, OCR index, Cleared Activities & Studio Settings.
    // =========================================================================

    fun cancelCloudSyncListeners() {
        mediaOverridesObserverJob?.cancel()
        mediaOverridesObserverJob = null
        studioStateObserverJob?.cancel()
        studioStateObserverJob = null
    }

    fun onFirebaseGoogleSignedIn(uid: String, email: String?) {
        if (uid.isBlank()) return
        if (_uiState.value.cloudSyncUserUid == uid && mediaOverridesObserverJob?.isActive == true) {
            if (!email.isNullOrBlank() && _uiState.value.cloudSyncUserEmail != email) {
                _uiState.update { it.copy(cloudSyncUserEmail = email) }
            }
            return
        }
        cancelCloudSyncListeners()
        _uiState.update {
            it.copy(
                cloudSyncUserUid = uid,
                cloudSyncUserEmail = email,
                isCloudSyncingMetadata = true,
                cloudSyncStatusMessage = "Syncing cloud metadata…"
            )
        }

        mediaOverridesObserverJob = viewModelScope.launch {
            try {
                companionCloudRepo.observeMediaOverrides(uid).collect { docs ->
                    applyCloudMediaOverrides(docs)
                    if (docs.isEmpty() && hasBootstrappedCloudUid != uid) {
                        bootstrapLocalOverridesToCloudAsync()
                    }
                }
            } catch (e: Throwable) {
                val firestoreError = generateSequence(e) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && e is CancellationException) throw e
                val errMsg = firestoreError?.localizedMessage ?: e.localizedMessage ?: "Cloud metadata sync paused"
                _uiState.update {
                    it.copy(
                        isCloudSyncingMetadata = false,
                        cloudSyncStatusMessage = errMsg
                    )
                }
            }
        }

        studioStateObserverJob = viewModelScope.launch {
            try {
                companionCloudRepo.observeStudioState(uid).collect { doc ->
                    if (doc != null) {
                        applyCloudStudioState(doc)
                    } else if (hasBootstrappedCloudUid != uid) {
                        hasBootstrappedCloudUid = uid
                        pushStudioStateToCloudAsync()
                    }
                }
            } catch (e: Throwable) {
                val firestoreError = generateSequence(e) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && e is CancellationException) throw e
                val errMsg = firestoreError?.localizedMessage ?: e.localizedMessage ?: "Studio settings sync paused"
                _uiState.update {
                    it.copy(
                        isCloudSyncingMetadata = false,
                        cloudSyncStatusMessage = errMsg
                    )
                }
            }
        }
    }

    fun onFirebaseGoogleSignedOut() {
        cancelCloudSyncListeners()
        hasBootstrappedCloudUid = null
        _uiState.update {
            it.copy(
                cloudSyncUserUid = null,
                cloudSyncUserEmail = null,
                isCloudSyncingMetadata = false,
                cloudSyncStatusMessage = null
            )
        }
    }

    private suspend fun applyCloudMediaOverrides(docs: List<MediaOverrideDoc>) {
        val bySafeId = docs.associateBy { it.mediaId.ifBlank { it.id } }
        val accountKey = currentAccountKey()
        val currentState = _uiState.value

        val updatedHidden = currentState.hiddenMediaIds.toMutableSet()
        val updatedOcr = currentState.ocrTextByMediaId.toMutableMap()

        val mergedMedia = currentState.allMedia.map { item ->
            val safeId = companionCloudRepo.sanitizeDocId(item.id)
            val doc = bySafeId[item.id] ?: bySafeId[safeId] ?: return@map item
            if (doc.hiddenInVault) updatedHidden.add(item.id) else updatedHidden.remove(item.id)
            if (!doc.ocrText.isNullOrBlank()) updatedOcr[item.id] = doc.ocrText
            item.copy(
                isFavorite = doc.favorite,
                tags = if (doc.customTags.isNotEmpty()) doc.customTags else item.tags,
                originalName = doc.renamedTitle?.takeIf { it.isNotBlank() } ?: item.originalName
            )
        }

        // Also mirror incoming overrides into local overlays & OCR store so offline refreshes stay consistent
        for (doc in docs) {
            val targetId = currentState.allMedia.firstOrNull {
                it.id == doc.mediaId || companionCloudRepo.sanitizeDocId(it.id) == doc.mediaId
            }?.id ?: doc.mediaId.ifBlank { doc.id }
            if (targetId.isBlank()) continue
            repository.syncQueueStore.setFavorite(accountKey, targetId, doc.favorite)
            if (doc.customTags.isNotEmpty()) {
                repository.syncQueueStore.setCustomTags(accountKey, targetId, doc.customTags)
            }
            if (!doc.renamedTitle.isNullOrBlank()) {
                repository.syncQueueStore.setRenamedTitle(accountKey, targetId, doc.renamedTitle)
            }
            if (doc.hiddenInVault) updatedHidden.add(targetId) else updatedHidden.remove(targetId)
            if (!doc.ocrText.isNullOrBlank()) updatedOcr[targetId] = doc.ocrText
        }

        if (updatedHidden != currentState.hiddenMediaIds) {
            preferences.saveHiddenMediaIds(updatedHidden)
        }
        if (updatedOcr != currentState.ocrTextByMediaId) {
            ocrIndexer.saveIndex(updatedOcr)
        }

        _uiState.update { current ->
            current.copy(
                allMedia = mergedMedia,
                previewItem = current.previewItem?.let { p -> mergedMedia.firstOrNull { it.id == p.id } ?: p },
                hiddenMediaIds = updatedHidden,
                ocrTextByMediaId = updatedOcr,
                isCloudSyncingMetadata = false,
                cloudSyncStatusMessage = "Live Synced · ${docs.size} item override(s)"
            )
        }
        refreshHomeScreenWidget()
    }

    private suspend fun applyCloudStudioState(doc: StudioStateDoc) {
        val current = _uiState.value
        val mergedCleared = current.clearedActivityIds + doc.clearedActivityIds.toSet()
        val resolvedTheme = ThemeMode.fromKey(doc.themeMode)
        val resolvedAccent = doc.accentHex.takeIf { it.startsWith("#") && it.length >= 4 } ?: current.accentHex
        val resolvedPinHash = doc.vaultPinHash?.takeIf { it.isNotBlank() }

        if (mergedCleared != current.clearedActivityIds) {
            preferences.saveClearedActivityIds(mergedCleared)
        }
        if (resolvedPinHash != current.vaultPinHash) {
            preferences.saveVaultPinHash(resolvedPinHash)
        }
        if (doc.vaultBiometricEnabled != current.vaultBiometricEnabled) {
            preferences.saveVaultBiometricEnabled(doc.vaultBiometricEnabled)
        }
        if (doc.exifScrubDefaultOn != current.exifScrubDefaultOn) {
            preferences.saveExifScrubDefaultOn(doc.exifScrubDefaultOn)
        }
        if (doc.wifiOnlyUploads != current.wifiOnlyUploads) {
            preferences.saveWifiOnlyUploads(doc.wifiOnlyUploads)
        }
        if (resolvedTheme != current.themeMode) {
            preferences.saveThemeMode(resolvedTheme)
        }
        if (!resolvedAccent.equals(current.accentHex, ignoreCase = true)) {
            preferences.saveAccentColor(resolvedAccent)
        }

        _uiState.update { state ->
            state.copy(
                vaultPinHash = resolvedPinHash,
                vaultBiometricEnabled = doc.vaultBiometricEnabled,
                clearedActivityIds = mergedCleared,
                activities = state.activities.filterNot { it.id in mergedCleared },
                themeMode = resolvedTheme,
                accentHex = resolvedAccent,
                exifScrubDefaultOn = doc.exifScrubDefaultOn,
                wifiOnlyUploads = doc.wifiOnlyUploads,
                isCloudSyncingMetadata = false
            )
        }
    }

    private fun bootstrapLocalOverridesToCloudAsync() {
        val state = _uiState.value
        if (state.cloudSyncUserUid == null) return
        val idsToBackUp = buildSet {
            state.allMedia.filter { it.isFavorite || it.tags.isNotEmpty() }.forEach { add(it.id) }
            addAll(state.hiddenMediaIds)
            addAll(state.ocrTextByMediaId.keys)
        }
        idsToBackUp.take(60).forEach { id -> pushMediaOverrideToCloudAsync(id) }
    }

    private fun pushMediaOverrideToCloudAsync(mediaId: String) {
        val state = _uiState.value
        if (state.cloudSyncUserUid == null) return
        val item = state.allMedia.firstOrNull { it.id == mediaId }
        val isFav = item?.isFavorite == true
        val tags = item?.tags.orEmpty()
        val title = item?.originalName
        val hidden = mediaId in state.hiddenMediaIds
        val ocr = state.ocrTextByMediaId[mediaId]
        viewModelScope.launch {
            companionCloudRepo.upsertMediaOverride(
                rawMediaId = mediaId,
                favorite = isFav,
                customTags = tags,
                renamedTitle = title,
                hiddenInVault = hidden,
                ocrText = ocr
            )
        }
    }

    private fun pushStudioStateToCloudAsync() {
        val state = _uiState.value
        if (state.cloudSyncUserUid == null) return
        viewModelScope.launch {
            companionCloudRepo.upsertStudioState(
                vaultPinHash = state.vaultPinHash,
                vaultBiometricEnabled = state.vaultBiometricEnabled,
                clearedActivityIds = state.clearedActivityIds.toList(),
                themeMode = state.themeMode.key,
                accentHex = state.accentHex,
                exifScrubDefaultOn = state.exifScrubDefaultOn,
                wifiOnlyUploads = state.wifiOnlyUploads
            )
        }
    }

    // --- Server URL & Cold Start Ping ---
    fun pingServer(customUrl: String? = null) {
        viewModelScope.launch {
            val targetUrl = customUrl?.trim()?.ifBlank { null } ?: _uiState.value.serverHealth.baseUrl
            _uiState.update {
                it.copy(
                    serverHealth = it.serverHealth.copy(
                        baseUrl = targetUrl,
                        isChecking = true,
                        statusMessage = "Connecting to GalleryHub Cloud…"
                    )
                )
            }
            val (ok, latency, msg) = repository.checkServerConnection(targetUrl)
            _uiState.update {
                it.copy(
                    serverHealth = ServerHealthState(
                        baseUrl = com.example.data.remote.RetrofitClient.baseUrl,
                        isChecking = false,
                        isReachable = ok,
                        latencyMs = latency,
                        statusMessage = msg,
                        lastCheckedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun setServerConfigDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isServerConfigDialogOpen = open) }
    }

    // --- Authentication ---
    fun clearAuthError() {
        if (_uiState.value.authError != null) {
            _uiState.update { it.copy(authError = null) }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(authError = "Please enter both your email address and password.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val result = repository.login(email, password)
            result.fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            userSession = session,
                            serverHealth = it.serverHealth.copy(
                                isReachable = true,
                                statusMessage = "Cloud Sync Active"
                            ),
                            isTourActive = !it.onboardingCompleted,
                            toastMessage = "Welcome back, ${session.name}"
                        )
                    }
                    refreshWorkspaceData()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = err.message ?: "Unable to sign in. Please check your credentials."
                        )
                    }
                }
            )
        }
    }

    fun register(name: String, email: String, password: String) {
        val cleanEmail = email.trim()
        val resolvedName = name.trim().ifBlank {
            cleanEmail.substringBefore('@')
                .replace(Regex("[0-9]+$"), "")
                .replaceFirstChar { it.uppercase() }
                .ifBlank { "GalleryHub User" }
        }
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            _uiState.update {
                it.copy(authError = "Please enter a valid email address.")
            }
            return
        }
        if (password.length < 6) {
            _uiState.update {
                it.copy(authError = "Password must be at least 6 characters long.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val result = repository.register(resolvedName, cleanEmail, password)
            result.fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            userSession = session,
                            serverHealth = it.serverHealth.copy(
                                isReachable = true,
                                statusMessage = "Cloud Sync Active"
                            ),
                            isTourActive = !it.onboardingCompleted,
                            toastMessage = "Account created! Welcome, ${session.name}"
                        )
                    }
                    refreshWorkspaceData()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = err.message ?: "Could not create account. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun enterInteractiveSandbox(name: String = "Akshat Jain", email: String = "akshat890jain@gmail.com") {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val session = repository.loginInteractiveSandbox(name, email)
            _uiState.update {
                it.copy(
                    authLoading = false,
                    userSession = session,
                    isTourActive = !it.onboardingCompleted,
                    toastMessage = "Workspace ready (${session.name})"
                )
            }
            refreshWorkspaceData()
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(
                    userSession = null,
                    activeFolder = null,
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    currentTab = MainNavTab.GALLERY,
                    toastMessage = "Signed out of GalleryHub"
                )
            }
        }
    }

    // --- Workspace Hydration (Instant Stale-While-Revalidate + Parallel Cloud Refresh) ---
    fun hydrateWorkspaceFast() {
        val session = _uiState.value.userSession ?: return
        val accountKey = currentAccountKey()
        viewModelScope.launch {
            // 1. Instant local snapshot paint (<50ms) so startup never blocks on network
            val cached = repository.loadCachedWorkspaceSnapshot(accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            val vaultStats = repository.getOfflineVaultStats()

            if (cached.hasData) {
                _uiState.update { current ->
                    current.copy(
                        isLoadingMedia = false,
                        allMedia = cached.activeMedia,
                        trashMedia = cached.trashMedia,
                        myFolders = cached.ownedFolders,
                        sharedFolders = cached.sharedFolders,
                        myShareLinks = cached.shareLinks,
                        activities = cached.activities.filterNot { it.id in current.clearedActivityIds },
                        pendingSyncCount = pendingCount,
                        offlineVaultStats = vaultStats
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingMedia = true,
                        pendingSyncCount = pendingCount,
                        offlineVaultStats = vaultStats
                    )
                }
            }

            // 2. Drain any queued offline edits if cloud is reachable, then fetch fresh bundle in parallel
            val isSandbox = session.isOfflineSandbox
            if (!isSandbox && pendingCount > 0) {
                repository.drainPendingSyncQueue(accountKey, isSandbox = false)
            }
            refreshWorkspaceData(silent = cached.hasData)
        }
    }

    fun refreshWorkspaceData(silent: Boolean = false) {
        val session = _uiState.value.userSession ?: return
        val accountKey = currentAccountKey()
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoadingMedia = true) }
            }
            val isSandbox = session.isOfflineSandbox
            val bundle = repository.fetchWorkspaceBundleParallel(accountKey, isSandbox)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            val vaultStats = repository.getOfflineVaultStats()

            _uiState.update { current ->
                val refreshedActiveFolder = current.activeFolder?.let { af ->
                    (bundle.ownedFolders + bundle.sharedFolders).firstOrNull { it.id == af.id }
                }
                val refreshedPreview = current.previewItem?.let { p ->
                    bundle.activeMedia.firstOrNull { it.id == p.id } ?: p
                }
                current.copy(
                    isLoadingMedia = false,
                    allMedia = bundle.activeMedia,
                    trashMedia = bundle.trashMedia,
                    myFolders = bundle.ownedFolders,
                    sharedFolders = bundle.sharedFolders,
                    activeFolder = refreshedActiveFolder,
                    previewItem = refreshedPreview,
                    myShareLinks = bundle.shareLinks,
                    activities = bundle.activities.filterNot { it.id in current.clearedActivityIds },
                    pendingSyncCount = pendingCount,
                    offlineVaultStats = vaultStats
                )
            }
        }
    }

    private fun persistCurrentUiSnapshotAsync() {
        val state = _uiState.value
        if (state.userSession == null) return
        val accountKey = currentAccountKey()
        val snapshot = CachedWorkspaceSnapshot(
            activeMedia = state.allMedia,
            trashMedia = state.trashMedia,
            ownedFolders = state.myFolders,
            sharedFolders = state.sharedFolders,
            shareLinks = state.myShareLinks,
            activities = state.activities,
            savedAtMillis = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveWorkspaceSnapshot(accountKey, snapshot)
        }
    }

    /**
     * Dedicated manual 'Sync Now' action:
     * 1. Re-checks cloud connectivity with fast-fail ping.
     * 2. Flushes any queued offline edits first.
     * 3. Fetches all 5 workspace collections concurrently in parallel.
     */
    fun syncNow() {
        val session = _uiState.value.userSession ?: return
        if (_uiState.value.isSyncing) return
        val accountKey = currentAccountKey()
        viewModelScope.launch {
            val isSandbox = session.isOfflineSandbox
            val targetUrl = _uiState.value.serverHealth.baseUrl
            _uiState.update {
                it.copy(
                    isSyncing = true,
                    serverHealth = if (isSandbox) it.serverHealth else it.serverHealth.copy(
                        isChecking = true,
                        statusMessage = "Syncing with GalleryHub Cloud…"
                    )
                )
            }

            val cloudReachable = if (isSandbox) {
                false
            } else {
                val (ok, latency, msg) = repository.checkServerConnection(targetUrl)
                _uiState.update {
                    it.copy(
                        serverHealth = ServerHealthState(
                            baseUrl = com.example.data.remote.RetrofitClient.baseUrl,
                            isChecking = false,
                            isReachable = ok,
                            latencyMs = latency,
                            statusMessage = msg,
                            lastCheckedAt = System.currentTimeMillis()
                        )
                    )
                }
                ok
            }

            val drainedCount = if (!isSandbox && cloudReachable) {
                repository.drainPendingSyncQueue(accountKey, isSandbox = false)
            } else {
                0
            }

            val bundle = repository.fetchWorkspaceBundleParallel(accountKey, isSandbox)
            val remainingPending = repository.getPendingSyncCount(accountKey)
            val vaultStats = repository.getOfflineVaultStats()

            val summaryToast = when {
                isSandbox -> "Offline Guest Mode · Local workspace refreshed"
                cloudReachable && drainedCount > 0 -> "Synced $drainedCount offline edit(s) · ${bundle.activeMedia.size} item(s) up to date"
                cloudReachable -> "Synced with cloud · ${bundle.activeMedia.size} item(s) up to date"
                remainingPending > 0 -> "Offline · $remainingPending change(s) queued for auto-sync"
                else -> "Offline · Showing ${bundle.activeMedia.size} cached item(s)"
            }

            _uiState.update { current ->
                val refreshedActiveFolder = current.activeFolder?.let { af ->
                    (bundle.ownedFolders + bundle.sharedFolders).firstOrNull { it.id == af.id }
                }
                current.copy(
                    isSyncing = false,
                    isLoadingMedia = false,
                    allMedia = bundle.activeMedia,
                    trashMedia = bundle.trashMedia,
                    myFolders = bundle.ownedFolders,
                    sharedFolders = bundle.sharedFolders,
                    activeFolder = refreshedActiveFolder,
                    myShareLinks = bundle.shareLinks,
                    activities = bundle.activities.filterNot { it.id in current.clearedActivityIds },
                    pendingSyncCount = remainingPending,
                    offlineVaultStats = vaultStats,
                    toastMessage = summaryToast
                )
            }
        }
    }

    // --- Navigation, Filtering & Sorting ---
    fun selectMainTab(tab: MainNavTab) {
        _uiState.update {
            it.copy(
                currentTab = tab,
                isSelectionMode = false,
                selectedIds = emptySet()
            )
        }
    }

    fun setCategoryFilter(category: CategoryFilter) {
        _uiState.update { it.copy(activeCategory = category) }
    }

    fun setSmartCollectionFilter(filter: SmartCollectionFilter) {
        _uiState.update { it.copy(smartCollectionFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortField(field: SortField) {
        _uiState.update { it.copy(sortField = field) }
    }

    fun toggleSortOrder() {
        _uiState.update {
            it.copy(sortOrder = if (it.sortOrder == SortOrder.DESC) SortOrder.ASC else SortOrder.DESC)
        }
    }

    fun setViewMode(mode: GalleryViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun openFolderInGallery(folder: FolderItem?) {
        _uiState.update {
            it.copy(
                activeFolder = folder,
                currentTab = MainNavTab.GALLERY,
                isSelectionMode = false,
                selectedIds = emptySet()
            )
        }
    }

    // --- Multi-Select & Bulk Operations ---
    fun toggleSelectionMode() {
        _uiState.update {
            val next = !it.isSelectionMode
            it.copy(
                isSelectionMode = next,
                selectedIds = if (next) it.selectedIds else emptySet()
            )
        }
    }

    fun toggleItemSelected(id: String) {
        _uiState.update { state ->
            val nextSet = state.selectedIds.toMutableSet()
            if (!nextSet.add(id)) nextSet.remove(id)
            state.copy(
                isSelectionMode = nextSet.isNotEmpty(),
                selectedIds = nextSet
            )
        }
    }

    fun selectAllVisible() {
        val visibleIds = filteredMedia.value.map { it.id }.toSet()
        _uiState.update { state ->
            val allAlreadySelected = visibleIds.isNotEmpty() && state.selectedIds.containsAll(visibleIds)
            if (allAlreadySelected) {
                state.copy(
                    isSelectionMode = false,
                    selectedIds = emptySet()
                )
            } else {
                state.copy(
                    isSelectionMode = true,
                    selectedIds = visibleIds
                )
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    fun shareSelectedMediaBatch(onLaunchIntent: (Intent) -> Unit) {
        val state = _uiState.value
        val items = state.allMedia.filter { it.id in state.selectedIds }
        if (items.isEmpty()) return

        viewModelScope.launch {
            val app = getApplication<Application>()
            val baseUrl = state.serverHealth.baseUrl
            val streamUris = ArrayList<Uri>()

            val scrubExif = state.exifScrubDefaultOn
            for (item in items) {
                val candidateFile = repository.prepareShareFileWithOptionalExifScrub(
                    item = item,
                    serverBaseUrl = baseUrl,
                    scrubExif = scrubExif
                )

                if (candidateFile != null) {
                    runCatching {
                        FileProvider.getUriForFile(
                            app,
                            "${app.packageName}.fileprovider",
                            candidateFile
                        )
                    }.getOrNull()?.let { streamUris.add(it) }
                }
            }

            val summaryText = items.joinToString("\n") { item ->
                "• ${item.originalName} (${item.formattedSize}): ${item.resolveMediaUrl(baseUrl)}"
            }

            val shareIntent = if (streamUris.size == 1 && items.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    val onlyItem = items.first()
                    type = when (onlyItem.mediaType) {
                        MediaType.IMAGE -> "image/*"
                        MediaType.VIDEO -> "video/*"
                        MediaType.DOCUMENT -> "*/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, streamUris.first())
                    putExtra(Intent.EXTRA_SUBJECT, "Shared from GalleryHub: ${onlyItem.originalName}")
                    putExtra(Intent.EXTRA_TEXT, summaryText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else if (streamUris.isNotEmpty()) {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, streamUris)
                    putExtra(Intent.EXTRA_SUBJECT, "GalleryHub Batch Share (${items.size} files)")
                    putExtra(Intent.EXTRA_TEXT, "Shared from GalleryHub (${items.size} files):\n$summaryText")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "GalleryHub Batch Share (${items.size} files)")
                    putExtra(Intent.EXTRA_TEXT, "Shared from GalleryHub (${items.size} files):\n$summaryText")
                }
            }

            _uiState.update {
                it.copy(
                    isSelectionMode = false,
                    selectedIds = emptySet(),
                    toastMessage = "Sharing ${items.size} selected file(s)"
                )
            }
            onLaunchIntent(Intent.createChooser(shareIntent, "Share ${items.size} File(s)"))
        }
    }

    fun bulkDeleteSelected() {
        val state = _uiState.value
        val items = state.allMedia.filter { it.id in state.selectedIds }
        if (items.isEmpty()) return
        val accountKey = currentAccountKey()
        val targetIds = items.map { it.id }.toSet()
        val nowIso = nowIsoString()

        // Optimistic instant UI update (zero lag)
        _uiState.update { current ->
            val movedToTrash = items.map { it.copy(isDeleted = true, deletedAt = nowIso, folderId = null) }
            current.copy(
                allMedia = current.allMedia.filterNot { it.id in targetIds },
                trashMedia = movedToTrash + current.trashMedia,
                isSelectionMode = false,
                selectedIds = emptySet(),
                toastMessage = "${items.size} file(s) moved to Trash"
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val queued = repository.softDeleteMedia(items, isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            _uiState.update { current ->
                current.copy(
                    pendingSyncCount = pendingCount,
                    toastMessage = if (queued) "${items.size} file(s) trashed · Queued for cloud sync" else current.toastMessage
                )
            }
            if (!queued) refreshWorkspaceData(silent = true)
        }
    }

    fun bulkMoveSelectedToFolder(folderId: String?) {
        val state = _uiState.value
        val items = state.allMedia.filter { it.id in state.selectedIds }
        if (items.isEmpty()) return
        val accountKey = currentAccountKey()
        val targetIds = items.map { it.id }.toSet()
        val folderName = state.myFolders.firstOrNull { it.id == folderId }?.name ?: "Root Gallery"

        // Optimistic instant UI update
        _uiState.update { current ->
            current.copy(
                allMedia = current.allMedia.map { m -> if (m.id in targetIds) m.copy(folderId = folderId) else m },
                isSelectionMode = false,
                selectedIds = emptySet(),
                toastMessage = "Moved ${items.size} file(s) to $folderName"
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val queued = repository.moveFilesToFolder(folderId, items, isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            _uiState.update { current ->
                current.copy(
                    pendingSyncCount = pendingCount,
                    toastMessage = if (queued) "Moved ${items.size} file(s) · Queued for cloud sync" else current.toastMessage
                )
            }
            if (!queued) refreshWorkspaceData(silent = true)
        }
    }

    /**
     * Batch-pins all currently selected items into the Offline Vault in parallel.
     */
    fun bulkPinSelectedOffline() {
        val state = _uiState.value
        val items = state.allMedia.filter { it.id in state.selectedIds }
        if (items.isEmpty()) return
        val baseUrl = state.serverHealth.baseUrl
        val targetIds = items.map { it.id }.toSet()

        _uiState.update {
            it.copy(
                downloadingOfflineIds = it.downloadingOfflineIds + targetIds,
                isSelectionMode = false,
                selectedIds = emptySet(),
                toastMessage = "Saving ${items.size} file(s) for offline access…"
            )
        }

        viewModelScope.launch {
            var successCount = 0
            val savedPaths = mutableMapOf<String, String>()
            coroutineScope {
                items.map { item ->
                    async {
                        repository.pinItemOffline(item, baseUrl).onSuccess { file ->
                            synchronized(savedPaths) {
                                savedPaths[item.id] = file.absolutePath
                                successCount++
                            }
                        }
                    }
                }.forEach { it.await() }
            }
            val stats = repository.getOfflineVaultStats()
            _uiState.update { current ->
                current.copy(
                    downloadingOfflineIds = current.downloadingOfflineIds - targetIds,
                    offlineVaultStats = stats,
                    allMedia = current.allMedia.map { m ->
                        val path = savedPaths[m.id]
                        if (path != null) {
                            m.copy(offlineState = OfflineAvailabilityState.PINNED, offlineFilePath = path)
                        } else m
                    },
                    toastMessage = if (successCount > 0) {
                        "$successCount file(s) now available offline"
                    } else {
                        "Could not download files offline while disconnected"
                    }
                )
            }
            persistCurrentUiSnapshotAsync()
        }
    }

    fun exportSelectedAsZip(onZipReadyToShare: (Intent) -> Unit) {
        val state = _uiState.value
        val items = state.allMedia.filter { it.id in state.selectedIds }
        if (items.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isZipExporting = true) }
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val res = repository.buildOrDownloadZipArchive(items, isSandbox)
            _uiState.update { it.copy(isZipExporting = false) }
            res.fold(
                onSuccess = { zipFile ->
                    val app = getApplication<Application>()
                    val uri = FileProvider.getUriForFile(
                        app,
                        "${app.packageName}.fileprovider",
                        zipFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "GalleryHub Archive (${items.size} files)")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    _uiState.update {
                        it.copy(
                            isSelectionMode = false,
                            selectedIds = emptySet(),
                            toastMessage = "ZIP archive ready (${zipFile.name})"
                        )
                    }
                    onZipReadyToShare(Intent.createChooser(shareIntent, "Save or Share ZIP Archive"))
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(toastMessage = "ZIP export failed: ${err.localizedMessage}")
                    }
                }
            )
        }
    }

    // --- Guest Mode Guard & Upload Pipeline (Multi-Format + 50MB Check) ---
    fun setGuestRestrictionDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isGuestRestrictionDialogOpen = open) }
    }

    private fun requireAuthenticatedAccount(): Boolean {
        val session = _uiState.value.userSession
        if (session == null || session.isOfflineSandbox) {
            _uiState.update {
                it.copy(
                    isUploadModalOpen = false,
                    isGuestRestrictionDialogOpen = true,
                    toastMessage = "Please sign in or create an account to upload files"
                )
            }
            return false
        }
        return true
    }

    fun setUploadModalOpen(open: Boolean) {
        if (open && !requireAuthenticatedAccount()) return
        _uiState.update {
            it.copy(
                isUploadModalOpen = open,
                uploadCandidates = if (open) it.uploadCandidates else emptyList()
            )
        }
    }

    fun addUploadUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!requireAuthenticatedAccount()) return
        val accepted = mutableListOf<UploadCandidate>()
        var errorMsg: String? = null
        for (uri in uris) {
            repository.inspectUriForUpload(uri).fold(
                onSuccess = { accepted.add(it) },
                onFailure = { errorMsg = it.message }
            )
        }
        _uiState.update { current ->
            val combined = (current.uploadCandidates + accepted).distinctBy { it.uri }
            current.copy(
                isUploadModalOpen = true,
                uploadCandidates = combined,
                toastMessage = errorMsg ?: if (accepted.isNotEmpty()) "${accepted.size} file(s) staged for upload" else current.toastMessage
            )
        }
    }

    fun removeUploadCandidate(uri: Uri) {
        _uiState.update { state ->
            state.copy(uploadCandidates = state.uploadCandidates.filterNot { it.uri == uri })
        }
    }

    fun executeBatchUpload(targetFolderIdOverride: String? = null) {
        if (!requireAuthenticatedAccount()) return
        val state = _uiState.value
        val candidates = state.uploadCandidates
        if (candidates.isEmpty()) return
        val folderId = targetFolderIdOverride ?: state.activeFolder?.id
        enqueueAndRunUploads(candidates, folderId, closeModalOnStart = true)
    }

    fun uploadImagesFromPicker(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!requireAuthenticatedAccount()) return
        val accepted = mutableListOf<UploadCandidate>()
        var errorMsg: String? = null
        for (uri in uris) {
            repository.inspectUriForUpload(uri).fold(
                onSuccess = { accepted.add(it) },
                onFailure = { errorMsg = it.message }
            )
        }
        if (accepted.isEmpty()) {
            if (!errorMsg.isNullOrBlank()) {
                _uiState.update { it.copy(toastMessage = errorMsg) }
            }
            return
        }
        enqueueAndRunUploads(accepted, _uiState.value.activeFolder?.id, closeModalOnStart = false)
    }

    private fun enqueueAndRunUploads(
        candidates: List<UploadCandidate>,
        folderId: String?,
        closeModalOnStart: Boolean
    ) {
        val state = _uiState.value
        val waitingForWifi = state.wifiOnlyUploads && !repository.isWifiConnected()
        val queuedItems = candidates.map { c ->
            UploadQueueItem(
                id = "uq_${UUID.randomUUID().toString().take(8)}",
                uriString = c.uri.toString(),
                fileName = c.displayName,
                mediaType = c.mediaType,
                progressPercent = 0,
                status = if (waitingForWifi) UploadQueueStatus.WAITING_WIFI else UploadQueueStatus.UPLOADING,
                targetFolderId = folderId
            )
        }

        if (waitingForWifi) {
            _uiState.update { current ->
                current.copy(
                    isUploadModalOpen = if (closeModalOnStart) false else current.isUploadModalOpen,
                    uploadCandidates = if (closeModalOnStart) emptyList() else current.uploadCandidates,
                    uploadQueue = queuedItems + current.uploadQueue,
                    toastMessage = "${queuedItems.size} file(s) queued · Waiting for Wi-Fi connection"
                )
            }
            return
        }

        val queueIdsByIndex = queuedItems.map { it.id }
        _uiState.update { current ->
            current.copy(
                isUploading = true,
                isUploadModalOpen = if (closeModalOnStart) false else current.isUploadModalOpen,
                uploadCandidates = if (closeModalOnStart) emptyList() else current.uploadCandidates,
                uploadQueue = queuedItems + current.uploadQueue
            )
        }

        viewModelScope.launch {
            val isSandbox = _uiState.value.userSession?.isOfflineSandbox == true
            val res = repository.uploadBatch(
                candidates = candidates,
                targetFolderId = folderId,
                isSandbox = isSandbox,
                onFileProgress = { idx, pct ->
                    val qId = queueIdsByIndex.getOrNull(idx) ?: return@uploadBatch
                    _uiState.update { current ->
                        current.copy(
                            uploadQueue = current.uploadQueue.map { q ->
                                if (q.id == qId) {
                                    q.copy(
                                        progressPercent = pct.coerceIn(0, 100),
                                        status = if (pct >= 100) UploadQueueStatus.COMPLETED else UploadQueueStatus.UPLOADING
                                    )
                                } else q
                            }
                        )
                    }
                }
            )
            res.fold(
                onSuccess = { count ->
                    val stats = repository.getOfflineVaultStats()
                    val qIdSet = queueIdsByIndex.toSet()
                    _uiState.update { current ->
                        current.copy(
                            isUploading = false,
                            offlineVaultStats = stats,
                            uploadQueue = current.uploadQueue.map { q ->
                                if (q.id in qIdSet) q.copy(progressPercent = 100, status = UploadQueueStatus.COMPLETED) else q
                            },
                            toastMessage = "Uploaded & pinned $count file(s) offline"
                        )
                    }
                    refreshWorkspaceData()
                },
                onFailure = { err ->
                    val qIdSet = queueIdsByIndex.toSet()
                    _uiState.update { current ->
                        current.copy(
                            isUploading = false,
                            uploadQueue = current.uploadQueue.map { q ->
                                if (q.id in qIdSet) {
                                    q.copy(status = UploadQueueStatus.FAILED, errorMessage = err.message ?: "Upload failed")
                                } else q
                            },
                            toastMessage = err.message ?: "Upload failed · Tap Retry when back online"
                        )
                    }
                }
            )
        }
    }

    fun retryFailedOrQueuedUploads() {
        val pending = _uiState.value.uploadQueue.filter {
            it.status == UploadQueueStatus.FAILED || it.status == UploadQueueStatus.WAITING_WIFI
        }
        if (pending.isEmpty()) return
        if (_uiState.value.wifiOnlyUploads && !repository.isWifiConnected()) {
            _uiState.update { it.copy(toastMessage = "Wi-Fi-only uploads enabled · Connect to Wi-Fi to resume") }
            return
        }
        val candidates = pending.mapNotNull { item ->
            runCatching { Uri.parse(item.uriString) }.getOrNull()?.let { uri ->
                repository.inspectUriForUpload(uri).getOrNull()
            }
        }
        val pendingIds = pending.map { it.id }.toSet()
        _uiState.update { current ->
            current.copy(uploadQueue = current.uploadQueue.filterNot { it.id in pendingIds })
        }
        if (candidates.isNotEmpty()) {
            enqueueAndRunUploads(candidates, pending.firstOrNull()?.targetFolderId, closeModalOnStart = false)
        }
    }

    fun clearCompletedUploadQueue() {
        _uiState.update { current ->
            current.copy(uploadQueue = current.uploadQueue.filterNot { it.status == UploadQueueStatus.COMPLETED })
        }
    }

    fun handleIncomingExternalShare(uris: List<Uri>) {
        if (uris.isEmpty()) return
        addUploadUris(uris)
    }

    fun updateItemTags(item: UnifiedMediaItem, rawTagsInput: String) {
        val accountKey = currentAccountKey()
        val parsedTags = rawTagsInput
            .split(',', ' ', '#')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()

        // Optimistic instant update
        _uiState.update { current ->
            current.copy(
                allMedia = current.allMedia.map { if (it.id == item.id) it.copy(tags = parsedTags) else it },
                previewItem = current.previewItem?.let { p -> if (p.id == item.id) p.copy(tags = parsedTags) else p },
                toastMessage = "Updated tags for \"${item.originalName}\""
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            repository.updateMediaTags(item.id, parsedTags, accountKey)
        }
        pushMediaOverrideToCloudAsync(item.id)
    }

    // --- Favorites, Inline Rename & Offline File Availability ---
    fun toggleFavorite(item: UnifiedMediaItem) {
        val accountKey = currentAccountKey()
        val nextFav = !item.isFavorite

        // Optimistic zero-lag toggle
        _uiState.update { current ->
            current.copy(
                allMedia = current.allMedia.map { if (it.id == item.id) it.copy(isFavorite = nextFav) else it },
                previewItem = current.previewItem?.let { p -> if (p.id == item.id) p.copy(isFavorite = nextFav) else p }
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            repository.toggleFavorite(accountKey, item.id, nextFav)
        }
        pushMediaOverrideToCloudAsync(item.id)
    }

    fun renameMediaItem(item: UnifiedMediaItem, rawNewName: String) {
        val accountKey = currentAccountKey()
        viewModelScope.launch {
            val res = repository.renameMediaItem(accountKey, item, rawNewName)
            res.fold(
                onSuccess = { finalName ->
                    _uiState.update { current ->
                        current.copy(
                            allMedia = current.allMedia.map { if (it.id == item.id) it.copy(originalName = finalName) else it },
                            previewItem = current.previewItem?.let { p -> if (p.id == item.id) p.copy(originalName = finalName) else p },
                            toastMessage = "Renamed to \"$finalName\""
                        )
                    }
                    persistCurrentUiSnapshotAsync()
                    pushMediaOverrideToCloudAsync(item.id)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(toastMessage = err.message ?: "Could not rename file") }
                }
            )
        }
    }

    fun toggleOfflinePin(item: UnifiedMediaItem) {
        if (item.id in _uiState.value.downloadingOfflineIds) return
        val baseUrl = _uiState.value.serverHealth.baseUrl

        if (item.offlineState == OfflineAvailabilityState.PINNED) {
            // Unpin and remove local vault file
            viewModelScope.launch {
                repository.unpinItemOffline(item.id)
                val stats = repository.getOfflineVaultStats()
                _uiState.update { current ->
                    current.copy(
                        offlineVaultStats = stats,
                        allMedia = current.allMedia.map { m ->
                            if (m.id == item.id) {
                                m.copy(offlineState = OfflineAvailabilityState.NONE, offlineFilePath = null)
                            } else m
                        },
                        previewItem = current.previewItem?.let { p ->
                            if (p.id == item.id) {
                                p.copy(offlineState = OfflineAvailabilityState.NONE, offlineFilePath = null)
                            } else p
                        },
                        toastMessage = "Removed \"${item.originalName}\" from Offline Vault"
                    )
                }
                persistCurrentUiSnapshotAsync()
            }
        } else {
            // Pin & download into protected Offline Vault
            _uiState.update {
                it.copy(downloadingOfflineIds = it.downloadingOfflineIds + item.id)
            }
            viewModelScope.launch {
                val res = repository.pinItemOffline(item, baseUrl)
                val stats = repository.getOfflineVaultStats()
                res.fold(
                    onSuccess = { file ->
                        _uiState.update { current ->
                            current.copy(
                                downloadingOfflineIds = current.downloadingOfflineIds - item.id,
                                offlineVaultStats = stats,
                                allMedia = current.allMedia.map { m ->
                                    if (m.id == item.id) {
                                        m.copy(
                                            offlineState = OfflineAvailabilityState.PINNED,
                                            offlineFilePath = file.absolutePath
                                        )
                                    } else m
                                },
                                previewItem = current.previewItem?.let { p ->
                                    if (p.id == item.id) {
                                        p.copy(
                                            offlineState = OfflineAvailabilityState.PINNED,
                                            offlineFilePath = file.absolutePath
                                        )
                                    } else p
                                },
                                toastMessage = "Pinned \"${item.originalName}\" for offline access"
                            )
                        }
                        persistCurrentUiSnapshotAsync()
                    },
                    onFailure = { err ->
                        if (err is MissingServerFileException) {
                            _uiState.update {
                                it.copy(
                                    downloadingOfflineIds = it.downloadingOfflineIds - item.id,
                                    offlineVaultStats = stats,
                                    missingServerMediaIds = it.missingServerMediaIds + item.id,
                                    recoveringMissingMediaItem = item
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    downloadingOfflineIds = it.downloadingOfflineIds - item.id,
                                    offlineVaultStats = stats,
                                    toastMessage = "Offline save failed: ${err.localizedMessage ?: "Check connection"}"
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    fun openMissingFileRecovery(item: UnifiedMediaItem) {
        _uiState.update {
            it.copy(
                missingServerMediaIds = it.missingServerMediaIds + item.id,
                recoveringMissingMediaItem = item
            )
        }
    }

    fun dismissMissingFileRecovery() {
        _uiState.update { it.copy(recoveringMissingMediaItem = null) }
    }

    fun relinkMissingMediaFromUri(item: UnifiedMediaItem, pickedUri: Uri) {
        val state = _uiState.value
        val isSandbox = state.userSession?.isOfflineSandbox == true
        val accountKey = currentAccountKey()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRelinkingMissingFile = true,
                    recoveringMissingMediaItem = null,
                    toastMessage = "Restoring \"${item.originalName}\"…"
                )
            }
            val res = repository.relinkMissingMediaFromDevice(item, pickedUri, isSandbox, accountKey)
            val stats = repository.getOfflineVaultStats()
            res.fold(
                onSuccess = { restored ->
                    _uiState.update { current ->
                        val updatedList = current.allMedia.map { existing ->
                            if (existing.id == item.id) restored else existing
                        }
                        current.copy(
                            isRelinkingMissingFile = false,
                            offlineVaultStats = stats,
                            missingServerMediaIds = current.missingServerMediaIds - item.id - restored.id,
                            allMedia = updatedList,
                            previewItem = current.previewItem?.let { p ->
                                if (p.id == item.id) restored else p
                            },
                            toastMessage = "Restored & pinned \"${restored.originalName}\" offline"
                        )
                    }
                    persistCurrentUiSnapshotAsync()
                    refreshWorkspaceData(silent = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isRelinkingMissingFile = false,
                            offlineVaultStats = stats,
                            toastMessage = "Re-link failed: ${err.localizedMessage ?: "Could not read file"}"
                        )
                    }
                }
            )
        }
    }

    fun removeMissingMediaItem(item: UnifiedMediaItem) {
        val state = _uiState.value
        val isSandbox = state.userSession?.isOfflineSandbox == true
        val accountKey = currentAccountKey()
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    recoveringMissingMediaItem = null,
                    missingServerMediaIds = current.missingServerMediaIds - item.id,
                    allMedia = current.allMedia.filterNot { it.id == item.id },
                    previewItem = if (current.previewItem?.id == item.id) null else current.previewItem,
                    toastMessage = "Removed missing entry \"${item.originalName}\""
                )
            }
            repository.removeMissingServerMedia(item, isSandbox, accountKey)
            val stats = repository.getOfflineVaultStats()
            _uiState.update { it.copy(offlineVaultStats = stats) }
            persistCurrentUiSnapshotAsync()
        }
    }

    /**
     * Automatically caches a remote file in the background when viewed in the Lightbox
     * (respecting the user's auto-cache setting and 150 MB LRU budget).
     */
    fun autoCacheViewedItem(item: UnifiedMediaItem) {
        if (item.offlineState == OfflineAvailabilityState.PINNED || item.isAvailableOffline) return
        val baseUrl = _uiState.value.serverHealth.baseUrl
        viewModelScope.launch {
            val file = repository.autoCacheViewedItem(item, baseUrl) ?: return@launch
            val stats = repository.getOfflineVaultStats()
            _uiState.update { current ->
                current.copy(
                    offlineVaultStats = stats,
                    allMedia = current.allMedia.map { m ->
                        if (m.id == item.id && m.offlineState != OfflineAvailabilityState.PINNED) {
                            m.copy(
                                offlineState = OfflineAvailabilityState.AUTO_CACHED,
                                offlineFilePath = file.absolutePath
                            )
                        } else m
                    },
                    previewItem = current.previewItem?.let { p ->
                        if (p.id == item.id && p.offlineState != OfflineAvailabilityState.PINNED) {
                            p.copy(
                                offlineState = OfflineAvailabilityState.AUTO_CACHED,
                                offlineFilePath = file.absolutePath
                            )
                        } else p
                    }
                )
            }
        }
    }

    fun setAutoCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val stats = repository.setAutoCacheEnabled(enabled)
            _uiState.update {
                it.copy(
                    offlineVaultStats = stats,
                    toastMessage = if (enabled) "Auto-cache viewed files enabled" else "Auto-cache viewed files paused"
                )
            }
        }
    }

    fun downloadAllFavoritesOffline() {
        val state = _uiState.value
        val favsToDownload = state.allMedia.filter {
            !it.isDeleted && it.isFavorite && it.offlineState != OfflineAvailabilityState.PINNED
        }
        if (favsToDownload.isEmpty()) {
            _uiState.update { it.copy(toastMessage = "All favorite files are already pinned offline") }
            return
        }
        val baseUrl = state.serverHealth.baseUrl
        val targetIds = favsToDownload.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                downloadingOfflineIds = it.downloadingOfflineIds + targetIds,
                toastMessage = "Pinning ${favsToDownload.size} favorite(s) offline…"
            )
        }
        viewModelScope.launch {
            val savedPaths = mutableMapOf<String, String>()
            coroutineScope {
                favsToDownload.map { item ->
                    async {
                        repository.pinItemOffline(item, baseUrl).onSuccess { f ->
                            synchronized(savedPaths) { savedPaths[item.id] = f.absolutePath }
                        }
                    }
                }.forEach { it.await() }
            }
            val stats = repository.getOfflineVaultStats()
            _uiState.update { current ->
                current.copy(
                    downloadingOfflineIds = current.downloadingOfflineIds - targetIds,
                    offlineVaultStats = stats,
                    allMedia = current.allMedia.map { m ->
                        val path = savedPaths[m.id]
                        if (path != null) m.copy(offlineState = OfflineAvailabilityState.PINNED, offlineFilePath = path) else m
                    },
                    toastMessage = "Pinned ${savedPaths.size} favorite file(s) for offline viewing"
                )
            }
            persistCurrentUiSnapshotAsync()
        }
    }

    fun clearAutoCacheOffline() {
        viewModelScope.launch {
            val cleared = repository.clearAutoCache()
            val stats = repository.getOfflineVaultStats()
            _uiState.update { current ->
                current.copy(
                    offlineVaultStats = stats,
                    allMedia = current.allMedia.map { m ->
                        if (m.offlineState == OfflineAvailabilityState.AUTO_CACHED) {
                            m.copy(offlineState = OfflineAvailabilityState.NONE, offlineFilePath = null)
                        } else m
                    },
                    toastMessage = "Cleared $cleared auto-cached file(s)"
                )
            }
            persistCurrentUiSnapshotAsync()
        }
    }

    fun clearAllOfflineVaultFiles() {
        viewModelScope.launch {
            val cleared = repository.clearAllOfflineFiles()
            val stats = repository.getOfflineVaultStats()
            _uiState.update { current ->
                current.copy(
                    offlineVaultStats = stats,
                    allMedia = current.allMedia.map { m ->
                        if (m.offlineFilePath != null) {
                            m.copy(offlineState = OfflineAvailabilityState.NONE, offlineFilePath = null)
                        } else m
                    },
                    toastMessage = "Cleared $cleared offline file(s) from device storage"
                )
            }
            persistCurrentUiSnapshotAsync()
        }
    }

    // --- Lightbox, Slideshow, Folder Move & Image Editor ---
    fun openMediaPreview(item: UnifiedMediaItem?) {
        _uiState.update {
            it.copy(
                previewItem = item,
                isSlideshowPlaying = false
            )
        }
        if (item != null) {
            autoCacheViewedItem(item)
        }
    }

    fun navigatePreview(delta: Int) {
        val list = filteredMedia.value
        val current = _uiState.value.previewItem ?: return
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx < 0) return
        val nextIdx = (idx + delta + list.size) % list.size
        val nextItem = list[nextIdx]
        _uiState.update { it.copy(previewItem = nextItem) }
        autoCacheViewedItem(nextItem)
    }

    fun selectPreviewItem(item: UnifiedMediaItem) {
        if (_uiState.value.previewItem?.id == item.id) return
        _uiState.update { it.copy(previewItem = item) }
        autoCacheViewedItem(item)
    }

    fun toggleSlideshow() {
        _uiState.update { it.copy(isSlideshowPlaying = !it.isSlideshowPlaying) }
    }

    fun pauseSlideshow(reasonMessage: String? = null) {
        if (!_uiState.value.isSlideshowPlaying) return
        _uiState.update {
            it.copy(
                isSlideshowPlaying = false,
                toastMessage = reasonMessage ?: it.toastMessage
            )
        }
    }

    fun openImageEditor(item: UnifiedMediaItem?) {
        _uiState.update {
            it.copy(
                editingImageItem = item,
                isSlideshowPlaying = false
            )
        }
    }

    fun saveEditedImageCopy(bitmap: Bitmap) {
        val state = _uiState.value
        val original = state.editingImageItem ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val result = repository.uploadEditedImageBitmap(bitmap, original, isSandbox)
            result.fold(
                onSuccess = { newItem ->
                    val updatedVaultStats = repository.getOfflineVaultStats()
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            editingImageItem = null,
                            previewItem = newItem,
                            offlineVaultStats = updatedVaultStats,
                            toastMessage = "Saved & auto-pinned edited copy \"${newItem.originalName}\""
                        )
                    }
                    refreshWorkspaceData(silent = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            toastMessage = "Could not save edited image: ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun moveSingleMediaToFolder(item: UnifiedMediaItem, folderId: String?) {
        val state = _uiState.value
        val accountKey = currentAccountKey()
        val destName = if (folderId == null) "Root Gallery" else state.myFolders.firstOrNull { it.id == folderId }?.name ?: "Folder"

        // Optimistic instant move
        _uiState.update { current ->
            current.copy(
                allMedia = current.allMedia.map { if (it.id == item.id) it.copy(folderId = folderId) else it },
                previewItem = current.previewItem?.let { p -> if (p.id == item.id) p.copy(folderId = folderId) else p },
                toastMessage = if (folderId == null) "Removed \"${item.originalName}\" from folder" else "Moved \"${item.originalName}\" to $destName"
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val queued = repository.moveFilesToFolder(folderId, listOf(item), isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            _uiState.update { current ->
                current.copy(
                    pendingSyncCount = pendingCount,
                    toastMessage = if (queued) "Moved locally · Queued for cloud sync" else current.toastMessage
                )
            }
            if (!queued) refreshWorkspaceData(silent = true)
        }
    }

    fun softDeleteSingleMedia(item: UnifiedMediaItem) {
        val state = _uiState.value
        val accountKey = currentAccountKey()
        val nowIso = nowIsoString()

        // Optimistic instant trash
        _uiState.update { current ->
            current.copy(
                allMedia = current.allMedia.filterNot { it.id == item.id },
                trashMedia = listOf(item.copy(isDeleted = true, deletedAt = nowIso, folderId = null)) + current.trashMedia,
                previewItem = if (current.previewItem?.id == item.id) null else current.previewItem,
                toastMessage = "Moved \"${item.originalName}\" to Trash"
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val queued = repository.softDeleteMedia(listOf(item), isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            _uiState.update { current ->
                current.copy(
                    pendingSyncCount = pendingCount,
                    toastMessage = if (queued) "Moved to Trash · Queued for cloud sync" else current.toastMessage
                )
            }
            if (!queued) refreshWorkspaceData(silent = true)
        }
    }

    // --- Trash Operations (Optimistic + Parallel Empty Trash) ---
    fun restoreTrashItem(item: UnifiedMediaItem) {
        val state = _uiState.value
        val accountKey = currentAccountKey()

        _uiState.update { current ->
            val restored = item.copy(isDeleted = false, deletedAt = null)
            current.copy(
                trashMedia = current.trashMedia.filterNot { it.id == item.id },
                allMedia = listOf(restored) + current.allMedia,
                toastMessage = "Restored \"${item.originalName}\" to Gallery"
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val queued = repository.restoreFromTrash(item, isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            _uiState.update { it.copy(pendingSyncCount = pendingCount) }
            if (!queued) refreshWorkspaceData(silent = true)
        }
    }

    fun permanentDeleteTrashItem(item: UnifiedMediaItem) {
        val state = _uiState.value
        val accountKey = currentAccountKey()

        _uiState.update { current ->
            current.copy(
                trashMedia = current.trashMedia.filterNot { it.id == item.id },
                toastMessage = "Permanently erased \"${item.originalName}\""
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val queued = repository.permanentDeleteMedia(item, isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            val stats = repository.getOfflineVaultStats()
            _uiState.update { it.copy(pendingSyncCount = pendingCount, offlineVaultStats = stats) }
            if (!queued) refreshWorkspaceData(silent = true)
        }
    }

    fun emptyTrashAll() {
        val state = _uiState.value
        val trashed = state.trashMedia
        if (trashed.isEmpty()) return
        val accountKey = currentAccountKey()

        _uiState.update {
            it.copy(
                trashMedia = emptyList(),
                toastMessage = "Emptied Trash (${trashed.size} files permanently erased)"
            )
        }
        persistCurrentUiSnapshotAsync()

        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            coroutineScope {
                trashed.map { item ->
                    async { repository.permanentDeleteMedia(item, isSandbox, accountKey) }
                }.forEach { it.await() }
            }
            val pendingCount = repository.getPendingSyncCount(accountKey)
            val stats = repository.getOfflineVaultStats()
            _uiState.update { it.copy(pendingSyncCount = pendingCount, offlineVaultStats = stats) }
            refreshWorkspaceData(silent = true)
        }
    }

    // --- Folders & Collaborators (RBAC) ---
    fun createFolder(name: String, colorHex: String) {
        if (!requireAuthenticatedAccount()) return
        val accountKey = currentAccountKey()
        viewModelScope.launch {
            val state = _uiState.value
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val res = repository.createFolder(name, colorHex, isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            res.fold(
                onSuccess = { folder ->
                    _uiState.update {
                        it.copy(
                            myFolders = listOf(folder) + it.myFolders.filterNot { f -> f.id == folder.id },
                            pendingSyncCount = pendingCount,
                            toastMessage = "Created folder \"${folder.name}\""
                        )
                    }
                    persistCurrentUiSnapshotAsync()
                    refreshWorkspaceData(silent = true)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(toastMessage = err.message ?: "Could not create folder") }
                }
            )
        }
    }

    fun updateFolder(folderId: String, name: String, colorHex: String) {
        val accountKey = currentAccountKey()
        _uiState.update { current ->
            current.copy(
                myFolders = current.myFolders.map { if (it.id == folderId) it.copy(name = name, color = colorHex) else it },
                toastMessage = "Updated folder \"$name\""
            )
        }
        persistCurrentUiSnapshotAsync()
        viewModelScope.launch {
            val state = _uiState.value
            val isSandbox = state.userSession?.isOfflineSandbox == true
            repository.updateFolder(folderId, name, colorHex, isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            _uiState.update { it.copy(pendingSyncCount = pendingCount) }
            refreshWorkspaceData(silent = true)
        }
    }

    fun deleteFolder(folder: FolderItem) {
        val accountKey = currentAccountKey()
        _uiState.update { current ->
            current.copy(
                myFolders = current.myFolders.filterNot { it.id == folder.id },
                allMedia = current.allMedia.map { if (it.folderId == folder.id) it.copy(folderId = null) else it },
                activeFolder = if (current.activeFolder?.id == folder.id) null else current.activeFolder,
                toastMessage = "Deleted folder \"${folder.name}\" (files kept in Gallery)"
            )
        }
        persistCurrentUiSnapshotAsync()
        viewModelScope.launch {
            val state = _uiState.value
            val isSandbox = state.userSession?.isOfflineSandbox == true
            repository.deleteFolder(folder.id, isSandbox, accountKey)
            val pendingCount = repository.getPendingSyncCount(accountKey)
            _uiState.update { it.copy(pendingSyncCount = pendingCount) }
            refreshWorkspaceData(silent = true)
        }
    }

    fun setManagingCollaboratorsFolder(folder: FolderItem?) {
        _uiState.update { it.copy(managingCollaboratorsFolder = folder) }
    }

    fun inviteFolderCollaborator(folderId: String, email: String, permission: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val currentEmail = state.userSession?.email.orEmpty()
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val res = repository.shareFolderWithCollaborator(folderId, email, permission, currentEmail, isSandbox)
            res.fold(
                onSuccess = { updatedFolder ->
                    _uiState.update {
                        it.copy(
                            managingCollaboratorsFolder = updatedFolder,
                            toastMessage = "Shared with $email ($permission access)"
                        )
                    }
                    refreshWorkspaceData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(toastMessage = err.message ?: "Share failed") }
                }
            )
        }
    }

    fun revokeFolderCollaborator(folderId: String, collaboratorUserId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val res = repository.removeFolderCollaborator(folderId, collaboratorUserId, isSandbox)
            res.onSuccess { updatedFolder ->
                _uiState.update {
                    it.copy(
                        managingCollaboratorsFolder = updatedFolder,
                        toastMessage = "Collaborator access revoked"
                    )
                }
                refreshWorkspaceData()
            }
        }
    }

    // --- Public Share Links & Cryptographic Token Explorer ---
    fun openShareModalForMedia(item: UnifiedMediaItem?) {
        _uiState.update {
            it.copy(
                sharingMediaItem = item,
                lastCreatedShareLink = null
            )
        }
    }

    fun generateShareLink(password: String?, expiresInDays: Int?) {
        val state = _uiState.value
        val item = state.sharingMediaItem ?: return
        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            val res = repository.createPublicShareLink(item, password, expiresInDays, isSandbox)
            res.fold(
                onSuccess = { link ->
                    _uiState.update {
                        it.copy(
                            lastCreatedShareLink = link,
                            toastMessage = "Generated 32-byte cryptographic share link"
                        )
                    }
                    refreshWorkspaceData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(toastMessage = err.message ?: "Failed to create share link") }
                }
            )
        }
    }

    fun revokeShareLink(link: ShareLinkItem) {
        viewModelScope.launch {
            val state = _uiState.value
            val isSandbox = state.userSession?.isOfflineSandbox == true
            repository.revokeShareLink(link.id, isSandbox)
            _uiState.update { it.copy(toastMessage = "Revoked link for \"${link.fileName}\"") }
            refreshWorkspaceData()
        }
    }

    fun inspectPublicShareToken(tokenOrUrl: String, password: String? = null) {
        if (tokenOrUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(publicShareViewState = PublicShareViewState.Loading) }
            val res = repository.inspectAndUnlockPublicToken(tokenOrUrl, password)
            res.fold(
                onSuccess = { (link, media) ->
                    _uiState.update {
                        it.copy(publicShareViewState = PublicShareViewState.Unlocked(link, media))
                    }
                    refreshWorkspaceData()
                },
                onFailure = { err ->
                    if (err is SecurityException && err.message == "PASSWORD_REQUIRED") {
                        _uiState.update {
                            it.copy(
                                publicShareViewState = PublicShareViewState.PasswordRequired(tokenOrUrl)
                            )
                        }
                    } else if (err is IllegalArgumentException && err.message?.contains("401") == true) {
                        _uiState.update {
                            it.copy(
                                publicShareViewState = PublicShareViewState.PasswordRequired(
                                    token = tokenOrUrl,
                                    errorMessage = "Incorrect password. Please try again."
                                )
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                publicShareViewState = PublicShareViewState.Error(
                                    err.message ?: "Link unavailable or expired."
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    fun resetPublicShareExplorer() {
        _uiState.update { it.copy(publicShareViewState = PublicShareViewState.Idle) }
    }

    /**
     * Handles any tapped or pasted GalleryHub share link (`https://.../s/...`, `/api/share/...`,
     * `/share/...`, `galleryhub://s/...`) directly in the full-screen viewer — works even when signed out.
     */
    fun handleIncomingShareLink(rawUrlOrToken: String, password: String? = null) {
        val trimmed = rawUrlOrToken.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeDeepLinkUrl = trimmed,
                    deepLinkShareState = PublicShareViewState.Loading,
                    isSavingDeepLinkOffline = false,
                    isDeepLinkSavedOffline = false
                )
            }
            val res = repository.inspectAndUnlockPublicToken(trimmed, password)
            res.fold(
                onSuccess = { (link, media) ->
                    _uiState.update {
                        it.copy(
                            deepLinkShareState = PublicShareViewState.Unlocked(link, media)
                        )
                    }
                    if (_uiState.value.userSession != null) {
                        refreshWorkspaceData()
                    }
                },
                onFailure = { err ->
                    when {
                        err is SecurityException && err.message == "PASSWORD_REQUIRED" -> {
                            _uiState.update {
                                it.copy(
                                    deepLinkShareState = PublicShareViewState.PasswordRequired(trimmed)
                                )
                            }
                        }
                        err is IllegalArgumentException && err.message?.contains("401") == true -> {
                            _uiState.update {
                                it.copy(
                                    deepLinkShareState = PublicShareViewState.PasswordRequired(
                                        token = trimmed,
                                        errorMessage = "Incorrect password. Please try again."
                                    )
                                )
                            }
                        }
                        else -> {
                            _uiState.update {
                                it.copy(
                                    deepLinkShareState = PublicShareViewState.Error(
                                        err.message ?: "Link unavailable or expired."
                                    )
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    fun dismissDeepLinkViewer() {
        _uiState.update {
            it.copy(
                activeDeepLinkUrl = null,
                deepLinkShareState = PublicShareViewState.Idle,
                isSavingDeepLinkOffline = false,
                isDeepLinkSavedOffline = false
            )
        }
    }

    fun saveDeepLinkMediaOffline(item: UnifiedMediaItem) {
        if (_uiState.value.isSavingDeepLinkOffline) return
        _uiState.update { it.copy(isSavingDeepLinkOffline = true) }
        viewModelScope.launch {
            val baseUrl = _uiState.value.serverHealth.baseUrl
            val localFile = item.localUri?.removePrefix("file://")?.let { File(it) }
                ?.takeIf { it.exists() && it.length() > 0L }
                ?: File(item.path).takeIf { it.exists() && it.length() > 0L }

            val res = if (localFile != null) {
                repository.pinLocalFileOffline(item, localFile)
            } else {
                repository.pinItemOffline(item, baseUrl)
            }
            val stats = repository.getOfflineVaultStats()
            res.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSavingDeepLinkOffline = false,
                            isDeepLinkSavedOffline = true,
                            offlineVaultStats = stats,
                            toastMessage = "Saved \"${item.originalName}\" to Offline Vault"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSavingDeepLinkOffline = false,
                            offlineVaultStats = stats,
                            toastMessage = "Offline save failed: ${err.localizedMessage ?: "Check connection"}"
                        )
                    }
                }
            )
        }
    }

    fun exportDeepLinkMedia(
        item: UnifiedMediaItem?,
        fallbackUrl: String,
        onLaunchIntent: (Intent) -> Unit
    ) {
        val app = getApplication<Application>()
        val localFile = item?.let { m ->
            listOfNotNull(
                m.offlineFilePath?.let { File(it) },
                m.localUri?.removePrefix("file://")?.let { File(it) },
                File(m.path)
            ).firstOrNull { it.exists() && it.length() > 0L }
        }

        if (localFile != null && item != null) {
            val uri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                localFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = when (item.mediaType) {
                    MediaType.IMAGE -> "image/*"
                    MediaType.VIDEO -> "video/*"
                    MediaType.DOCUMENT -> if (item.extension == "pdf") "application/pdf" else "*/*"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, item.originalName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onLaunchIntent(Intent.createChooser(intent, "Export \"${item.originalName}\""))
        } else {
            shareMediaOrLinkViaAndroidSheet(item = item, shareUrl = fallbackUrl, onLaunchIntent = onLaunchIntent)
        }
    }

    fun shareQrCodeForUrl(
        fileName: String,
        shareUrl: String,
        onLaunchIntent: (Intent) -> Unit
    ) {
        val app = getApplication<Application>()
        val matrix = com.example.util.QrCodeGenerator.encode(shareUrl)
        if (matrix == null) {
            shareMediaOrLinkViaAndroidSheet(null, shareUrl, onLaunchIntent)
            return
        }
        val bmp = com.example.util.QrCodeGenerator.renderToBitmap(matrix, pixelSize = 720)
        val qrDir = File(app.cacheDir, "shared_qr").apply { mkdirs() }
        val qrFile = File(qrDir, "galleryhub_qr_${System.currentTimeMillis()}.png")
        runCatching {
            java.io.FileOutputStream(qrFile).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", qrFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "QR Code for $fileName")
                putExtra(Intent.EXTRA_TEXT, "Scan or tap to open in GalleryHub:\n$shareUrl")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onLaunchIntent(Intent.createChooser(intent, "Share QR Code"))
        }.onFailure {
            shareMediaOrLinkViaAndroidSheet(null, shareUrl, onLaunchIntent)
        }
    }

    fun shareMediaOrLinkViaAndroidSheet(
        item: UnifiedMediaItem?,
        shareUrl: String?,
        onLaunchIntent: (Intent) -> Unit,
        scrubExif: Boolean = _uiState.value.exifScrubDefaultOn
    ) {
        val app = getApplication<Application>()
        if (!shareUrl.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Shared via GalleryHub: ${item?.originalName ?: "Media Link"}")
                putExtra(Intent.EXTRA_TEXT, "Open in GalleryHub:\n$shareUrl")
            }
            onLaunchIntent(Intent.createChooser(intent, "Share via GalleryHub"))
            return
        }
        if (item == null) return
        val baseUrl = _uiState.value.serverHealth.baseUrl
        viewModelScope.launch {
            val preparedFile = repository.prepareShareFileWithOptionalExifScrub(
                item = item,
                serverBaseUrl = baseUrl,
                scrubExif = scrubExif
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                if (preparedFile != null) {
                    val uri = FileProvider.getUriForFile(
                        app,
                        "${app.packageName}.fileprovider",
                        preparedFile
                    )
                    type = when (item.mediaType) {
                        MediaType.IMAGE -> "image/*"
                        MediaType.VIDEO -> "video/*"
                        MediaType.DOCUMENT -> if (item.extension == "pdf") "application/pdf" else "*/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, item.originalName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${item.originalName} (${item.formattedSize}) — ${item.resolveMediaUrl(baseUrl)}"
                    )
                }
            }
            if (scrubExif && item.mediaType == MediaType.IMAGE && preparedFile != null) {
                _uiState.update { it.copy(toastMessage = "EXIF GPS & camera metadata stripped before sharing") }
            }
            onLaunchIntent(Intent.createChooser(intent, "Share via GalleryHub"))
        }
    }

    // --- Activity Feed & AI Assistant Chatbot ---
    fun setActivityDrawerOpen(open: Boolean) {
        _uiState.update { current ->
            val updatedSeen = if (open) {
                current.seenActivityIds + current.activities.map { it.id }
            } else {
                current.seenActivityIds
            }
            current.copy(
                isActivityDrawerOpen = open,
                seenActivityIds = updatedSeen
            )
        }
    }

    fun clearActivityHistory() {
        val current = _uiState.value
        val updatedCleared = (current.clearedActivityIds + current.activities.map { it.id }).take(200).toSet()
        _uiState.update { state ->
            state.copy(
                seenActivityIds = state.seenActivityIds + state.activities.map { it.id },
                clearedActivityIds = updatedCleared,
                activities = emptyList(),
                toastMessage = "Recent activity cleared"
            )
        }
        viewModelScope.launch {
            preferences.saveClearedActivityIds(updatedCleared)
        }
        pushStudioStateToCloudAsync()
    }

    fun setChatbotOpen(open: Boolean) {
        _uiState.update { it.copy(isChatbotOpen = open) }
    }

    fun sendChatMessage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _uiState.value.isChatSending) return
        val userMsg = ChatMessage(
            id = "usr_${UUID.randomUUID().toString().take(6)}",
            text = clean,
            isUser = true
        )
        val botMsgId = "bot_${UUID.randomUUID().toString().take(6)}"
        val streamingPlaceholder = ChatMessage(
            id = botMsgId,
            text = "",
            isUser = false,
            isAiBadge = true,
            suggestions = emptyList()
        )
        val historySnapshot = _uiState.value.chatMessages.takeLast(6).map { it.isUser to it.text }
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg + streamingPlaceholder,
                isChatSending = true
            )
        }
        viewModelScope.launch {
            val state = _uiState.value
            val visibleMedia = state.allMedia.filter { !it.isDeleted && it.id !in state.hiddenMediaIds }
            val topTags = visibleMedia
                .flatMap { it.effectiveTags }
                .groupingBy { it.lowercase() }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(10)
            val libraryContext = GeminiStudioAssistant.LibraryContext(
                totalFiles = visibleMedia.size,
                photoCount = visibleMedia.count { it.mediaType == MediaType.IMAGE },
                videoCount = visibleMedia.count { it.mediaType == MediaType.VIDEO },
                docCount = visibleMedia.count { it.mediaType == MediaType.DOCUMENT },
                favoriteCount = visibleMedia.count { it.isFavorite },
                offlineCount = state.offlineVaultStats.totalOfflineCount,
                hiddenVaultCount = state.hiddenMediaIds.size,
                folderNames = (state.myFolders + state.sharedFolders).map { it.name },
                usedStorageFormatted = formatBytes(state.totalUsedBytes),
                quotaStorageFormatted = formatBytes(GalleryUiState.STORAGE_QUOTA_BYTES),
                recentFilenames = visibleMedia.sortedByDescending { it.timestampMillis }.take(8).map { it.originalName },
                topTags = topTags,
                ocrIndexedCount = state.ocrTextByMediaId.size
            )

            try {
                geminiAssistant.streamReply(
                    userPrompt = clean,
                    libraryContext = libraryContext,
                    recentHistory = historySnapshot
                ).collect { partialText ->
                    _uiState.update { current ->
                        current.copy(
                            chatMessages = current.chatMessages.map { msg ->
                                if (msg.id == botMsgId) msg.copy(text = partialText) else msg
                            }
                        )
                    }
                }
                _uiState.update { current ->
                    current.copy(
                        isChatSending = false,
                        chatMessages = current.chatMessages.map { msg ->
                            if (msg.id == botMsgId) {
                                msg.copy(suggestions = repository.defaultChatbotSuggestions())
                            } else msg
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val friendlyError = "Studio AI couldn't complete that request right now (${e.localizedMessage ?: "check your network connection"}). Please try again."
                _uiState.update { current ->
                    current.copy(
                        isChatSending = false,
                        chatMessages = current.chatMessages.map { msg ->
                            if (msg.id == botMsgId) {
                                msg.copy(
                                    text = friendlyError,
                                    isAiBadge = false,
                                    suggestions = repository.defaultChatbotSuggestions()
                                )
                            } else msg
                        }
                    )
                }
            }
        }
    }

    // --- Settings, Profile, Avatar, Theme, Accent & Danger Zone ---
    fun updateProfileInfo(name: String, email: String, currentPassword: String?, newPassword: String?) {
        val session = _uiState.value.userSession ?: return
        viewModelScope.launch {
            val res = repository.updateProfile(session, name, email, currentPassword, newPassword)
            res.fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            userSession = updated,
                            toastMessage = if (!newPassword.isNullOrBlank()) "Profile and password updated" else "Profile updated"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(toastMessage = err.message ?: "Update failed") }
                }
            )
        }
    }

    fun uploadAvatarImage(uri: Uri) {
        val session = _uiState.value.userSession ?: return
        viewModelScope.launch {
            val res = repository.updateProfileAvatar(session, uri)
            res.onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        userSession = updated,
                        toastMessage = "Profile picture updated"
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val currentState = _uiState.value
        val appContext = getApplication<Application>()
        val targetVariant = DynamicAppIconManager.resolveVariant(
            accentHex = currentState.accentHex,
            themeMode = mode,
            isSystemDark = DynamicAppIconManager.isSystemDarkMode(appContext),
            dynamicSyncEnabled = currentState.dynamicAppIconEnabled
        )
        // Update UI state synchronously on tap for zero-delay visual feedback
        _uiState.update {
            it.copy(
                themeMode = mode,
                hasPendingAppIconChange = false
            )
        }
        viewModelScope.launch {
            preferences.saveThemeMode(mode)
        }
        pushStudioStateToCloudAsync()
        viewModelScope.launch(Dispatchers.IO) {
            DynamicAppIconManager.applyVariantIfChanged(appContext, targetVariant)
        }
    }

    fun setAccentColor(hex: String) {
        val currentState = _uiState.value
        val appContext = getApplication<Application>()
        val targetVariant = DynamicAppIconManager.resolveVariant(
            accentHex = hex,
            themeMode = currentState.themeMode,
            isSystemDark = DynamicAppIconManager.isSystemDarkMode(appContext),
            dynamicSyncEnabled = currentState.dynamicAppIconEnabled
        )
        // Update UI state synchronously on tap for zero-delay visual feedback
        _uiState.update {
            it.copy(
                accentHex = hex,
                hasPendingAppIconChange = false
            )
        }
        viewModelScope.launch {
            preferences.saveAccentColor(hex)
        }
        pushStudioStateToCloudAsync()
        viewModelScope.launch(Dispatchers.IO) {
            DynamicAppIconManager.applyVariantIfChanged(appContext, targetVariant)
        }
    }

    fun setDynamicAppIconEnabled(enabled: Boolean) {
        val currentState = _uiState.value
        val appContext = getApplication<Application>()
        val targetVariant = DynamicAppIconManager.resolveVariant(
            accentHex = currentState.accentHex,
            themeMode = currentState.themeMode,
            isSystemDark = DynamicAppIconManager.isSystemDarkMode(appContext),
            dynamicSyncEnabled = enabled
        )
        _uiState.update {
            it.copy(
                dynamicAppIconEnabled = enabled,
                hasPendingAppIconChange = false,
                toastMessage = if (enabled) {
                    "App drawer icon switched to ${targetVariant.displayLabel}"
                } else {
                    "App drawer icon reset to default Terracotta"
                }
            )
        }
        viewModelScope.launch {
            preferences.saveDynamicAppIconEnabled(enabled)
        }
        viewModelScope.launch(Dispatchers.IO) {
            DynamicAppIconManager.applyVariantIfChanged(appContext, targetVariant)
        }
    }

    /**
     * Safety sync on `MainActivity.onStop()` (e.g., if the OS dark mode changed while in Auto mode).
     */
    fun applyPendingDynamicAppIconOnBackground(context: Context) {
        val state = _uiState.value
        if (!state.isInitialized) return
        val targetVariant = DynamicAppIconManager.resolveVariant(
            accentHex = state.accentHex,
            themeMode = state.themeMode,
            isSystemDark = DynamicAppIconManager.isSystemDarkMode(context),
            dynamicSyncEnabled = state.dynamicAppIconEnabled
        )
        DynamicAppIconManager.applyVariantIfChanged(context, targetVariant)
        _uiState.update { it.copy(hasPendingAppIconChange = false) }
    }

    fun executeDangerZoneDeleteAllFiles() {
        val state = _uiState.value
        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            repository.dangerZoneDeleteAllFiles(state.allMedia + state.trashMedia, isSandbox)
            _uiState.update {
                it.copy(
                    toastMessage = "All files permanently deleted. Storage quota reset to 0 B."
                )
            }
            refreshWorkspaceData()
        }
    }

    // --- Guided 6-Step Onboarding Tour ---
    fun startOnboardingTour() {
        _uiState.update { it.copy(isTourActive = true, tourStepIndex = 0) }
    }

    fun nextOnboardingStep() {
        val current = _uiState.value.tourStepIndex
        if (current >= 5) {
            completeOnboardingTour()
        } else {
            _uiState.update { it.copy(tourStepIndex = current + 1) }
        }
    }

    fun previousOnboardingStep() {
        val current = _uiState.value.tourStepIndex
        if (current > 0) {
            _uiState.update { it.copy(tourStepIndex = current - 1) }
        }
    }

    fun completeOnboardingTour() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            _uiState.update {
                it.copy(
                    onboardingCompleted = true,
                    isTourActive = false,
                    tourStepIndex = 0
                )
            }
        }
    }

    fun showToastMessage(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // =========================================================================
    // Pro Suite Features: Biometric Vault, Cleaner, OCR, Scanners, Video & Widget
    // =========================================================================

    fun openVaultSheet() {
        _uiState.update { it.copy(isVaultSheetOpen = true) }
    }

    fun dismissVaultSheet() {
        _uiState.update { it.copy(isVaultSheetOpen = false) }
    }

    fun unlockVault() {
        _uiState.update { it.copy(isVaultUnlocked = true) }
    }

    fun lockVault() {
        _uiState.update { it.copy(isVaultUnlocked = false) }
    }

    fun setupVaultPin(pin: String) {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return
        viewModelScope.launch {
            preferences.saveVaultPin(pin)
            _uiState.update {
                it.copy(
                    vaultPinHash = SessionPreferences.hashVaultPin(pin),
                    isVaultUnlocked = true,
                    toastMessage = "Private Vault 4-digit PIN saved & unlocked"
                )
            }
            pushStudioStateToCloudAsync()
        }
    }

    fun verifyVaultPin(pin: String): Boolean {
        val ok = SessionPreferences.verifyVaultPinHash(pin, _uiState.value.vaultPinHash)
        if (ok) {
            _uiState.update { it.copy(isVaultUnlocked = true) }
        }
        return ok
    }

    fun resetVaultPin() {
        viewModelScope.launch {
            preferences.clearVaultPin()
            _uiState.update {
                it.copy(
                    vaultPinHash = null,
                    isVaultUnlocked = false,
                    toastMessage = "Vault PIN reset · Set a new PIN next time you open Private Vault"
                )
            }
            pushStudioStateToCloudAsync()
        }
    }

    fun setVaultBiometricEnabled(enabled: Boolean) {
        _uiState.update { it.copy(vaultBiometricEnabled = enabled) }
        viewModelScope.launch { preferences.saveVaultBiometricEnabled(enabled) }
        pushStudioStateToCloudAsync()
    }

    fun toggleHideInVault(item: UnifiedMediaItem) {
        val currentIds = _uiState.value.hiddenMediaIds
        val nowHidden = item.id !in currentIds
        val updated = if (nowHidden) currentIds + item.id else currentIds - item.id
        _uiState.update {
            it.copy(
                hiddenMediaIds = updated,
                previewItem = if (nowHidden && it.previewItem?.id == item.id) null else it.previewItem,
                toastMessage = if (nowHidden) {
                    "Moved \"${item.originalName}\" to Private Vault"
                } else {
                    "Restored \"${item.originalName}\" to Gallery"
                }
            )
        }
        viewModelScope.launch { preferences.saveHiddenMediaIds(updated) }
        pushMediaOverrideToCloudAsync(item.id)
        refreshHomeScreenWidget()
    }

    fun bulkHideSelectedInVault() {
        val selected = _uiState.value.selectedIds
        if (selected.isEmpty()) return
        val updated = _uiState.value.hiddenMediaIds + selected
        _uiState.update {
            it.copy(
                hiddenMediaIds = updated,
                isSelectionMode = false,
                selectedIds = emptySet(),
                toastMessage = "Hidden ${selected.size} item(s) inside Private Vault"
            )
        }
        viewModelScope.launch { preferences.saveHiddenMediaIds(updated) }
        selected.forEach { id -> pushMediaOverrideToCloudAsync(id) }
        refreshHomeScreenWidget()
    }

    fun setExifScrubDefaultOn(enabled: Boolean) {
        _uiState.update {
            it.copy(
                exifScrubDefaultOn = enabled,
                toastMessage = if (enabled) "EXIF Privacy Scrubber enabled by default" else "EXIF Scrubber set to manual per-share toggle"
            )
        }
        viewModelScope.launch { preferences.saveExifScrubDefaultOn(enabled) }
        pushStudioStateToCloudAsync()
    }

    fun setWifiOnlyUploads(enabled: Boolean) {
        _uiState.update {
            it.copy(
                wifiOnlyUploads = enabled,
                toastMessage = if (enabled) "Wi-Fi-only uploads enabled" else "Uploads allowed on Cellular & Wi-Fi"
            )
        }
        viewModelScope.launch { preferences.saveWifiOnlyUploads(enabled) }
        pushStudioStateToCloudAsync()
        if (!enabled) retryFailedOrQueuedUploads()
    }

    // --- Duplicate & Large File Cleaner ---
    fun openCleanerSheet() {
        val report = StorageCleanerEngine.analyze(_uiState.value.allMedia)
        _uiState.update {
            it.copy(
                isCleanerSheetOpen = true,
                storageAuditReport = report
            )
        }
    }

    fun dismissCleanerSheet() {
        _uiState.update { it.copy(isCleanerSheetOpen = false) }
    }

    fun trashItemsFromCleaner(items: List<UnifiedMediaItem>) {
        if (items.isEmpty()) return
        val state = _uiState.value
        val accountKey = currentAccountKey()
        val targetIds = items.map { it.id }.toSet()
        val nowIso = nowIsoString()
        val updatedActive = state.allMedia.filterNot { it.id in targetIds }
        val updatedReport = StorageCleanerEngine.analyze(updatedActive)
        _uiState.update { current ->
            current.copy(
                allMedia = updatedActive,
                trashMedia = items.map { it.copy(isDeleted = true, deletedAt = nowIso, folderId = null) } + current.trashMedia,
                storageAuditReport = updatedReport,
                toastMessage = "Moved ${items.size} file(s) to Trash (${formatBytes(items.sumOf { it.size })} reclaimed)"
            )
        }
        persistCurrentUiSnapshotAsync()
        refreshHomeScreenWidget()
        viewModelScope.launch {
            val isSandbox = state.userSession?.isOfflineSandbox == true
            repository.softDeleteMedia(items, isSandbox, accountKey)
        }
    }

    fun unpinOfflineItemsFromCleaner(items: List<UnifiedMediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val targetIds = items.map { it.id }.toSet()
            for (item in items) {
                repository.unpinItemOffline(item.id)
            }
            val stats = repository.getOfflineVaultStats()
            _uiState.update { current ->
                val updatedMedia = current.allMedia.map { m ->
                    if (m.id in targetIds) m.copy(offlineState = OfflineAvailabilityState.NONE, offlineFilePath = null) else m
                }
                current.copy(
                    allMedia = updatedMedia,
                    offlineVaultStats = stats,
                    storageAuditReport = StorageCleanerEngine.analyze(updatedMedia),
                    toastMessage = "Freed offline space for ${items.size} file(s) · Cloud copies kept safe"
                )
            }
            persistCurrentUiSnapshotAsync()
        }
    }

    // --- On-Device ML Kit OCR Text Search ---
    fun scanOcrForSingleItem(item: UnifiedMediaItem) {
        val baseUrl = _uiState.value.serverHealth.baseUrl
        val cachedText = _uiState.value.ocrTextByMediaId[item.id]
        if (!cachedText.isNullOrBlank()) {
            _uiState.update { it.copy(ocrExtractedDialogContent = item.originalName to cachedText) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrIndexing = true, toastMessage = "Scanning text on-device…") }
            val extracted = ocrIndexer.extractTextForItem(item, baseUrl)
            if (extracted.isNotBlank()) {
                val updatedMap = _uiState.value.ocrTextByMediaId + (item.id to extracted)
                ocrIndexer.saveIndex(updatedMap)
                _uiState.update {
                    it.copy(
                        isOcrIndexing = false,
                        ocrTextByMediaId = updatedMap,
                        ocrExtractedDialogContent = item.originalName to extracted,
                        toastMessage = "Text extracted & indexed for search"
                    )
                }
                pushMediaOverrideToCloudAsync(item.id)
            } else {
                _uiState.update {
                    it.copy(
                        isOcrIndexing = false,
                        toastMessage = "No readable text detected in \"${item.originalName}\""
                    )
                }
            }
        }
    }

    fun dismissOcrDialog() {
        _uiState.update { it.copy(ocrExtractedDialogContent = null) }
    }

    fun indexLibraryForOcr() {
        val state = _uiState.value
        if (state.isOcrIndexing) return
        val baseUrl = state.serverHealth.baseUrl
        val candidates = state.allMedia.filter { item ->
            !item.isDeleted &&
                item.id !in state.ocrTextByMediaId &&
                (item.mediaType == MediaType.IMAGE || item.extension.equals("pdf", ignoreCase = true))
        }.take(20)
        if (candidates.isEmpty()) {
            _uiState.update { it.copy(toastMessage = "All photos & PDFs are already indexed for text search") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isOcrIndexing = true, toastMessage = "Indexing ${candidates.size} item(s) for on-device text search…")
            }
            val updated = _uiState.value.ocrTextByMediaId.toMutableMap()
            val newlyIndexedIds = mutableListOf<String>()
            for (item in candidates) {
                val text = ocrIndexer.extractTextForItem(item, baseUrl)
                if (text.isNotBlank()) {
                    updated[item.id] = text
                    newlyIndexedIds.add(item.id)
                }
            }
            ocrIndexer.saveIndex(updated)
            _uiState.update {
                it.copy(
                    isOcrIndexing = false,
                    ocrTextByMediaId = updated,
                    toastMessage = "OCR Index complete · ${newlyIndexedIds.size} file(s) with searchable text"
                )
            }
            newlyIndexedIds.forEach { id -> pushMediaOverrideToCloudAsync(id) }
        }
    }

    // --- Document Scanner, QR Scanner & Video Frame/Trim Uploads ---
    fun setDocumentScannerOpen(open: Boolean) {
        if (open && !requireAuthenticatedAccount()) return
        _uiState.update { it.copy(isDocumentScannerOpen = open) }
    }

    fun setQrScannerOpen(open: Boolean) {
        _uiState.update { it.copy(isQrScannerOpen = open) }
    }

    fun uploadScannedPdf(pdfFile: File, displayName: String) {
        uploadGeneratedLocalMedia(
            file = pdfFile,
            displayName = displayName,
            mediaType = MediaType.DOCUMENT,
            successToast = "PDF \"$displayName\" saved & pinned offline"
        )
        _uiState.update { it.copy(isDocumentScannerOpen = false) }
    }

    fun saveVideoFrameAsPhoto(frameFile: File) {
        uploadGeneratedLocalMedia(
            file = frameFile,
            displayName = frameFile.name,
            mediaType = MediaType.IMAGE,
            successToast = "Video frame saved as new photo"
        )
    }

    fun saveTrimmedVideoClip(clipFile: File) {
        uploadGeneratedLocalMedia(
            file = clipFile,
            displayName = clipFile.name,
            mediaType = MediaType.VIDEO,
            successToast = "Trimmed clip saved & pinned offline"
        )
    }

    private fun uploadGeneratedLocalMedia(
        file: File,
        displayName: String,
        mediaType: MediaType,
        successToast: String
    ) {
        val state = _uiState.value
        val isSandbox = state.userSession?.isOfflineSandbox == true
        val folderId = state.activeFolder?.id
        val qId = "uq_${UUID.randomUUID().toString().take(8)}"
        val qItem = UploadQueueItem(
            id = qId,
            uriString = Uri.fromFile(file).toString(),
            fileName = displayName,
            mediaType = mediaType,
            progressPercent = 10,
            status = UploadQueueStatus.UPLOADING,
            targetFolderId = folderId
        )
        _uiState.update { it.copy(isUploading = true, uploadQueue = listOf(qItem) + it.uploadQueue) }

        viewModelScope.launch {
            val res = repository.uploadSingleLocalFile(
                sourceFile = file,
                displayName = displayName,
                mediaType = mediaType,
                targetFolderId = folderId,
                isSandbox = isSandbox,
                onProgress = { pct ->
                    _uiState.update { current ->
                        current.copy(
                            uploadQueue = current.uploadQueue.map { q ->
                                if (q.id == qId) q.copy(progressPercent = pct) else q
                            }
                        )
                    }
                }
            )
            res.fold(
                onSuccess = { created ->
                    val stats = repository.getOfflineVaultStats()
                    _uiState.update { current ->
                        current.copy(
                            isUploading = false,
                            offlineVaultStats = stats,
                            allMedia = listOf(created) + current.allMedia.filterNot { it.id == created.id },
                            uploadQueue = current.uploadQueue.map { q ->
                                if (q.id == qId) q.copy(progressPercent = 100, status = UploadQueueStatus.COMPLETED) else q
                            },
                            toastMessage = successToast
                        )
                    }
                    refreshHomeScreenWidget()
                    refreshWorkspaceData(silent = true)
                },
                onFailure = { err ->
                    _uiState.update { current ->
                        current.copy(
                            isUploading = false,
                            uploadQueue = current.uploadQueue.map { q ->
                                if (q.id == qId) q.copy(status = UploadQueueStatus.FAILED, errorMessage = err.message) else q
                            },
                            toastMessage = err.message ?: "Could not save file"
                        )
                    }
                }
            )
        }
    }

    // --- Home Screen Widget Sync ---
    fun refreshHomeScreenWidget() {
        val app = getApplication<Application>()
        val state = _uiState.value
        val isDark = when (state.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> DynamicAppIconManager.isSystemDarkMode(app)
        }
        val visible = state.allMedia.filter { !it.isDeleted && it.id !in state.hiddenMediaIds }
        val favThumbPath = visible
            .filter { it.mediaType == MediaType.IMAGE }
            .sortedByDescending { it.isFavorite }
            .firstNotNullOfOrNull { item ->
                listOfNotNull(
                    item.offlineFilePath,
                    item.localUri?.removePrefix("file://"),
                    item.path.takeIf { !it.startsWith("http", ignoreCase = true) }
                ).firstOrNull { path ->
                    val f = File(path)
                    f.exists() && f.length() > 0L
                }
            }
        val usedStr = formatBytes(state.totalUsedBytes)
        val quotaStr = formatBytes(GalleryUiState.STORAGE_QUOTA_BYTES)
        GalleryHubDashboardWidget.updateSnapshotAndRefresh(
            context = app,
            accentHex = state.accentHex,
            isDark = isDark,
            usedBytes = state.totalUsedBytes,
            quotaBytes = GalleryUiState.STORAGE_QUOTA_BYTES,
            quotaLabel = "$usedStr / $quotaStr used",
            subtitle = "${visible.size} files · ${state.offlineVaultStats.totalOfflineCount} offline",
            favoriteThumbPath = favThumbPath
        )
    }
}
