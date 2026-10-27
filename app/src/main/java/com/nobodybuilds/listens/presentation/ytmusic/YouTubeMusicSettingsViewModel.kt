package com.nobodybuilds.listens.presentation.ytmusic

import androidx.lifecycle.ViewModel
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicAudioQuality
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Backs the YouTube Music rows in Settings › Playback. */
@HiltViewModel
class YouTubeMusicSettingsViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
) : ViewModel() {
    val streamQuality: StateFlow<YouTubeMusicAudioQuality> = repository.streamQuality
    val downloadQuality: StateFlow<YouTubeMusicAudioQuality> = repository.downloadQuality
    val searchSuggestionsEnabled: StateFlow<Boolean> = repository.searchSuggestionsEnabled

    fun setStreamQuality(quality: YouTubeMusicAudioQuality) = repository.setStreamQuality(quality)

    fun setDownloadQuality(quality: YouTubeMusicAudioQuality) = repository.setDownloadQuality(quality)

    fun setSearchSuggestionsEnabled(enabled: Boolean) = repository.setSearchSuggestionsEnabled(enabled)
}
