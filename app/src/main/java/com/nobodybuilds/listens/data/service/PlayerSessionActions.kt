package com.nobodybuilds.listens.data.service

import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/** Shuffle and like actions from the notification, widgets, Auto and gestures. */
internal class PlayerSessionActions(
    private val serviceScope: CoroutineScope,
    private val state: MusicServiceState,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val mediaButtons: MediaSessionButtons,
    private val widgetUpdates: WidgetUpdateScheduler
) {
    private var isManualShuffleEnabled by state::isManualShuffleEnabled
    private val persistentShuffleEnabled by state::persistentShuffleEnabled
    private var favoriteSongIds by state::favoriteSongIds

    private fun refreshMediaSessionUi(session: MediaSession) = mediaButtons.refreshMediaSessionUi(session)

    private fun requestWidgetFullUpdate(force: Boolean) = widgetUpdates.requestWidgetFullUpdate(force)

    fun updateManualShuffleState(
        session: MediaSession,
        enabled: Boolean,
        broadcast: Boolean
    ) {
        val changed = isManualShuffleEnabled != enabled
        isManualShuffleEnabled = enabled
        session.player.shuffleModeEnabled = enabled
        
        if (persistentShuffleEnabled) {
            serviceScope.launch {
                userPreferencesRepository.setShuffleOn(enabled)
            }
        }

        if (broadcast && changed) {
            val args = Bundle().apply {
                putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
            }
            session.broadcastCustomCommand(
                SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle.EMPTY),
                args
            )
        }
        refreshMediaSessionUi(session)
        requestWidgetFullUpdate(force = true)
    }

    fun setCurrentSongFavoriteState(
        session: MediaSession,
        targetFavoriteState: Boolean
    ): ListenableFuture<SessionResult> {
        val songId = session.player.currentMediaItem?.mediaId
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_UNKNOWN))

        val isCurrentlyFavorite = favoriteSongIds.contains(songId)
        if (isCurrentlyFavorite == targetFavoriteState) {
            refreshMediaSessionUi(session)
            requestWidgetFullUpdate(force = true)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        favoriteSongIds = if (targetFavoriteState) {
            favoriteSongIds + songId
        } else {
            favoriteSongIds - songId
        }

        refreshMediaSessionUi(session)
        requestWidgetFullUpdate(force = true)

        serviceScope.launch {
            Timber.tag("MusicService")
                .d("Applying favorite=$targetFavoriteState for songId: $songId")
            musicRepository.setFavoriteStatus(songId, targetFavoriteState)
            refreshMediaSessionUi(session)
            requestWidgetFullUpdate(force = true)
        }

        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}
