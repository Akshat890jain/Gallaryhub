package com.example.util

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream

/**
 * OkHttp RequestBody wrapper that reports per-file byte upload progress (0..100%)
 * in real time to drive live upload progress bars in GalleryHub's Upload Queue.
 */
class UploadProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length().coerceAtLeast(0L)

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength().coerceAtLeast(1L)
        val buffer = ByteArray(16 * 1024)
        var uploaded = 0L
        var lastReportedPercent = -1

        FileInputStream(file).use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                val pct = ((uploaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                if (pct != lastReportedPercent && (pct == 100 || pct - lastReportedPercent >= 3)) {
                    lastReportedPercent = pct
                    onProgress(pct)
                }
            }
        }
        if (lastReportedPercent < 100) {
            onProgress(100)
        }
    }
}
