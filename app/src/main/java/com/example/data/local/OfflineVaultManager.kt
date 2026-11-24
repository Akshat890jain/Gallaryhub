package com.example.data.local

import android.content.Context
import com.example.data.model.MediaType
import com.example.data.model.OfflineAvailabilityState
import com.example.data.model.OfflineVaultStats
import com.example.data.model.UnifiedMediaItem
import com.example.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Typed exception raised when every remote storage candidate and ZIP fallback returns HTTP 404,
 * allowing the UI to offer actionable Re-link from Device / Remove Entry recovery.
 */
class MissingServerFileException(
    val mediaId: String,
    val originalName: String,
    message: String
) : IllegalStateException(message)

/**
 * App-private two-tier Offline Vault (requires ZERO external storage permissions):
 * 1. Pinned Tier (`filesDir/offline_vault/pinned/`): Protected files explicitly pinned by the user.
 * 2. Auto-Cache Tier (`filesDir/offline_vault/autocache/`): Automatically cached when viewing remote
 *    files in the lightbox, bounded by a 150 MB LRU cap so device storage never bloats.
 */
class OfflineVaultManager(private val context: Context) {

    companion object {
        private const val AUTO_CACHE_MAX_BYTES = 150L * 1024L * 1024L // 150 MB LRU budget
    }

    private data class VaultEntry(
        val mediaId: String,
        val filePath: String,
        val tier: OfflineAvailabilityState,
        val sizeBytes: Long,
        val cachedAtMillis: Long,
        val lastAccessedMillis: Long,
        val originalName: String,
        val mediaTypeKey: String
    )

    private val mutex = Mutex()
    private val vaultRoot: File = File(context.filesDir, "offline_vault").apply { mkdirs() }
    private val pinnedDir: File = File(vaultRoot, "pinned").apply { mkdirs() }
    private val autoCacheDir: File = File(vaultRoot, "autocache").apply { mkdirs() }
    private val manifestFile: File = File(vaultRoot, "manifest.json")

    @Volatile
    private var loaded = false

    @Volatile
    private var autoCacheEnabledFlag = true

    private val entries = linkedMapOf<String, VaultEntry>()

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        entries.clear()
        if (!manifestFile.exists()) return
        try {
            val root = JSONObject(manifestFile.readText())
            autoCacheEnabledFlag = root.optBoolean("autoCacheEnabled", true)
            val arr = root.optJSONArray("entries") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("mediaId")
                val path = o.optString("filePath")
                val file = File(path)
                if (id.isNotBlank() && path.isNotBlank() && file.exists() && file.length() > 0L) {
                    entries[id] = VaultEntry(
                        mediaId = id,
                        filePath = path,
                        tier = OfflineAvailabilityState.fromKey(o.optString("tier")),
                        sizeBytes = file.length(),
                        cachedAtMillis = o.optLong("cachedAtMillis", System.currentTimeMillis()),
                        lastAccessedMillis = o.optLong("lastAccessedMillis", System.currentTimeMillis()),
                        originalName = o.optString("originalName"),
                        mediaTypeKey = o.optString("mediaTypeKey", "image")
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun persistManifestLocked() {
        try {
            val root = JSONObject().apply {
                put("autoCacheEnabled", autoCacheEnabledFlag)
                val arr = JSONArray()
                entries.values.forEach { e ->
                    arr.put(
                        JSONObject().apply {
                            put("mediaId", e.mediaId)
                            put("filePath", e.filePath)
                            put("tier", e.tier.key)
                            put("sizeBytes", e.sizeBytes)
                            put("cachedAtMillis", e.cachedAtMillis)
                            put("lastAccessedMillis", e.lastAccessedMillis)
                            put("originalName", e.originalName)
                            put("mediaTypeKey", e.mediaTypeKey)
                        }
                    )
                }
                put("entries", arr)
            }
            val tmp = File(vaultRoot, "manifest.json.tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(manifestFile)
        } catch (_: Exception) {
        }
    }

    suspend fun getSnapshotIndex(): Map<String, Pair<OfflineAvailabilityState, String>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoadedLocked()
                val staleIds = mutableListOf<String>()
                val map = HashMap<String, Pair<OfflineAvailabilityState, String>>(entries.size)
                for ((id, entry) in entries) {
                    val f = File(entry.filePath)
                    if (f.exists() && f.length() > 0L) {
                        map[id] = entry.tier to entry.filePath
                    } else {
                        staleIds.add(id)
                    }
                }
                if (staleIds.isNotEmpty()) {
                    staleIds.forEach { entries.remove(it) }
                    persistManifestLocked()
                }
                map
            }
        }

    suspend fun getLocalFile(mediaId: String): File? = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            val entry = entries[mediaId] ?: return@withContext null
            val f = File(entry.filePath)
            if (f.exists() && f.length() > 0L) {
                entries[mediaId] = entry.copy(lastAccessedMillis = System.currentTimeMillis())
                f
            } else {
                entries.remove(mediaId)
                persistManifestLocked()
                null
            }
        }
    }

    suspend fun isAutoCacheEnabled(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            autoCacheEnabledFlag
        }
    }

    suspend fun setAutoCacheEnabled(enabled: Boolean): OfflineVaultStats =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoadedLocked()
                autoCacheEnabledFlag = enabled
                persistManifestLocked()
                buildStatsLocked()
            }
        }

    suspend fun computeStats(): OfflineVaultStats = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            buildStatsLocked()
        }
    }

    private fun buildStatsLocked(): OfflineVaultStats {
        var pinnedCount = 0
        var pinnedBytes = 0L
        var autoCount = 0
        var autoBytes = 0L
        entries.values.forEach { e ->
            val f = File(e.filePath)
            if (f.exists() && f.length() > 0L) {
                when (e.tier) {
                    OfflineAvailabilityState.PINNED -> {
                        pinnedCount++
                        pinnedBytes += f.length()
                    }
                    OfflineAvailabilityState.AUTO_CACHED -> {
                        autoCount++
                        autoBytes += f.length()
                    }
                    OfflineAvailabilityState.NONE -> Unit
                }
            }
        }
        return OfflineVaultStats(
            pinnedCount = pinnedCount,
            pinnedBytes = pinnedBytes,
            autoCachedCount = autoCount,
            autoCachedBytes = autoBytes,
            autoCacheEnabled = autoCacheEnabledFlag
        )
    }

    /**
     * Explicitly pins a file into the protected Offline Vault.
     * Reuses an existing auto-cached or local uploaded file instantaneously without network traffic.
     */
    suspend fun pinItemOffline(
        item: UnifiedMediaItem,
        serverBaseUrl: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val existing = mutex.withLock {
            ensureLoadedLocked()
            entries[item.id]
        }

        // Already pinned and valid on disk
        if (existing != null && existing.tier == OfflineAvailabilityState.PINNED) {
            val file = File(existing.filePath)
            if (file.exists() && file.length() > 0L) {
                return@withContext Result.success(file)
            }
        }

        val ext = item.extension.ifBlank {
            when (item.mediaType) {
                MediaType.IMAGE -> "jpg"
                MediaType.VIDEO -> "mp4"
                MediaType.DOCUMENT -> "bin"
            }
        }
        val safeId = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(pinnedDir, "${safeId}.$ext")

        // Fast path 1: Promote existing auto-cached copy
        if (existing != null) {
            val cachedFile = File(existing.filePath)
            if (cachedFile.exists() && cachedFile.length() > 0L) {
                try {
                    cachedFile.copyTo(destFile, overwrite = true)
                    if (cachedFile.absolutePath != destFile.absolutePath) {
                        cachedFile.delete()
                    }
                    recordEntry(item, destFile, OfflineAvailabilityState.PINNED)
                    return@withContext Result.success(destFile)
                } catch (_: Exception) {
                }
            }
        }

        // Fast path 2: Copy existing local file (offline path, localUri, uploaded_media, edited_images, doc cache, Coil disk cache)
        val localSource = findExistingLocalSource(item, serverBaseUrl)
        if (localSource != null) {
            return@withContext runCatching {
                if (localSource.absolutePath != destFile.absolutePath) {
                    localSource.copyTo(destFile, overwrite = true)
                }
                recordEntry(item, destFile, OfflineAvailabilityState.PINNED)
                destFile
            }
        }

        // Fast path 3: Copy from content:// URI if present
        if (!item.localUri.isNullOrBlank() && item.localUri.startsWith("content://", ignoreCase = true)) {
            val copiedFromContent = runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(item.localUri))?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                destFile.exists() && destFile.length() > 0L
            }.getOrDefault(false)
            if (copiedFromContent) {
                recordEntry(item, destFile, OfflineAvailabilityState.PINNED)
                return@withContext Result.success(destFile)
            }
        }

        // Network download path: try all normalized & encoded candidate URLs, plus single-item ZIP fallback for images
        val downloadResult = downloadFromCandidates(item, serverBaseUrl, destFile)
        downloadResult.onSuccess { file ->
            recordEntry(item, file, OfflineAvailabilityState.PINNED)
        }
        downloadResult
    }

    /**
     * Pins an already-downloaded local file (e.g. from the Public Share Deep-Link Viewer)
     * directly into the protected Offline Vault without any extra network call.
     */
    suspend fun pinLocalFileOffline(
        item: UnifiedMediaItem,
        sourceFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            return@withContext Result.failure(IllegalStateException("Source file is unavailable"))
        }
        val ext = item.extension.ifBlank { sourceFile.extension.ifBlank { "bin" } }
        val safeId = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(pinnedDir, "${safeId}.$ext")
        runCatching {
            if (sourceFile.absolutePath != destFile.absolutePath) {
                sourceFile.copyTo(destFile, overwrite = true)
            }
            recordEntry(item, destFile, OfflineAvailabilityState.PINNED)
            destFile
        }
    }

    suspend fun unpinItemOffline(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            val entry = entries.remove(mediaId) ?: return@withContext false
            runCatching { File(entry.filePath).delete() }
            persistManifestLocked()
            true
        }
    }

    /**
     * Silently auto-caches a remote item when opened in the Lightbox if auto-caching is enabled.
     * Skips if already pinned or cached, and enforces a 150 MB LRU budget.
     */
    suspend fun autoCacheViewedItem(
        item: UnifiedMediaItem,
        serverBaseUrl: String
    ): File? = withContext(Dispatchers.IO) {
        val (enabled, existing) = mutex.withLock {
            ensureLoadedLocked()
            autoCacheEnabledFlag to entries[item.id]
        }
        if (!enabled) return@withContext null

        if (existing != null) {
            val f = File(existing.filePath)
            if (f.exists() && f.length() > 0L) {
                mutex.withLock {
                    entries[item.id] = existing.copy(lastAccessedMillis = System.currentTimeMillis())
                }
                return@withContext f
            }
        }

        // If the file is already local (uploaded from this device), reuse without duplicating
        val alreadyLocal = findExistingLocalSource(item, serverBaseUrl)
        if (alreadyLocal != null) return@withContext alreadyLocal

        val ext = item.extension.ifBlank {
            when (item.mediaType) {
                MediaType.IMAGE -> "jpg"
                MediaType.VIDEO -> "mp4"
                MediaType.DOCUMENT -> "bin"
            }
        }
        val safeId = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(autoCacheDir, "${safeId}.$ext")

        val downloaded = downloadFromCandidates(item, serverBaseUrl, destFile).getOrNull() ?: return@withContext null
        recordEntry(item, downloaded, OfflineAvailabilityState.AUTO_CACHED)
        evictAutoCacheIfNeeded()
        downloaded
    }

    suspend fun clearAutoCache(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            val toRemove = entries.values.filter { it.tier == OfflineAvailabilityState.AUTO_CACHED }
            toRemove.forEach { entry ->
                runCatching { File(entry.filePath).delete() }
                entries.remove(entry.mediaId)
            }
            autoCacheDir.listFiles()?.forEach { runCatching { it.delete() } }
            persistManifestLocked()
            toRemove.size
        }
    }

    suspend fun clearAllOfflineFiles(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            val count = entries.size
            entries.values.forEach { entry ->
                runCatching { File(entry.filePath).delete() }
            }
            entries.clear()
            pinnedDir.listFiles()?.forEach { runCatching { it.delete() } }
            autoCacheDir.listFiles()?.forEach { runCatching { it.delete() } }
            persistManifestLocked()
            count
        }
    }

    private suspend fun recordEntry(
        item: UnifiedMediaItem,
        file: File,
        tier: OfflineAvailabilityState
    ) {
        mutex.withLock {
            ensureLoadedLocked()
            val now = System.currentTimeMillis()
            entries[item.id] = VaultEntry(
                mediaId = item.id,
                filePath = file.absolutePath,
                tier = tier,
                sizeBytes = file.length(),
                cachedAtMillis = now,
                lastAccessedMillis = now,
                originalName = item.originalName,
                mediaTypeKey = item.mediaType.key
            )
            persistManifestLocked()
        }
    }

    private suspend fun evictAutoCacheIfNeeded() {
        mutex.withLock {
            val autoEntries = entries.values
                .filter { it.tier == OfflineAvailabilityState.AUTO_CACHED }
                .sortedBy { it.lastAccessedMillis }
            var totalBytes = autoEntries.sumOf { it.sizeBytes }
            if (totalBytes <= AUTO_CACHE_MAX_BYTES) return

            for (candidate in autoEntries) {
                if (totalBytes <= AUTO_CACHE_MAX_BYTES) break
                runCatching { File(candidate.filePath).delete() }
                entries.remove(candidate.mediaId)
                totalBytes -= candidate.sizeBytes
            }
            persistManifestLocked()
        }
    }

    /**
     * Searches all device-local sources before hitting the network:
     * 1. Existing offlineFilePath / file:// localUri / direct filesystem path
     * 2. filesDir/uploaded_media and filesDir/edited_images
     * 3. cacheDir document preview cache (`studio_doc_<md5>.<ext>`)
     * 4. Coil's persistent disk cache for any candidate URL of this item
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private fun findExistingLocalSource(item: UnifiedMediaItem, serverBaseUrl: String): File? {
        // 1. Direct paths
        val direct = listOfNotNull(
            item.offlineFilePath?.takeIf { it.isNotBlank() }?.let { File(it) },
            item.localUri?.takeIf { it.startsWith("file://", ignoreCase = true) }?.let {
                runCatching { File(android.net.Uri.parse(it).path ?: it.removePrefix("file://")) }.getOrNull()
            },
            item.path.takeIf { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }?.let { File(it) }
        ).firstOrNull { it.exists() && it.isFile && it.length() > 0L }
        if (direct != null) return direct

        // 2. Local uploaded_media and edited_images directories
        val sanitizedOriginal = item.originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val ext = item.extension
        val localDirs = listOf(
            File(context.filesDir, "uploaded_media"),
            File(context.filesDir, "edited_images")
        )
        for (dir in localDirs) {
            if (!dir.exists()) continue
            val matched = dir.listFiles()?.firstOrNull { f ->
                f.isFile && f.length() > 0L && (
                    f.name.equals(item.originalName, ignoreCase = true) ||
                        f.name.equals(item.filename, ignoreCase = true) ||
                        (sanitizedOriginal.isNotBlank() && f.name.endsWith("-$sanitizedOriginal", ignoreCase = true)) ||
                        (item.size > 0L && f.length() == item.size && ext.isNotBlank() && f.extension.equals(ext, ignoreCase = true))
                    )
            }
            if (matched != null) return matched
        }

        // 3. Document preview cache in cacheDir
        if (ext.isNotBlank()) {
            val md5 = runCatching {
                java.security.MessageDigest.getInstance("MD5")
                    .digest((item.id + "_" + item.filename).toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull()
            if (!md5.isNullOrBlank()) {
                val docCacheFile = File(context.cacheDir, "studio_doc_${md5}.$ext")
                if (docCacheFile.exists() && docCacheFile.length() > 0L) return docCacheFile
            }
        }

        // 4. Coil persistent disk cache (if thumbnail/photo was already loaded before)
        val coilDiskCache = runCatching { coil.Coil.imageLoader(context).diskCache }.getOrNull()
        if (coilDiskCache != null) {
            val candidateUrls = item.resolveRemoteCandidateUrls(serverBaseUrl)
            for (url in candidateUrls) {
                val snap = runCatching { coilDiskCache.openSnapshot(url) }.getOrNull()
                if (snap != null) {
                    try {
                        val cachedData = snap.data.toFile()
                        if (cachedData.exists() && cachedData.length() > 0L) {
                            val staging = File(context.cacheDir, "coil_vault_copy_${item.id.hashCode()}.${ext.ifBlank { "bin" }}")
                            cachedData.copyTo(staging, overwrite = true)
                            if (staging.exists() && staging.length() > 0L) {
                                return staging
                            }
                        }
                    } finally {
                        runCatching { snap.close() }
                    }
                }
            }
        }

        return null
    }

    private suspend fun downloadFromCandidates(
        item: UnifiedMediaItem,
        serverBaseUrl: String,
        destFile: File
    ): Result<File> {
        val candidates = item.resolveRemoteCandidateUrls(serverBaseUrl)
        if (candidates.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid remote URL available for offline download"))
        }

        val expectHtml = item.extension in setOf("html", "htm")
        var sawOnly404 = true
        var lastError: Throwable? = null
        var lastHttpCode: Int? = null

        for (url in candidates) {
            val attempt = downloadToFile(url, destFile, expectHtml)
            if (attempt.isSuccess) return attempt
            val err = attempt.exceptionOrNull()
            lastError = err
            val code = (err as? HttpStatusException)?.code
            if (code != null) {
                lastHttpCode = code
                if (code != 404) sawOnly404 = false
            } else {
                sawOnly404 = false
            }
        }

        // Secondary fallback for all cloud media types: try single-item ZIP export endpoint
        val isRemoteServerId = item.id.isNotBlank() && !item.id.startsWith("med_") && !item.id.startsWith("edt_")
        if (isRemoteServerId) {
            val zipExtracted = tryExtractSingleMediaViaZip(item.mediaType, item.id, destFile)
            if (zipExtracted != null) {
                return Result.success(zipExtracted)
            }
        }

        if (sawOnly404 && lastHttpCode == 404) {
            return Result.failure(
                MissingServerFileException(
                    mediaId = item.id,
                    originalName = item.originalName,
                    message = "File \"${item.originalName}\" is missing from the server disk (HTTP 404). Re-link it from your device or remove the old entry."
                )
            )
        }
        return Result.failure(lastError ?: IllegalStateException("Download failed"))
    }

    private suspend fun tryExtractSingleMediaViaZip(
        mediaType: MediaType,
        mediaId: String,
        destFile: File
    ): File? {
        return runCatching {
            val res = when (mediaType) {
                MediaType.IMAGE -> RetrofitClient.apiService.downloadImagesZip(
                    com.example.data.remote.ZipImagesRequest(listOf(mediaId))
                )
                MediaType.VIDEO -> RetrofitClient.apiService.downloadVideosZip(
                    com.example.data.remote.ZipVideosRequest(listOf(mediaId))
                )
                MediaType.DOCUMENT -> RetrofitClient.apiService.downloadDocumentsZip(
                    com.example.data.remote.ZipDocumentsRequest(listOf(mediaId))
                )
            }
            if (!res.isSuccessful) return@runCatching null
            val body = res.body() ?: return@runCatching null
            java.util.zip.ZipInputStream(body.byteStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val tmpFile = File(destFile.parentFile, "${destFile.name}.zipextract")
                        FileOutputStream(tmpFile).use { out -> zis.copyTo(out) }
                        if (tmpFile.exists() && tmpFile.length() > 0L) {
                            if (destFile.exists()) destFile.delete()
                            if (!tmpFile.renameTo(destFile)) {
                                tmpFile.copyTo(destFile, overwrite = true)
                                tmpFile.delete()
                            }
                            return@runCatching destFile
                        }
                        tmpFile.delete()
                    }
                    entry = zis.nextEntry
                }
            }
            null
        }.getOrNull()
    }

    private class HttpStatusException(val code: Int, message: String) : IllegalStateException(message)

    private fun downloadToFile(url: String, destFile: File, allowHtml: Boolean): Result<File> {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Invalid remote URL for offline download"))
        }
        val tmpFile = File(destFile.parentFile, "${destFile.name}.download")
        return try {
            val req = Request.Builder().url(url).get().build()
            RetrofitClient.okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(HttpStatusException(response.code, "Download failed (HTTP ${response.code})"))
                }
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (!allowHtml && contentType.contains("text/html")) {
                    return Result.failure(HttpStatusException(404, "Server returned HTML fallback instead of file"))
                }
                val body = response.body ?: return Result.failure(IllegalStateException("Empty download body"))
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
            }
            if (tmpFile.exists() && tmpFile.length() > 0L) {
                if (destFile.exists()) destFile.delete()
                if (!tmpFile.renameTo(destFile)) {
                    tmpFile.copyTo(destFile, overwrite = true)
                    tmpFile.delete()
                }
                Result.success(destFile)
            } else {
                tmpFile.delete()
                Result.failure(IllegalStateException("Downloaded file was empty"))
            }
        } catch (e: Exception) {
            runCatching { tmpFile.delete() }
            Result.failure(e)
        }
    }
}
