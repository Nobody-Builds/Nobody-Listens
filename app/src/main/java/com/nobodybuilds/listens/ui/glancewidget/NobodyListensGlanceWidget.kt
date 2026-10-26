package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
// import androidx.datastore.preferences.protobuf.ByteString // No longer needed
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import com.nobodybuilds.listens.MainActivity
import com.nobodybuilds.listens.data.model.PlayerInfo
import com.nobodybuilds.listens.R
import timber.log.Timber

class NobodyListensGlanceWidget : GlanceAppWidget() {

    companion object {
        // Tamaños definidos para diferentes configuraciones del widget
        private val VERY_THIN_LAYOUT_SIZE = DpSize(width = 200.dp, height = 60.dp)
        private val THIN_LAYOUT_SIZE = DpSize(width = 250.dp, height = 80.dp)
        private val SMALL_HORIZONTAL_LAYOUT_SIZE = DpSize(width = 110.dp, height = 60.dp)
        private val ONE_BY_ONE_LAYOUT_SIZE = DpSize(width = 110.dp, height = 110.dp)
        private val GABE_LAYOUT_SIZE = DpSize(width = 110.dp, height = 220.dp)
        private val GABE_TWO_HEIGHT_LAYOUT_SIZE = DpSize(width = 110.dp, height = 200.dp)
        private val SMALL_LAYOUT_SIZE = DpSize(width = 120.dp, height = 100.dp)
        private val MEDIUM_LAYOUT_SIZE = DpSize(width = 250.dp, height = 150.dp)
        private val LARGE_LAYOUT_SIZE = DpSize(width = 300.dp, height = 180.dp)
        private val EXTRA_LARGE_LAYOUT_SIZE = DpSize(width = 300.dp, height = 220.dp)
        private val EXTRA_LARGE_PLUS_LAYOUT_SIZE = DpSize(width = 350.dp, height = 260.dp)
        private val HUGE_LAYOUT_SIZE = DpSize(width = 400.dp, height = 300.dp)
    }

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val playerInfo = currentState<PlayerInfo>().forDisplay()
            val currentSize = LocalSize.current

            Timber.tag("NobodyListensGlanceWidget")
                .d("Providing Glance. PlayerInfo: title='${playerInfo.songTitle}', artist='${playerInfo.artistName}', isPlaying=${playerInfo.isPlaying}, hasBitmap=${playerInfo.albumArtBitmapData != null}, progress=${playerInfo.currentPositionMs}/${playerInfo.totalDurationMs}")

            GlanceTheme(colors = NbGlanceColors) {
                WidgetUi(playerInfo = playerInfo, size = currentSize, context = context)
            }
        }
    }

    @Composable
    private fun WidgetUi(
        playerInfo: PlayerInfo,
        size: DpSize,
        context: Context
    ) {
        val title = playerInfo.songTitle.ifEmpty { context.getString(R.string.app_name) }
        val artist = playerInfo.artistName.ifEmpty { context.getString(R.string.widget_tap_to_open) }
        val isPlaying = playerInfo.isPlaying
        val isFavorite = playerInfo.isFavorite
        val albumArtBitmapData = playerInfo.albumArtBitmapData
        val albumArtUri = playerInfo.albumArtUri

        Timber.tag("NobodyListensGlanceWidget")
            .d("WidgetUi: PlayerInfo received. Title: $title, Artist: $artist, HasBitmapData: ${albumArtBitmapData != null}, BitmapDataSize: ${albumArtBitmapData?.size ?: "N/A"}")

        val actualBackgroundColor = GlanceTheme.colors.surface
        val onBackgroundColor = GlanceTheme.colors.onSurface

        val baseModifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>())

        Box(
            GlanceModifier.fillMaxSize()
        ) {
            val isOneColumn = size.width < SMALL_LAYOUT_SIZE.width
            val isSmallHeight = size.height < SMALL_LAYOUT_SIZE.height

            if (isOneColumn) {
                when {
                    size.height <= ONE_BY_ONE_LAYOUT_SIZE.height -> OneByOneWidgetLayout(
                        modifier = baseModifier,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp,
                        isPlaying = isPlaying
                    )
                    size.height <= GABE_TWO_HEIGHT_LAYOUT_SIZE.height -> GabeTwoHeightWidgetLayout(
                        modifier = baseModifier,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        isPlaying = isPlaying,
                        context = context
                    )
                    else -> GabeWidgetLayout(
                        modifier = baseModifier,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        isPlaying = isPlaying,
                        context = context
                    )
                }
            } else if (isSmallHeight) {
                when {
                    size.width < VERY_THIN_LAYOUT_SIZE.width -> SmallHorizontalWidgetLayout(
                        modifier = baseModifier,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        isPlaying = isPlaying,
                        context = context
                    )
                    size.width < THIN_LAYOUT_SIZE.width -> VeryThinWidgetLayout(
                        modifier = baseModifier,
                        title = title,
                        artist = artist,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        isPlaying = isPlaying,
                        textColor = onBackgroundColor,
                        context = context,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp
                    )
                    else -> ThinWidgetLayout(
                        modifier = baseModifier,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp,
                        title = title,
                        artist = artist,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        isPlaying = isPlaying,
                        textColor = onBackgroundColor,
                        context = context
                    )
                }
            } else {
                when {
                    size.width < MEDIUM_LAYOUT_SIZE.width || size.height < MEDIUM_LAYOUT_SIZE.height -> SmallWidgetLayout(
                        modifier = baseModifier,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        isPlaying = isPlaying,
                        context = context
                    )
                    size.width < LARGE_LAYOUT_SIZE.width || size.height < LARGE_LAYOUT_SIZE.height -> MediumWidgetLayout(
                        modifier = baseModifier,
                        title = title,
                        artist = artist,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        isPlaying = isPlaying,
                        textColor = onBackgroundColor,
                        context = context,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp
                    )
                    size.width < EXTRA_LARGE_LAYOUT_SIZE.width || size.height < EXTRA_LARGE_LAYOUT_SIZE.height -> LargeWidgetLayout(
                        modifier = baseModifier,
                        title = title,
                        artist = artist,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        textColor = onBackgroundColor,
                        context = context
                    )
                    else -> ExtraLargeWidgetLayout(
                        modifier = baseModifier,
                        title = title,
                        artist = artist,
                        albumArtBitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        isPlaying = isPlaying,
                        backgroundColor = actualBackgroundColor,
                        bgCornerRadius = 24.dp,
                        textColor = onBackgroundColor,
                        context = context,
                        queue = playerInfo.queue
                    )
                }
            }
        }
    }
}

// Helper para formatear duración en Glance (no puede usar TimeUnit directamente)
private fun formatDurationGlance(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
