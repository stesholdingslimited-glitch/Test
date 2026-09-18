package com.stesholdings.snagreport.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Handles copying photos (from the camera or from the gallery/file picker) into the app's
 * private storage so entries keep working fully offline, independent of the lifetime of any
 * content:// Uri handed to us by another app.
 */
object ImageStorage {

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 85

    private fun imagesDir(context: Context): File =
        File(context.filesDir, "images").apply { mkdirs() }

    private fun cameraCacheDir(context: Context): File =
        File(context.cacheDir, "camera_images").apply { mkdirs() }

    /** Creates a new empty file (in cache) plus a content Uri for it, ready to pass to the camera app. */
    fun createCameraCaptureUri(context: Context): Uri {
        val file = File(cameraCacheDir(context), "capture_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Copies the given source Uri into a normalised, correctly-oriented, size-capped JPEG in
     * app-private storage and returns its absolute file path. Safe to call for both camera and
     * picker Uris.
     */
    fun processAndStore(context: Context, sourceUri: Uri): String {
        val tempFile = copyToTempFile(context, sourceUri)
        try {
            val orientation = runCatching {
                ExifInterface(tempFile.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val sampled = decodeSampledBitmap(tempFile, MAX_DIMENSION)
            val corrected = applyExifOrientation(sampled, orientation)

            val outFile = File(imagesDir(context), "${UUID.randomUUID()}.jpg")
            FileOutputStream(outFile).use { out ->
                corrected.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (corrected !== sampled) sampled.recycle()
            corrected.recycle()
            return outFile.absolutePath
        } finally {
            tempFile.delete()
        }
    }

    fun deleteStoredImage(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun copyToTempFile(context: Context, uri: Uri): File {
        val tempFile = File.createTempFile("import_", ".tmp", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open image: $uri")
        return tempFile
    }

    private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

        var sampleSize = 1
        var width = boundsOptions.outWidth
        var height = boundsOptions.outHeight
        while (width / 2 >= maxDimension || height / 2 >= maxDimension) {
            sampleSize *= 2
            width /= 2
            height /= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("Unable to decode image: ${file.absolutePath}")
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
