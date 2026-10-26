package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.backup.BackupManager
import com.nobodybuilds.listens.data.backup.model.BackupSection
import com.nobodybuilds.listens.data.backup.model.BackupOperationType
import com.nobodybuilds.listens.data.backup.model.BackupTransferProgressUpdate
import com.nobodybuilds.listens.data.backup.model.BackupHistoryEntry
import com.nobodybuilds.listens.data.backup.model.RestorePlan
import com.nobodybuilds.listens.data.backup.model.RestoreResult
import com.nobodybuilds.listens.data.backup.model.ValidationError
import com.nobodybuilds.listens.data.preferences.AppThemeMode
import com.nobodybuilds.listens.data.preferences.CarouselStyle
import com.nobodybuilds.listens.data.preferences.LibraryNavigationMode
import com.nobodybuilds.listens.data.preferences.ThemePreference
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.database.AiUsageDao
import com.nobodybuilds.listens.data.database.AiUsageEntity
import com.nobodybuilds.listens.data.preferences.AiPreferencesRepository
import com.nobodybuilds.listens.data.preferences.AlbumArtQuality
import com.nobodybuilds.listens.data.preferences.AlbumArtColorAccuracy
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.data.preferences.AppLanguage
import com.nobodybuilds.listens.data.preferences.CollagePattern
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.repository.LyricsRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.worker.SyncManager
import com.nobodybuilds.listens.data.worker.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.preferences.NavBarStyle
import com.nobodybuilds.listens.data.ai.GeminiModel
import com.nobodybuilds.listens.data.ai.provider.AiClientFactory
import com.nobodybuilds.listens.data.ai.provider.AiProvider
import com.nobodybuilds.listens.data.preferences.LaunchTab
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.service.player.HiFiCapabilityChecker
import com.nobodybuilds.listens.utils.AppLocaleManager
import java.io.File

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val syncManager: SyncManager,
    private val aiClientFactory: AiClientFactory,
    private val aiUsageDao: AiUsageDao,
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository,
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // AI settings (provider, keys, models, prompts, usage) live in SettingsAiSettings.
    private val ai = SettingsAiSettings(aiPreferencesRepository, aiClientFactory, aiUsageDao, viewModelScope, _uiState, context)

    // Experimental full-player loading tweaks and animated lyrics live in SettingsFullPlayerTweaks.
    private val fullPlayerTweaks = SettingsFullPlayerTweaks(userPreferencesRepository, viewModelScope)

    val aiProvider: StateFlow<String> = ai.aiProvider
    val currentAiApiKey: StateFlow<String> = ai.currentAiApiKey
    val currentAiModel: StateFlow<String> = ai.currentAiModel
    val currentAiSystemPrompt: StateFlow<String> = ai.currentAiSystemPrompt

    // Specific Provider StateFlows for UI Compatibility
    val geminiApiKey: StateFlow<String> = ai.geminiApiKey
    val geminiModel: StateFlow<String> = ai.geminiModel
    val geminiSystemPrompt: StateFlow<String> = ai.geminiSystemPrompt
    val deepseekApiKey: StateFlow<String> = ai.deepseekApiKey
    val deepseekModel: StateFlow<String> = ai.deepseekModel
    val deepseekSystemPrompt: StateFlow<String> = ai.deepseekSystemPrompt
    val groqApiKey: StateFlow<String> = ai.groqApiKey
    val groqModel: StateFlow<String> = ai.groqModel
    val groqSystemPrompt: StateFlow<String> = ai.groqSystemPrompt
    val mistralApiKey: StateFlow<String> = ai.mistralApiKey
    val mistralModel: StateFlow<String> = ai.mistralModel
    val mistralSystemPrompt: StateFlow<String> = ai.mistralSystemPrompt
    val nvidiaApiKey: StateFlow<String> = ai.nvidiaApiKey
    val nvidiaModel: StateFlow<String> = ai.nvidiaModel
    val nvidiaSystemPrompt: StateFlow<String> = ai.nvidiaSystemPrompt
    val kimiApiKey: StateFlow<String> = ai.kimiApiKey
    val kimiModel: StateFlow<String> = ai.kimiModel
    val kimiSystemPrompt: StateFlow<String> = ai.kimiSystemPrompt
    val glmApiKey: StateFlow<String> = ai.glmApiKey
    val glmModel: StateFlow<String> = ai.glmModel
    val glmSystemPrompt: StateFlow<String> = ai.glmSystemPrompt
    val openaiApiKey: StateFlow<String> = ai.openaiApiKey
    val openaiModel: StateFlow<String> = ai.openaiModel
    val openaiSystemPrompt: StateFlow<String> = ai.openaiSystemPrompt
    val openrouterApiKey: StateFlow<String> = ai.openrouterApiKey
    val openrouterModel: StateFlow<String> = ai.openrouterModel
    val openrouterSystemPrompt: StateFlow<String> = ai.openrouterSystemPrompt

    fun onAiApiKeyChange(apiKey: String) = ai.onAiApiKeyChange(apiKey)
    fun onGeminiApiKeyChange(apiKey: String) = ai.onGeminiApiKeyChange(apiKey)
    fun onDeepseekApiKeyChange(apiKey: String) = ai.onDeepseekApiKeyChange(apiKey)
    fun onGroqApiKeyChange(apiKey: String) = ai.onGroqApiKeyChange(apiKey)
    fun onMistralApiKeyChange(apiKey: String) = ai.onMistralApiKeyChange(apiKey)
    fun onNvidiaApiKeyChange(apiKey: String) = ai.onNvidiaApiKeyChange(apiKey)
    fun onKimiApiKeyChange(apiKey: String) = ai.onKimiApiKeyChange(apiKey)
    fun onGlmApiKeyChange(apiKey: String) = ai.onGlmApiKeyChange(apiKey)
    fun onOpenAiApiKeyChange(apiKey: String) = ai.onOpenAiApiKeyChange(apiKey)
    fun onOpenrouterApiKeyChange(apiKey: String) = ai.onOpenrouterApiKeyChange(apiKey)
    fun onAiModelChange(model: String) = ai.onAiModelChange(model)
    fun onGeminiModelChange(model: String) = ai.onGeminiModelChange(model)
    fun onDeepseekModelChange(model: String) = ai.onDeepseekModelChange(model)
    fun onGroqModelChange(model: String) = ai.onGroqModelChange(model)
    fun onMistralModelChange(model: String) = ai.onMistralModelChange(model)
    fun onNvidiaModelChange(model: String) = ai.onNvidiaModelChange(model)
    fun onKimiModelChange(model: String) = ai.onKimiModelChange(model)
    fun onGlmModelChange(model: String) = ai.onGlmModelChange(model)
    fun onOpenAiModelChange(model: String) = ai.onOpenAiModelChange(model)
    fun onOpenrouterModelChange(model: String) = ai.onOpenrouterModelChange(model)
    fun onAiSystemPromptChange(prompt: String) = ai.onAiSystemPromptChange(prompt)
    fun onGeminiSystemPromptChange(prompt: String) = ai.onGeminiSystemPromptChange(prompt)
    fun onDeepseekSystemPromptChange(prompt: String) = ai.onDeepseekSystemPromptChange(prompt)
    fun onGroqSystemPromptChange(prompt: String) = ai.onGroqSystemPromptChange(prompt)
    fun onMistralSystemPromptChange(prompt: String) = ai.onMistralSystemPromptChange(prompt)
    fun onNvidiaSystemPromptChange(prompt: String) = ai.onNvidiaSystemPromptChange(prompt)
    fun onKimiSystemPromptChange(prompt: String) = ai.onKimiSystemPromptChange(prompt)
    fun onGlmSystemPromptChange(prompt: String) = ai.onGlmSystemPromptChange(prompt)
    fun onOpenAiSystemPromptChange(prompt: String) = ai.onOpenAiSystemPromptChange(prompt)
    fun onOpenrouterSystemPromptChange(prompt: String) = ai.onOpenrouterSystemPromptChange(prompt)
    fun resetAiSystemPrompt() = ai.resetAiSystemPrompt()
    fun resetGeminiSystemPrompt() = ai.resetGeminiSystemPrompt()
    fun resetDeepseekSystemPrompt() = ai.resetDeepseekSystemPrompt()
    fun resetGroqSystemPrompt() = ai.resetGroqSystemPrompt()
    fun resetMistralSystemPrompt() = ai.resetMistralSystemPrompt()
    fun resetNvidiaSystemPrompt() = ai.resetNvidiaSystemPrompt()
    fun resetKimiSystemPrompt() = ai.resetKimiSystemPrompt()
    fun resetGlmSystemPrompt() = ai.resetGlmSystemPrompt()
    fun resetOpenAiSystemPrompt() = ai.resetOpenAiSystemPrompt()
    fun resetOpenrouterSystemPrompt() = ai.resetOpenrouterSystemPrompt()

    fun clearAiUsageData() = ai.clearAiUsageData()
    val isSafeTokenLimitEnabled: StateFlow<Boolean> = ai.isSafeTokenLimitEnabled
    val recentAiUsage: StateFlow<List<AiUsageEntity>> = ai.recentAiUsage
    val totalPromptTokens: StateFlow<Int> = ai.totalPromptTokens
    val totalOutputTokens: StateFlow<Int> = ai.totalOutputTokens
    val totalThoughtTokens: StateFlow<Int> = ai.totalThoughtTokens

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading
    val isExplorerPriming = fileExplorerStateHolder.isPrimingExplorer
    val isExplorerReady = fileExplorerStateHolder.isExplorerReady
    val isCurrentDirectoryResolved = fileExplorerStateHolder.isCurrentDirectoryResolved
    private var hasPendingDirectoryRuleChanges = false
    private var latestDirectoryRuleUpdateJob: Job? = null

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val syncProgress: StateFlow<SyncProgress> = syncManager.syncProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncProgress()
        )

    // Backup export / inspect / restore live in SettingsBackupActions.
    private val backup = SettingsBackupActions(backupManager, syncManager, viewModelScope, _uiState, context)
    val dataTransferEvents: SharedFlow<String> = backup.dataTransferEvents

    init {
        viewModelScope.launch {
            backupManager.getBackupHistory().collect { history ->
                _uiState.update { it.copy(backupHistory = history) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.collagePatternFlow.collect { pattern ->
                _uiState.update { it.copy(collagePattern = pattern) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.collageAutoRotateFlow.collect { autoRotate ->
                _uiState.update { it.copy(collageAutoRotate = autoRotate) }
            }
        }
    }

    val dataTransferProgress: StateFlow<BackupTransferProgressUpdate?> = backup.dataTransferProgress

    init {
        // One-time device capability check — result is cached inside HiFiCapabilityChecker
        _uiState.update {
            it.copy(
                hiFiModeDeviceSupported = HiFiCapabilityChecker.isSupported(),
                appLanguageTag = AppLocaleManager.currentLanguageTag(context)
            )
        }

        // Preference collectors live in SettingsUiStateCollectors.kt
        launchSettingsUiStateCollectors(
            scope = viewModelScope,
            uiState = _uiState,
            userPreferencesRepository = userPreferencesRepository,
            themePreferencesRepository = themePreferencesRepository,
            aiPreferencesRepository = aiPreferencesRepository,
            fileExplorerStateHolder = fileExplorerStateHolder
        )
    }

    fun setAppRebrandDialogShown(wasShown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAppRebrandDialogShown(wasShown)
        }
    }

    fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBeta05CleanInstallDisclaimerDismissed(dismissed)
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        hasPendingDirectoryRuleChanges = true
        latestDirectoryRuleUpdateJob = viewModelScope.launch {
            fileExplorerStateHolder.toggleDirectoryAllowed(file)
        }
    }

    fun applyPendingDirectoryRuleChanges() {
        if (!hasPendingDirectoryRuleChanges) return
        hasPendingDirectoryRuleChanges = false
        viewModelScope.launch {
            latestDirectoryRuleUpdateJob?.join()
            syncManager.forceRefresh()
        }
    }

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun primeExplorer() {
        fileExplorerStateHolder.primeExplorerRoot()
    }

    fun openExplorer() {
        fileExplorerStateHolder.openExplorerRoot()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun refreshExplorer() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun selectStorage(index: Int) {
        fileExplorerStateHolder.selectStorage(index)
    }

    fun refreshAvailableStorages() {
        fileExplorerStateHolder.refreshAvailableStorages()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

    // Método para guardar la preferencia de tema del reproductor
    fun setPlayerThemePreference(preference: String) {
        viewModelScope.launch {
            themePreferencesRepository.setPlayerThemePreference(preference)
        }
    }

    fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) {
        viewModelScope.launch {
            themePreferencesRepository.setAlbumArtPaletteStyle(style)
        }
    }

    fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) {
        viewModelScope.launch {
            themePreferencesRepository.setAlbumArtPaletteSettings(style, accuracyLevel)
        }
    }

    suspend fun getAlbumArtPalettePreview(
        uriString: String,
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ): ColorSchemePair? {
        return colorSchemeProcessor.getPreviewColorScheme(
            albumArtUri = uriString,
            paletteStyle = style,
            colorAccuracyLevel = accuracyLevel
        )
    }

    fun setCollagePattern(pattern: CollagePattern) {
        viewModelScope.launch {
            userPreferencesRepository.setCollagePattern(pattern)
        }
    }

    fun setCollageAutoRotate(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCollageAutoRotate(enabled)
        }
    }

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferencesRepository.setAppThemeMode(mode)
        }
    }

    fun setAppLanguage(languageTag: String) {
        val normalized = AppLanguage.normalize(languageTag)
        AppLocaleManager.applyLanguage(context, normalized)
        _uiState.update { it.copy(appLanguageTag = normalized) }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setNavBarCompactMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCompactMode(enabled)
        }
    }

    fun setLibraryNavigationMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLibraryNavigationMode(mode)
        }
    }

    fun setCarouselStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setCarouselStyle(style)
        }
    }

    fun setShowPlayerFileInfo(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowPlayerFileInfo(show)
        }
    }

    fun setLaunchTab(tab: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLaunchTab(tab)
        }
    }

    fun setKeepPlayingInBackground(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepPlayingInBackground(enabled)
        }
    }

    fun setDisableCastAutoplay(disabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDisableCastAutoplay(disabled)
        }
    }

    fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setResumeOnHeadsetReconnect(enabled)
        }
    }

    fun setHiFiModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHiFiModeEnabled(enabled)
        }
    }

    fun setShowQueueHistory(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowQueueHistory(show)
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeEnabled(enabled)
        }
    }

    fun setCrossfadeDuration(duration: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeDuration(duration)
        }
    }

    fun setPersistentShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPersistentShuffleEnabled(enabled)
        }
    }

    fun setFolderBackGestureNavigation(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFolderBackGestureNavigation(enabled)
        }
    }

    fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        viewModelScope.launch {
            userPreferencesRepository.setLyricsSourcePreference(preference)
        }
    }

    fun setAutoScanLrcFiles(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoScanLrcFiles(enabled)
        }
    }

    // Experimental full-player loading tweaks and animated lyrics
    fun setDelayAllFullPlayerContent(enabled: Boolean) = fullPlayerTweaks.setDelayAllFullPlayerContent(enabled)
    fun setDelayAlbumCarousel(enabled: Boolean) = fullPlayerTweaks.setDelayAlbumCarousel(enabled)
    fun setDelaySongMetadata(enabled: Boolean) = fullPlayerTweaks.setDelaySongMetadata(enabled)
    fun setDelayProgressBar(enabled: Boolean) = fullPlayerTweaks.setDelayProgressBar(enabled)
    fun setDelayControls(enabled: Boolean) = fullPlayerTweaks.setDelayControls(enabled)
    fun setFullPlayerPlaceholders(enabled: Boolean) = fullPlayerTweaks.setFullPlayerPlaceholders(enabled)
    fun setTransparentPlaceholders(enabled: Boolean) = fullPlayerTweaks.setTransparentPlaceholders(enabled)
    fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) = fullPlayerTweaks.setFullPlayerPlaceholdersOnClose(enabled)
    fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) = fullPlayerTweaks.setFullPlayerSwitchOnDragRelease(enabled)
    fun setFullPlayerAppearThreshold(thresholdPercent: Int) = fullPlayerTweaks.setFullPlayerAppearThreshold(thresholdPercent)
    fun setFullPlayerCloseThreshold(thresholdPercent: Int) = fullPlayerTweaks.setFullPlayerCloseThreshold(thresholdPercent)
    fun setUseAnimatedLyrics(enabled: Boolean) = fullPlayerTweaks.setUseAnimatedLyrics(enabled)
    fun setAnimatedLyricsBlurEnabled(enabled: Boolean) = fullPlayerTweaks.setAnimatedLyricsBlurEnabled(enabled)
    fun setAnimatedLyricsBlurStrength(strength: Float) = fullPlayerTweaks.setAnimatedLyricsBlurStrength(strength)

    fun refreshLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.forceRefresh()
        }
    }

    fun setSafeTokenLimitEnabled(enabled: Boolean) = ai.setSafeTokenLimitEnabled(enabled)

    /**
     * Performs a full library rescan - rescans all files from scratch.
     * Use when songs are missing or metadata is incorrect.
     */
    fun fullSyncLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.fullSync()
        }
    }

    fun setMinSongDuration(durationMs: Int) {
        viewModelScope.launch {
            if (durationMs == _uiState.value.minSongDuration) return@launch
            userPreferencesRepository.setMinSongDuration(durationMs)
            // Trigger a library rescan so the change takes effect in the database
            syncManager.fullSync()
        }
    }

    fun setMinTracksPerAlbum(minTracks: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setMinTracksPerAlbum(minTracks)
        }
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setReplayGainEnabled(enabled)
        }
    }

    fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setReplayGainUseAlbumGain(useAlbumGain)
        }
    }

    fun setImmersiveLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveLyricsEnabled(enabled)
        }
    }

    fun setImmersiveLyricsTimeout(timeout: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveLyricsTimeout(timeout)
        }
    }

    /**
     * Completely rebuilds the database from scratch.
     * Clears all data including user edits (lyrics, favorites) and rescans.
     * Use when database is corrupted or as a last resort.
     */
    fun rebuildDatabase() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.rebuildDatabase()
        }
    }

    fun onAiProviderChange(provider: String) = ai.onAiProviderChange(provider)
    fun loadModelsForCurrentProvider() = ai.loadModelsForCurrentProvider()

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch { userPreferencesRepository.setNavBarCornerRadius(radius) }
    }
    /**
     * Triggers a test crash to verify the crash handler is working correctly.
     * This should only be used for testing in Developer Options.
     */
    fun triggerTestCrash() {
        throw RuntimeException(context.getString(R.string.dev_test_crash_message))
    }

    fun resetSetupFlow() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(false)
        }
    }

    // ===== Developer Options =====

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.MEDIUM)

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val appColorStyle: StateFlow<String> = themePreferencesRepository.appColorStyleFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.nobodybuilds.listens.data.preferences.AppColorStyle.DAYLIGHT
        )

    fun setAppColorStyle(style: String) {
        viewModelScope.launch { themePreferencesRepository.setAppColorStyle(style) }
    }

    val appPitchBlack: StateFlow<Boolean> = themePreferencesRepository.appPitchBlackFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAppPitchBlack(enabled: Boolean) {
        viewModelScope.launch { themePreferencesRepository.setAppPitchBlack(enabled) }
    }

    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAlbumArtQuality(quality: AlbumArtQuality) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumArtQuality(quality)
        }
    }

    fun setAlbumArtCacheLimitMb(limitMb: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumArtCacheLimitMb(limitMb)
            com.nobodybuilds.listens.utils.AlbumArtCacheManager.configuredCacheLimitMb = limitMb.toLong()
        }
    }

    fun setUseSmoothCorners(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseSmoothCorners(enabled)
        }
    }

    fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTapBackgroundClosesPlayer(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHapticsEnabled(enabled)
        }
    }

    fun setBackupInfoDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBackupInfoDismissed(dismissed)
        }
    }

    fun exportAppData(uri: Uri, sections: Set<BackupSection>) = backup.exportAppData(uri, sections)
    fun inspectBackupFile(uri: Uri) = backup.inspectBackupFile(uri)
    fun updateRestorePlanSelection(selectedModules: Set<BackupSection>) = backup.updateRestorePlanSelection(selectedModules)
    fun restoreFromPlan(uri: Uri) = backup.restoreFromPlan(uri)
    fun clearRestorePlan() = backup.clearRestorePlan()
    fun removeBackupHistoryEntry(entry: BackupHistoryEntry) = backup.removeBackupHistoryEntry(entry)
}
