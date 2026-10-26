package com.nobodybuilds.listens.presentation.lofi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.lofi.AmbienceKind
import com.nobodybuilds.listens.data.lofi.AmbienceLayerState
import com.nobodybuilds.listens.data.lofi.AmbientLayer
import com.nobodybuilds.listens.data.lofi.LofiController
import com.nobodybuilds.listens.data.lofi.LofiMood
import com.nobodybuilds.listens.data.lofi.LofiPreferences
import com.nobodybuilds.listens.data.lofi.LofiPreset
import com.nobodybuilds.listens.data.lofi.LofiRadioRepository
import com.nobodybuilds.listens.data.lofi.LofiSettings
import com.nobodybuilds.listens.data.lofi.LofiSource
import com.nobodybuilds.listens.data.lofi.LofiState
import com.nobodybuilds.listens.data.lofi.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadioBrowseState(
    val tag: String = "lofi",
    val loading: Boolean = false,
    val stations: List<RadioStation> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class LofiViewModel @Inject constructor(
    private val controller: LofiController,
    private val ambient: AmbientLayer,
    private val preferences: LofiPreferences,
    private val radio: LofiRadioRepository,
) : ViewModel() {

    init {
        controller.ensureLoaded()
        ambient.ensureLoaded()
    }

    val settings: StateFlow<LofiSettings> = controller.settings
    val state: StateFlow<LofiState> = controller.state
    val presets: StateFlow<List<LofiPreset>> =
        preferences.presets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favourites: StateFlow<List<RadioStation>> =
        preferences.favourites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ambienceLayers: StateFlow<Map<AmbienceKind, AmbienceLayerState>> = ambient.layers
    val ambienceSounding: StateFlow<Boolean> = ambient.isSounding

    val curatedStations: List<RadioStation> get() = radio.curated
    val suggestedTags: List<String> get() = radio.suggestedTags

    private val _browse = MutableStateFlow(RadioBrowseState())
    val browse: StateFlow<RadioBrowseState> = _browse.asStateFlow()
    private var browseJob: Job? = null

    // ---------------------------------------------------------------- generator
    fun toggleGenerator() {
        val st = state.value
        when {
            st.source == LofiSource.Generator && st.isPlaying -> controller.pause()
            st.source == LofiSource.Generator -> controller.resume()
            else -> controller.start()
        }
    }

    fun update(transform: (LofiSettings) -> LofiSettings) = controller.updateSettings(transform)
    fun setMood(mood: LofiMood) = controller.setMood(mood)
    fun newTune() {
        controller.newTune()
        val st = state.value
        if (st.source != LofiSource.Generator || !st.isPlaying) controller.start()
    }
    fun stop() = controller.stop()
    fun setSleepTimer(minutes: Int?) = controller.setSleepTimer(minutes)
    fun meters(): FloatArray? = controller.meters()

    fun savePreset(name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        viewModelScope.launch { preferences.savePreset(clean, settings.value) }
    }

    fun loadPreset(preset: LofiPreset) = controller.setSettings(preset.settings)
    fun deletePreset(preset: LofiPreset) { viewModelScope.launch { preferences.deletePreset(preset.name) } }

    // ---------------------------------------------------------------- radio
    fun ensureRadioLoaded() {
        if (!_browse.value.loaded && !_browse.value.loading) loadTag(_browse.value.tag)
    }

    fun loadTag(tag: String, force: Boolean = false) {
        val clean = tag.trim().lowercase().ifEmpty { return }
        browseJob?.cancel()
        _browse.value = _browse.value.copy(tag = clean, loading = true, error = null)
        browseJob = viewModelScope.launch {
            val result = radio.byTag(clean, forceRefresh = force)
            _browse.value = result.fold(
                onSuccess = { RadioBrowseState(clean, false, it, null, true) },
                onFailure = { RadioBrowseState(clean, false, emptyList(), "Couldn't reach the radio directory", true) }
            )
        }
    }

    fun playStation(station: RadioStation) {
        val st = state.value
        if (st.source == LofiSource.Radio && st.station?.id == station.id) {
            if (st.isPlaying) controller.pause() else controller.resume()
        } else {
            controller.playRadio(station)
        }
    }

    fun toggleRadioPlayback() = controller.togglePlayPause()

    fun toggleFavourite(station: RadioStation) {
        viewModelScope.launch { preferences.toggleFavourite(station) }
    }

    // ---------------------------------------------------------------- ambience
    fun setAmbienceEnabled(kind: AmbienceKind, enabled: Boolean) = ambient.setEnabled(kind, enabled)
    fun setAmbienceVolume(kind: AmbienceKind, volume: Float) = ambient.setVolume(kind, volume)
    fun ambienceAllOff() = ambient.turnAllOff()
}

/** Small view model for the ambience sheet opened from the song options sheet. */
@HiltViewModel
class AmbienceViewModel @Inject constructor(
    private val ambient: AmbientLayer,
) : ViewModel() {
    init { ambient.ensureLoaded() }

    val layers: StateFlow<Map<AmbienceKind, AmbienceLayerState>> = ambient.layers
    val sounding: StateFlow<Boolean> = ambient.isSounding

    fun setEnabled(kind: AmbienceKind, enabled: Boolean) = ambient.setEnabled(kind, enabled)
    fun setVolume(kind: AmbienceKind, volume: Float) = ambient.setVolume(kind, volume)
    fun allOff() = ambient.turnAllOff()
}
