package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.model.MediaType
import com.example.data.model.UnifiedMediaItem
import com.example.data.remote.RetrofitClient
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * On-device ML Kit OCR engine that extracts searchable text from photos, screenshots,
 * receipts, onboarding passes, and PDF pages strictly locally with a persisted JSON index.
 */
class OcrTextIndexer(private val context: Context) {

    private val indexFile: File
        get() = File(context.filesDir, "ocr_text_index.json")

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun loadIndex(): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!indexFile.exists()) return@withContext emptyMap()
            val json = JSONObject(indexFile.readText())
            val out = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val text = json.optString(key)
                if (text.isNotBlank()) out[key] = text
            }
            out
        }.getOrDefault(emptyMap())
    }

    suspend fun saveIndex(index: Map<String, String>) = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject()
            index.forEach { (k, v) ->
                if (v.isNotBlank()) json.put(k, v.take(6000))
            }
            indexFile.writeText(json.toString())
        }
    }

    suspend fun extractTextForItem(
        item: UnifiedMediaItem,
        serverBaseUrl: String
    ): String = withContext(Dispatchers.IO) {
        val localFile = resolveReadableFile(item, serverBaseUrl) ?: return@withContext ""
        return@withContext when {
            item.mediaType == MediaType.IMAGE -> extractFromImageFile(localFile)
            item.extension.equals("pdf", ignoreCase = true) -> extractFromPdfFile(localFile)
            item.extension in setOf("txt", "md", "csv", "json", "log", "xml", "html") -> {
                runCatching { localFile.readText().take(4000).trim() }.getOrDefault("")
            }
            else -> ""
        }
    }

    private suspend fun extractFromImageFile(file: File): String {
        val bitmap = decodeDownsampledBitmap(file, maxDim = 1600) ?: return ""
        return try {
            recognizeBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun extractFromPdfFile(pdfFile: File): String {
        val sb = StringBuilder()
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val maxPages = renderer.pageCount.coerceAtMost(4)
                    for (i in 0 until maxPages) {
                        renderer.openPage(i).use { page ->
                            val scale = (1200f / page.width.coerceAtLeast(1)).coerceIn(1f, 2f)
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            try {
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pageText = recognizeBitmap(bmp)
                                if (pageText.isNotBlank()) {
                                    if (sb.isNotEmpty()) sb.append("\n")
                                    sb.append(pageText)
                                }
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.text.trim())
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume("")
                }
        }

    private fun resolveReadableFile(item: UnifiedMediaItem, serverBaseUrl: String): File? {
        item.offlineFilePath?.takeIf { it.isNotBlank() }?.let { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0L) return f
        }
        item.localUri?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                val uri = Uri.parse(raw)
                if (uri.scheme == "file") {
                    uri.path?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
                } else {
                    val tmp = File(context.cacheDir, "ocr_tmp_${item.id}.${item.extension.ifBlank { "bin" }}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { out -> input.copyTo(out) }
                    }
                    tmp.takeIf { it.exists() && it.length() > 0L }
                }
            }.getOrNull()?.let { return it }
        }
        if (item.path.isNotBlank() && !item.path.startsWith("http", ignoreCase = true)) {
            val f = File(item.path)
            if (f.exists() && f.length() > 0L) return f
        }
        val tmp = File(context.cacheDir, "ocr_net_${item.id}.${item.extension.ifBlank { "bin" }}")
        if (tmp.exists() && tmp.length() > 0L) return tmp
        for (url in item.resolveRemoteCandidateUrls(serverBaseUrl)) {
            val downloaded = runCatching {
                val req = Request.Builder().url(url).get().build()
                RetrofitClient.okHttpClient.newCall(req).execute().use { res ->
                    val ct = res.header("Content-Type").orEmpty().lowercase()
                    if (res.isSuccessful && !ct.contains("text/html")) {
                        res.body?.byteStream()?.use { input ->
                            tmp.outputStream().use { out -> input.copyTo(out) }
                        }
                        tmp.exists() && tmp.length() > 0L
                    } else false
                }
            }.getOrDefault(false)
            if (downloaded) return tmp
        }
        return null
    }

    private fun decodeDownsampledBitmap(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }
}
