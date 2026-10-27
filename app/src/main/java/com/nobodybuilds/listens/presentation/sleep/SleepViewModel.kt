package com.nobodybuilds.listens.presentation.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.sleep.SleepController
import com.nobodybuilds.listens.data.sleep.SleepSession
import com.nobodybuilds.listens.data.sleep.SleepSettings
import com.nobodybuilds.listens.data.sleep.SleepSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val controller: SleepController,
) : ViewModel() {

    init {
        controller.ensureLoaded()
    }

    val settings: StateFlow<SleepSettings> = controller.settings
    val notice: StateFlow<String?> = controller.notice
    val preparing: StateFlow<Boolean> = controller.preparing
    val session: StateFlow<SleepSession?> =
        controller.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSource(source: SleepSource) = controller.update { it.copy(source = source) }
    fun setMinutes(minutes: Int) = controller.update { it.copy(minutes = minutes) }
    fun setFadeMinutes(minutes: Int) = controller.update { it.copy(fadeMinutes = minutes) }
    fun start(songPlayer: ((List<Song>) -> Unit)?) = controller.start(songPlayer)
    fun extend(minutes: Int) = controller.extend(minutes, session.value?.endsAt)
    fun stop() = controller.stop()
    fun dismissNotice() = controller.dismissNotice()
}
