package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Native Android video tools for:
 * 1. Extracting a high-res still frame at a specific playback timestamp (Frame Grab).
 * 2. Fast lossless video trimming using MediaExtractor + MediaMuxer without FFmpeg bloat.
 */
object VideoMediaTools {

    suspend fun extractVideoFrameToFile(
        context: Context,
        videoUrl: String,
        positionMs: Long,
        baseName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(videoUrl)
            when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    val headers = RetrofitClient.buildAuthHeadersForUrl(videoUrl)
                    retriever.setDataSource(videoUrl, headers)
                }
                "content" -> retriever.setDataSource(context, uri)
                "file" -> retriever.setDataSource(uri.path)
                else -> retriever.setDataSource(videoUrl)
            }
            val timeUs = (positionMs.coerceAtLeast(0L)) * 1000L
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return@withContext Result.failure(IllegalStateException("Could not capture frame from video"))

            val outDir = File(context.cacheDir, "frame_grabs").apply { mkdirs() }
            val stem = baseName.substringBeforeLast('.')
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                .take(32)
                .ifBlank { "video" }
            val secLabel = (positionMs / 1000L).coerceAtLeast(0L)
            val outFile = File(outDir, "${stem}_frame_${secLabel}s_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            frame.recycle()
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun trimVideoLossless(
        context: Context,
        videoUrl: String,
        startMs: Long,
        endMs: Long,
        baseName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        if (endMs <= startMs + 300L) {
            return@withContext Result.failure(IllegalArgumentException("Trim range must be at least 0.3 seconds"))
        }

        val sourceFile = ensureLocalVideoFile(context, videoUrl)
            ?: return@withContext Result.failure(IllegalStateException("Could not access video file for trimming"))

        val outDir = File(context.cacheDir, "trimmed_videos").apply { mkdirs() }
        val stem = baseName.substringBeforeLast('.')
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .take(32)
            .ifBlank { "clip" }
        val outFile = File(outDir, "${stem}_trimmed_${System.currentTimeMillis()}.mp4")

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(sourceFile.absolutePath)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) {
                return@withContext Result.failure(IllegalStateException("No playable tracks found in video"))
            }

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val indexMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 512 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (size > maxBufferSize) maxBufferSize = size
                    }
                }
            }

            if (indexMap.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No compatible audio/video tracks found"))
            }

            // Preserve source rotation hint if present
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(sourceFile.absolutePath)
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
                if (rotation != null && rotation in setOf(0, 90, 180, 270)) {
                    muxer.setOrientationHint(rotation)
                }
            }
            runCatching { retriever.release() }

            muxer.start()
            val startUs = startMs.coerceAtLeast(0L) * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            val firstSampleTimeByTrack = mutableMapOf<Int, Long>()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                val srcTrack = extractor.sampleTrackIndex
                val dstTrack = indexMap[srcTrack]
                if (dstTrack != null && sampleTimeUs >= startUs - 500_000L) {
                    val baseUs = firstSampleTimeByTrack.getOrPut(srcTrack) { sampleTimeUs }
                    bufferInfo.presentationTimeUs = (sampleTimeUs - baseUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            if (!outFile.exists() || outFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Trimmed video output was empty"))
            }
            Result.success(outFile)
        } catch (e: Exception) {
            runCatching { outFile.delete() }
            Result.failure(e)
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun ensureLocalVideoFile(context: Context, videoUrl: String): File? {
        val uri = Uri.parse(videoUrl)
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
            "content" -> {
                val tmp = File(context.cacheDir, "trim_src_${System.currentTimeMillis()}.mp4")
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tmp).use { out -> input.copyTo(out) }
                    }
                    tmp.takeIf { it.exists() && it.length() > 0L }
                }.getOrNull()
            }
            "http", "https" -> {
                val tmp = File(context.cacheDir, "trim_remote_${videoUrl.hashCode()}.mp4")
                if (tmp.exists() && tmp.length() > 0L) return tmp
                runCatching {
                    val req = Request.Builder().url(videoUrl).get().build()
                    RetrofitClient.okHttpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            res.body?.byteStream()?.use { input ->
                                FileOutputStream(tmp).use { out -> input.copyTo(out) }
                            }
                            tmp.takeIf { it.exists() && it.length() > 0L }
                        } else null
                    }
                }.getOrNull()
            }
            else -> File(videoUrl).takeIf { it.exists() && it.length() > 0L }
        }
    }
}
