package com.nobodybuilds.listens.data.service

import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaSession
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.MusicService.Companion.ACTION_SLEEP_FADE
import com.nobodybuilds.listens.data.service.MusicService.Companion.ACTION_SLEEP_FADE_CANCEL
import com.nobodybuilds.listens.data.service.MusicService.Companion.ACTION_SLEEP_TIMER_EXPIRED
import com.nobodybuilds.listens.data.service.MusicService.Companion.EXTRA_SLEEP_FADE_END_AT
import com.nobodybuilds.listens.data.service.MusicService.Companion.EXTRA_SLEEP_FADE_START_AT
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.ui.glancewidget.PlayerActions
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/** Commands sent to [MusicService] with startService: widget buttons, the sleep timer and Sleep space. */
internal class WidgetCommandHandler(
    private val engine: DualPlayerEngine,
    private val musicRepository: MusicRepository,
    private val serviceScope: CoroutineScope,
    private val state: MusicServiceState,
    private val sessionProvider: () -> MediaSession?,
    private val sessionActions: PlayerSessionActions,
    private val mediaButtons: MediaSessionButtons,
    private val widgetUpdates: WidgetUpdateScheduler,
    private val sleepTimer: SleepTimerScheduler,
    private val playbackVolume: PlaybackVolumeController
) {
    private val mediaSession: MediaSession? get() = sessionProvider()
    private var favoriteSongIds by state::favoriteSongIds
    private val isManualShuffleEnabled by state::isManualShuffleEnabled

    private fun refreshMediaSessionUi(session: MediaSession) = mediaButtons.refreshMediaSessionUi(session)

    private fun requestWidgetFullUpdate(force: Boolean) = widgetUpdates.requestWidgetFullUpdate(force)

    private fun updateManualShuffleState(session: MediaSession, enabled: Boolean, broadcast: Boolean) =
        sessionActions.updateManualShuffleState(session, enabled, broadcast)

    private fun cancelDurationSleepTimerInternal() = sleepTimer.cancelDurationSleepTimerInternal()

    private fun startSleepFade(startAt: Long, endAt: Long) = playbackVolume.startSleepFade(startAt, endAt)

    private fun cancelSleepFade(restore: Boolean) = playbackVolume.cancelSleepFade(restore)

    /** Widget / internal commands sent with startService. [coldStart]: the service was just started for it. */
    suspend fun handleStartCommandAction(player: Player, action: String, intent: Intent, coldStart: Boolean) {
        when (action) {
            PlayerActions.PLAY_PAUSE -> {
                if (coldStart || !player.playWhenReady) {
                    // From a killed app the restored queue may already be set to play; "play"
                    // must never turn into a pause there.
                    preparePlayerForPlayback(player)
                    player.play()
                } else {
                    player.pause()
                }
            }
            PlayerActions.PLAY_SONG -> {
                val songId = intent.getLongExtra(PlayerActions.EXTRA_SONG_ID, -1L)
                if (songId != -1L) playSongFromWidget(player, songId)
            }
            PlayerActions.NEXT -> player.seekToNext()
            PlayerActions.PREVIOUS -> player.seekToPrevious()
            PlayerActions.FAVORITE -> {
                val songId = player.currentMediaItem?.mediaId
                if (!songId.isNullOrBlank()) {
                    serviceScope.launch {
                        val updatedFavorite = musicRepository.toggleFavoriteStatus(songId)
                        favoriteSongIds = if (updatedFavorite) {
                            favoriteSongIds + songId
                        } else {
                            favoriteSongIds - songId
                        }
                        mediaSession?.let { refreshMediaSessionUi(it) }
                        requestWidgetFullUpdate(force = true)
                    }
                }
            }
            PlayerActions.PLAY_FROM_QUEUE -> {
                val songId = intent.getLongExtra("song_id", -1L)
                if (songId != -1L) {
                    val timeline = player.currentTimeline
                    if (!timeline.isEmpty) {
                        val window = Timeline.Window()
                        for (i in 0 until timeline.windowCount) {
                            timeline.getWindow(i, window)
                            if (window.mediaItem.mediaId.toLongOrNull() == songId) {
                                player.seekTo(i, C.TIME_UNSET)
                                preparePlayerForPlayback(player)
                                player.play()
                                break
                            }
                        }
                    }
                }
            }
            PlayerActions.SHUFFLE -> {
                val newState = !isManualShuffleEnabled
                mediaSession?.let { session ->
                    updateManualShuffleState(session, enabled = newState, broadcast = true)
                }
            }
            PlayerActions.REPEAT -> {
                val newMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                player.repeatMode = newMode
                requestWidgetFullUpdate(force = true)
            }
            ACTION_SLEEP_TIMER_EXPIRED -> {
                Timber.tag(TAG).d("Sleep timer expired action received. Pausing player.")
                cancelDurationSleepTimerInternal()
                player.pause()
                cancelSleepFade(restore = true)
            }
            ACTION_SLEEP_FADE -> startSleepFade(
                startAt = intent.getLongExtra(EXTRA_SLEEP_FADE_START_AT, 0L),
                endAt = intent.getLongExtra(EXTRA_SLEEP_FADE_END_AT, 0L),
            )
            ACTION_SLEEP_FADE_CANCEL -> cancelSleepFade(restore = true)
        }
    }

    /** Ensures the player can actually start: re-prepares after an error/stop, rewinds at the end. */
    private fun preparePlayerForPlayback(player: Player) {
        if (player.mediaItemCount == 0) return
        when (player.playbackState) {
            Player.STATE_IDLE -> player.prepare()
            Player.STATE_ENDED -> player.seekToDefaultPosition(player.currentMediaItemIndex)
            else -> Unit
        }
    }

    /** Widget "play this song": jump to it in the queue, or play it next. */
    private suspend fun playSongFromWidget(player: Player, songId: Long) {
        val timeline = player.currentTimeline
        if (!timeline.isEmpty) {
            val window = Timeline.Window()
            for (i in 0 until timeline.windowCount) {
                timeline.getWindow(i, window)
                if (window.mediaItem.mediaId.toLongOrNull() == songId) {
                    player.seekTo(i, C.TIME_UNSET)
                    preparePlayerForPlayback(player)
                    player.play()
                    return
                }
            }
        }
        run {
            val song = runCatching {
                musicRepository.getSongsByIds(listOf(songId.toString())).first().firstOrNull()
            }.getOrNull() ?: return
            val item = runCatching { engine.resolveMediaItem(MediaItemBuilder.build(song)) }
                .getOrElse { MediaItemBuilder.build(song) }
            val p = mediaSession?.player ?: return
            if (p.mediaItemCount == 0) {
                p.setMediaItems(listOf(item))
                p.prepare()
            } else {
                val index = (p.currentMediaItemIndex + 1).coerceAtMost(p.mediaItemCount)
                p.addMediaItem(index, item)
                p.seekTo(index, C.TIME_UNSET)
                preparePlayerForPlayback(p)
            }
            p.play()
        }
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
    }
}
