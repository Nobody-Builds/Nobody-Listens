package com.nobodybuilds.listens.presentation.viewmodel

import android.os.SystemClock
import androidx.media3.common.C
import com.nobodybuilds.listens.data.DailyMixManager
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Tracks listening statistics for songs.
 * Extracted from PlayerViewModel to reduce its size and improve modularity.
 *
 * Responsibilities:
 * - Track active listening sessions
 * - Record play statistics when session ends
 * - Handle voluntary vs automatic plays
 */
@Singleton
class ListeningStatsTracker @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository,
    // Nobody Listens garden: refreshes the Garden widget when today becomes a listening day.
    // Lazy (null in tests): resolved on the main thread on first use, never at injection time.
    private val gardenWidgetRefresher: dagger.Lazy<com.nobodybuilds.listens.data.garden.GardenWidgetRefresher>? = null
) {
    @Volatile
    private var resolvedGardenRefresher: com.nobodybuilds.listens.data.garden.GardenWidgetRefresher? = null

    /** The refresher once resolved; resolves it only on the main thread (posts there otherwise). */
    private fun gardenRefresher(): com.nobodybuilds.listens.data.garden.GardenWidgetRefresher? {
        val lazy = gardenWidgetRefresher ?: return null
        resolvedGardenRefresher?.let { return it }
        val main = android.os.Looper.getMainLooper()
        if (android.os.Looper.myLooper() == main) {
            resolvedGardenRefresher = runCatching { lazy.get() }.getOrNull()
        } else {
            runCatching {
                android.os.Handler(main).post {
                    if (resolvedGardenRefresher == null) resolvedGardenRefresher = runCatching { lazy.get() }.getOrNull()
                }
            }
        }
        return resolvedGardenRefresher
    }

    private var currentSession: ActiveSession? = null
    private var pendingVoluntarySongId: String? = null
    private var scope: CoroutineScope? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _playbackHistory = MutableStateFlow<List<PlaybackStatsRepository.PlaybackHistoryEntry>>(emptyList())
    val playbackHistory: StateFlow<List<PlaybackStatsRepository.PlaybackHistoryEntry>> = _playbackHistory.asStateFlow()

    /**
     * Must be called to set the coroutine scope for async operations.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        val activeScope = scope
        if (activeScope == null || activeScope.coroutineContext[Job]?.isActive != true) {
            scope = coroutineScope
        }
        coroutineScope.launch(Dispatchers.IO) {
            _playbackHistory.value = playbackStatsRepository.loadPlaybackHistory(
                limit = MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS
            )
        }
    }

    @Synchronized
    fun onVoluntarySelection(songId: String) {
        pendingVoluntarySongId = songId
    }

    fun onSongChanged(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        onTrackChanged(
            songId = song?.id,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = song?.duration ?: 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        onTrackChanged(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean
    ) {
        finalizeCurrentSession(recordSkipIfShort = true)
        val safeSongId = songId?.takeIf { it.isNotBlank() }
        if (safeSongId == null) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val normalizedDuration = normalizeDuration(durationMs, fallbackDurationMs)

        currentSession = ActiveSession(
            songId = safeSongId,
            totalDurationMs = normalizedDuration,
            startedAtEpochMs = nowEpoch,
            lastKnownPositionMs = positionMs.coerceAtLeast(0L),
            accumulatedListeningMs = 0L,
            lastRealtimeMs = nowRealtime,
            lastUpdateEpochMs = nowEpoch,
            isPlaying = isPlaying,
            isVoluntary = pendingVoluntarySongId == safeSongId
        )
        if (pendingVoluntarySongId == safeSongId) {
            pendingVoluntarySongId = null
        }
    }

    @Synchronized
    fun onPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        accumulateRealtimeListening(session, nowRealtime)
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()
    }

    @Synchronized
    fun onProgress(positionMs: Long, isPlaying: Boolean) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        accumulateRealtimeListening(session, nowRealtime)
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()
    }

    fun ensureSession(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        ensureSession(
            songId = song?.id,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = song?.duration ?: 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        ensureSession(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean
    ) {
        val safeSongId = songId?.takeIf { it.isNotBlank() }
        if (safeSongId == null) {
            finalizeCurrentSession()
            return
        }
        val existing = currentSession
        if (existing?.songId == safeSongId) {
            updateDuration(normalizeDuration(durationMs, fallbackDurationMs))
            val nowRealtime = SystemClock.elapsedRealtime()
            accumulateRealtimeListening(existing, nowRealtime)
            existing.isPlaying = isPlaying
            existing.lastRealtimeMs = nowRealtime
            existing.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
            existing.lastUpdateEpochMs = System.currentTimeMillis()
            return
        }
        onTrackChanged(
            songId = safeSongId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = fallbackDurationMs,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun updateDuration(durationMs: Long) {
        val session = currentSession ?: return
        if (durationMs > 0 && durationMs != C.TIME_UNSET) {
            session.totalDurationMs = durationMs
        }
    }

    @Synchronized
    fun finalizeCurrentSession(
        forceSynchronousPersistence: Boolean = false,
        // Only a move to another track counts as a skip — not pausing, stopping or closing the app.
        recordSkipIfShort: Boolean = false
    ) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        accumulateRealtimeListening(session, nowRealtime)
        val listened = session.accumulatedListeningMs.coerceAtLeast(0L)
        // Nobody Listens counts a play after 30 seconds of listening. Tracks shorter than that
        // count once nearly all of them has been heard.
        val trackDuration = session.totalDurationMs
        val playThresholdMs = if (trackDuration in 1 until MIN_SESSION_LISTEN_MS) {
            (trackDuration * 9 / 10).coerceAtLeast(1_000L)
        } else {
            MIN_SESSION_LISTEN_MS
        }
        if (recordSkipIfShort && listened < playThresholdMs && listened >= MIN_SKIP_LISTEN_MS) {
            val skippedSongId = session.songId
            persistenceScope.launch {
                runCatching { playbackStatsRepository.recordSkip(skippedSongId) }
                    .onFailure { Timber.e(it, "Failed to record skip for song=%s", skippedSongId) }
            }
        }
        if (listened >= playThresholdMs) {
            val rawEndTimestamp = when {
                session.isPlaying -> nowEpoch
                session.lastUpdateEpochMs > 0L -> session.lastUpdateEpochMs
                else -> session.startedAtEpochMs + listened
            }
            val timestamp = rawEndTimestamp
                .coerceAtLeast(session.startedAtEpochMs.coerceAtLeast(0L))
                .coerceAtMost(nowEpoch)
            val songId = session.songId
            val historyEntry = PlaybackStatsRepository.PlaybackHistoryEntry(
                songId = songId,
                timestamp = timestamp
            )
            _playbackHistory.update { current ->
                (listOf(historyEntry) + current).take(MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS)
            }
            persistPlayback(
                songId = songId,
                listened = listened,
                timestamp = timestamp,
                forceSynchronous = forceSynchronousPersistence
            )
        }
        currentSession = null
        if (pendingVoluntarySongId == session.songId) {
            pendingVoluntarySongId = null
        }
    }

    @Synchronized
    fun onPlaybackStopped() {
        finalizeCurrentSession()
    }

    @Synchronized
    fun onCleared() {
        finalizeCurrentSession(forceSynchronousPersistence = true)
        scope = null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun persistPlayback(
        songId: String,
        listened: Long,
        timestamp: Long,
        forceSynchronous: Boolean
    ) {
        val refresher = gardenRefresher() // on the caller's thread (main), before going to IO
        persistenceScope.launch {
            runCatching {
                persistPlaybackInternal(songId = songId, listened = listened, timestamp = timestamp, refresher = refresher)
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to persist listening session for song=%s", songId)
            }
        }
    }

    private suspend fun persistPlaybackInternal(
        songId: String,
        listened: Long,
        timestamp: Long,
        refresher: com.nobodybuilds.listens.data.garden.GardenWidgetRefresher?,
    ) {
        dailyMixManager.recordPlay(
            songId = songId,
            songDurationMs = listened,
            timestamp = timestamp
        )
        playbackStatsRepository.recordPlayback(
            songId = songId,
            durationMs = listened,
            timestamp = timestamp
        )
        refresher?.onListeningRecorded()
    }

    private fun accumulateRealtimeListening(session: ActiveSession, nowRealtime: Long) {
        if (!session.isPlaying) return
        val delta = (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
        if (delta > 0L) {
            session.accumulatedListeningMs += delta
        }
    }

    private fun normalizeDuration(durationMs: Long, fallbackDurationMs: Long): Long {
        return when {
            durationMs > 0 && durationMs != C.TIME_UNSET -> durationMs
            fallbackDurationMs > 0 && fallbackDurationMs != C.TIME_UNSET -> fallbackDurationMs
            else -> 0L
        }
    }

    companion object {
        private val MIN_SESSION_LISTEN_MS = TimeUnit.SECONDS.toMillis(30)
        /** Anything started for at least this long but stopped before it counts is a skip. */
        private val MIN_SKIP_LISTEN_MS = TimeUnit.SECONDS.toMillis(1)
        private const val MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS = 500
    }
}

/**
 * Represents an active listening session for a song.
 */
data class ActiveSession(
    val songId: String,
    var totalDurationMs: Long,
    val startedAtEpochMs: Long,
    var lastKnownPositionMs: Long,
    var accumulatedListeningMs: Long,
    var lastRealtimeMs: Long,
    var lastUpdateEpochMs: Long,
    var isPlaying: Boolean,
    val isVoluntary: Boolean
)
