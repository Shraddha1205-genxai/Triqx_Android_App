package com.example.triqx.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppIconCache {
    private const val MAX_ENTRIES = 250
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(packageName: String): ImageBitmap? {
        return synchronized(cache) {
            cache.get(packageName)
        }
    }

    fun put(packageName: String, bitmap: ImageBitmap) {
        synchronized(cache) {
            cache.put(packageName, bitmap)
        }
    }
}

@Composable
fun AppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier = Modifier.size(48.dp)
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val cached = AppIconCache.get(packageName)
                    if (cached != null) return@withContext cached

                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val drawable = pm.getApplicationIcon(appInfo)
                    val imgBitmap = drawableToSoftwareImageBitmap(drawable, 96, 96)
                    if (imgBitmap != null) {
                        AppIconCache.put(packageName, imgBitmap)
                    }
                    imgBitmap
                } catch (e: Throwable) {
                    null
                }
            }
            bitmap = loaded
        }
    }

    val currentBitmap = bitmap ?: AppIconCache.get(packageName)

    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = appName,
            modifier = modifier
        )
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = appName.trim().take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun drawableToSoftwareImageBitmap(drawable: Drawable, width: Int, height: Int): ImageBitmap? {
    return try {
        val w = if (width > 0) width else 96
        val h = if (height > 0) height else 96
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Throwable) {
        null
    }
}
