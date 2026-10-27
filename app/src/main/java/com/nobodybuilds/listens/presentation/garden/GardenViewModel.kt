package com.nobodybuilds.listens.presentation.garden

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.garden.GardenGrowth
import com.nobodybuilds.listens.data.garden.GardenRepository
import com.nobodybuilds.listens.data.garden.GardenSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GardenViewModel @Inject constructor(
    private val repository: GardenRepository,
) : ViewModel() {

    private val _snapshot = MutableStateFlow<GardenSnapshot?>(null)
    val snapshot: StateFlow<GardenSnapshot?> = _snapshot.asStateFlow()

    private val _flowerColors = MutableStateFlow(GardenColors.AccentFlowers)
    val flowerColors: StateFlow<List<Color>> = _flowerColors.asStateFlow()

    /** Bumps each time the plant grows a stage (also once on open if it grew while away). */
    private val _growKey = MutableStateFlow(0)
    val growKey: StateFlow<Int> = _growKey.asStateFlow()

    private val celebrations = Channel<List<Int>>(Channel.BUFFERED)
    /** Newly reached milestones; the screen plays the celebration haptic once per emission. */
    val celebrate: Flow<List<Int>> = celebrations.receiveAsFlow()

    init {
        viewModelScope.launch {
            _flowerColors.value = GardenColors.flowerColors(repository.recentMoodMix())
        }
        viewModelScope.launch {
            repository.snapshots.collect { s ->
                _snapshot.value = s
                onSnapshot(s)
            }
        }
    }

    private suspend fun onSnapshot(s: GardenSnapshot) {
        val prefs = repository.preferences
        val stage = s.growth.stage.ordinal
        val lastSeen = prefs.lastSeenStage()
        if (lastSeen in 0 until stage) _growKey.value += 1
        if (lastSeen != stage) prefs.setLastSeenStage(maxOf(stage, lastSeen))

        val fresh = GardenGrowth.newMilestones(s.growth.milestonesReached, prefs.shownMilestones.first())
        if (fresh.isNotEmpty()) {
            prefs.markMilestonesShown(fresh)
            celebrations.trySend(fresh)
            _growKey.value += 1
        }
    }
}
