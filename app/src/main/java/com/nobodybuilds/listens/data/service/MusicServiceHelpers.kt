package com.nobodybuilds.listens.data.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import com.nobodybuilds.listens.MainActivity
import com.nobodybuilds.listens.NobodyListensApplication
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.service.MusicService.Companion.NOTIFICATION_ID
import com.nobodybuilds.listens.presentation.viewmodel.ListeningStatsTracker
import com.nobodybuilds.listens.shared.WearIntents
import com.nobodybuilds.listens.utils.MediaItemBuilder
import timber.log.Timber

/** Starts or continues the listening-stats session for what [player] is playing. */
internal fun syncListeningStatsFromPlayer(
    listeningStatsTracker: ListeningStatsTracker,
    player: Player,
    forceNewSession: Boolean = false
) {
    val mediaItem = player.currentMediaItem
    val songId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
    if (songId == null) {
        if (
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            listeningStatsTracker.onPlaybackStopped()
        }
        return
    }

    val positionMs = player.currentPosition.coerceAtLeast(0L)
    val durationMs = player.duration
    val fallbackDurationMs = mediaItem.mediaMetadata.extras
        ?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, 0L)
        ?: 0L

    if (forceNewSession) {
        listeningStatsTracker.onTrackChanged(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = fallbackDurationMs,
            isPlaying = player.isPlaying
        )
    } else {
        listeningStatsTracker.ensureSession(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = fallbackDurationMs,
            isPlaying = player.isPlaying
        )
    }
}

internal fun isPlayerActive(player: Player?): Boolean =
    player != null && player.playWhenReady &&
        player.playbackState != Player.STATE_IDLE &&
        player.playbackState != Player.STATE_ENDED

/** Opens the app on the player; the media notification's and the temporary notification's tap target. */
internal fun Context.openPlayerPendingIntent(): PendingIntent {
    val intent = Intent(this, MainActivity::class.java).apply {
        setPackage(packageName)
        action = WearIntents.ACTION_OPEN_PLAYER
        addCategory(Intent.CATEGORY_DEFAULT)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("ACTION_SHOW_PLAYER", true) // Signal to MainActivity to show the player
    }
    return PendingIntent.getActivity(
        this,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * Promotes the service to the foreground with a short "processing" notification, for a command
 * (media button, widget) that started it as a foreground service.
 */
internal fun Service.startTemporaryForegroundForCommand() {
    val notification = NotificationCompat.Builder(
        this,
        NobodyListensApplication.NOTIFICATION_CHANNEL_ID
    )
        .setSmallIcon(com.nobodybuilds.listens.shared.R.drawable.nl_mark_mono)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.service_processing_action))
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(openPlayerPendingIntent())
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setOngoing(true)
        .build()
    // Pass mediaPlayback explicitly. On API 34+ a missing/mismatched type can throw
    // MissingForegroundServiceTypeException; ServiceCompat picks the right overload
    // per API level and matches the manifest-declared foregroundServiceType.
    try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    } catch (e: Exception) {
        Timber.tag("MusicService_NobodyListens").w(e, "Failed to promote service to foreground for external command")
    }
}

internal fun Context.isTemporaryForegroundNotificationShowing(): Boolean = runCatching {
    getSystemService(android.app.NotificationManager::class.java)
        ?.activeNotifications?.any { it.id == NOTIFICATION_ID } == true
}.getOrDefault(false)
