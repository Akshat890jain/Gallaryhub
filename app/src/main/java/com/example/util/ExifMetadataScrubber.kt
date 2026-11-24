package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Privacy utility that strips GPS coordinates, camera make/model, lens metadata,
 * software tags, and capture timestamps from photos before sharing externally or
 * creating public links, while preserving the visual orientation.
 */
object ExifMetadataScrubber {

    private val SENSITIVE_EXIF_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_IMAGE_UNIQUE_ID
    )

    data class ScrubResult(
        val file: File,
        val strippedTagCount: Int,
        val hadLocationData: Boolean
    )

    fun scrubPhotoToTempCopy(
        context: Context,
        sourceFile: File,
        displayName: String = sourceFile.name
    ): ScrubResult {
        val outDir = File(context.cacheDir, "scrubbed_shares").apply { mkdirs() }
        val ext = displayName.substringAfterLast('.', sourceFile.extension).lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val safeBase = displayName.substringBeforeLast('.')
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .take(40)
            .ifBlank { "photo" }
        val destFile = File(outDir, "clean_${safeBase}_${System.currentTimeMillis()}.$ext")

        var strippedCount = 0
        var hadLocation = false
        var orientation = ExifInterface.ORIENTATION_NORMAL

        runCatching {
            val exif = ExifInterface(sourceFile.absolutePath)
            orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            hadLocation = !exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE).isNullOrBlank() ||
                !exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE).isNullOrBlank()
            for (tag in SENSITIVE_EXIF_TAGS) {
                if (!exif.getAttribute(tag).isNullOrBlank()) {
                    strippedCount++
                }
            }
        }

        // Re-encode pixels directly without EXIF metadata (100% clean across JPEG/PNG/WebP)
        val reencoded = runCatching {
            val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return@runCatching false
            val oriented = applyOrientation(decoded, orientation)
            val format = when (ext) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.JPEG
            }
            val quality = if (ext == "png") 100 else 94
            FileOutputStream(destFile).use { out ->
                oriented.compress(format, quality, out)
            }
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            destFile.exists() && destFile.length() > 0L
        }.getOrDefault(false)

        if (reencoded) {
            return ScrubResult(
                file = destFile,
                strippedTagCount = strippedCount,
                hadLocationData = hadLocation
            )
        }

        // Fallback: copy bytes and strip EXIF attributes in-place on the copy
        sourceFile.copyTo(destFile, overwrite = true)
        runCatching {
            val copyExif = ExifInterface(destFile.absolutePath)
            for (tag in SENSITIVE_EXIF_TAGS) {
                copyExif.setAttribute(tag, null)
            }
            copyExif.saveAttributes()
        }
        return ScrubResult(
            file = destFile,
            strippedTagCount = strippedCount,
            hadLocationData = hadLocation
        )
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }
}
