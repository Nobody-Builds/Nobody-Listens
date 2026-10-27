package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.nobodybuilds.listens.utils.AlbumArtUtils
import com.nobodybuilds.listens.utils.ArtworkTransportSanitizer
import com.nobodybuilds.listens.utils.LocalArtworkUri

private val SUPPORTED_WIDGET_LOCAL_ARTWORK_SCHEMES = setOf(
    "content",
    "file",
    "android.resource",
    LocalArtworkUri.SCHEME,
)

internal fun decodeWidgetAlbumArtBitmap(
    context: Context,
    rawUri: String,
    targetWidthPx: Int,
    targetHeightPx: Int,
): Bitmap? {
    val isLocalArtworkUri = LocalArtworkUri.isLocalArtworkUri(rawUri)
    val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val isSupportedLocalUri = isLocalArtworkUri ||
        scheme in SUPPORTED_WIDGET_LOCAL_ARTWORK_SCHEMES ||
        (scheme.isNullOrBlank() && rawUri.startsWith("/"))
    if (!isSupportedLocalUri) {
        return null
    }

    val appContext = context.applicationContext
    val encodedBytes = AlbumArtUtils.openArtworkInputStream(appContext, uri)?.use { input ->
        readBytesCapped(input, ArtworkTransportSanitizer.WIDGET_CONFIG.sourceBytesLimit)
    } ?: return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(encodedBytes, 0, encodedBytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    var inSampleSize = 1
    if (bounds.outHeight > targetHeightPx || bounds.outWidth > targetWidthPx) {
        val halfHeight = bounds.outHeight / 2
        val halfWidth = bounds.outWidth / 2
        while (
            halfHeight / inSampleSize >= targetHeightPx &&
            halfWidth / inSampleSize >= targetWidthPx
        ) {
            inSampleSize *= 2
        }
    }

    return BitmapFactory.decodeByteArray(
        encodedBytes,
        0,
        encodedBytes.size,
        BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inJustDecodeBounds = false
        }
    )
}

private fun readBytesCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalRead = 0
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        totalRead += read
        if (totalRead > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray().takeIf { it.isNotEmpty() }
}

/**
 * Artwork for widgets, sized for RemoteViews. A widget update travels to the launcher in one
 * binder transaction (about 1 MB for everything, and SizeMode.Exact sends one layout per size),
 * so covers are cut to a square of at most [COVER_PX] (RGB_565, ~180 KB) and small images to
 * [THUMB_PX]. One bitmap instance per (image, tier) is cached and reused across sizes, so
 * RemoteViews' bitmap cache sends it once. Cached bitmaps are never recycled.
 */
internal object WidgetBitmaps {
    const val THUMB_PX = 144
    const val COVER_PX = 300

    fun tierFor(requestedPx: Int): Int = if (requestedPx in 1..THUMB_PX) THUMB_PX else COVER_PX

    fun load(context: Context, bytes: ByteArray?, uri: String?, requestedPx: Int): Bitmap? {
        val target = tierFor(requestedPx)
        if (bytes != null && bytes.isNotEmpty()) {
            val key = "b:${bytes.size}:${bytes.contentHashCode()}@$target"
            AlbumArtBitmapCache.getBitmap(key)?.let { return it }
            val decoded = runCatching { decodeBytes(bytes, target) }.getOrNull()
            if (decoded != null) {
                AlbumArtBitmapCache.putBitmap(key, decoded)
                return decoded
            }
        }
        if (!uri.isNullOrBlank()) {
            val key = "u:$uri@$target"
            AlbumArtBitmapCache.getBitmap(key)?.let { return it }
            val decoded = runCatching {
                decodeWidgetAlbumArtBitmap(context, uri, target, target)?.let { squareCrop(it, target) }
            }.getOrNull()
            if (decoded != null) {
                AlbumArtBitmapCache.putBitmap(key, decoded)
                return decoded
            }
        }
        return null
    }

    private fun decodeBytes(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        val raw = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return squareCrop(raw, target)
    }

    /** Centre-crops [src] to a square of at most [target] px in RGB_565; recycles [src] when a copy was made. */
    private fun squareCrop(src: Bitmap, target: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val out = minOf(side, target)
        if (src.width == out && src.height == out && src.config == Bitmap.Config.RGB_565) return src
        val dst = Bitmap.createBitmap(out, out, Bitmap.Config.RGB_565)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        android.graphics.Canvas(dst).drawBitmap(
            src,
            android.graphics.Rect(left, top, left + side, top + side),
            android.graphics.Rect(0, 0, out, out),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        src.recycle()
        return dst
    }
}
