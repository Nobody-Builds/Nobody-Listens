package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.PlayerInfo

/**
 * Lock-screen player (also works on the home screen): translucent dark surface over the wallpaper,
 * cover, title and big prev / play / next targets. The buttons start the music service directly,
 * so they work while the device is locked; tapping the cover or title opens the app (unlock first).
 */
class LockScreenWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val info = currentState<PlayerInfo>().forDisplay()
            androidx.glance.GlanceTheme(colors = NbGlanceColors) { Content(context, info) }
        }
    }

    @Composable
    private fun Content(context: Context, info: PlayerInfo) {
        val size = LocalSize.current
        val stacked = size.height >= 140.dp
        val compact = size.height < 76.dp
        val button = if (compact) 44.dp else 48.dp
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(NbWidget.LockSurface)
                .cornerRadius(NbWidget.Radius)
                .padding(if (compact) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(NbWidget.openApp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val art = if (stacked) 56.dp else if (compact) 40.dp else 44.dp
                AlbumArtImage(
                    modifier = GlanceModifier.size(art),
                    bitmapData = info.albumArtBitmapData,
                    albumArtUri = info.albumArtUri,
                    size = art,
                    context = context,
                    cornerRadius = NbWidget.InnerRadius,
                )
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WidgetTitle(info.songTitle, color = NbWidget.LockText, size = 15.sp)
                    WidgetMeta(info.artistName, color = NbWidget.LockTextMuted, size = 12.sp)
                }
                if (!stacked) {
                    Spacer(GlanceModifier.width(8.dp))
                    Controls(context, info, button)
                }
            }
            if (stacked) {
                Spacer(GlanceModifier.height(12.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Controls(context, info, 52.dp)
                }
            }
        }
    }

    @Composable
    private fun Controls(context: Context, info: PlayerInfo, button: androidx.compose.ui.unit.Dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetCircleButton(
                icon = R.drawable.rounded_skip_previous_24,
                contentDescription = "Previous",
                action = musicServiceAction(context, PlayerActions.PREVIOUS),
                size = button, iconSize = 24.dp,
                background = NbWidget.LockButton, tint = NbWidget.White,
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetPlayPause(
                context, info.isPlaying, size = button, iconSize = 26.dp,
                background = androidx.glance.unit.ColorProvider(NbWidgetPalette.Accent),
                tint = androidx.glance.unit.ColorProvider(NbWidgetPalette.OnAccent),
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetCircleButton(
                icon = R.drawable.rounded_skip_next_24,
                contentDescription = "Next",
                action = musicServiceAction(context, PlayerActions.NEXT),
                size = button, iconSize = 24.dp,
                background = NbWidget.LockButton, tint = NbWidget.White,
            )
        }
    }
}

class LockScreenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LockScreenWidget()
}
