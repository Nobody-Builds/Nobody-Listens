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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.PlayerInfo

/**
 * Album art: the cover fills the widget (2x2 up to 4x4) with a translucent control bar on top.
 * From about 3x3 the bar also shows the title and artist.
 */
class AlbumArtWidget : GlanceAppWidget() {
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
        val side = min(size.width, size.height)
        val large = side >= 200.dp
        val button = if (large) 48.dp else 36.dp
        val icon = if (large) 24.dp else 18.dp
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(NbWidget.Radius)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(NbWidget.openApp()),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AlbumArtImage(
                modifier = GlanceModifier.fillMaxSize(),
                bitmapData = info.albumArtBitmapData,
                albumArtUri = info.albumArtUri,
                size = side,
                context = context,
                cornerRadius = NbWidget.Radius,
            )
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(if (large) 12.dp else 8.dp),
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(NbWidget.OnArtScrim)
                        .cornerRadius(if (large) 20.dp else 16.dp)
                        .padding(horizontal = 10.dp, vertical = if (large) 10.dp else 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (large) {
                        WidgetTitle(info.songTitle, color = NbWidget.White, size = 15.sp)
                        WidgetMeta(info.artistName, color = NbWidget.LockTextMuted, size = 12.sp)
                        Spacer(GlanceModifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetCircleButton(
                            icon = R.drawable.rounded_skip_previous_24,
                            contentDescription = "Previous",
                            action = musicServiceAction(context, PlayerActions.PREVIOUS),
                            size = button, iconSize = icon,
                            background = NbWidget.LockButton, tint = NbWidget.White,
                        )
                        Spacer(GlanceModifier.width(if (large) 16.dp else 8.dp))
                        WidgetPlayPause(context, info.isPlaying, size = button, iconSize = icon)
                        Spacer(GlanceModifier.width(if (large) 16.dp else 8.dp))
                        WidgetCircleButton(
                            icon = R.drawable.rounded_skip_next_24,
                            contentDescription = "Next",
                            action = musicServiceAction(context, PlayerActions.NEXT),
                            size = button, iconSize = icon,
                            background = NbWidget.LockButton, tint = NbWidget.White,
                        )
                    }
                }
            }
        }
    }
}

class AlbumArtWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlbumArtWidget()
}
