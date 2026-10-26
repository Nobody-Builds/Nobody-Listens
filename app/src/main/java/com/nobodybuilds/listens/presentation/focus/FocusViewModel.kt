package com.nobodybuilds.listens.presentation.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.focus.FocusConfig
import com.nobodybuilds.listens.data.focus.FocusController
import com.nobodybuilds.listens.data.focus.FocusMusicSource
import com.nobodybuilds.listens.data.focus.FocusRepository
import com.nobodybuilds.listens.data.focus.FocusTimerState
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.smart.SmartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One bar of the 7-day chart. */
data class FocusDay(val date: LocalDate, val minutes: Int)

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val controller: FocusController,
    private val repository: FocusRepository,
    smartRepository: SmartRepository,
) : ViewModel() {

    init {
        controller.ensureRestored()
    }

    val timer: StateFlow<FocusTimerState> = controller.state
    val sessionMusic: StateFlow<FocusMusicSource> = controller.music
    val notice: StateFlow<String?> = controller.notice
    val exactAlarmNeeded: StateFlow<Boolean> = controller.exactAlarmNeeded

    val config: StateFlow<FocusConfig> =
        repository.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusConfig())

    val smartEnabled: StateFlow<Boolean> =
        smartRepository.isEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The chosen source; the Focus mix falls back to Instrumental when Smart features are off. */
    val musicSource: StateFlow<FocusMusicSource> =
        combine(repository.musicSource, smartRepository.isEnabled) { source, smart ->
            if (source == FocusMusicSource.Smart && !smart) FocusMusicSource.Library else source
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusMusicSource.Lofi)

    val todayMinutes: StateFlow<Int> =
        repository.todayMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayRounds: StateFlow<Int> = repository.history
        .map { list -> val today = LocalDate.now().toString(); list.count { it.date == today } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val week: StateFlow<List<FocusDay>> = repository.minutesByDay
        .map { byDay ->
            val today = LocalDate.now()
            (6 downTo 0).map { back -> today.minusDays(back.toLong()).let { FocusDay(it, byDay[it] ?: 0) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateConfig(transform: (FocusConfig) -> FocusConfig) {
        val next = transform(config.value).normalized()
        viewModelScope.launch { repository.setConfig(next) }
    }

    fun setMusicSource(source: FocusMusicSource) {
        viewModelScope.launch { repository.setMusicSource(source) }
    }

    fun start() = controller.start(config.value, musicSource.value)
    fun togglePause() = controller.togglePause()
    fun skip() = controller.skip()
    fun stop() = controller.stop()
    fun reset() = controller.reset()
    fun dismissNotice() = controller.dismissNotice()
    fun dismissExactAlarmNotice() = controller.dismissExactAlarmNotice()

    /** The screen registers the main player while it is shown. */
    fun attachSongPlayer(player: ((List<Song>) -> Unit)?) {
        controller.songPlayer = player
    }
}
