package com.nobodybuilds.listens.presentation.smart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.smart.IndexProgress
import com.nobodybuilds.listens.data.smart.SmartInsights
import com.nobodybuilds.listens.data.smart.SmartRepository
import com.nobodybuilds.listens.data.smart.SongSmartTags
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SmartSettingsViewModel @Inject constructor(
    private val repository: SmartRepository
) : ViewModel() {

    val isEnabled: StateFlow<Boolean> = repository.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val progress: StateFlow<IndexProgress> = repository.indexProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndexProgress())

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun deleteSmartData() {
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                repository.deleteSmartData()
            } finally {
                _isDeleting.value = false
            }
        }
    }
}

/** Song-level smart actions (tags + song radio) used by the song options sheet. */
@HiltViewModel
class SmartSongViewModel @Inject constructor(
    private val repository: SmartRepository
) : ViewModel() {

    val isEnabled: StateFlow<Boolean> = repository.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    suspend fun tagsFor(songId: String): SongSmartTags? = repository.tagsFor(songId)

    /** Seed song first, then similar songs. Empty when nothing similar is known. */
    suspend fun radioFor(seed: Song, limit: Int = 50): List<Song> {
        val ids = repository.similarSongs(seed.id, limit)
        if (ids.isEmpty()) return emptyList()
        return listOf(seed) + repository.songsFor(ids).filter { it.id != seed.id }
    }
}

@HiltViewModel
class SmartInsightsViewModel @Inject constructor(
    private val repository: SmartRepository
) : ViewModel() {

    val isEnabled: StateFlow<Boolean> = repository.isEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _insights = MutableStateFlow<SmartInsights?>(null)
    val insights: StateFlow<SmartInsights?> = _insights.asStateFlow()

    private var loadedRange: StatsTimeRange? = null

    fun load(range: StatsTimeRange, force: Boolean = false) {
        if (!force && range == loadedRange && _insights.value != null) return
        loadedRange = range
        viewModelScope.launch {
            _insights.value = runCatching { repository.insights(range) }.getOrNull()
        }
    }
}
