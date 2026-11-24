package com.example.data.local

import android.content.Context
import com.example.data.model.ActivityItem
import com.example.data.model.FolderCollaborator
import com.example.data.model.FolderItem
import com.example.data.model.MediaType
import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.ShareLinkItem
import com.example.data.model.UnifiedMediaItem
import com.example.data.model.nowIsoString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PendingSyncActionType(val key: String) {
    MOVE_FOLDER("move_folder"),
    SOFT_DELETE("soft_delete"),
    RESTORE_TRASH("restore_trash"),
    PERMANENT_DELETE("permanent_delete"),
    CREATE_FOLDER("create_folder"),
    UPDATE_FOLDER("update_folder"),
    DELETE_FOLDER("delete_folder"),
    RENAME_ITEM("rename_item"),
    UPDATE_TAGS("update_tags"),
    TOGGLE_FAVORITE("toggle_favorite");

    companion object {
        fun fromKey(key: String?): PendingSyncActionType? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

data class PendingSyncAction(
    val id: String = "sync_${UUID.randomUUID().toString().take(10)}",
    val type: PendingSyncActionType,
    val targetId: String,
    val mediaTypeKey: String = "image",
    val folderId: String? = null,
    val textValue: String? = null,
    val colorHex: String? = null,
    val tags: List<String> = emptyList(),
    val boolValue: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class LocalMetadataOverlays(
    val favoriteIds: Set<String> = emptySet(),
    val renamedTitles: Map<String, String> = emptyMap(),
    val customTags: Map<String, List<String>> = emptyMap()
)

data class CachedWorkspaceSnapshot(
    val activeMedia: List<UnifiedMediaItem> = emptyList(),
    val trashMedia: List<UnifiedMediaItem> = emptyList(),
    val ownedFolders: List<FolderItem> = emptyList(),
    val sharedFolders: List<FolderItem> = emptyList(),
    val shareLinks: List<ShareLinkItem> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
    val savedAtMillis: Long = 0L
) {
    val hasData: Boolean
        get() = activeMedia.isNotEmpty() || trashMedia.isNotEmpty() || ownedFolders.isNotEmpty() || sharedFolders.isNotEmpty()
}

/**
 * Fast, atomic file-backed store for:
 * 1. Per-account cloud workspace snapshot (for instant stale-while-revalidate startup & offline browsing)
 * 2. Per-account persistent offline mutation queue (replayed automatically upon network reconnection)
 * 3. Per-account local metadata overlays (favorites, inline renames, custom tags)
 */
class OfflineSyncQueueStore(private val context: Context) {

    private val mutex = Mutex()
    private val storeDir: File = File(context.filesDir, "offline_sync_store").apply { mkdirs() }

    private fun safeAccountKey(accountKey: String): String {
        return accountKey.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_").ifBlank { "default" }
    }

    private fun snapshotFile(accountKey: String) = File(storeDir, "snapshot_${safeAccountKey(accountKey)}.json")
    private fun queueFile(accountKey: String) = File(storeDir, "queue_${safeAccountKey(accountKey)}.json")
    private fun overlaysFile(accountKey: String) = File(storeDir, "overlays_${safeAccountKey(accountKey)}.json")

    // ========================================================================
    // 1. Local Metadata Overlays (Favorites, Inline Renames, Custom Tags)
    // ========================================================================

    suspend fun loadOverlays(accountKey: String): LocalMetadataOverlays = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadOverlaysLocked(accountKey)
        }
    }

    private fun loadOverlaysLocked(accountKey: String): LocalMetadataOverlays {
        val file = overlaysFile(accountKey)
        if (!file.exists()) return LocalMetadataOverlays()
        return try {
            val root = JSONObject(file.readText())
            val favs = mutableSetOf<String>()
            val favArr = root.optJSONArray("favoriteIds")
            if (favArr != null) {
                for (i in 0 until favArr.length()) {
                    favArr.optString(i).takeIf { it.isNotBlank() }?.let { favs.add(it) }
                }
            }
            val renames = mutableMapOf<String, String>()
            val renameObj = root.optJSONObject("renamedTitles")
            if (renameObj != null) {
                renameObj.keys().forEach { k ->
                    val v = renameObj.optString(k)
                    if (k.isNotBlank() && v.isNotBlank()) renames[k] = v
                }
            }
            val tagsMap = mutableMapOf<String, List<String>>()
            val tagsObj = root.optJSONObject("customTags")
            if (tagsObj != null) {
                tagsObj.keys().forEach { k ->
                    val arr = tagsObj.optJSONArray(k)
                    if (arr != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let { list.add(it) }
                        }
                        tagsMap[k] = list
                    }
                }
            }
            LocalMetadataOverlays(favs, renames, tagsMap)
        } catch (_: Exception) {
            LocalMetadataOverlays()
        }
    }

    private fun saveOverlaysLocked(accountKey: String, overlays: LocalMetadataOverlays) {
        try {
            val root = JSONObject()
            val favArr = JSONArray()
            overlays.favoriteIds.forEach { favArr.put(it) }
            root.put("favoriteIds", favArr)

            val renameObj = JSONObject()
            overlays.renamedTitles.forEach { (k, v) -> renameObj.put(k, v) }
            root.put("renamedTitles", renameObj)

            val tagsObj = JSONObject()
            overlays.customTags.forEach { (k, list) ->
                val arr = JSONArray()
                list.forEach { arr.put(it) }
                tagsObj.put(k, arr)
            }
            root.put("customTags", tagsObj)

            atomicWrite(overlaysFile(accountKey), root.toString())
        } catch (_: Exception) {
        }
    }

    suspend fun setFavorite(accountKey: String, itemId: String, isFavorite: Boolean): LocalMetadataOverlays =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = loadOverlaysLocked(accountKey)
                val nextFavs = current.favoriteIds.toMutableSet()
                if (isFavorite) nextFavs.add(itemId) else nextFavs.remove(itemId)
                val updated = current.copy(favoriteIds = nextFavs)
                saveOverlaysLocked(accountKey, updated)
                updated
            }
        }

    suspend fun setRenamedTitle(accountKey: String, itemId: String, newTitle: String): LocalMetadataOverlays =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = loadOverlaysLocked(accountKey)
                val nextRenames = current.renamedTitles.toMutableMap()
                nextRenames[itemId] = newTitle
                val updated = current.copy(renamedTitles = nextRenames)
                saveOverlaysLocked(accountKey, updated)
                updated
            }
        }

    suspend fun setCustomTags(accountKey: String, itemId: String, tags: List<String>): LocalMetadataOverlays =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = loadOverlaysLocked(accountKey)
                val nextTags = current.customTags.toMutableMap()
                nextTags[itemId] = tags
                val updated = current.copy(customTags = nextTags)
                saveOverlaysLocked(accountKey, updated)
                updated
            }
        }

    // ========================================================================
    // 2. Persistent Offline Pending Mutation Queue
    // ========================================================================

    suspend fun loadPendingQueue(accountKey: String): List<PendingSyncAction> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                loadQueueLocked(accountKey)
            }
        }

    private fun loadQueueLocked(accountKey: String): List<PendingSyncAction> {
        val file = queueFile(accountKey)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val result = mutableListOf<PendingSyncAction>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = PendingSyncActionType.fromKey(o.optString("type")) ?: continue
                val tags = mutableListOf<String>()
                val tArr = o.optJSONArray("tags")
                if (tArr != null) {
                    for (j in 0 until tArr.length()) {
                        tArr.optString(j).takeIf { it.isNotBlank() }?.let { tags.add(it) }
                    }
                }
                result.add(
                    PendingSyncAction(
                        id = o.optString("id"),
                        type = type,
                        targetId = o.optString("targetId"),
                        mediaTypeKey = o.optString("mediaTypeKey", "image"),
                        folderId = o.optString("folderId").takeIf { it.isNotBlank() },
                        textValue = o.optString("textValue").takeIf { it.isNotBlank() },
                        colorHex = o.optString("colorHex").takeIf { it.isNotBlank() },
                        tags = tags,
                        boolValue = o.optBoolean("boolValue", false),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis())
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveQueueLocked(accountKey: String, list: List<PendingSyncAction>) {
        try {
            val arr = JSONArray()
            list.forEach { a ->
                val tArr = JSONArray()
                a.tags.forEach { tArr.put(it) }
                arr.put(
                    JSONObject().apply {
                        put("id", a.id)
                        put("type", a.type.key)
                        put("targetId", a.targetId)
                        put("mediaTypeKey", a.mediaTypeKey)
                        put("folderId", a.folderId ?: "")
                        put("textValue", a.textValue ?: "")
                        put("colorHex", a.colorHex ?: "")
                        put("tags", tArr)
                        put("boolValue", a.boolValue)
                        put("createdAtMillis", a.createdAtMillis)
                    }
                )
            }
            atomicWrite(queueFile(accountKey), arr.toString())
        } catch (_: Exception) {
        }
    }

    suspend fun enqueueAction(accountKey: String, action: PendingSyncAction): Int =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val list = loadQueueLocked(accountKey).toMutableList()
                // Deduplicate identical latest-wins actions for the same target item
                if (action.type in setOf(
                        PendingSyncActionType.MOVE_FOLDER,
                        PendingSyncActionType.RENAME_ITEM,
                        PendingSyncActionType.UPDATE_TAGS,
                        PendingSyncActionType.TOGGLE_FAVORITE,
                        PendingSyncActionType.UPDATE_FOLDER
                    )
                ) {
                    list.removeAll { it.type == action.type && it.targetId == action.targetId }
                }
                list.add(action)
                saveQueueLocked(accountKey, list)
                list.size
            }
        }

    suspend fun removeAction(accountKey: String, actionId: String): Int =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val list = loadQueueLocked(accountKey).filterNot { it.id == actionId }
                saveQueueLocked(accountKey, list)
                list.size
            }
        }

    suspend fun clearQueue(accountKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            saveQueueLocked(accountKey, emptyList())
        }
    }

    // ========================================================================
    // 3. Per-Account Cloud Workspace Snapshot Cache (Stale-While-Revalidate)
    // ========================================================================

    suspend fun saveWorkspaceSnapshot(
        accountKey: String,
        snapshot: CachedWorkspaceSnapshot
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val root = JSONObject().apply {
                    put("savedAtMillis", System.currentTimeMillis())
                    put("activeMedia", serializeMediaList(snapshot.activeMedia))
                    put("trashMedia", serializeMediaList(snapshot.trashMedia))
                    put("ownedFolders", serializeFolderList(snapshot.ownedFolders))
                    put("sharedFolders", serializeFolderList(snapshot.sharedFolders))
                    put("shareLinks", serializeShareLinks(snapshot.shareLinks))
                    put("activities", serializeActivities(snapshot.activities))
                }
                atomicWrite(snapshotFile(accountKey), root.toString())
            } catch (_: Exception) {
            }
        }
    }

    suspend fun loadWorkspaceSnapshot(accountKey: String): CachedWorkspaceSnapshot =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = snapshotFile(accountKey)
                if (!file.exists()) return@withContext CachedWorkspaceSnapshot()
                try {
                    val root = JSONObject(file.readText())
                    CachedWorkspaceSnapshot(
                        activeMedia = deserializeMediaList(root.optJSONArray("activeMedia")),
                        trashMedia = deserializeMediaList(root.optJSONArray("trashMedia")),
                        ownedFolders = deserializeFolderList(root.optJSONArray("ownedFolders")),
                        sharedFolders = deserializeFolderList(root.optJSONArray("sharedFolders")),
                        shareLinks = deserializeShareLinks(root.optJSONArray("shareLinks")),
                        activities = deserializeActivities(root.optJSONArray("activities")),
                        savedAtMillis = root.optLong("savedAtMillis", 0L)
                    )
                } catch (_: Exception) {
                    CachedWorkspaceSnapshot()
                }
            }
        }

    private fun serializeMediaList(items: List<UnifiedMediaItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { m ->
            val tagsArr = JSONArray()
            m.tags.forEach { tagsArr.put(it) }
            arr.put(
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
                    put("tags", tagsArr)
                    put("isFavorite", m.isFavorite)
                    put("offlineState", m.offlineState.key)
                    put("offlineFilePath", m.offlineFilePath ?: "")
                }
            )
        }
        return arr
    }

    private fun deserializeMediaList(arr: JSONArray?): List<UnifiedMediaItem> {
        if (arr == null) return emptyList()
        val list = ArrayList<UnifiedMediaItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tags = mutableListOf<String>()
            val tArr = o.optJSONArray("tags")
            if (tArr != null) {
                for (j in 0 until tArr.length()) {
                    tArr.optString(j).takeIf { it.isNotBlank() }?.let { tags.add(it) }
                }
            }
            list.add(
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
                    tags = tags,
                    isFavorite = o.optBoolean("isFavorite", false),
                    offlineState = OfflineAvailabilityState.fromKey(o.optString("offlineState")),
                    offlineFilePath = o.optString("offlineFilePath").takeIf { it.isNotBlank() }
                )
            )
        }
        return list
    }

    private fun serializeFolderList(folders: List<FolderItem>): JSONArray {
        val arr = JSONArray()
        folders.forEach { f ->
            val collabs = JSONArray()
            f.sharedWith.forEach { c ->
                collabs.put(
                    JSONObject().apply {
                        put("userId", c.userId)
                        put("name", c.name)
                        put("email", c.email)
                        put("permission", c.permission)
                    }
                )
            }
            arr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("color", f.color)
                    put("ownerId", f.ownerId)
                    put("ownerName", f.ownerName)
                    put("ownerEmail", f.ownerEmail)
                    put("fileCount", f.fileCount)
                    put("imageCount", f.imageCount)
                    put("videoCount", f.videoCount)
                    put("docCount", f.docCount)
                    put("isShared", f.isShared)
                    put("myPermission", f.myPermission)
                    put("createdAt", f.createdAt)
                    put("sharedWith", collabs)
                }
            )
        }
        return arr
    }

    private fun deserializeFolderList(arr: JSONArray?): List<FolderItem> {
        if (arr == null) return emptyList()
        val list = ArrayList<FolderItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val collabs = mutableListOf<FolderCollaborator>()
            val cArr = o.optJSONArray("sharedWith")
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
            list.add(
                FolderItem(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    color = o.optString("color", "#4285F4"),
                    ownerId = o.optString("ownerId"),
                    ownerName = o.optString("ownerName", "Me"),
                    ownerEmail = o.optString("ownerEmail"),
                    fileCount = o.optInt("fileCount", 0),
                    imageCount = o.optInt("imageCount", 0),
                    videoCount = o.optInt("videoCount", 0),
                    docCount = o.optInt("docCount", 0),
                    sharedWith = collabs,
                    isShared = o.optBoolean("isShared", false),
                    myPermission = o.optString("myPermission", "edit"),
                    createdAt = o.optString("createdAt")
                )
            )
        }
        return list
    }

    private fun serializeShareLinks(links: List<ShareLinkItem>): JSONArray {
        val arr = JSONArray()
        links.forEach { l ->
            arr.put(
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
        return arr
    }

    private fun deserializeShareLinks(arr: JSONArray?): List<ShareLinkItem> {
        if (arr == null) return emptyList()
        val list = ArrayList<ShareLinkItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                ShareLinkItem(
                    id = o.optString("id"),
                    fileId = o.optString("fileId"),
                    fileName = o.optString("fileName"),
                    fileType = MediaType.fromKey(o.optString("fileType")),
                    token = o.optString("token"),
                    hasPassword = o.optBoolean("hasPassword", false),
                    expiresAt = o.optString("expiresAt").takeIf { it.isNotBlank() },
                    viewCount = o.optInt("viewCount", 0),
                    createdAt = o.optString("createdAt"),
                    localPasswordPlain = o.optString("localPasswordPlain").takeIf { it.isNotBlank() }
                )
            )
        }
        return list
    }

    private fun serializeActivities(activities: List<ActivityItem>): JSONArray {
        val arr = JSONArray()
        activities.take(60).forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("action", a.action)
                    put("targetName", a.targetName)
                    put("targetType", a.targetType)
                    put("permanent", a.permanent)
                    put("createdAt", a.createdAt)
                }
            )
        }
        return arr
    }

    private fun deserializeActivities(arr: JSONArray?): List<ActivityItem> {
        if (arr == null) return emptyList()
        val list = ArrayList<ActivityItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                ActivityItem(
                    id = o.optString("id"),
                    action = o.optString("action"),
                    targetName = o.optString("targetName"),
                    targetType = o.optString("targetType"),
                    permanent = o.optBoolean("permanent", false),
                    createdAt = o.optString("createdAt", nowIsoString())
                )
            )
        }
        return list
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
