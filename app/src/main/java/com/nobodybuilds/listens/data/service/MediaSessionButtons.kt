package com.nobodybuilds.listens.data.service

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.nobodybuilds.listens.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The like / close / shuffle / repeat buttons [MusicService] publishes on its media session,
 * refreshed (debounced) only when what they show has changed.
 */
@OptIn(UnstableApi::class)
internal class MediaSessionButtons(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val state: MusicServiceState,
    private val sessionProvider: () -> MediaSession?
) {
    private val mediaSession: MediaSession? get() = sessionProvider()
    private val isManualShuffleEnabled by state::isManualShuffleEnabled
    private var followUpMediaSessionUiRefreshJob: Job? = null
    private var mediaSessionButtonRefreshJob: Job? = null
    private var lastAppliedMediaButtonSignature: String? = null

    private fun isSongFavorite(songId: String?): Boolean = state.isSongFavorite(songId)

    private fun getString(resId: Int): String = context.getString(resId)

    fun cancelPendingRefreshes() {
        followUpMediaSessionUiRefreshJob?.cancel()
        mediaSessionButtonRefreshJob?.cancel()
    }

    fun refreshMediaSessionUi(session: MediaSession, force: Boolean = false) {
        val pendingSignature = buildMediaButtonPreferencesSignature(session)
        if (!force && pendingSignature == lastAppliedMediaButtonSignature) {
            return
        }

        mediaSessionButtonRefreshJob?.cancel()
        mediaSessionButtonRefreshJob = serviceScope.launch {
            if (!force) {
                delay(MEDIA_SESSION_BUTTON_DEBOUNCE_MS)
            }
            if (mediaSession !== session) {
                return@launch
            }

            val latestSignature = buildMediaButtonPreferencesSignature(session)
            if (latestSignature == lastAppliedMediaButtonSignature) {
                return@launch
            }

            val buttons = buildMediaButtonPreferences(session)
            // setMediaButtonPreferences triggers a notification update internally via
            // MediaControllerListener.onMediaButtonPreferencesChanged → onUpdateNotificationInternal,
            // which correctly determines if the service should run in foreground.
            // Do NOT manually call onUpdateNotification(session, false) here — that bypasses
            // Media3's shouldRunInForeground logic and can remove foreground status, leading to
            // ForegroundServiceStartNotAllowedException when async callbacks fire later.
            session.setMediaButtonPreferences(buttons)
            lastAppliedMediaButtonSignature = latestSignature
        }
    }

    fun refreshMediaSessionUiWithFollowUp(
        session: MediaSession,
        delayMs: Long = 250L
    ) {
        refreshMediaSessionUi(session, force = true)
        followUpMediaSessionUiRefreshJob?.cancel()
        followUpMediaSessionUiRefreshJob = serviceScope.launch {
            delay(delayMs)
            if (mediaSession === session) {
                refreshMediaSessionUi(session)
            }
        }
    }

    private fun buildMediaButtonPreferencesSignature(session: MediaSession): String {
        val player = session.player
        return buildString {
            append(player.currentMediaItem?.mediaId.orEmpty())
            append('|')
            append(isSongFavorite(player.currentMediaItem?.mediaId))
            append('|')
            append(isManualShuffleEnabled)
            append('|')
            append(player.repeatMode)
        }
    }

    private fun buildMediaButtonPreferences(session: MediaSession): List<CommandButton> {
        val player = session.player
        val songId = player.currentMediaItem?.mediaId
        val isFavorite = isSongFavorite(songId)
        val likeButton = CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            // First custom button, so it shows in the lock-screen media controls / Now Bar.
            .setDisplayName(if (isFavorite) "Unlike" else "Like")
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val shuffleOn = isManualShuffleEnabled
        val shuffleCommandAction = if (shuffleOn) {
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF
        } else {
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON
        }
        val shuffleButton = CommandButton.Builder(
            if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName("Shuffle")
            .setSessionCommand(SessionCommand(shuffleCommandAction, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val repeatButton = CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName("Repeat")
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.rounded_close_24)
            .setDisplayName(getString(R.string.close_notification_player))
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        // Let Media3 provide the primary previous/play-next transport buttons from player
        // commands instead of advertising custom back/forward slots here. When custom
        // SLOT_BACK/SLOT_FORWARD buttons are present, Media3 strips the legacy
        // ACTION_SKIP_TO_PREVIOUS/NEXT flags from PlaybackStateCompat, which causes some
        // OEM compact system players (including ColorOS Control Center) to gray out skip.
        return listOf(likeButton, closeButton, shuffleButton, repeatButton)
    }

    private companion object {
        const val MEDIA_SESSION_BUTTON_DEBOUNCE_MS = 250L
    }
}
