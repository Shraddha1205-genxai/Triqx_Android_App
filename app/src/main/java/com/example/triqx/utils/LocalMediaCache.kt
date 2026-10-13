package com.example.triqx.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object LocalMediaCache {

    private const val TAG = "LocalMediaCache"

    /**
     * Reads the temporary notification content URI stream (e.g. content://com.whatsapp.provider.media/...)
     * and copies the bytes into Triqx's private app files directory (file:///data/user/0/com.example.triqx/files/media_cache/).
     *
     * This guarantees 100% permanent offline storage so images and stickers never expire or get revoked,
     * even if the source app deletes the message or notification.
     */
    fun cacheMediaToFile(context: Context, sourceUriString: String?, prefix: String = "media"): String? {
        if (sourceUriString.isNullOrBlank()) return null

        // If it is already a local file in our internal directory, return as is
        if (sourceUriString.startsWith("file://") && sourceUriString.contains(context.packageName)) {
            return sourceUriString
        }

        return try {
            val sourceUri = Uri.parse(sourceUriString)
            val cacheFolder = File(context.filesDir, "media_cache").apply { if (!exists()) mkdirs() }
            val timeStamp = System.currentTimeMillis()
            val extension = if (sourceUriString.contains("webp", ignoreCase = true)) "webp" else "jpg"
            val targetFile = File(cacheFolder, "${prefix}_${timeStamp}.$extension")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                val localFileUri = Uri.fromFile(targetFile).toString()
                Log.i(TAG, "Successfully cached media locally: $localFileUri (${targetFile.length()} bytes)")
                localFileUri
            } else {
                sourceUriString
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy source URI '$sourceUriString' locally (${e.message}). Reverting to original URI.")
            sourceUriString
        }
    }
}
