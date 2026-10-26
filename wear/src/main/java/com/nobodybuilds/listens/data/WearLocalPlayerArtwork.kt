package com.nobodybuilds.listens.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.nobodybuilds.listens.data.local.LocalSongEntity
import timber.log.Timber
import java.io.File
import java.net.URL

/**
 * Loads album art and palette seed colours for [WearLocalPlayerRepository], from stored artwork files or the
 * picture embedded in the audio.
 */
internal class WearLocalPlayerArtworkLoader(
    private val application: Application,
) {
    @Volatile
    private var lastRemoteArtwork: Pair<String, Bitmap>? = null

    companion object {
        private const val TAG = "WearLocalPlayer"
        private const val REMOTE_TIMEOUT_MS = 15_000
    }

    fun loadLocalAlbumArtBitmap(song: LocalSongEntity): Bitmap? {
        val fromStoredArtwork = song.artworkPath
            ?.takeIf { it.isNotBlank() }
            ?.let { artworkPath ->
                decodeBoundedBitmapFromFile(artworkPath, maxDimension = 1024)
            }
        if (fromStoredArtwork != null) return fromStoredArtwork

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(song.localPath)
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmapFromBytes(embedded, maxDimension = 1024)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load local artwork for songId=${song.songId}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeBoundedBitmapFromFile(path: String, maxDimension: Int): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        )
    }

    private fun decodeBoundedBitmapFromBytes(data: ByteArray, maxDimension: Int): Bitmap? {
        if (data.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        )
    }

    fun extractSeedFromLocalSong(song: LocalSongEntity): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(song.localPath)
            val embedded = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            ) ?: return null

            try {
                extractSeedColorArgb(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract local artwork seed for songId=${song.songId}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun extractSeedFromUri(uri: Uri, songId: String): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(application, uri)
            val embedded = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            ) ?: return null

            try {
                extractSeedColorArgb(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract artwork seed from URI for songId=$songId")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Downloads a cover (YouTube thumbnails); the last one is kept, since art and palette both ask for it. */
    fun loadRemoteArtwork(url: String): Bitmap? {
        lastRemoteArtwork?.takeIf { it.first == url }?.let { return it.second }
        val bitmap = runCatching {
            val connection = URL(url).openConnection().apply {
                connectTimeout = REMOTE_TIMEOUT_MS
                readTimeout = REMOTE_TIMEOUT_MS
            }
            connection.getInputStream().use { decodeBoundedBitmapFromBytes(it.readBytes(), maxDimension = 1024) }
        }.onFailure { Timber.tag(TAG).w(it, "Failed to download artwork") }.getOrNull()
        if (bitmap != null) lastRemoteArtwork = url to bitmap
        return bitmap
    }

    fun extractSeed(bitmap: Bitmap): Int? = extractSeedColorArgb(bitmap)

    fun loadArtworkBitmapFromUri(uri: Uri, songId: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(application, uri)
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmapFromBytes(embedded, maxDimension = 1024)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load artwork from URI for songId=$songId")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractSeedColorArgb(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val step = (minOf(bitmap.width, bitmap.height) / 24).coerceAtLeast(1)
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0L

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 28) {
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)
                    if (red + green + blue > 36) {
                        redSum += red
                        greenSum += green
                        blueSum += blue
                        count++
                    }
                }
                x += step
            }
            y += step
        }

        if (count == 0L) return null
        return Color.rgb(
            (redSum / count).toInt(),
            (greenSum / count).toInt(),
            (blueSum / count).toInt(),
        )
    }
}
