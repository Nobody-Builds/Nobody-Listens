package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.nobodybuilds.listens.data.model.PlayerInfo

/** 2x1 now playing: cover, title and a play/pause button. */
class NowPlayingWidget2x1 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val info = currentState<PlayerInfo>().forDisplay()
            GlanceTheme(colors = NbGlanceColors) { Content(context, info) }
        }
    }

    @Composable
    private fun Content(context: Context, info: PlayerInfo) {
        val size = LocalSize.current
        val inner = (min(size.width, size.height) - 16.dp).coerceIn(32.dp, 56.dp)
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(NbWidget.Radius)
                .padding(8.dp)
                .clickable(NbWidget.openApp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArtImage(
                modifier = GlanceModifier.size(inner),
                bitmapData = info.albumArtBitmapData,
                albumArtUri = info.albumArtUri,
                size = inner,
                context = context,
                cornerRadius = NbWidget.InnerRadius,
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetTitle(info.songTitle, size = 13.sp)
                if (size.height >= 56.dp) WidgetMeta(info.artistName, size = 11.sp)
            }
            Spacer(GlanceModifier.width(6.dp))
            WidgetPlayPause(context, info.isPlaying, size = inner.coerceAtMost(44.dp), iconSize = 20.dp)
        }
    }
}

class NowPlayingWidget2x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget2x1()
}
