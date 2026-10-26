package com.nobodybuilds.listens.data.lofi

import com.nobodybuilds.listens.data.lofi.dsp.AmbienceMixer
import com.nobodybuilds.listens.presentation.viewmodel.PlaybackStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ambient sounds (rain, café, fireplace, waves) mixed over normal music on their own
 * AudioTrack — no audio focus, so the music keeps playing. Sounds only while music (or lofi
 * radio) is actually playing; the audio thread is gone otherwise.
 *
 * On/off choices last for this app session; volumes are remembered.
 */
@Singleton
class AmbientLayer @Inject constructor(
    private val preferences: LofiPreferences,
    private val lofiController: LofiController,
    private val playbackStateHolder: dagger.Lazy<PlaybackStateHolder>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _layers = MutableStateFlow(AmbienceKind.entries.associateWith { AmbienceLayerState() })
    val layers: StateFlow<Map<AmbienceKind, AmbienceLayerState>> = _layers.asStateFlow()

    private val _isSounding = MutableStateFlow(false)
    /** True while the ambience is audible (some layer on and music playing). */
    val isSounding: StateFlow<Boolean> = _isSounding.asStateFlow()

    private var mixer: AmbienceMixer? = null
    private var out: AudioOut? = null
    private var observeJob: Job? = null
    private var loaded = false
    private val saveJobs = HashMap<AmbienceKind, Job>()

    val level: Float get() = mixer?.level ?: 0f

    /** Loads saved volumes once. Call from UI. */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        scope.launch {
            val saved = runCatching { preferences.ambienceVolumes() }.getOrNull() ?: return@launch
            _layers.update { cur -> cur.mapValues { (k, v) -> v.copy(volume = saved[k] ?: v.volume) } }
            pushVolumes()
        }
    }

    fun setEnabled(kind: AmbienceKind, enabled: Boolean) {
        _layers.update { it + (kind to (it[kind] ?: AmbienceLayerState()).copy(enabled = enabled)) }
        pushVolumes()
        onLayersChanged()
    }

    fun setVolume(kind: AmbienceKind, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _layers.update { it + (kind to (it[kind] ?: AmbienceLayerState()).copy(volume = v)) }
        pushVolumes()
        saveJobs[kind]?.cancel()
        saveJobs[kind] = scope.launch {
            delay(500)
            runCatching { preferences.saveAmbienceVolume(kind, v) }
        }
    }

    fun turnAllOff() {
        _layers.update { m -> m.mapValues { it.value.copy(enabled = false) } }
        pushVolumes()
        onLayersChanged()
    }

    val anyEnabled: Boolean get() = _layers.value.values.any { it.enabled }

    private fun pushVolumes() {
        val m = mixer ?: return
        for ((kind, state) in _layers.value) m.setVolume(kind, if (state.enabled) state.volume else 0f)
    }

    private fun onLayersChanged() {
        if (anyEnabled) {
            if (observeJob == null) {
                val holder = runCatching { playbackStateHolder.get() }.getOrNull()
                val musicPlaying = holder?.stablePlayerState?.map { it.isPlaying }
                    ?: MutableStateFlow(false)
                val radioPlaying = lofiController.state.map { it.source == LofiSource.Radio && it.isPlaying }
                observeJob = scope.launch {
                    combine(musicPlaying, radioPlaying) { a, b -> a || b }
                        .distinctUntilChanged()
                        .collect { setSounding(it) }
                }
            }
        } else {
            observeJob?.cancel()
            observeJob = null
            setSounding(false)
        }
    }

    private fun setSounding(on: Boolean) {
        if (on) {
            val m = mixer ?: AmbienceMixer(AudioOut.SAMPLE_RATE).also { mixer = it }
            pushVolumes()
            m.fadeIn()
            if (out?.isRunning != true) startOut(m)
            _isSounding.value = true
        } else {
            mixer?.fadeOut()
            _isSounding.value = false
        }
    }

    private fun startOut(m: AmbienceMixer) {
        val o = AudioOut(m, "ambience", onFinished = {
            scope.launch { if (_isSounding.value && out?.isRunning != true) startOut(m) }
        })
        out = o
        o.start()
    }
}
