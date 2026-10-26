package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.PlayerInfo
import com.nobodybuilds.listens.data.model.QueueItem

/** 4x3 queue: the current song with controls, then the next four songs; tap a row to play it. */
class QueueWidget4x3 : GlanceAppWidget() {
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
        val height = LocalSize.current.height
        // Rows that fit under the current-song block (Samsung 4x5 / 4x6 cells differ).
        val rowHeight = 40.dp
        val fit = ((height - 16.dp * 2 - 56.dp - 28.dp) / (rowHeight + 4.dp)).toInt().coerceIn(1, 4)
        val queue = if (WidgetPlaybackLiveness.serviceAlive) info.queue.take(fit) else emptyList()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(NbWidget.Radius)
                .padding(16.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(NbWidget.openApp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtImage(
                    modifier = GlanceModifier.size(56.dp),
                    bitmapData = info.albumArtBitmapData,
                    albumArtUri = info.albumArtUri,
                    size = 56.dp,
                    context = context,
                    cornerRadius = NbWidget.InnerRadius,
                )
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WidgetTitle(info.songTitle, size = 15.sp)
                    WidgetMeta(info.artistName)
                }
                Spacer(GlanceModifier.width(8.dp))
                WidgetCircleButton(
                    icon = R.drawable.rounded_skip_next_24,
                    contentDescription = "Next",
                    action = musicServiceAction(context, PlayerActions.NEXT),
                    size = 40.dp,
                )
                Spacer(GlanceModifier.width(6.dp))
                WidgetPlayPause(context, info.isPlaying, size = 44.dp)
            }
            Spacer(GlanceModifier.height(10.dp))
            WidgetOverline("Up next")
            Spacer(GlanceModifier.height(4.dp))
            if (queue.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(NbWidget.openApp()),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    WidgetMeta(if (WidgetPlaybackLiveness.serviceAlive) "Nothing queued" else "Tap play to pick up where you left off")
                }
            } else {
                queue.forEachIndexed { index, item ->
                    if (index > 0) Spacer(GlanceModifier.height(4.dp))
                    QueueRow(context, item, rowHeight)
                }
            }
        }
    }

    @Composable
    private fun QueueRow(context: Context, item: QueueItem, rowHeight: androidx.compose.ui.unit.Dp) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(rowHeight)
                .cornerRadius(NbWidget.InnerRadius)
                .clickable(musicServiceAction(context, PlayerActions.PLAY_FROM_QUEUE, item.id)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArtImage(
                modifier = GlanceModifier.size(36.dp),
                bitmapData = null,
                albumArtUri = item.albumArtUri,
                size = 36.dp,
                context = context,
                cornerRadius = 8.dp,
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetTitle(item.title.ifBlank { "Unknown song" }, size = 13.sp)
                if (item.artist.isNotBlank()) WidgetMeta(item.artist, size = 11.sp)
            }
        }
    }
}

class QueueWidget4x3Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QueueWidget4x3()
}
