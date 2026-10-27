package com.nobodybuilds.listens.data.noise

import com.nobodybuilds.listens.data.lofi.LofiController
import com.nobodybuilds.listens.data.lofi.LofiSource
import com.nobodybuilds.listens.data.noise.dsp.NoiseMixer
import com.nobodybuilds.listens.presentation.viewmodel.PlaybackStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the noise generator is doing right now. */
data class NoisePlayState(
    /** On (playing on its own through the media service, or turned on under music). */
    val active: Boolean = false,
    /** Audible right now (under music it only sounds while music plays). */
    val sounding: Boolean = false,
    val underMusic: Boolean = false,
)

/**
 * Public entry point for the noise generator (Focus, Sleep, Quick Settings tile).
 *
 * On its own, noise plays through the lofi foreground media service (notification, lock-screen
 * controls, audio focus). "Under music" plays it softly on its own track without audio focus while
 * the main player plays, like the ambience layer. Main thread; nothing heavy in the constructor.
 */
@Singleton
class NoiseController @Inject constructor(
    private val engine: NoiseEngine,
    private val preferences: NoisePreferences,
    private val lofi: LofiController,
    private val playbackStateHolder: dagger.Lazy<PlaybackStateHolder>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _settings = MutableStateFlow(NoiseSettings())
    val settings: StateFlow<NoiseSettings> = _settings.asStateFlow()

    private val underOn = MutableStateFlow(false)
    private val underSounding = MutableStateFlow(false)

    val state: StateFlow<NoisePlayState> = combine(lofi.state, underOn, underSounding) { st, on, sounding ->
        val solo = st.source == LofiSource.Noise && st.isPlaying
        NoisePlayState(active = solo || on, sounding = solo || sounding, underMusic = on && !solo)
    }.stateIn(scope, SharingStarted.Eagerly, NoisePlayState())

    /** Built-in presets first, then yours. */
    val presets: Flow<List<NoisePreset>> = preferences.userPresets.map { NoiseDefaults.all + it }

    val focusOptions: StateFlow<NoiseFocusOptions> =
        preferences.focusOptions.stateIn(scope, SharingStarted.Eagerly, NoiseFocusOptions())

    private var loaded = false
    private var userTouched = false
    private var saveJob: Job? = null
    private var observeJob: Job? = null

    /** Loads the last-used settings once (cheap, async). */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        scope.launch {
            val saved = runCatching { preferences.loadSettings() }.getOrNull()
            if (saved != null && !userTouched) {
                _settings.value = saved
                pushToEngine()
            }
        }
    }

    /** Loads saved settings now (for tiles started cold). */
    suspend fun loadNow() {
        if (userTouched) return
        loaded = true
        runCatching { preferences.loadSettings() }.getOrNull()?.let { _settings.value = it }
    }

    val isSoloPlaying: Boolean get() = lofi.state.value.source == LofiSource.Noise && lofi.state.value.isPlaying

    /** Paused on its own, with the media service still up. */
    val isPausedSolo: Boolean get() = lofi.state.value.source == LofiSource.Noise && !lofi.state.value.isPlaying

    // ---------------------------------------------------------------- playback

    /**
     * Starts noise as set: under music when that's chosen and music is playing right now,
     * otherwise on its own. Returns false when Android refused the service.
     */
    fun play(): Boolean = if (_settings.value.underMusic && isMusicPlaying()) {
        startUnderMusic()
        true
    } else {
        stopUnderMusic()
        lofi.startNoise()
    }

    /** Always plays on its own through the media service (focus sessions, sleep). */
    fun playSolo(): Boolean {
        stopUnderMusic()
        return lofi.startNoise()
    }

    fun pause() {
        if (lofi.state.value.source == LofiSource.Noise) lofi.pause()
        stopUnderMusic()
    }

    fun resume(): Boolean {
        val st = lofi.state.value
        // Paused on its own (service still up): resume there, even if "under music" was switched on since.
        return if (st.source == LofiSource.Noise) lofi.resume() else play()
    }

    fun toggle(): Boolean {
        val s = state.value
        return if (s.active) { pause(); true } else resume()
    }

    /** Stops with a fade (default 4 s). */
    fun stop(fadeMs: Long = 4_000L) {
        if (lofi.state.value.source == LofiSource.Noise) lofi.stopWithFade(fadeMs)
        stopUnderMusic(fadeMs.toFloat())
    }

    // ---------------------------------------------------------------- settings

    fun setSettings(settings: NoiseSettings) {
        userTouched = true
        val old = _settings.value
        val n = settings.normalized()
        _settings.value = n
        pushToEngine()
        scheduleSave()
        if (old.underMusic != n.underMusic && state.value.active) {
            // Switch modes while playing.
            if (n.underMusic) {
                if (isMusicPlaying()) {
                    if (lofi.state.value.source == LofiSource.Noise) lofi.stopWithFade(600L)
                    startUnderMusic()
                } // else: keeps playing on its own until music starts and takes over
            }
            else { stopUnderMusic(); lofi.startNoise() }
        }
    }

    fun update(transform: (NoiseSettings) -> NoiseSettings) = setSettings(transform(_settings.value))

    /** Hand edits clear the preset label. */
    fun edit(transform: (NoiseSettings) -> NoiseSettings) = update { transform(it).copy(presetName = null) }

    /** Adds the sound, or removes it when it's already on. With three on, the oldest makes room. */
    fun toggleLayer(kind: NoiseKind) = edit { s ->
        if (s.layers.any { it.kind == kind }) s.copy(layers = s.layers.filterNot { it.kind == kind })
        else {
            val base = if (s.layers.size >= NoiseMixer.MAX_LAYERS) s.layers.drop(1) else s.layers
            s.copy(layers = base + NoiseLayer(kind, 0.6f))
        }
    }

    fun setLayerVolume(kind: NoiseKind, volume: Float) = edit { s ->
        s.copy(layers = s.layers.map { if (it.kind == kind) it.copy(volume = volume) else it })
    }

    fun applyPreset(preset: NoisePreset) {
        val keepUnder = _settings.value.underMusic
        setSettings(preset.settings.copy(presetName = preset.name, underMusic = keepUnder))
    }

    fun savePreset(name: String) {
        val trimmed = name.trim().take(40)
        if (trimmed.isEmpty()) return
        update { it.copy(presetName = trimmed) }
        scope.launch { runCatching { preferences.savePreset(trimmed, _settings.value) } }
    }

    fun deletePreset(name: String) {
        scope.launch { runCatching { preferences.deletePreset(name) } }
    }

    fun setFocusOptions(options: NoiseFocusOptions) {
        scope.launch { runCatching { preferences.setFocusOptions(options) } }
    }

    // ---------------------------------------------------------------- internals

    private fun isMusicPlaying(): Boolean =
        runCatching { playbackStateHolder.get().stablePlayerState.value.isPlaying }.getOrDefault(false)

    private fun pushToEngine() {
        val s = _settings.value
        engine.update(s, scale = if (underOn.value && !isSoloPlaying) UNDER_MUSIC_LEVEL else 1f)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            runCatching { preferences.saveSettings(_settings.value) }
        }
    }

    private fun startUnderMusic() {
        underOn.value = true
        if (observeJob != null) return
        val holder = runCatching { playbackStateHolder.get() }.getOrNull()
        val musicPlaying = holder?.stablePlayerState?.map { it.isPlaying } ?: MutableStateFlow(false)
        observeJob = scope.launch {
            musicPlaying.distinctUntilChanged().collect { playing ->
                if (playing) {
                    engine.start(_settings.value, scale = UNDER_MUSIC_LEVEL)
                    underSounding.value = true
                } else {
                    engine.stop(600f)
                    underSounding.value = false
                }
            }
        }
    }

    private fun stopUnderMusic(fadeMs: Float = 600f) {
        if (!underOn.value && observeJob == null) return
        observeJob?.cancel()
        observeJob = null
        underOn.value = false
        if (underSounding.value && !isSoloPlaying) engine.stop(fadeMs)
        underSounding.value = false
    }

    private companion object {
        const val UNDER_MUSIC_LEVEL = 0.4f
    }
}
