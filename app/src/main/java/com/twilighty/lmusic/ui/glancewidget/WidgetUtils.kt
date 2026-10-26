package com.twilighty.lmusic.ui.glancewidget

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.twilighty.lmusic.data.model.PlayerInfo

object AlbumArtBitmapCache {
    private const val CACHE_SIZE_BYTES = 4 * 1024 * 1024 // 4 MiB
    private val lruCache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun getBitmap(key: String): Bitmap? = lruCache.get(key)

    fun putBitmap(key: String, bitmap: Bitmap) {
        if (getBitmap(key) == null) {
            lruCache.put(key, bitmap)
        }
    }

    fun getKey(byteArray: ByteArray): String {
        return byteArray.contentHashCode().toString()
    }
}

data class WidgetColors(
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val artist: ColorProvider,
    val playPauseBackground: ColorProvider,
    val playPauseIcon: ColorProvider,
    val prevNextBackground: ColorProvider,
    val prevNextIcon: ColorProvider
)

/**
 * Widget colours, Daylight style: flat canvas (Paper / Night Shift), ink title, muted artist,
 * Hard Hat play button with a Night Shift icon, muted circles for the other controls.
 * Album colours are no longer used so every widget matches the app.
 */
@Suppress("UnusedReceiverParameter")
@Composable
fun PlayerInfo.getWidgetColors(): WidgetColors = WidgetColors(
    surface = ColorProvider(day = NbWidgetPalette.LightCanvas, night = NbWidgetPalette.DarkCanvas),
    onSurface = ColorProvider(day = NbWidgetPalette.LightText, night = NbWidgetPalette.DarkText),
    artist = ColorProvider(day = NbWidgetPalette.LightTextMuted, night = NbWidgetPalette.DarkTextMuted),
    playPauseBackground = ColorProvider(day = NbWidgetPalette.Accent, night = NbWidgetPalette.Accent),
    playPauseIcon = ColorProvider(day = NbWidgetPalette.OnAccent, night = NbWidgetPalette.OnAccent),
    prevNextBackground = ColorProvider(day = NbWidgetPalette.LightMuted, night = NbWidgetPalette.DarkMuted),
    prevNextIcon = ColorProvider(day = NbWidgetPalette.LightText, night = NbWidgetPalette.DarkText),
)
