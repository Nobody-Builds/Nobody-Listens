package com.nobodybuilds.listens.data.sleep

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.SongFeaturesDao
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.lofi.LofiController
import com.nobodybuilds.listens.data.lofi.LofiMood
import com.nobodybuilds.listens.data.lofi.LofiSource
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.noise.NoiseController
import com.nobodybuilds.listens.data.noise.NoiseDefaults
import com.nobodybuilds.listens.data.service.MusicService
import com.nobodybuilds.listens.data.smart.SmartModel
import com.nobodybuilds.listens.data.smart.SmartRepository
import com.nobodybuilds.listens.presentation.viewmodel.PlaybackStateHolder
import com.nobodybuilds.listens.presentation.viewmodel.SleepTimerStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

private val Context.sleepDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_sleep")

enum class SleepSource(val label: String, val blurb: String) {
    Noise("Noise", "Steady sound, made on your phone"),
    Lofi("Lofi", "Slow, dreamy, no hurry"),
    Music("Calm mix", "Quiet songs from your library");
}

data class SleepSettings(
    val source: SleepSource = SleepSource.Noise,
    val minutes: Int = 30,
    /** The volume lowers slowly over this many final minutes. */
    val fadeMinutes: Int = 10,
)

/** A running sleep session. */
data class SleepSession(val source: SleepSource, val endsAt: Long?)

/**
 * The Sleep space: plays noise, lofi or a calm mix and stops it with a long, slow fade.
 *
 * It never adds a timer of its own: noise and lofi use the lofi service's sleep timer, and the calm
 * mix uses the player's sleep timer ([SleepTimerStateHolder]); this class only lowers the player's
 * volume over the last minutes. Main thread; nothing heavy in the constructor.
 */
@Singleton
class SleepController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lofi: LofiController,
    private val noise: NoiseController,
    private val sleepTimer: dagger.Lazy<SleepTimerStateHolder>,
    private val playback: dagger.Lazy<PlaybackStateHolder>,
    private val smart: dagger.Lazy<SmartRepository>,
    private val features: dagger.Lazy<SongFeaturesDao>,
    private val musicDao: dagger.Lazy<MusicDao>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store get() = context.sleepDataStore

    private val _settings = MutableStateFlow(SleepSettings())
    val settings: StateFlow<SleepSettings> = _settings.asStateFlow()

    private val _active = MutableStateFlow<SleepSource?>(null)
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private val _preparing = MutableStateFlow(false)
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private var loaded = false
    private var fadeJob: Job? = null
    private var watchJob: Job? = null

    /** The running session, or null. Ends by itself when its timer runs out or playback stops. */
    val session: Flow<SleepSession?> by lazy {
        val musicEnd: Flow<Long?> = runCatching { sleepTimer.get().sleepTimerEndTimeMillis }.getOrNull() ?: flowOf(null)
        combine(_active, lofi.state, musicEnd) { active, st, musicEndsAt ->
            when (active) {
                null -> null
                SleepSource.Noise -> if (st.source == LofiSource.Noise) SleepSession(active, st.sleepTimerEndsAt) else null
                SleepSource.Lofi -> if (st.source == LofiSource.Generator) SleepSession(active, st.sleepTimerEndsAt) else null
                SleepSource.Music -> if (musicEndsAt != null || _preparing.value) SleepSession(active, musicEndsAt) else null
            }
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        scope.launch {
            val prefs = runCatching { store.data.first() }.getOrNull() ?: return@launch
            val source = prefs[SOURCE]?.let { v -> SleepSource.entries.firstOrNull { it.name == v } } ?: SleepSource.Noise
            _settings.value = SleepSettings(
                source = source,
                minutes = prefs[MINUTES] ?: 30,
                fadeMinutes = prefs[FADE] ?: 10,
            )
            // First visit: noise starts from the Sleep preset.
            if (prefs[NOISE_INIT] != true) {
                noise.applyPreset(NoiseDefaults.Sleep)
                runCatching { store.edit { it[NOISE_INIT] = true } }
            }
        }
    }

    fun update(transform: (SleepSettings) -> SleepSettings) {
        val n = transform(_settings.value).let {
            it.copy(minutes = it.minutes.coerceIn(5, 240), fadeMinutes = it.fadeMinutes.coerceIn(1, 30))
        }
        _settings.value = n
        scope.launch {
            runCatching {
                store.edit {
                    it[SOURCE] = n.source.name
                    it[MINUTES] = n.minutes
                    it[FADE] = n.fadeMinutes
                }
            }
        }
    }

    fun dismissNotice() { _notice.value = null }

    /** Starts the chosen sound with the timer. [songPlayer] plays a list through the main player. */
    fun start(songPlayer: ((List<Song>) -> Unit)?) {
        val s = _settings.value
        _notice.value = null
        // Noise ↔ lofi switch inside the same service; only a calm mix needs undoing first.
        if (_active.value == SleepSource.Music) stopInternal(fadeMs = 600L, pauseMusic = false)
        val fadeMs = minOf(s.fadeMinutes, s.minutes) * 60_000L
        when (s.source) {
            SleepSource.Noise -> {
                noise.ensureLoaded()
                if (noise.settings.value.layers.isEmpty()) noise.applyPreset(NoiseDefaults.Sleep)
                if (!noise.playSolo()) { _notice.value = "Couldn't start noise"; return }
                lofi.setSleepTimer(s.minutes, fadeMs)
                _active.value = SleepSource.Noise
            }
            SleepSource.Lofi -> {
                lofi.ensureLoaded()
                val calm = lofi.settings.value.withMood(LofiMood.Dreamy).let {
                    it.copy(tempoBpm = 62, drumsVolume = it.drumsVolume * 0.4f, crackle = it.crackle * 0.7f)
                }
                if (!lofi.start(calm)) { _notice.value = "Couldn't start lofi"; return }
                lofi.setSleepTimer(s.minutes, fadeMs)
                _active.value = SleepSource.Lofi
            }
            SleepSource.Music -> startMusic(s, fadeMs, songPlayer)
        }
    }

    /** Adds [minutes] to the running timer. */
    fun extend(minutes: Int, currentEndsAt: Long?) {
        val active = _active.value ?: return
        val remaining = ((currentEndsAt ?: System.currentTimeMillis()) - System.currentTimeMillis()).coerceAtLeast(0L)
        val total = ceil(remaining / 60_000.0).toInt() + minutes
        val fadeMs = minOf(_settings.value.fadeMinutes, total) * 60_000L
        when (active) {
            SleepSource.Noise, SleepSource.Lofi -> lofi.setSleepTimer(total, fadeMs)
            SleepSource.Music -> {
                fadeJob?.cancel(); watchJob?.cancel()
                restoreVolume()
                runCatching { sleepTimer.get().setSleepTimer(total) }
                startMusicFade(total, fadeMs)
            }
        }
    }

    fun stop() = stopInternal(fadeMs = 2_000L, pauseMusic = true)

    private fun stopInternal(fadeMs: Long, pauseMusic: Boolean) {
        when (_active.value) {
            SleepSource.Noise, SleepSource.Lofi -> {
                lofi.setSleepTimer(null, 0L)
                lofi.stopWithFade(fadeMs)
            }
            SleepSource.Music -> {
                fadeJob?.cancel(); watchJob?.cancel()
                runCatching { sleepTimer.get().cancelSleepTimer(suppressDefaultToast = true) }
                if (pauseMusic) runCatching { playback.get().mediaController?.pause() }
                restoreVolume()
            }
            null -> Unit
        }
        _active.value = null
        _preparing.value = false
    }

    // ---------------------------------------------------------------- calm mix

    private fun startMusic(s: SleepSettings, fadeMs: Long, songPlayer: ((List<Song>) -> Unit)?) {
        if (songPlayer == null) { _notice.value = "Open Sleep to start the music"; return }
        _active.value = SleepSource.Music
        _preparing.value = true
        scope.launch {
            val songs = runCatching { calmSongs(s.minutes) }
                .onFailure { Timber.tag(TAG).w(it, "Calm mix failed") }
                .getOrDefault(emptyList())
            _preparing.value = false
            if (_active.value != SleepSource.Music) return@launch
            if (songs.isEmpty()) {
                _active.value = null
                _notice.value = "No calm songs found. Try noise or lofi"
                return@launch
            }
            songPlayer(songs)
            runCatching { sleepTimer.get().setSleepTimer(s.minutes) }
            startMusicFade(s.minutes, fadeMs)
        }
    }

    /**
     * Asks MusicService to lower its volume over the last [fadeMs] (the service owns the fade and
     * always puts the volume back); the player's own sleep timer then pauses it.
     */
    private fun startMusicFade(minutes: Int, fadeMs: Long) {
        fadeJob?.cancel()
        watchJob?.cancel()
        val total = minutes * 60_000L
        val endsAt = System.currentTimeMillis() + total
        sendToMusicService(MusicService.ACTION_SLEEP_FADE) {
            putExtra(MusicService.EXTRA_SLEEP_FADE_START_AT, endsAt - fadeMs)
            putExtra(MusicService.EXTRA_SLEEP_FADE_END_AT, endsAt)
        }
        fadeJob = scope.launch {
            delay(total + 5_000L)
            _active.value = null
        }
        // The player's timer was cancelled or replaced elsewhere: stop fading.
        watchJob = scope.launch {
            var seen = false
            sleepTimer.get().sleepTimerEndTimeMillis.collect { end ->
                if (end != null && kotlin.math.abs(end - endsAt) <= 5_000L) {
                    seen = true
                } else if (seen) {
                    if (System.currentTimeMillis() < endsAt - 2_000L) {
                        fadeJob?.cancel()
                        restoreVolume()
                        _active.value = null
                    } else {
                        _active.value = null
                    }
                    watchJob?.cancel()
                }
            }
        }
    }

    /** Cancels a service-side fade; the service restores the volume it had. */
    private fun restoreVolume() = sendToMusicService(MusicService.ACTION_SLEEP_FADE_CANCEL)

    private fun sendToMusicService(action: String, extras: Intent.() -> Unit = {}) {
        // MusicService is running (it plays the calm mix), so a plain start is allowed.
        runCatching {
            context.startService(Intent(context, MusicService::class.java).setAction(action).apply(extras))
        }.onFailure { Timber.tag(TAG).w(it, "Could not reach MusicService for %s", action) }
    }

    /**
     * Smart "Calm" / "Late night" mixes when available; else calm genres or low-energy songs; else
     * favourites. With Smart data the order gets gradually calmer (energy falls).
     */
    private suspend fun calmSongs(minutes: Int): List<Song> = withContext(Dispatchers.IO) {
        val want = (minutes / 3 + 6).coerceIn(8, 80)
        val smartRepo = smart.get()
        val smartOn = runCatching { smartRepo.isEnabled.first() }.getOrDefault(false)
        val energy: Map<String, Float> = if (smartOn) {
            runCatching { features.get().getAllAnalyzed(SmartModel.MODEL_VERSION).associate { it.songId to it.energy } }
                .getOrDefault(emptyMap())
        } else emptyMap()

        var pool: List<Song> = emptyList()
        if (smartOn) {
            pool = runCatching {
                val mixes = smartRepo.mixes()
                val ids = listOfNotNull(
                    mixes.firstOrNull { it.id == "smart_calm" || it.title == "Calm" },
                    mixes.firstOrNull { it.id == "smart_late_night" || it.title == "Late night" },
                ).flatMap { it.songIds }.distinct()
                if (ids.isEmpty()) emptyList() else smartRepo.songsFor(ids)
            }.getOrDefault(emptyList())
        }
        val library by lazy {
            runCatching { musicDao.get().getSongs(emptyList(), false) }.getOrNull()
        }
        if (pool.size < want / 2) {
            val all = library?.first().orEmpty()
            val lowEnergyCut = energy.values.sorted().let { if (it.size >= 20) it[it.size * 35 / 100] else null }
            pool = (pool + all.asSequence()
                .filter { e ->
                    val g = e.genre?.lowercase().orEmpty()
                    CALM_GENRES.any { it in g } ||
                        (lowEnergyCut != null && (energy[e.id.toString()] ?: Float.MAX_VALUE) <= lowEnergyCut)
                }
                .map { it.toSong() }
                .toList()).distinctBy { it.id }
        }
        if (pool.isEmpty()) {
            pool = library?.first().orEmpty().filter { it.isFavorite }.map { it.toSong() }
        }
        val chosen = pool.shuffled().take(want)
        val known = chosen.count { energy.containsKey(it.id) }
        if (known >= chosen.size / 2 && known > 0) {
            val median = chosen.mapNotNull { energy[it.id] }.sorted().let { it[it.size / 2] }
            chosen.sortedByDescending { energy[it.id] ?: median }
        } else chosen
    }

    private companion object {
        const val TAG = "SleepController"
        val SOURCE = stringPreferencesKey("source")
        val MINUTES = intPreferencesKey("minutes")
        val FADE = intPreferencesKey("fade_minutes")
        val NOISE_INIT = booleanPreferencesKey("noise_init")
        val CALM_GENRES = listOf(
            "ambient", "classical", "piano", "new age", "lofi", "lo-fi", "chill", "acoustic", "sleep",
            "meditation", "instrumental", "soundtrack", "score", "downtempo", "folk",
        )
    }
}

/** For backup/restore (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupSleepDataStore: DataStore<Preferences> get() = sleepDataStore
