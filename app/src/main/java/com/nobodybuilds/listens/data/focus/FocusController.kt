package com.nobodybuilds.listens.data.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.nobodybuilds.listens.data.crash.SafeMode
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.lofi.LofiController
import com.nobodybuilds.listens.data.lofi.LofiEngine
import com.nobodybuilds.listens.data.lofi.LofiMood
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.noise.NoiseController
import com.nobodybuilds.listens.data.smart.SmartRepository
import com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics
import com.nobodybuilds.listens.presentation.viewmodel.PlaybackStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Implemented by [FocusService] while it runs. */
internal interface FocusServiceHandle {
    /** Re-posts the notification from the controller's current state. */
    fun refresh()

    /** Leaves the foreground, optionally posting a "session done" notification, and stops. */
    fun finish(summary: String?)
}

/**
 * Runs focus sessions: owns the [FocusTimer], keeps [FocusService] in the foreground while a
 * session is active, schedules a wake-up alarm for each phase end and drives the music.
 *
 * Call everything from the main thread. The constructor does no work; [ensureRestored] (from the
 * Focus screen) brings back a session that was running when the process died.
 */
@Singleton
class FocusController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: FocusRepository,
    private val lofi: dagger.Lazy<LofiController>,
    private val lofiEngine: dagger.Lazy<LofiEngine>,
    private val smart: dagger.Lazy<SmartRepository>,
    private val musicDao: dagger.Lazy<MusicDao>,
    private val playbackStateHolder: dagger.Lazy<PlaybackStateHolder>,
    private val noise: dagger.Lazy<NoiseController>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val timer = FocusTimer()

    private val _state = MutableStateFlow(FocusTimerState())
    val state: StateFlow<FocusTimerState> = _state.asStateFlow()

    private val _music = MutableStateFlow(FocusMusicSource.Silence)
    /** The music source of the current session. */
    val music: StateFlow<FocusMusicSource> = _music.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    /** A short note for the screen, e.g. when the chosen music wasn't available. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _exactAlarmNeeded = MutableStateFlow(false)
    /** True when a session started without exact-alarm access (breaks may start a little late). */
    val exactAlarmNeeded: StateFlow<Boolean> = _exactAlarmNeeded.asStateFlow()

    /** Set by the Focus screen: plays a list of songs through the main player. */
    var songPlayer: ((List<Song>) -> Unit)? = null

    @Volatile internal var service: FocusServiceHandle? = null

    private var restored = false
    private val restoreMutex = Mutex()
    private var tickJob: Job? = null
    private var musicStarted = false
    private var musicJob: Job? = null

    // ---------------------------------------------------------------- public API

    /** Restores a session saved before process death (once), in the background. */
    fun ensureRestored() {
        if (restored) {
            // A session carried on without the service after a background alarm: bring it back now
            // that the app is in the foreground.
            if (service == null && timer.state.phase.isActive) startService()
            return
        }
        scope.launch { restore() }
    }

    /**
     * Restores a session saved before process death (once; later calls wait for the first one to
     * finish). Never auto-resumes in safe mode. Main thread. Called by [FocusService] before it
     * handles an alarm or notification action, so a cold-started process acts on the real session.
     */
    internal suspend fun restore() {
        restore(fromAlarm = false)
    }

    /**
     * [fromAlarm]: called for a phase-end alarm in a background process. The catch-up advance then
     * chimes (that phase change is what the alarm was for) and no foreground service is started.
     * Returns true when this call did the restore (so the caller must not advance again).
     */
    private suspend fun restore(fromAlarm: Boolean): Boolean = restoreMutex.withLock {
        if (restored) return@withLock false
        restored = true
        run {
            val saved = runCatching { repository.loadActive() }.getOrNull() ?: return@run
            if (timer.state.phase.isActive) return@run // a session was started meanwhile
            val now = System.currentTimeMillis()
            if (!saved.timer.phase.isActive || now - saved.savedAt > STALE_MS) {
                runCatching { repository.saveActive(null) }
                return@run
            }
            timer.restore(saved.timer)
            _music.value = saved.music
            musicStarted = false
            if (SafeMode.isActive(context)) {
                timer.pause(now)
                _notice.value = "Safe mode is on, so your session is paused"
            } else {
                // Catch up on phase ends missed while the process was gone; music stays off until the user resumes it.
                handle(timer.advance(now), playChime = fromAlarm, applyMusicChange = false)
            }
            if (timer.state.phase.isActive) {
                publish()
                if (!fromAlarm) startService()
                scheduleNext()
            } else {
                publish()
            }
        }
        true
    }

    /** Widget: starts a session with the saved config and music, or resumes a paused one. Main thread. */
    internal suspend fun quickStart() {
        val s = timer.state
        if (s.phase.isActive) {
            if (s.isPaused) resume()
            return
        }
        val config = runCatching { repository.config.first() }.getOrDefault(FocusConfig())
        val source = runCatching { repository.musicSource.first() }.getOrDefault(FocusMusicSource.Lofi)
        start(config, source)
    }

    fun start(config: FocusConfig, source: FocusMusicSource) {
        _notice.value = null
        _exactAlarmNeeded.value = runCatching {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == false
        }.getOrDefault(false)
        _music.value = source
        musicStarted = false
        timer.start(config, System.currentTimeMillis())
        publish()
        startService()
        scheduleNext()
        NobodyListensHaptics.get().success()
        applyMusic(FocusPhase.Focus)
    }

    fun pause() {
        if (!timer.pause(System.currentTimeMillis())) return
        cancelScheduled()
        pauseMusic()
        publish()
    }

    fun resume() {
        if (!timer.resume(System.currentTimeMillis())) return
        scheduleNext()
        publish()
        applyMusic(timer.state.phase)
    }

    fun togglePause() {
        if (timer.state.isRunning) pause() else resume()
    }

    fun skip() {
        val wasPaused = timer.state.isPaused
        val events = timer.skip(System.currentTimeMillis())
        if (events.isEmpty()) return
        handle(events, playChime = true, applyMusicChange = !wasPaused)
        if (timer.state.isRunning) scheduleNext() else cancelScheduled()
    }

    fun stop() {
        if (!timer.state.phase.isActive && timer.state.phase != FocusPhase.Done) return
        timer.stop()
        cancelScheduled()
        endMusic()
        publish()
        scope.launch { runCatching { repository.saveActive(null) } }
        service?.finish(null)
    }

    /** Clears a finished session so the screen shows the set-up again. */
    fun reset() {
        if (timer.state.phase == FocusPhase.Done) {
            timer.stop()
            publish()
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun dismissExactAlarmNotice() {
        _exactAlarmNeeded.value = false
    }

    // ---------------------------------------------------------------- service hooks

    /** A phase-end alarm fired (the device may have been asleep). */
    internal fun onAlarm() {
        val pm = context.getSystemService(PowerManager::class.java)
        val wl = runCatching {
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NobodyListens:FocusChime")?.apply {
                setReferenceCounted(false)
                acquire(8_000L)
            }
        }.getOrNull()
        tick()
        if (wl == null) Timber.tag(TAG).d("No wake lock for focus alarm")
    }

    /**
     * A phase-end alarm broadcast ([FocusAlarmReceiver]). With the service running this is the same
     * as [onAlarm]; otherwise the saved session is restored, advanced (with the chime), the alarm
     * re-armed and the notification posted directly, without starting a foreground service.
     * [onDone] finishes the broadcast.
     */
    internal fun onAlarmBroadcast(onDone: () -> Unit) {
        scope.launch {
            try {
                if (service != null) {
                    onAlarm()
                    return@launch
                }
                val wl = runCatching {
                    context.getSystemService(PowerManager::class.java)
                        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NobodyListens:FocusChime")
                        ?.apply { setReferenceCounted(false); acquire(8_000L) }
                }.getOrNull()
                val didRestore = runCatching { restore(fromAlarm = true) }
                    .onFailure { Timber.tag(TAG).w(it, "Focus restore failed") }
                    .getOrDefault(true)
                // Process was alive (already restored): advance now. tick() is a no-op when the
                // in-process timer already handled this phase end, so nothing chimes twice.
                if (!didRestore) tick()
                val s = timer.state
                // A finished session already posted its "done" note in handle().
                if (service == null && s.phase.isActive) FocusService.notifyWithoutService(context, s, null)
                // Let the chime (its own short AudioTrack) finish before the process may be frozen.
                delay(CHIME_GRACE_MS)
                runCatching { if (wl?.isHeld == true) wl.release() }
            } finally {
                onDone()
            }
        }
    }

    // ---------------------------------------------------------------- internals

    private fun tick() {
        val events = timer.advance(System.currentTimeMillis())
        if (events.isNotEmpty()) handle(events, playChime = true)
        if (timer.state.isRunning) scheduleNext()
    }

    private fun handle(events: List<FocusEvent>, playChime: Boolean, applyMusicChange: Boolean = true) {
        var newPhase: FocusPhase? = null
        var finished: FocusEvent.Finished? = null
        for (e in events) {
            when (e) {
                is FocusEvent.FocusCompleted -> scope.launch {
                    runCatching { repository.recordCompleted(e.minutes, e.at) }
                        .onFailure { Timber.tag(TAG).w(it, "Could not record focus round") }
                }
                is FocusEvent.PhaseStarted -> newPhase = e.phase
                is FocusEvent.Finished -> finished = e
            }
        }
        if (newPhase != null && playChime) {
            runCatching { lofi.get().playChime() }
            NobodyListensHaptics.get().chime()
        }
        publish()
        val phase = newPhase
        if (phase != null && applyMusicChange) applyMusic(phase)
        if (finished != null) {
            cancelScheduled()
            endMusic(sessionFinished = true)
            scope.launch { runCatching { repository.saveActive(null) } }
            val rounds = finished.completedRounds
            val minutes = rounds * timer.state.config.focusMinutes
            val summary = if (rounds == 0) null
                else "Session done · $rounds ${if (rounds == 1) "round" else "rounds"}, $minutes min focused"
            val svc = service
            if (svc != null) svc.finish(summary)
            else FocusService.notifyWithoutService(context, timer.state, summary)
        }
    }

    private fun publish() {
        val s = timer.state
        _state.value = s
        service?.refresh()
        runCatching { com.nobodybuilds.listens.ui.glancewidget.ExtraWidgetUpdater.requestLofiFocus(context) }
        if (s.phase.isActive) {
            val snapshot = ActiveFocusSession(s, _music.value, System.currentTimeMillis())
            scope.launch { runCatching { repository.saveActive(snapshot) } }
        }
    }

    private fun startService() {
        if (service != null) {
            service?.refresh()
            return
        }
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java).setAction(FocusService.ACTION_SHOW))
        }.onFailure { Timber.tag(TAG).w(it, "Could not start focus service") }
    }

    /** Wakes us at the phase end: an in-process timer while awake, plus an alarm for when the device sleeps. */
    private fun scheduleNext() {
        tickJob?.cancel()
        val at = timer.nextDeadline() ?: return cancelScheduled()
        tickJob = scope.launch {
            delay((at - System.currentTimeMillis()).coerceAtLeast(0L) + 50L)
            tick()
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            val pi = alarmIntent()
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not schedule focus alarm") }
    }

    private fun cancelScheduled() {
        tickJob?.cancel()
        tickJob = null
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent()) }
    }

    // A broadcast: delivered in the background / to a killed process, where a foreground service can't be started.
    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_ALARM,
        Intent(context, FocusAlarmReceiver::class.java).setAction(FocusAlarmReceiver.ACTION_PHASE_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // ---------------------------------------------------------------- music

    private fun applyMusic(phase: FocusPhase) {
        when {
            phase == FocusPhase.Focus -> playMusic()
            phase.isBreak -> pauseMusic()
            else -> endMusic()
        }
    }

    private fun playMusic() {
        when (_music.value) {
            FocusMusicSource.Silence -> Unit
            FocusMusicSource.Lofi -> runCatching {
                val l = lofi.get()
                // The session owns lofi: its service must not idle-stop while paused or on a break.
                l.holdForFocus = true
                if (!musicStarted) {
                    l.ensureLoaded()
                    val calm = l.settings.value.withMood(LofiMood.Dreamy)
                    if (!l.start(calm.copy(drumsVolume = (calm.drumsVolume * 0.7f)))) lofiRefused()
                    musicStarted = true
                } else {
                    // Back to full level after a break (lowered without saving), or restart if stopped.
                    runCatching { lofiEngine.get().update(l.settings.value) }
                    if (!l.state.value.isPlaying && !l.resume()) lofiRefused()
                }
            }.onFailure { Timber.tag(TAG).w(it, "Lofi failed") }
            FocusMusicSource.Noise -> runCatching {
                val n = noise.get()
                // Same service as lofi: keep it up while paused or on a break.
                lofi.get().holdForFocus = true
                if (!musicStarted) {
                    n.ensureLoaded()
                    if (!n.playSolo()) lofiRefused()
                    musicStarted = true
                } else if (!n.isSoloPlaying && !n.resume()) lofiRefused()
            }.onFailure { Timber.tag(TAG).w(it, "Noise failed") }
            FocusMusicSource.Smart, FocusMusicSource.Library -> {
                if (musicStarted) {
                    runCatching { playbackStateHolder.get().mediaController?.play() }
                    return
                }
                val wanted = _music.value
                val smartRepo = smart.get()
                val dao = musicDao.get()
                musicJob?.cancel()
                musicJob = scope.launch {
                    val songs = resolveSongs(wanted, smartRepo, dao)
                    if (timer.state.phase != FocusPhase.Focus || !timer.state.isRunning) return@launch
                    val player = songPlayer
                    when {
                        songs.isEmpty() -> _notice.value = "No calm instrumental songs found, so it's quiet for now"
                        player == null -> _notice.value = "Open Focus to start the music"
                        else -> {
                            player(songs)
                            musicStarted = true
                        }
                    }
                }
            }
        }
    }

    private suspend fun resolveSongs(source: FocusMusicSource, smartRepo: SmartRepository, dao: MusicDao): List<Song> {
        if (source == FocusMusicSource.Smart) {
            val mixSongs = runCatching {
                if (!smartRepo.isEnabled.first()) emptyList()
                else {
                    val mix = smartRepo.mixes().firstOrNull { it.id == SMART_FOCUS_ID || it.title == "Focus" }
                    if (mix == null) emptyList() else smartRepo.songsFor(mix.songIds)
                }
            }.getOrDefault(emptyList())
            if (mixSongs.isNotEmpty()) return mixSongs
            _notice.value = "No Focus mix yet, so playing instrumental songs from your library"
        }
        return runCatching {
            withContext(Dispatchers.IO) {
                dao.getSongs(emptyList(), false).first()
                    .asSequence()
                    .filter { FocusMusicFilter.isInstrumentalLeaning(it.title, it.genre) }
                    .map { it.toSong() }
                    .toList()
                    .shuffled()
                    .take(MAX_SONGS)
            }
        }.getOrDefault(emptyList())
    }

    private fun pauseMusic() {
        musicJob?.cancel()
        when (_music.value) {
            // Lofi keeps playing softly through breaks (pausing it would let its service time out).
            FocusMusicSource.Lofi -> if (musicStarted) runCatching {
                val l = lofi.get()
                if (timer.state.isPaused) l.pause() // service stays up: holdForFocus
                else {
                    if (!l.state.value.isPlaying && !l.resume()) lofiRefused()
                    lofiEngine.get().update(l.settings.value.let { it.copy(masterVolume = it.masterVolume * BREAK_LOFI_LEVEL) })
                }
            }
            // Noise keeps going through breaks unless the user chose otherwise; a paused session pauses it.
            FocusMusicSource.Noise -> if (musicStarted) runCatching {
                val n = noise.get()
                if (timer.state.isPaused || !n.focusOptions.value.keepThroughBreaks) n.pause()
                else if (!n.isSoloPlaying && !n.resume()) lofiRefused()
            }
            FocusMusicSource.Smart, FocusMusicSource.Library -> if (musicStarted) runCatching {
                playbackStateHolder.get().mediaController?.let { if (it.isPlaying) it.pause() }
            }
            FocusMusicSource.Silence -> Unit
        }
    }

    private fun endMusic(sessionFinished: Boolean = false) {
        musicJob?.cancel()
        if (!musicStarted) return
        when (_music.value) {
            FocusMusicSource.Lofi -> runCatching {
                val l = lofi.get()
                l.holdForFocus = false
                l.stop()
            }
            FocusMusicSource.Noise -> runCatching {
                lofi.get().holdForFocus = false
                val n = noise.get()
                // A finished session respects "Stop at end of session"; stopping by hand always stops.
                if (!sessionFinished || n.focusOptions.value.stopAtSessionEnd) n.stop(NOISE_END_FADE_MS)
            }
            FocusMusicSource.Smart, FocusMusicSource.Library -> runCatching {
                playbackStateHolder.get().mediaController?.let { if (it.isPlaying) it.pause() }
            }
            FocusMusicSource.Silence -> Unit
        }
        musicStarted = false
    }

    /** Android refused to (re)start lofi from the background; the timer keeps running. */
    private fun lofiRefused() {
        _notice.value = "Lofi couldn't start in the background. Open Focus and press play to bring it back"
    }

    private companion object {
        const val TAG = "FocusController"
        const val REQUEST_ALARM = 0x0F0C
        const val SMART_FOCUS_ID = "smart_focus"
        const val MAX_SONGS = 120
        const val BREAK_LOFI_LEVEL = 0.3f
        const val NOISE_END_FADE_MS = 6_000L
        const val STALE_MS = 12 * 60 * 60 * 1000L
        const val CHIME_GRACE_MS = 2_500L
    }
}
