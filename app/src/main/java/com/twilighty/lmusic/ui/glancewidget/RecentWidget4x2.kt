package com.twilighty.lmusic.ui.glancewidget

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
import com.twilighty.lmusic.data.database.EngagementDao
import com.twilighty.lmusic.data.repository.MusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecentWidgetEntryPoint {
    fun engagementDao(): EngagementDao
    fun musicRepository(): MusicRepository
    fun playbackStatsRepository(): com.twilighty.lmusic.data.stats.PlaybackStatsRepository
}

private data class RecentSong(val id: Long, val title: String, val artUri: String?)

/** 4x2 recently played: four covers, tap one to play it. */
class RecentWidget4x2 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, RecentWidgetEntryPoint::class.java)
                .playbackStatsRepository()
        }.getOrNull()
        val initial = loadSafely(context)
        provideContent {
            // Reloads whenever a play is recorded while the Glance session is open.
            val songs by produceState(initial) {
                stats?.refreshFlow?.drop(1)?.collect { value = loadSafely(context) }
            }
            GlanceTheme(colors = NbGlanceColors) { Content(context, songs) }
        }
    }

    private suspend fun loadSafely(context: Context): List<RecentSong> =
        runCatching { load(context) }
            .onFailure { Timber.w(it, "Recently played widget: load failed") }
            .getOrElse { emptyList() }

    private suspend fun load(context: Context): List<RecentSong> {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, RecentWidgetEntryPoint::class.java)
        val recent = ep.engagementDao().getRecentlyPlayedSongs(12).map { it.songId }
        if (recent.isEmpty()) return emptyList()
        val byId = ep.musicRepository().getSongsByIds(recent).first().associateBy { it.id }
        return recent.mapNotNull { sid ->
            val song = byId[sid] ?: return@mapNotNull null
            val numeric = song.id.toLongOrNull() ?: return@mapNotNull null
            RecentSong(numeric, song.title, song.albumArtUriString)
        }.take(4)
    }

    @Composable
    private fun Content(context: Context, songs: List<RecentSong>) {
        val size = LocalSize.current
        val cover = min((size.width - 32.dp - 24.dp) / 4, size.height - 32.dp - 20.dp - 18.dp).coerceIn(40.dp, 96.dp)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(NbWidget.Radius)
                .padding(16.dp),
        ) {
            Box(modifier = GlanceModifier.fillMaxWidth().clickable(NbWidget.openApp())) {
                WidgetOverline("Recently played")
            }
            Spacer(GlanceModifier.height(8.dp))
            if (songs.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize().clickable(NbWidget.openApp()),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    WidgetMeta("Songs you play show up here")
                }
                return@Column
            }
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (i in 0 until 4) {
                    if (i > 0) Spacer(GlanceModifier.width(8.dp))
                    val song = songs.getOrNull(i)
                    Column(
                        modifier = GlanceModifier.defaultWeight().let {
                            if (song != null) it.clickable(musicServiceAction(context, PlayerActions.PLAY_SONG, song.id)) else it
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (song != null) {
                            AlbumArtImage(
                                modifier = GlanceModifier.size(cover),
                                bitmapData = null,
                                albumArtUri = song.artUri,
                                size = cover,
                                context = context,
                                cornerRadius = NbWidget.InnerRadius,
                            )
                            Spacer(GlanceModifier.height(4.dp))
                            WidgetMeta(song.title, color = GlanceTheme.colors.onSurface, size = 11.sp)
                        } else {
                            Box(
                                modifier = GlanceModifier.size(cover)
                                    .background(GlanceTheme.colors.surfaceVariant)
                                    .cornerRadius(NbWidget.InnerRadius),
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

class RecentWidget4x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentWidget4x2()
}
