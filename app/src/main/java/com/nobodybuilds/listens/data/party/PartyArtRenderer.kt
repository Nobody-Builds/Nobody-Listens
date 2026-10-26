package com.nobodybuilds.listens.data.party

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.core.net.toUri
import com.nobodybuilds.listens.data.model.Song
import java.io.ByteArrayOutputStream

/** Square JPEG cover art for the party page, cached in memory. */
internal class PartyArtRenderer(
    private val context: Context
) {
    private val contentResolver: ContentResolver
        get() = context.contentResolver

    private val artCache = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    fun clear() = artCache.evictAll()

    fun artJpeg(song: Song, size: Int): ByteArray? {
        val key = "${song.id}@$size"
        artCache.get(key)?.let { return it }
        val bitmap = decodeArt(song, size) ?: return null
        val out = ByteArrayOutputStream()
        val scaled = scaleToFit(bitmap, size)
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return out.toByteArray().also { artCache.put(key, it) }
    }

    private fun decodeArt(song: Song, size: Int): Bitmap? {
        val artUri = song.albumArtUriString?.takeIf { it.isNotBlank() }?.toUri()
        if (artUri != null && artUri.scheme != "http" && artUri.scheme != "https") {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(artUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth > 0) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
                    contentResolver.openInputStream(artUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                } else null
            }.getOrNull()?.let { return it }
        }
        val uri = song.contentUriString.takeIf { it.startsWith("content://") || it.startsWith("file://") }?.toUri()
            ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        var s = 1
        while (w / (s * 2) >= target && h / (s * 2) >= target) s *= 2
        return s
    }

    private fun scaleToFit(b: Bitmap, size: Int): Bitmap {
        val side = minOf(b.width, b.height)
        val x = (b.width - side) / 2
        val y = (b.height - side) / 2
        val square = if (b.width != b.height) Bitmap.createBitmap(b, x, y, side, side) else b
        if (side <= size) return square
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        if (square !== b && square !== scaled) square.recycle()
        return scaled
    }
}
