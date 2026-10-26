package com.nobodybuilds.listens.data.ytmusic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Picks sharp artwork for YouTube songs.
 *
 * - Album art (lh3.googleusercontent / ggpht) is square and can be asked for at any size.
 * - Video thumbnails (i.ytimg.com) only exist at fixed sizes. The HD ones (maxresdefault,
 *   hq720) are true 16:9; the smaller sddefault/hqdefault are 4:3 with black bars baked in.
 *   So try HD first and fall back, and crop to a square for covers.
 */
object YouTubeMusicThumbnails {

    private val bestVideoThumbnail = ConcurrentHashMap<String, String>()

    fun isVideoThumbnail(url: String?): Boolean = url?.contains("ytimg.com/vi") == true

    /** Album art at [size]px square; other URLs are returned as they are. */
    fun albumArt(url: String?, size: Int): String? {
        if (url.isNullOrBlank()) return null
        if (!url.contains("googleusercontent.com") && !url.contains("ggpht.com")) return url
        val base = url.substringBefore('=')
        return "$base=w$size-h$size-l90-rj"
    }

    fun videoThumbnailCandidates(videoId: String): List<String> =
        listOf("maxresdefault", "hq720", "sddefault", "hqdefault").map { "https://i.ytimg.com/vi/$videoId/$it.jpg" }

    /** The largest thumbnail that exists for [videoId]; remembered for the session. */
    suspend fun bestVideoThumbnail(http: OkHttpClient, videoId: String): String? {
        bestVideoThumbnail[videoId]?.let { return it }
        return withContext(Dispatchers.IO) {
            videoThumbnailCandidates(videoId).firstOrNull { url ->
                runCatching {
                    http.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                        // A missing size can come back as a tiny grey placeholder.
                        val length = response.header("Content-Length")?.toLongOrNull()
                        response.isSuccessful && (length == null || length > MIN_REAL_THUMBNAIL_BYTES)
                    }
                }.getOrDefault(false)
            }?.also { bestVideoThumbnail[videoId] = it }
        }
    }

    /** Is this one of the 4:3 sizes that carry letterbox bars? */
    fun hasLetterbox(url: String): Boolean = url.contains("/sddefault") || url.contains("/hqdefault")

    /**
     * Square, centred cover from a thumbnail. 4:3 letterboxed images lose their bars
     * (an eighth of the height at the top and bottom) before the crop.
     */
    fun squareCover(bytes: ByteArray, letterboxed: Boolean): ByteArray {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val barHeight = if (letterboxed) source.height / 8 else 0
        val height = source.height - 2 * barHeight
        val side = minOf(source.width, height)
        if (side <= 0 || (source.width == side && height == side && barHeight == 0)) return bytes
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, barHeight + (height - side) / 2, side, side)
        return ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
    }

    private const val MIN_REAL_THUMBNAIL_BYTES = 2_000L
}
