package com.nobodybuilds.listens.presentation.noise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.noise.BinauralMode
import com.nobodybuilds.listens.data.noise.NoiseController
import com.nobodybuilds.listens.data.noise.NoiseDefaults
import com.nobodybuilds.listens.data.noise.NoiseFocusOptions
import com.nobodybuilds.listens.data.noise.NoiseKind
import com.nobodybuilds.listens.data.noise.NoisePlayState
import com.nobodybuilds.listens.data.noise.NoisePreset
import com.nobodybuilds.listens.data.noise.NoiseSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NoiseViewModel @Inject constructor(
    private val controller: NoiseController,
) : ViewModel() {

    init {
        controller.ensureLoaded()
    }

    val settings: StateFlow<NoiseSettings> = controller.settings
    val state: StateFlow<NoisePlayState> = controller.state
    val focusOptions: StateFlow<NoiseFocusOptions> = controller.focusOptions
    val presets: StateFlow<List<NoisePreset>> =
        controller.presets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoiseDefaults.all)

    fun togglePlay() { controller.toggle() }
    fun stop() = controller.stop()
    fun toggleLayer(kind: NoiseKind) = controller.toggleLayer(kind)
    fun setLayerVolume(kind: NoiseKind, volume: Float) = controller.setLayerVolume(kind, volume)
    fun setTone(tone: Float) = controller.edit { it.copy(tone = tone) }
    fun setMaster(volume: Float) = controller.update { it.copy(masterVolume = volume) }
    fun setSwell(on: Boolean) = controller.edit { it.copy(swell = on) }
    fun setFadeIn(seconds: Int) = controller.update { it.copy(fadeInSeconds = seconds) }
    fun setUnderMusic(on: Boolean) = controller.update { it.copy(underMusic = on) }
    fun setBinaural(mode: BinauralMode) = controller.edit { it.copy(binaural = mode) }
    fun setCarrier(hz: Int) = controller.edit { it.copy(carrierHz = hz) }
    fun applyPreset(preset: NoisePreset) = controller.applyPreset(preset)
    fun savePreset(name: String) = controller.savePreset(name)
    fun deletePreset(name: String) = controller.deletePreset(name)
    fun setFocusOptions(options: NoiseFocusOptions) = controller.setFocusOptions(options)
}
