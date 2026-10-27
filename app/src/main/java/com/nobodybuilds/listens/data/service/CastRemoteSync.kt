package com.nobodybuilds.listens.data.service

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.nobodybuilds.listens.presentation.viewmodel.ListeningStatsTracker
import timber.log.Timber

/**
 * Follows the active Cast session for [MusicService]: keeps listening stats in step with the
 * receiver and asks for a widget / watch refresh whenever the remote status changes.
 */
internal class CastRemoteSync(
    private val context: Context,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val onRemoteStateChanged: (force: Boolean) -> Unit
) {
    private var castSessionManager: SessionManager? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var castRemoteClientCallback: RemoteMediaClient.Callback? = null
    private var observedCastSession: CastSession? = null
    private var activeCastStatsOccurrenceId: String? = null

    /** The Cast receiver's media client, while a session is active. */
    fun currentRemoteClient(): RemoteMediaClient? =
        observedCastSession?.remoteMediaClient
            ?: castSessionManager?.currentCastSession?.remoteMediaClient

    private fun requestWidgetFullUpdate(force: Boolean) = onRemoteStateChanged(force)

    fun initializeCastWearSync() {
        val sessionManager = runCatching {
            CastContext.getSharedInstance(context).sessionManager
        }.getOrElse { error ->
            Timber.tag(TAG).w(error, "CastContext unavailable; skipping cast wear sync setup")
            return
        }
        castSessionManager = sessionManager

        val remoteCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                syncCastListeningStatsFromRemote()
                requestWidgetFullUpdate(force = false)
            }

            override fun onMetadataUpdated() {
                syncCastListeningStatsFromRemote()
                requestWidgetFullUpdate(force = false)
            }

            override fun onQueueStatusUpdated() {
                syncCastListeningStatsFromRemote()
                requestWidgetFullUpdate(force = false)
            }

            override fun onPreloadStatusUpdated() {
                requestWidgetFullUpdate(force = false)
            }
        }
        castRemoteClientCallback = remoteCallback

        val sessionListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                attachCastRemoteClient(session)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                attachCastRemoteClient(session)
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                if (observedCastSession === session) {
                    attachCastRemoteClient(null)
                } else {
                    requestWidgetFullUpdate(force = true)
                }
            }

            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStartFailed(session: CastSession, error: Int) = requestWidgetFullUpdate(force = true)
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) = requestWidgetFullUpdate(force = true)
            override fun onSessionSuspended(session: CastSession, reason: Int) = requestWidgetFullUpdate(force = true)
        }
        castSessionManagerListener = sessionListener
        runCatching {
            sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to register Cast session listener")
        }

        attachCastRemoteClient(sessionManager.currentCastSession)
    }

    private fun attachCastRemoteClient(session: CastSession?) {
        if (observedCastSession === session) return

        observedCastSession?.remoteMediaClient?.let { oldClient ->
            castRemoteClientCallback?.let { callback ->
                runCatching { oldClient.unregisterCallback(callback) }
            }
        }

        observedCastSession = session
        session?.remoteMediaClient?.let { remoteClient ->
            castRemoteClientCallback?.let { callback ->
                runCatching { remoteClient.registerCallback(callback) }
            }
            remoteClient.requestStatus()
            syncCastListeningStatsFromRemote()
        } ?: run {
            activeCastStatsOccurrenceId = null
            listeningStatsTracker.onPlaybackStopped()
        }
        requestWidgetFullUpdate(force = true)
    }

    fun stopCastWearSync() {
        observedCastSession?.remoteMediaClient?.let { remoteClient ->
            castRemoteClientCallback?.let { callback ->
                runCatching { remoteClient.unregisterCallback(callback) }
            }
        }
        observedCastSession = null

        val listener = castSessionManagerListener
        val manager = castSessionManager
        if (listener != null && manager != null) {
            runCatching { manager.removeSessionManagerListener(listener, CastSession::class.java) }
                .onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to remove Cast session listener")
                }
        }
        castSessionManagerListener = null
        castRemoteClientCallback = null
        castSessionManager = null
    }

    data class RemotePlaybackSnapshot(
        val occurrenceId: String,
        val songId: String?,
        val title: String,
        val artist: String,
        val artworkUri: Uri?,
        val isPlaying: Boolean,
        val currentPositionMs: Long,
        val totalDurationMs: Long,
        val repeatMode: Int,
        val isShuffleEnabled: Boolean,
    )

    private fun syncCastListeningStatsFromRemote() {
        val snapshot = resolveCastRemoteSnapshot() ?: return
        val songId = snapshot.songId?.takeIf { it.isNotBlank() }
        if (songId == null) {
            activeCastStatsOccurrenceId = null
            listeningStatsTracker.onPlaybackStopped()
            return
        }

        if (activeCastStatsOccurrenceId != snapshot.occurrenceId) {
            activeCastStatsOccurrenceId = snapshot.occurrenceId
            listeningStatsTracker.onTrackChanged(
                songId = songId,
                positionMs = snapshot.currentPositionMs,
                durationMs = snapshot.totalDurationMs,
                isPlaying = snapshot.isPlaying
            )
            return
        }

        listeningStatsTracker.ensureSession(
            songId = songId,
            positionMs = snapshot.currentPositionMs,
            durationMs = snapshot.totalDurationMs,
            isPlaying = snapshot.isPlaying
        )
    }

    fun resolveCastRemoteSnapshot(): RemotePlaybackSnapshot? {
        val remoteClient = observedCastSession?.remoteMediaClient
            ?: castSessionManager?.currentCastSession?.remoteMediaClient
            ?: return null

        val mediaStatus = remoteClient.mediaStatus ?: return null
        if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_UNKNOWN) {
            return null
        }

        val currentItem = mediaStatus.getQueueItemById(mediaStatus.currentItemId)
        val mediaInfo = currentItem?.media ?: remoteClient.mediaInfo
        val metadata = mediaInfo?.metadata
        if (metadata == null && currentItem == null) {
            return null
        }

        val songId = currentItem
            ?.customData
            ?.optString("songId")
            ?.takeIf { it.isNotBlank() }
        val occurrenceId = currentItem
            ?.itemId
            ?.takeIf { it > 0 }
            ?.toString()
            ?: songId
            ?: mediaInfo?.contentId
            ?: return null

        val durationHintMs = currentItem
            ?.customData
            ?.optLong("durationHintMs", -1L)
            ?.takeIf { it > 0L }

        val streamDurationMs = remoteClient.streamDuration.takeIf { it > 0L }
        val effectiveDurationMs = (streamDurationMs ?: durationHintMs ?: 0L).coerceAtLeast(0L)
        val imageUri = metadata
                ?.images
                ?.firstOrNull()
                ?.url
                ?.toString()
                ?.takeIf { it.isNotBlank() }?.toUri()

        val mappedRepeatMode = when (mediaStatus.queueRepeatMode) {
            MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
            MediaStatus.REPEAT_MODE_REPEAT_ALL,
            MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }

        return RemotePlaybackSnapshot(
            occurrenceId = occurrenceId,
            songId = songId,
            title = metadata?.getString(CastMediaMetadata.KEY_TITLE).orEmpty(),
            artist = metadata?.getString(CastMediaMetadata.KEY_ARTIST).orEmpty(),
            artworkUri = imageUri,
            isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            currentPositionMs = remoteClient.approximateStreamPosition.coerceAtLeast(0L),
            totalDurationMs = effectiveDurationMs,
            repeatMode = mappedRepeatMode,
            isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE,
        )
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
    }
}
