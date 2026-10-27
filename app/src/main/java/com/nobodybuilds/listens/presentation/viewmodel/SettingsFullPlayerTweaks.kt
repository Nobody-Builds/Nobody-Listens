package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Experimental full-player loading and animated-lyrics setters behind [SettingsViewModel]. */
internal class SettingsFullPlayerTweaks(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope
) {
    fun setDelayAllFullPlayerContent(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setDelayAllFullPlayerContent(enabled)
        }
    }

    fun setDelayAlbumCarousel(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setDelayAlbumCarousel(enabled)
        }
    }

    fun setDelaySongMetadata(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setDelaySongMetadata(enabled)
        }
    }

    fun setDelayProgressBar(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setDelayProgressBar(enabled)
        }
    }

    fun setDelayControls(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setDelayControls(enabled)
        }
    }

    fun setFullPlayerPlaceholders(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setFullPlayerPlaceholders(enabled)
            if (!enabled) {
                userPreferencesRepository.setTransparentPlaceholders(false)
            }
        }
    }

    fun setTransparentPlaceholders(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setTransparentPlaceholders(enabled)
        }
    }

    fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setFullPlayerPlaceholdersOnClose(enabled)
        }
    }

    fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setFullPlayerSwitchOnDragRelease(enabled)
        }
    }

    fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        scope.launch {
            userPreferencesRepository.setFullPlayerAppearThreshold(thresholdPercent)
        }
    }

    fun setFullPlayerCloseThreshold(thresholdPercent: Int) {
        scope.launch {
            userPreferencesRepository.setFullPlayerCloseThreshold(thresholdPercent)
        }
    }

    fun setUseAnimatedLyrics(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setUseAnimatedLyrics(enabled)
        }
    }

    fun setAnimatedLyricsBlurEnabled(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setAnimatedLyricsBlurEnabled(enabled)
        }
    }

    fun setAnimatedLyricsBlurStrength(strength: Float) {
        scope.launch {
            userPreferencesRepository.setAnimatedLyricsBlurStrength(strength)
        }
    }
}
