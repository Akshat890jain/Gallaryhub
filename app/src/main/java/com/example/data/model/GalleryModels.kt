package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

// ============================================================================
// Moshi-Compatible Data Classes Matching MongoDB Mongoose Schemas
// ============================================================================

/**
 * Matches MongoDB `User` Schema (`backend/models/User.js`)
 */
@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "_id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "email") val email: String = "",
    @Json(name = "profilePicture") val profilePicture: String? = null,
    @Json(name = "token") val token: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null,
    @Json(name = "message") val message: String? = null
)

/**
 * Matches MongoDB `Image`, `Video`, and `Document` Schemas
 * (`backend/models/Image.js`, `Video.js`, `Document.js`)
 */
data class MediaItem(
    @Json(name = "_id") val id: String = "",
    @Json(name = "filename") val filename: String = "",
    @Json(name = "originalName") val originalName: String = "",
    @Json(name = "path") val path: String = "",
    @Json(name = "size") val size: Long = 0L,
    @Json(name = "user") val user: Any? = null,
    @Json(name = "folder") val folder: Any? = null,
    @Json(name = "isDeleted") val isDeleted: Boolean = false,
    @Json(name = "deletedAt") val deletedAt: String? = null,
    @Json(name = "uploadDate") val uploadDate: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    // Video-specific fields
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "duration") val duration: Double? = null,
    // Document-specific field
    @Json(name = "type") val type: String? = null,
    // Optional external CDN / cloud storage URL fields returned by various backend storage adapters
    @Json(name = "url") val url: String? = null,
    @Json(name = "secure_url") val secureUrl: String? = null,
    @Json(name = "fileUrl") val fileUrl: String? = null,
    @Json(name = "downloadUrl") val downloadUrl: String? = null,
    @Json(name = "cloudinaryUrl") val cloudinaryUrl: String? = null,
    // Optional tags array for search & filtering
    @Json(name = "tags") val tags: List<String>? = null
) {
    fun resolveBestRemotePath(): String {
        val candidates = listOfNotNull(secureUrl, cloudinaryUrl, fileUrl, downloadUrl, url, path.takeIf { it.isNotBlank() })
            .map { it.trim() }
            .filter { it.isNotBlank() }
        // Prefer full HTTPS/HTTP cloud URLs (Cloudinary, S3, GCS, etc.) when present
        candidates.firstOrNull { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            ?.let { return it }
        return candidates.firstOrNull() ?: filename
    }

    fun extractFolderId(): String? {
        return when (val f = folder) {
            is String -> f.takeIf { it.isNotBlank() }
            is Map<*, *> -> (f["_id"] ?: f["id"])?.toString()
            else -> null
        }
    }
}

/**
 * Matches MongoDB `Folder` Schema (`backend/models/Folder.js`)
 */
data class Folder(
    @Json(name = "_id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "color") val color: String = "#4285f4",
    @Json(name = "user") val user: Any? = null,
    @Json(name = "sharedWith") val sharedWith: List<Map<String, Any?>> = emptyList(),
    @Json(name = "fileCount") val fileCount: Int = 0,
    @Json(name = "imageCount") val imageCount: Int = 0,
    @Json(name = "videoCount") val videoCount: Int = 0,
    @Json(name = "docCount") val docCount: Int = 0,
    @Json(name = "isShared") val isShared: Boolean = false,
    @Json(name = "myPermission") val myPermission: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

/**
 * Matches MongoDB `ShareLink` Schema (`backend/models/ShareLink.js`)
 */
data class ShareLink(
    @Json(name = "_id") val id: String = "",
    @Json(name = "fileId") val fileId: Any? = null,
    @Json(name = "fileName") val fileName: String? = null,
    @Json(name = "originalName") val originalName: String? = null,
    @Json(name = "fileType") val fileType: String = "image",
    @Json(name = "user") val user: Any? = null,
    @Json(name = "token") val token: String = "",
    @Json(name = "hasPassword") val hasPassword: Boolean? = null,
    @Json(name = "password") val password: Any? = null,
    @Json(name = "expiresAt") val expiresAt: String? = null,
    @Json(name = "viewCount") val viewCount: Int = 0,
    @Json(name = "createdAt") val createdAt: String? = null
)

/**
 * Matches MongoDB `Activity` Schema (`backend/models/Activity.js`)
 */
data class ActivityLog(
    @Json(name = "_id") val id: String = "",
    @Json(name = "user") val user: Any? = null,
    @Json(name = "action") val action: String = "upload",
    @Json(name = "targetName") val targetName: String = "",
    @Json(name = "targetType") val targetType: String = "image",
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ActivityPageResponse(
    @Json(name = "activities") val activities: List<ActivityLog> = emptyList(),
    @Json(name = "total") val total: Int = 0,
    @Json(name = "page") val page: Int = 1,
    @Json(name = "pages") val pages: Int = 1
)

// ============================================================================
// UI & Domain Layer Models
// ============================================================================

enum class MediaType(val key: String, val pluralEndpoint: String, val label: String) {
    IMAGE("image", "images", "Photo"),
    VIDEO("video", "videos", "Video"),
    DOCUMENT("document", "documents", "Document");

    companion object {
        fun fromKey(key: String?): MediaType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: IMAGE
        }
    }
}

enum class CategoryFilter(val key: String, val label: String) {
    ALL("all", "All"),
    FAVORITES("favorites", "Favorites"),
    OFFLINE("offline", "Offline"),
    IMAGES("images", "Photos"),
    VIDEOS("videos", "Videos"),
    DOCUMENTS("documents", "Docs")
}

enum class OfflineAvailabilityState(val key: String, val label: String) {
    NONE("none", "Cloud Only"),
    AUTO_CACHED("auto_cached", "Cached Offline"),
    PINNED("pinned", "Pinned Offline");

    val isAvailableOffline: Boolean
        get() = this != NONE

    companion object {
        fun fromKey(key: String?): OfflineAvailabilityState {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NONE
        }
    }
}

data class OfflineVaultStats(
    val pinnedCount: Int = 0,
    val pinnedBytes: Long = 0L,
    val autoCachedCount: Int = 0,
    val autoCachedBytes: Long = 0L,
    val autoCacheEnabled: Boolean = true
) {
    val totalOfflineCount: Int get() = pinnedCount + autoCachedCount
    val totalOfflineBytes: Long get() = pinnedBytes + autoCachedBytes
}

data class MediaStorageBreakdown(
    val imageCount: Int = 0,
    val imageBytes: Long = 0L,
    val videoCount: Int = 0,
    val videoBytes: Long = 0L,
    val documentCount: Int = 0,
    val documentBytes: Long = 0L
) {
    val totalBytes: Long get() = imageBytes + videoBytes + documentBytes
    val totalCount: Int get() = imageCount + videoCount + documentCount
}

enum class SortField(val key: String, val label: String) {
    DATE("date", "Date Added"),
    NAME("name", "Name"),
    SIZE("size", "File Size"),
    TYPE("type", "File Type")
}

enum class SortOrder(val key: String, val label: String) {
    DESC("desc", "Newest First"),
    ASC("asc", "Oldest First")
}

enum class GalleryViewMode(val key: String, val label: String) {
    GRID("grid", "Grid"),
    LIST("list", "List"),
    MASONRY("masonry", "Masonry"),
    TIMELINE("timeline", "Timeline")
}

enum class SmartCollectionFilter(val key: String, val label: String) {
    ALL("all", "All"),
    SCREENSHOTS("screenshots", "Screenshots"),
    RECEIPTS_PDFS("receipts_pdfs", "Receipts & PDFs"),
    VIDEOS("videos", "Videos"),
    AUDIO("audio", "Audio"),
    RECENTLY_EDITED("edited", "Edited"),
    UNCATEGORIZED("uncategorized", "Uncategorized");

    fun matches(item: UnifiedMediaItem): Boolean {
        val nameLower = item.originalName.lowercase()
        val ext = item.extension.lowercase()
        return when (this) {
            ALL -> true
            SCREENSHOTS -> item.mediaType == MediaType.IMAGE && (
                nameLower.contains("screenshot") ||
                    nameLower.contains("screen_shot") ||
                    nameLower.startsWith("scr_") ||
                    item.tags.any { it.equals("screenshot", ignoreCase = true) }
                )
            RECEIPTS_PDFS -> ext == "pdf" ||
                nameLower.contains("receipt") ||
                nameLower.contains("invoice") ||
                nameLower.contains("bill") ||
                nameLower.contains("pass") ||
                nameLower.contains("scan") ||
                item.tags.any { it.equals("receipt", ignoreCase = true) || it.equals("invoice", ignoreCase = true) }
            VIDEOS -> item.mediaType == MediaType.VIDEO
            AUDIO -> ext in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus")
            RECENTLY_EDITED -> item.id.startsWith("edt_") ||
                nameLower.contains("_edited") ||
                nameLower.contains("_trim") ||
                nameLower.contains("_frame") ||
                item.tags.any { it.equals("edited", ignoreCase = true) }
            UNCATEGORIZED -> item.folderId.isNullOrBlank()
        }
    }
}

enum class UploadQueueStatus(val label: String) {
    QUEUED("Queued"),
    WAITING_WIFI("Waiting for Wi-Fi"),
    UPLOADING("Uploading"),
    COMPLETED("Uploaded"),
    FAILED("Failed")
}

data class UploadQueueItem(
    val id: String,
    val uriString: String,
    val fileName: String,
    val mediaType: MediaType,
    val progressPercent: Int = 0,
    val status: UploadQueueStatus = UploadQueueStatus.QUEUED,
    val errorMessage: String? = null,
    val targetFolderId: String? = null
)

data class UserSession(
    val id: String,
    val name: String,
    val email: String,
    val profilePicture: String? = null,
    val token: String,
    val isOfflineSandbox: Boolean = false
) {
    fun resolveAvatarUrl(baseUrl: String): String? {
        val pic = profilePicture?.trim().orEmpty()
        if (pic.isBlank()) return null
        if (pic.startsWith("http://") || pic.startsWith("https://") || pic.startsWith("file://") || pic.startsWith("content://")) {
            return pic
        }
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = pic.trimStart('/')
        return if (cleanPath.startsWith("uploads/")) {
            "$cleanBase/$cleanPath"
        } else {
            "$cleanBase/uploads/$cleanPath"
        }
    }
}

data class UnifiedMediaItem(
    val id: String,
    val filename: String,
    val originalName: String,
    val path: String,
    val size: Long,
    val folderId: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: String? = null,
    val uploadDate: String,
    val mediaType: MediaType,
    val thumbnail: String? = null,
    val duration: Double? = null,
    val docType: String? = null,
    val localUri: String? = null,
    val aspectRatio: Float = 1.25f,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val offlineState: OfflineAvailabilityState = OfflineAvailabilityState.NONE,
    val offlineFilePath: String? = null
) {
    // Precomputed once on instantiation to avoid allocating SimpleDateFormat during sorting or Compose scrolling
    val timestampMillis: Long = parseIsoMillis(uploadDate) ?: 0L
    val relativeDateLabel: String = formatRelativeTimeFromMillis(timestampMillis)

    val isAvailableOffline: Boolean
        get() = offlineState.isAvailableOffline ||
            (!offlineFilePath.isNullOrBlank()) ||
            (!localUri.isNullOrBlank() && localUri.startsWith("file://"))

    val effectiveTags: List<String>
        get() {
            val base = tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
            if (base.isNotEmpty()) return base
            val ext = extension
            val typeTag = mediaType.label.lowercase()
            return listOfNotNull(typeTag, ext.takeIf { it.isNotBlank() }).distinct()
        }

    fun matchesSearchQuery(rawQuery: String, folderName: String? = null): Boolean {
        val q = rawQuery.trim().lowercase().removePrefix("#")
        if (q.isEmpty()) return true
        if (originalName.lowercase().contains(q)) return true
        if (filename.lowercase().contains(q)) return true
        if (mediaType.label.lowercase().contains(q) || mediaType.key.lowercase().contains(q)) return true
        if (extension.lowercase().contains(q)) return true
        if (!folderName.isNullOrBlank() && folderName.lowercase().contains(q)) return true
        return effectiveTags.any { it.lowercase().contains(q) } ||
            tags.any { it.lowercase().removePrefix("#").contains(q) }
    }

    fun resolveRemoteMediaUrl(baseUrl: String): String {
        return resolveRemoteCandidateUrls(baseUrl).firstOrNull()
            ?: "${baseUrl.trimEnd('/')}/uploads/${encodePathSegments(filename)}"
    }

    /**
     * Generates an ordered, deduplicated list of properly URL-encoded remote download URLs.
     * Handles absolute server disk paths (e.g. `/opt/render/project/src/uploads/...`),
     * spaces/special characters in filenames, and alternate backend media directories.
     */
    fun resolveRemoteCandidateUrls(baseUrl: String): List<String> {
        val cleanBase = baseUrl.trimEnd('/')
        if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
            return listOf(path)
        }

        val normalizedRaw = path.replace("\\", "/").trimStart('/')
        // Strip server-side container/working-dir prefixes if present
        val relativePath = stripServerDiskPrefix(normalizedRaw)
        val cleanFilename = filename.replace("\\", "/").substringAfterLast('/').trim()
        val encodedRelative = encodePathSegments(relativePath)
        val encodedFilename = encodePathSegments(cleanFilename)
        val encodedOriginal = encodePathSegments(originalName.trim())

        return buildList {
            if (encodedRelative.isNotBlank()) {
                when {
                    relativePath.startsWith("uploads/", ignoreCase = true) ||
                        relativePath.startsWith("videos/", ignoreCase = true) ||
                        relativePath.startsWith("documents/", ignoreCase = true) ||
                        relativePath.startsWith("api/", ignoreCase = true) -> {
                        add("$cleanBase/$encodedRelative")
                    }
                    else -> {
                        val primaryDir = when (mediaType) {
                            MediaType.IMAGE -> "uploads"
                            MediaType.VIDEO -> "videos"
                            MediaType.DOCUMENT -> "documents"
                        }
                        add("$cleanBase/$primaryDir/$encodedRelative")
                    }
                }
            }

            if (encodedFilename.isNotBlank()) {
                when (mediaType) {
                    MediaType.IMAGE -> {
                        add("$cleanBase/uploads/$encodedFilename")
                        add("$cleanBase/uploads/images/$encodedFilename")
                        add("$cleanBase/images/$encodedFilename")
                    }
                    MediaType.VIDEO -> {
                        add("$cleanBase/videos/$encodedFilename")
                        add("$cleanBase/uploads/videos/$encodedFilename")
                        add("$cleanBase/uploads/$encodedFilename")
                    }
                    MediaType.DOCUMENT -> {
                        add("$cleanBase/documents/$encodedFilename")
                        add("$cleanBase/uploads/documents/$encodedFilename")
                        add("$cleanBase/uploads/$encodedFilename")
                    }
                }
            }

            // Fallback: try non-encoded raw path in case filename was already percent-encoded by backend
            if (relativePath.isNotBlank() && relativePath.contains('%')) {
                val rawDir = when {
                    relativePath.startsWith("uploads/") ||
                        relativePath.startsWith("videos/") ||
                        relativePath.startsWith("documents/") -> "$cleanBase/$relativePath"
                    mediaType == MediaType.IMAGE -> "$cleanBase/uploads/$relativePath"
                    mediaType == MediaType.VIDEO -> "$cleanBase/videos/$relativePath"
                    else -> "$cleanBase/documents/$relativePath"
                }
                add(rawDir)
            }

            // Fallback: try originalName under media directories if filename differed
            if (encodedOriginal.isNotBlank() && encodedOriginal != encodedFilename) {
                when (mediaType) {
                    MediaType.IMAGE -> add("$cleanBase/uploads/$encodedOriginal")
                    MediaType.VIDEO -> {
                        add("$cleanBase/videos/$encodedOriginal")
                        add("$cleanBase/uploads/$encodedOriginal")
                    }
                    MediaType.DOCUMENT -> {
                        add("$cleanBase/documents/$encodedOriginal")
                        add("$cleanBase/uploads/$encodedOriginal")
                    }
                }
            }

            // Fallback: try standard authenticated backend API download/stream routes
            val isRemoteServerId = id.isNotBlank() && !id.startsWith("med_") && !id.startsWith("edt_")
            if (isRemoteServerId) {
                val encodedId = encodePathSegments(id)
                when (mediaType) {
                    MediaType.IMAGE -> {
                        add("$cleanBase/api/images/$encodedId/download")
                        add("$cleanBase/api/images/$encodedId/file")
                    }
                    MediaType.VIDEO -> {
                        add("$cleanBase/api/videos/$encodedId/download")
                        add("$cleanBase/api/videos/$encodedId/stream")
                    }
                    MediaType.DOCUMENT -> {
                        add("$cleanBase/api/documents/$encodedId/download")
                        add("$cleanBase/api/documents/$encodedId/file")
                    }
                }
            }
        }.distinct()
    }

    private fun stripServerDiskPrefix(normalizedPath: String): String {
        val markers = listOf("uploads/", "videos/", "documents/")
        for (marker in markers) {
            val idx = normalizedPath.indexOf(marker, ignoreCase = true)
            if (idx > 0) {
                return normalizedPath.substring(idx)
            }
        }
        return normalizedPath
    }

    private fun encodePathSegments(rawPath: String): String {
        if (rawPath.isBlank()) return ""
        return rawPath.split("/")
            .filter { it.isNotBlank() }
            .joinToString("/") { segment ->
                runCatching { android.net.Uri.encode(segment) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() || segment.isEmpty() }
                    ?: java.net.URLEncoder.encode(segment, "UTF-8")
                        .replace("+", "%20")
                        .replace("%21", "!")
                        .replace("%27", "'")
                        .replace("%28", "(")
                        .replace("%29", ")")
                        .replace("%7E", "~")
            }
    }

    fun resolveMediaUrl(baseUrl: String): String {
        if (!offlineFilePath.isNullOrBlank()) {
            val offlineFile = java.io.File(offlineFilePath)
            if (offlineFile.exists() && offlineFile.length() > 0L) {
                return android.net.Uri.fromFile(offlineFile).toString()
            }
        }
        if (!localUri.isNullOrBlank()) return localUri
        return resolveRemoteMediaUrl(baseUrl)
    }

    fun resolveThumbnailUrl(baseUrl: String): String? {
        if (mediaType == MediaType.IMAGE && !offlineFilePath.isNullOrBlank()) {
            val offlineFile = java.io.File(offlineFilePath)
            if (offlineFile.exists() && offlineFile.length() > 0L) {
                return android.net.Uri.fromFile(offlineFile).toString()
            }
        }
        if (!localUri.isNullOrBlank() && mediaType == MediaType.IMAGE) return localUri
        val cleanBase = baseUrl.trimEnd('/')
        return when (mediaType) {
            MediaType.IMAGE -> resolveRemoteMediaUrl(baseUrl)
            MediaType.VIDEO -> {
                if (!thumbnail.isNullOrBlank()) {
                    if (thumbnail.startsWith("http") || thumbnail.startsWith("file://")) thumbnail
                    else "$cleanBase/videos/thumbnails/${encodePathSegments(thumbnail.trimStart('/'))}"
                } else null
            }
            MediaType.DOCUMENT -> null
        }
    }

    val extension: String
        get() {
            val extFromDoc = docType?.trim()?.lowercase()?.removePrefix(".")
            if (!extFromDoc.isNullOrBlank()) return extFromDoc
            return originalName.substringAfterLast('.', "").lowercase()
        }

    val iconEmoji: String
        get() = when (mediaType) {
            MediaType.IMAGE -> "🖼️"
            MediaType.VIDEO -> "🎥"
            MediaType.DOCUMENT -> when (extension) {
                "pdf" -> "📕"
                "doc", "docx" -> "📘"
                "xls", "xlsx", "csv" -> "📊"
                "ppt", "pptx" -> "📑"
                "zip", "rar", "7z", "tar", "gz" -> "📦"
                "txt", "json", "js", "ts", "py", "kt", "html", "css", "md" -> "📝"
                else -> "📄"
            }
        }

    val formattedSize: String
        get() = formatBytes(size)

    val formattedDuration: String?
        get() {
            val sec = duration?.toInt() ?: return null
            if (sec <= 0) return null
            val m = sec / 60
            val s = sec % 60
            return String.format(Locale.US, "%d:%02d", m, s)
        }

    val daysLeftInTrash: Int
        get() {
            val deletedTimestamp = parseIsoMillis(deletedAt) ?: System.currentTimeMillis()
            val elapsedDays = ((System.currentTimeMillis() - deletedTimestamp) / 86_400_000L).toInt()
            return max(0, 30 - elapsedDays)
        }
}

data class FolderCollaborator(
    val userId: String,
    val name: String,
    val email: String,
    val permission: String, // "view" or "edit"
    val addedAt: String? = null
)

data class FolderItem(
    val id: String,
    val name: String,
    val color: String = "#4285F4",
    val ownerId: String = "",
    val ownerName: String = "Me",
    val ownerEmail: String = "",
    val fileCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val docCount: Int = 0,
    val sharedWith: List<FolderCollaborator> = emptyList(),
    val isShared: Boolean = false,
    val myPermission: String = "edit", // "view" or "edit"
    val createdAt: String = ""
)

data class ShareLinkItem(
    val id: String,
    val fileId: String,
    val fileName: String,
    val fileType: MediaType,
    val token: String,
    val hasPassword: Boolean,
    val expiresAt: String? = null,
    val viewCount: Int = 0,
    val createdAt: String = "",
    val localPasswordPlain: String? = null
) {
    fun buildPublicShareUrl(baseUrl: String): String {
        return com.example.util.ShareLinkCodec.buildDirectServerUrl(baseUrl, token)
    }

    fun buildShortMaskedUrl(baseUrl: String): String {
        return com.example.util.ShareLinkCodec.buildShortMaskedUrl(baseUrl, token)
    }

    fun buildAppSchemeUrl(): String {
        return com.example.util.ShareLinkCodec.buildAppSchemeUrl(token)
    }

    fun buildMaskedPreview(
        baseUrl: String,
        format: com.example.util.ShareLinkFormat = com.example.util.ShareLinkFormat.SHORT_MASKED
    ): String {
        return com.example.util.ShareLinkCodec.buildMaskedDisplayPreview(baseUrl, token, format)
    }

    val isExpired: Boolean
        get() {
            val exp = parseIsoMillis(expiresAt) ?: return false
            return System.currentTimeMillis() > exp
        }
}

data class ActivityItem(
    val id: String,
    val action: String,
    val targetName: String,
    val targetType: String,
    val permanent: Boolean = false,
    val createdAt: String
) {
    val actionEmoji: String
        get() = when (action) {
            "upload" -> "📤"
            "delete" -> "🗑️"
            "restore" -> "♻️"
            "move", "create_folder", "delete_folder" -> "📁"
            "share" -> "🔗"
            "edit_image" -> "🎨"
            else -> "⚡"
        }

    val actionDescription: String
        get() = when (action) {
            "upload" -> "Uploaded \"$targetName\""
            "delete" -> if (permanent) "Permanently deleted \"$targetName\"" else "Moved \"$targetName\" to Trash"
            "restore" -> "Restored \"$targetName\" from Trash"
            "move" -> "Organized \"$targetName\" into folder"
            "create_folder" -> "Created folder \"$targetName\""
            "delete_folder" -> "Removed folder \"$targetName\""
            "share" -> "Shared link for \"$targetName\""
            "edit_image" -> "Saved edited photo \"$targetName\""
            else -> "Updated \"$targetName\""
        }

    val relativeTime: String
        get() = formatRelativeTime(createdAt)
}

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val isAiBadge: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestions: List<String> = emptyList()
)

data class ServerHealthState(
    val baseUrl: String = "https://galleryhub-n66c.onrender.com",
    val isChecking: Boolean = false,
    val isReachable: Boolean = false,
    val latencyMs: Long? = null,
    val statusMessage: String = "Cloud Sync Active",
    val lastCheckedAt: Long? = null
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return if (digitGroups == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}

private val isoParsersThreadLocal: ThreadLocal<List<SimpleDateFormat>> = ThreadLocal.withInitial {
    listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd HH:mm:ss"
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

private val shortDateFormatterThreadLocal: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("MMM d, yyyy", Locale.US)
}

private val isoOutputFormatterThreadLocal: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

fun parseIsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    iso.toLongOrNull()?.let { return it }
    val parsers = isoParsersThreadLocal.get() ?: return null
    for (sdf in parsers) {
        try {
            val parsed = sdf.parse(iso)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {
        }
    }
    return null
}

fun formatRelativeTimeFromMillis(timeMillis: Long): String {
    if (timeMillis <= 0L) return "Just now"
    val diffSec = ((System.currentTimeMillis() - timeMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        diffSec < 172800 -> "Yesterday"
        else -> shortDateFormatterThreadLocal.get()?.format(Date(timeMillis)) ?: "Earlier"
    }
}

fun formatRelativeTime(iso: String?): String {
    val time = parseIsoMillis(iso) ?: return "Just now"
    return formatRelativeTimeFromMillis(time)
}

fun nowIsoString(): String {
    return isoOutputFormatterThreadLocal.get()?.format(Date()) ?: ""
}
