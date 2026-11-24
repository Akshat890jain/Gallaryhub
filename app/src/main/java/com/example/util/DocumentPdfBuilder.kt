package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ScanFilterPreset(val label: String) {
    ORIGINAL("Original"),
    HIGH_CONTRAST("Enhance"),
    CRISP_BW("Crisp B&W"),
    WARM_PAPER("Warm Paper")
}

data class ScannedPageDraft(
    val id: String,
    val imageFile: File,
    val filter: ScanFilterPreset = ScanFilterPreset.HIGH_CONTRAST,
    val rotationDegrees: Int = 0
)

/**
 * Multi-page Document Scanner enhancement & native Android PdfDocument assembly engine.
 */
object DocumentPdfBuilder {

    fun loadFilteredPreviewBitmap(page: ScannedPageDraft, maxDim: Int = 1200): Bitmap? {
        val raw = decodeOrientedBitmap(page.imageFile, maxDim) ?: return null
        val rotated = if (page.rotationDegrees % 360 != 0) {
            val m = Matrix().apply { postRotate(page.rotationDegrees.toFloat()) }
            val r = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            if (r !== raw) raw.recycle()
            r
        } else raw

        if (page.filter == ScanFilterPreset.ORIGINAL) return rotated
        val styled = applyScanFilter(rotated, page.filter)
        if (styled !== rotated) rotated.recycle()
        return styled
    }

    suspend fun buildMultiPagePdf(
        context: Context,
        pages: List<ScannedPageDraft>,
        outputTitle: String
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Add at least one scanned page"))
        }
        val outDir = File(context.cacheDir, "scanned_pdfs").apply { mkdirs() }
        val cleanName = outputTitle.trim()
            .removeSuffix(".pdf")
            .replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
            .ifBlank { "Scan_${System.currentTimeMillis()}" }
        val outFile = File(outDir, "$cleanName.pdf")

        val pdf = PdfDocument()
        try {
            // Standard A4 @ 150 DPI = 1240 x 1754 px (sharp text, compact file size)
            val pageW = 1240
            val pageH = 1754

            pages.forEachIndexed { idx, draft ->
                val bmp = loadFilteredPreviewBitmap(draft, maxDim = 1650) ?: return@forEachIndexed
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                val pdfPage = pdf.startPage(pageInfo)
                val canvas = pdfPage.canvas
                canvas.drawColor(Color.WHITE)

                val margin = 36f
                val availW = pageW - margin * 2f
                val availH = pageH - margin * 2f
                val scale = minOf(availW / bmp.width.coerceAtLeast(1), availH / bmp.height.coerceAtLeast(1))
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val left = (pageW - drawW) / 2f
                val top = (pageH - drawH) / 2f

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), paint)
                pdf.finishPage(pdfPage)
                bmp.recycle()
            }

            FileOutputStream(outFile).use { out ->
                pdf.writeTo(out)
            }
            if (!outFile.exists() || outFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Generated PDF was empty"))
            }
            Result.success(outFile)
        } catch (e: Exception) {
            runCatching { outFile.delete() }
            Result.failure(e)
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun applyScanFilter(source: Bitmap, filter: ScanFilterPreset): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val matrix = ColorMatrix()

        when (filter) {
            ScanFilterPreset.ORIGINAL -> return source
            ScanFilterPreset.HIGH_CONTRAST -> {
                val c = 1.32f
                val t = (-0.5f * c + 0.5f) * 255f + 12f
                matrix.set(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            ScanFilterPreset.CRISP_BW -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val c = 1.75f
                val t = (-0.5f * c + 0.5f) * 255f + 18f
                val contrast = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.postConcat(gray)
                matrix.postConcat(contrast)
            }
            ScanFilterPreset.WARM_PAPER -> {
                matrix.set(
                    floatArrayOf(
                        1.08f, 0.04f, 0f, 0f, 8f,
                        0.02f, 1.04f, 0f, 0f, 4f,
                        0f, 0f, 0.92f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun decodeOrientedBitmap(file: File, maxDim: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return decoded
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        }.getOrDefault(decoded)
    }
}
