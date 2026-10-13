package com.example.triqx.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageUtils {

    private const val TAG = "ImageUtils"

    /**
     * Reads a media content/file URI, downscales it to [maxDimension] pixels,
     * compresses it as JPEG with [compressQuality]%, and converts it to a compact Base64 string.
     *
     * This prevents HTTP 413 / 500 "request entity too large" payload errors on backend services.
     */
    fun convertUriToBase64(
        context: Context,
        uriString: String?,
        maxDimension: Int = 640,
        compressQuality: Int = 70
    ): String? {
        if (uriString.isNullOrBlank()) return null

        return try {
            val uri = Uri.parse(uriString)

            // 1. First pass: Decode bounds only to calculate inSampleSize
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null
            }

            // Calculate sample size to downscale efficiently
            val largestSide = max(options.outWidth, options.outHeight)
            var sampleSize = 1
            while (largestSide / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }

            // 2. Second pass: Load downsampled Bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 3. Precise scale down if largest side still exceeds maxDimension
            val currentMax = max(rawBitmap.width, rawBitmap.height)
            val scaledBitmap = if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax
                val targetW = (rawBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (rawBitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true).also {
                    if (it != rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            // 4. Compress to JPEG byte array
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, outputStream)
            val finalWidth = scaledBitmap.width
            val finalHeight = scaledBitmap.height
            scaledBitmap.recycle()

            val bytes = outputStream.toByteArray()
            if (bytes.isNotEmpty()) {
                val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Log.i(TAG, "Compressed media '$uriString' from ${options.outWidth}x${options.outHeight} -> ${finalWidth}x${finalHeight} (${bytes.size / 1024} KB, Base64 len: ${base64Str.length})")
                base64Str
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Could not compress and convert media URI '$uriString' to Base64: ${e.message}")
            null
        }
    }
}
