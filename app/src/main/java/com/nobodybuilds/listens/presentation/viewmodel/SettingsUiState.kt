package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.ai.GeminiModel
import com.nobodybuilds.listens.data.backup.model.BackupHistoryEntry
import com.nobodybuilds.listens.data.backup.model.RestorePlan
import com.nobodybuilds.listens.data.backup.model.ValidationError
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.preferences.AlbumArtColorAccuracy
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.data.preferences.AlbumArtQuality
import com.nobodybuilds.listens.data.preferences.AppLanguage
import com.nobodybuilds.listens.data.preferences.AppThemeMode
import com.nobodybuilds.listens.data.preferences.CarouselStyle
import com.nobodybuilds.listens.data.preferences.CollagePattern
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.data.preferences.LaunchTab
import com.nobodybuilds.listens.data.preferences.LibraryNavigationMode
import com.nobodybuilds.listens.data.preferences.NavBarStyle
import com.nobodybuilds.listens.data.preferences.ThemePreference

data class SettingsUiState(
    val isLoadingDirectories: Boolean = false,
    val appLanguageTag: String = AppLanguage.SYSTEM.tag,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val playerThemePreference: String = ThemePreference.ALBUM_ART,
    val albumArtPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default,
    val albumArtColorAccuracy: Int = AlbumArtColorAccuracy.DEFAULT,
    val mockGenresEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val navBarCompactMode: Boolean = false,
    val carouselStyle: String = CarouselStyle.NO_PEEK,
    val libraryNavigationMode: String = LibraryNavigationMode.TAB_ROW,
    val launchTab: String = LaunchTab.HOME,
    val keepPlayingInBackground: Boolean = true,
    val disableCastAutoplay: Boolean = false,
    val resumeOnHeadsetReconnect: Boolean = false,
    val showQueueHistory: Boolean = true,
    val isCrossfadeEnabled: Boolean = false,
    val hiFiModeEnabled: Boolean = false,
    val hiFiModeDeviceSupported: Boolean = true,
    val crossfadeDuration: Int = 2000,
    val persistentShuffleEnabled: Boolean = false,
    val folderBackGestureNavigation: Boolean = true,
    val lyricsSourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
    val autoScanLrcFiles: Boolean = false,
    val blockedDirectories: Set<String> = emptySet(),
    val availableModels: List<GeminiModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val appRebrandDialogShown: Boolean = false,
    val beta05CleanInstallDisclaimerDismissed: Boolean? = null,
    val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
    val showPlayerFileInfo: Boolean = true,
    // Developer Options
    val albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
    val albumArtCacheLimitMb: Int = 200,
    val tapBackgroundClosesPlayer: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val immersiveLyricsEnabled: Boolean = false,
    val immersiveLyricsTimeout: Long = 4000L,
    val useAnimatedLyrics: Boolean = false,
    val animatedLyricsBlurEnabled: Boolean = true,
    val animatedLyricsBlurStrength: Float = 2.5f,
    val backupInfoDismissed: Boolean = false,
    val isDataTransferInProgress: Boolean = false,
    val restorePlan: RestorePlan? = null,
    val backupHistory: List<BackupHistoryEntry> = emptyList(),
    val backupValidationErrors: List<ValidationError> = emptyList(),
    val isInspectingBackup: Boolean = false,
    val collagePattern: CollagePattern = CollagePattern.default,
    val collageAutoRotate: Boolean = false,
    val minSongDuration: Int = 10000,
    val minTracksPerAlbum: Int = 1,
    val replayGainEnabled: Boolean = false,
    val replayGainUseAlbumGain: Boolean = false,
    val isSafeTokenLimitEnabled: Boolean = true
)

data class FailedSongInfo(
    val id: String,
    val title: String,
    val artist: String
)

data class LyricsRefreshProgress(
    val totalSongs: Int = 0,
    val currentCount: Int = 0,
    val savedCount: Int = 0,
    val notFoundCount: Int = 0,
    val skippedCount: Int = 0,
    val isComplete: Boolean = false,
    val failedSongs: List<FailedSongInfo> = emptyList()
) {
    val hasProgress: Boolean get() = totalSongs > 0
    val progress: Float get() = if (totalSongs > 0) currentCount.toFloat() / totalSongs else 0f
    val hasFailedSongs: Boolean get() = failedSongs.isNotEmpty()
}
