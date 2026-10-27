package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.preferences.AiPreferencesRepository
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Helper classes for consolidated combine() collectors to reduce coroutine overhead
private sealed interface SettingsUiUpdate {
    data class Group1(
        val appRebrandDialogShown: Boolean,
        val appThemeMode: String,
        val playerThemePreference: String,
        val albumArtPaletteStyle: AlbumArtPaletteStyle,
        val albumArtColorAccuracy: Int,
        val mockGenresEnabled: Boolean,
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val navBarCompactMode: Boolean,
        val libraryNavigationMode: String,
        val carouselStyle: String,
        val launchTab: String,
        val showPlayerFileInfo: Boolean
    ) : SettingsUiUpdate
    
    data class Group2(
        val keepPlayingInBackground: Boolean,
        val disableCastAutoplay: Boolean,
        val resumeOnHeadsetReconnect: Boolean,
        val showQueueHistory: Boolean,
        val isCrossfadeEnabled: Boolean,
        val hiFiModeEnabled: Boolean,
        val crossfadeDuration: Int,
        val persistentShuffleEnabled: Boolean,
        val folderBackGestureNavigation: Boolean,
        val lyricsSourcePreference: LyricsSourcePreference,
        val autoScanLrcFiles: Boolean,
        val blockedDirectories: Set<String>,
        val hapticsEnabled: Boolean,
        val immersiveLyricsEnabled: Boolean,
        val immersiveLyricsTimeout: Long,
        val animatedLyricsBlurEnabled: Boolean,
        val animatedLyricsBlurStrength: Float
    ) : SettingsUiUpdate
}

/** Keeps [SettingsViewModel]'s ui state in sync with the stored preferences. */
internal fun launchSettingsUiStateCollectors(
    scope: CoroutineScope,
    uiState: MutableStateFlow<SettingsUiState>,
    userPreferencesRepository: UserPreferencesRepository,
    themePreferencesRepository: ThemePreferencesRepository,
    aiPreferencesRepository: AiPreferencesRepository,
    fileExplorerStateHolder: FileExplorerStateHolder
) {
    // Consolidated collectors using combine() to reduce coroutine overhead
    // Instead of 20 separate coroutines, we use 2 combined flows
    
    // Group 1: Core UI settings (theme, navigation, appearance)
    scope.launch {
        combine<Any?, SettingsUiUpdate.Group1>(
            userPreferencesRepository.appRebrandDialogShownFlow,
            themePreferencesRepository.appThemeModeFlow,
            themePreferencesRepository.playerThemePreferenceFlow,
            themePreferencesRepository.albumArtPaletteStyleFlow,
            themePreferencesRepository.albumArtColorAccuracyFlow,
            userPreferencesRepository.mockGenresEnabledFlow,
            userPreferencesRepository.navBarCornerRadiusFlow,
            userPreferencesRepository.navBarStyleFlow,
            userPreferencesRepository.navBarCompactModeFlow,
            userPreferencesRepository.libraryNavigationModeFlow,
            userPreferencesRepository.carouselStyleFlow,
            userPreferencesRepository.launchTabFlow,
            userPreferencesRepository.showPlayerFileInfoFlow
        ) { values ->
            SettingsUiUpdate.Group1(
                appRebrandDialogShown = values[0] as Boolean,
                appThemeMode = values[1] as String,
                playerThemePreference = values[2] as String,
                albumArtPaletteStyle = values[3] as AlbumArtPaletteStyle,
                albumArtColorAccuracy = values[4] as Int,
                mockGenresEnabled = values[5] as Boolean,
                navBarCornerRadius = values[6] as Int,
                navBarStyle = values[7] as String,
                navBarCompactMode = values[8] as Boolean,
                libraryNavigationMode = values[9] as String,
                carouselStyle = values[10] as String,
                launchTab = values[11] as String,
                showPlayerFileInfo = values[12] as Boolean
            )
        }.collect { update ->
            uiState.update { state ->
                state.copy(
                    appRebrandDialogShown = update.appRebrandDialogShown,
                    appThemeMode = update.appThemeMode,
                    playerThemePreference = update.playerThemePreference,
                    albumArtPaletteStyle = update.albumArtPaletteStyle,
                    albumArtColorAccuracy = update.albumArtColorAccuracy,
                    mockGenresEnabled = update.mockGenresEnabled,
                    navBarCornerRadius = update.navBarCornerRadius,
                    navBarStyle = update.navBarStyle,
                    navBarCompactMode = update.navBarCompactMode,
                    libraryNavigationMode = update.libraryNavigationMode,
                    carouselStyle = update.carouselStyle,
                    launchTab = update.launchTab,
                    showPlayerFileInfo = update.showPlayerFileInfo
                )
            }
        }
    }
    
    // Group 2: Playback and system settings
    scope.launch {
        combine<Any?, SettingsUiUpdate.Group2>(
            userPreferencesRepository.keepPlayingInBackgroundFlow,
            userPreferencesRepository.disableCastAutoplayFlow,
            userPreferencesRepository.resumeOnHeadsetReconnectFlow,
            userPreferencesRepository.showQueueHistoryFlow,
            userPreferencesRepository.isCrossfadeEnabledFlow,
            userPreferencesRepository.hiFiModeEnabledFlow,
            userPreferencesRepository.crossfadeDurationFlow,
            userPreferencesRepository.persistentShuffleEnabledFlow,
            userPreferencesRepository.folderBackGestureNavigationFlow,
            userPreferencesRepository.lyricsSourcePreferenceFlow,
            userPreferencesRepository.autoScanLrcFilesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.hapticsEnabledFlow,
            userPreferencesRepository.immersiveLyricsEnabledFlow,
            userPreferencesRepository.immersiveLyricsTimeoutFlow,
            userPreferencesRepository.animatedLyricsBlurEnabledFlow,
            userPreferencesRepository.animatedLyricsBlurStrengthFlow
        ) { values ->
            SettingsUiUpdate.Group2(
                keepPlayingInBackground = values[0] as Boolean,
                disableCastAutoplay = values[1] as Boolean,
                resumeOnHeadsetReconnect = values[2] as Boolean,
                showQueueHistory = values[3] as Boolean,
                isCrossfadeEnabled = values[4] as Boolean,
                hiFiModeEnabled = values[5] as Boolean,
                crossfadeDuration = values[6] as Int,
                persistentShuffleEnabled = values[7] as Boolean,
                folderBackGestureNavigation = values[8] as Boolean,
                lyricsSourcePreference = values[9] as LyricsSourcePreference,
                autoScanLrcFiles = values[10] as Boolean,
                blockedDirectories = @Suppress("UNCHECKED_CAST") (values[11] as Set<String>),
                hapticsEnabled = values[12] as Boolean,
                immersiveLyricsEnabled = values[13] as Boolean,
                immersiveLyricsTimeout = values[14] as Long,
                animatedLyricsBlurEnabled = values[15] as Boolean,
                animatedLyricsBlurStrength = values[16] as Float
            )
        }.collect { update ->
            uiState.update { state ->
                state.copy(
                    keepPlayingInBackground = update.keepPlayingInBackground,
                    disableCastAutoplay = update.disableCastAutoplay,
                    resumeOnHeadsetReconnect = update.resumeOnHeadsetReconnect,
                    showQueueHistory = update.showQueueHistory,
                    isCrossfadeEnabled = update.isCrossfadeEnabled,
                    hiFiModeEnabled = update.hiFiModeEnabled,
                    crossfadeDuration = update.crossfadeDuration,
                    persistentShuffleEnabled = update.persistentShuffleEnabled,
                    folderBackGestureNavigation = update.folderBackGestureNavigation,
                    lyricsSourcePreference = update.lyricsSourcePreference,
                    autoScanLrcFiles = update.autoScanLrcFiles,
                    blockedDirectories = update.blockedDirectories,
                    hapticsEnabled = update.hapticsEnabled,
                    immersiveLyricsEnabled = update.immersiveLyricsEnabled,
                    immersiveLyricsTimeout = update.immersiveLyricsTimeout,
                    animatedLyricsBlurEnabled = update.animatedLyricsBlurEnabled,
                    animatedLyricsBlurStrength = update.animatedLyricsBlurStrength
                )
            }
        }
    }
    
    // Group 3: Remaining individual collectors (loading state, tweaks)
    scope.launch {
        userPreferencesRepository.fullPlayerLoadingTweaksFlow.collect { tweaks ->
            uiState.update { it.copy(fullPlayerLoadingTweaks = tweaks) }
        }
    }

    scope.launch {
        userPreferencesRepository.useAnimatedLyricsFlow.collect { enabled ->
            uiState.update { it.copy(useAnimatedLyrics = enabled) }
        }
    }

    scope.launch {
        userPreferencesRepository.backupInfoDismissedFlow.collect { dismissed ->
            uiState.update { it.copy(backupInfoDismissed = dismissed) }
        }
    }

    scope.launch {
        userPreferencesRepository.beta05CleanInstallDisclaimerDismissedFlow.collect { dismissed ->
            uiState.update { it.copy(beta05CleanInstallDisclaimerDismissed = dismissed) }
        }
    }

    scope.launch {
        fileExplorerStateHolder.isLoading.collect { loading ->
            uiState.update { it.copy(isLoadingDirectories = loading) }
        }
    }

    // Beta Features Collectors
    scope.launch {
        userPreferencesRepository.albumArtQualityFlow.collect { quality ->
            uiState.update { it.copy(albumArtQuality = quality) }
        }
    }

    scope.launch {
        userPreferencesRepository.albumArtCacheLimitMbFlow.collect { limitMb ->
            uiState.update { it.copy(albumArtCacheLimitMb = limitMb) }
        }
    }

    scope.launch {
        userPreferencesRepository.tapBackgroundClosesPlayerFlow.collect { enabled ->
            uiState.update { it.copy(tapBackgroundClosesPlayer = enabled) }
        }
    }

    scope.launch {
        userPreferencesRepository.minSongDurationFlow.collect { duration ->
            uiState.update { it.copy(minSongDuration = duration) }
        }
    }

    scope.launch {
        userPreferencesRepository.minTracksPerAlbumFlow.collect { minTracks ->
            uiState.update { it.copy(minTracksPerAlbum = minTracks) }
        }
    }

    scope.launch {
        userPreferencesRepository.replayGainEnabledFlow.collect { enabled ->
            uiState.update { it.copy(replayGainEnabled = enabled) }
        }
    }

    scope.launch {
        userPreferencesRepository.replayGainUseAlbumGainFlow.collect { useAlbum ->
            uiState.update { it.copy(replayGainUseAlbumGain = useAlbum) }
        }
    }

    scope.launch {
        aiPreferencesRepository.isSafeTokenLimitEnabled.collect { enabled ->
            uiState.update { it.copy(isSafeTokenLimitEnabled = enabled) }
        }
    }
}
