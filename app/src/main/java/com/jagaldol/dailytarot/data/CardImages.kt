package com.jagaldol.dailytarot.data

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.annotation.DrawableRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bounded caches; all decoding/rotation happens away from the UI thread. */
object CardImages {
    private val thumbnails = BitmapCache(12 * 1024 * 1024)
    private val widgets = BitmapCache(6 * 1024 * 1024)
    private val thumbnailLock = Mutex()
    private val widgetLock = Mutex()

    fun cachedThumbnail(@DrawableRes res: Int): Bitmap? = thumbnails[res.toString()]

    suspend fun thumbnail(resources: Resources, @DrawableRes res: Int): Bitmap =
        withContext(Dispatchers.IO) {
            thumbnailLock.withLock {
                thumbnails[res.toString()] ?: decode(resources, res).also {
                    thumbnails.put(res.toString(), it)
                }
            }
        }

    suspend fun widget(resources: Resources, @DrawableRes res: Int, reversed: Boolean): Bitmap =
        withContext(Dispatchers.Default) {
            val metrics = resources.displayMetrics
            val maxHeight = widgetImageHeight(metrics.widthPixels, metrics.heightPixels)
            val key = "$res:$reversed:$maxHeight"
            widgetLock.withLock {
                widgets[key] ?: run {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeResource(resources, res, bounds)
                    var sample = 1
                    while (bounds.outHeight / sample > maxHeight * 2) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    val source = requireNotNull(BitmapFactory.decodeResource(resources, res, options))
                    val scale = minOf(1f, maxHeight.toFloat() / source.height)
                    val matrix = Matrix().apply {
                        postScale(scale, scale)
                        if (reversed) postRotate(180f)
                    }
                    val result = Bitmap.createBitmap(
                        source, 0, 0, source.width, source.height, matrix, true,
                    )
                    if (result !== source) source.recycle()
                    widgets.put(key, result)
                    result
                }
            }
        }

    private fun decode(resources: Resources, @DrawableRes res: Int): Bitmap =
        requireNotNull(BitmapFactory.decodeResource(resources, res)).apply { prepareToDraw() }

    private class BitmapCache(bytes: Int) : LruCache<String, Bitmap>(bytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        // Evicted bitmaps may still be displayed; let the GC reclaim them.
    }
}

// A 3:5 card must also fit small displays, whose RemoteViews bitmap budget is lower.
internal fun widgetImageHeight(screenWidth: Int, screenHeight: Int): Int =
    minOf(1000, screenHeight, screenWidth * 5 / 3).coerceAtLeast(1)
