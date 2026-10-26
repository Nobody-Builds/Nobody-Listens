package com.nobodybuilds.listens.data.service.wear

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.core.graphics.get
import androidx.core.net.toUri
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.data.preferences.AlbumArtColorAccuracy
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.preferences.ThemePreference
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemeProcessor
import com.nobodybuilds.listens.shared.WearThemePalette
import com.nobodybuilds.listens.utils.AlbumArtUtils
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The cover art, palette seed and theme colours sent to the watch with a transferred song. */
internal class WatchTransferArtwork(
    private val application: Application,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val colorSchemeProcessor: ColorSchemeProcessor,
) {
    private val albumPaletteSeedCache = ConcurrentHashMap<Long, Int>()
    private val albumArtworkTransferCache = ConcurrentHashMap<Long, ByteArray>()

    fun resolvePaletteSeedArgb(song: Song): Int? {
        if (song.albumId > 0L) {
            albumPaletteSeedCache[song.albumId]?.let { return it }
        }

        val bitmap = loadSongAlbumArtBitmap(song) ?: return null
        return try {
            extractSeedColorArgb(bitmap)?.also { seed ->
                if (song.albumId > 0L) {
                    albumPaletteSeedCache[song.albumId] = seed
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun resolveTransferArtworkBytes(song: Song): ByteArray? {
        if (song.albumId > 0L) {
            albumArtworkTransferCache[song.albumId]?.let { return it }
        }

        val bitmap = loadSongAlbumArtBitmapForTransfer(song) ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, TRANSFER_ARTWORK_QUALITY, stream)
            val bytes = stream.toByteArray()
            if (bytes.isEmpty() || bytes.size > TRANSFER_ARTWORK_MAX_BYTES) {
                null
            } else {
                if (song.albumId > 0L) {
                    albumArtworkTransferCache[song.albumId] = bytes
                }
                bytes
            }
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to encode transfer artwork for songId=%s", song.id)
            null
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun resolveTransferThemePalette(song: Song): WearThemePalette? {
        val playerTheme = themePreferencesRepository.playerThemePreferenceFlow.first()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && playerTheme == ThemePreference.DYNAMIC) {
            return buildWearThemePalette(dynamicDarkColorScheme(application))
        }

        val artUriString = song.albumArtUriString?.takeIf { it.isNotBlank() }
        if (artUriString != null) {
            val paletteStyle = AlbumArtPaletteStyle.fromStorageKey(
                themePreferencesRepository.albumArtPaletteStyleFlow.first().storageKey
            )
            val colorAccuracyLevel = AlbumArtColorAccuracy.clamp(
                themePreferencesRepository.albumArtColorAccuracyFlow.first()
            )
            val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(
                albumArtUri = artUriString,
                paletteStyle = paletteStyle,
                colorAccuracyLevel = colorAccuracyLevel
            )
            if (schemePair != null) {
                return buildWearThemePalette(schemePair.dark)
            }
        }

        val fallbackBitmap = loadSongAlbumArtBitmapForTransfer(song) ?: return null
        return try {
            buildWearThemePalette(fallbackBitmap)
        } finally {
            fallbackBitmap.recycle()
        }
    }

    private fun loadSongAlbumArtBitmapForTransfer(song: Song): Bitmap? {
        val fromUri = song.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.let { uriString -> decodeBoundedBitmapFromUri(uriString) }
        if (fromUri != null) return fromUri

        val retriever = MediaMetadataRetriever()
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                retriever.setDataSource(song.path)
            } else {
                retriever.setDataSource(application, song.contentUriString.toUri())
            }
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmap(embedded)
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to load transfer artwork for songId=%s", song.id)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeBoundedBitmapFromUri(uriString: String): Bitmap? {
        val uri = uriString.toUri()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        AlbumArtUtils.openArtworkInputStream(application, uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > TRANSFER_ARTWORK_MAX_DIMENSION * 2 ||
            (srcHeight / sampleSize) > TRANSFER_ARTWORK_MAX_DIMENSION * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }

        return AlbumArtUtils.openArtworkInputStream(application, uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun decodeBoundedBitmap(data: ByteArray): Bitmap? {
        if (data.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > TRANSFER_ARTWORK_MAX_DIMENSION * 2 ||
            (srcHeight / sampleSize) > TRANSFER_ARTWORK_MAX_DIMENSION * 2
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

    private fun loadSongAlbumArtBitmap(song: Song): Bitmap? {
        val artFromUri = song.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.let { uriString ->
                runCatching {
                    AlbumArtUtils.openArtworkInputStream(application, uriString.toUri())?.use { input ->
                        BitmapFactory.decodeStream(
                            input,
                            null,
                            BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                                inSampleSize = 4
                            },
                        )
                    }
                }.getOrNull()
            }
        if (artFromUri != null) return artFromUri

        val retriever = MediaMetadataRetriever()
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                retriever.setDataSource(song.path)
            } else {
                retriever.setDataSource(application, song.contentUriString.toUri())
            }
            val embedded = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            )
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to extract album art for palette seed: songId=%s", song.id)
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
                val pixel = bitmap[x, y]
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

    private companion object {
        const val TAG = "PhoneDirectTransfer"
        const val TRANSFER_ARTWORK_MAX_DIMENSION = 1024
        const val TRANSFER_ARTWORK_QUALITY = 95
        const val TRANSFER_ARTWORK_MAX_BYTES = 1_500_000
    }
}
