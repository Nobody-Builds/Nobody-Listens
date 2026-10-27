package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.preferences.AiPreferencesRepository
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.data.preferences.AlbumArtQuality
import com.nobodybuilds.listens.data.preferences.CarouselStyle
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.data.preferences.LibraryNavigationMode
import com.nobodybuilds.listens.data.preferences.NavBarStyle
import com.nobodybuilds.listens.data.preferences.ThemePreference
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/** Settings PlayerViewModel exposes to the UI, read from the preference repositories. */
internal class PlayerPreferenceFlows(
    private val scope: CoroutineScope,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository
) {
    val albumArtPaletteStyle: StateFlow<AlbumArtPaletteStyle> = themePreferencesRepository
        .albumArtPaletteStyleFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AlbumArtPaletteStyle.default
        )

    val playerThemePreference: StateFlow<String> = themePreferencesRepository.playerThemePreferenceFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreference.ALBUM_ART
        )

    val navBarCornerRadius: StateFlow<Int> = userPreferencesRepository.navBarCornerRadiusFlow.stateIn(scope, SharingStarted.WhileSubscribed(5000), 32)

    val navBarStyle: StateFlow<String> = userPreferencesRepository.navBarStyleFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = NavBarStyle.DEFAULT
        )

    val navBarCompactMode: StateFlow<Boolean> = userPreferencesRepository.navBarCompactModeFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val libraryNavigationMode: StateFlow<String> = userPreferencesRepository.libraryNavigationModeFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryNavigationMode.TAB_ROW
        )

    val carouselStyle: StateFlow<String> = userPreferencesRepository.carouselStyleFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CarouselStyle.NO_PEEK
        )

    val hasActiveAiProviderApiKey: StateFlow<Boolean> = combine(
        aiPreferencesRepository.aiProvider,
        aiPreferencesRepository.geminiApiKey,
        aiPreferencesRepository.deepseekApiKey,
        aiPreferencesRepository.groqApiKey,
        aiPreferencesRepository.mistralApiKey,
        aiPreferencesRepository.nvidiaApiKey,
        aiPreferencesRepository.kimiApiKey,
        aiPreferencesRepository.glmApiKey,
        aiPreferencesRepository.openaiApiKey
    ) { values ->
        val provider = values[0]
        val gemini = values[1]
        val deepseek = values[2]
        val groq = values[3]
        val mistral = values[4]
        val nvidia = values[5]
        val kimi = values[6]
        val glm = values[7]
        val openai = values[8]
        when (provider) {
            "DEEPSEEK" -> deepseek.isNotBlank()
            "GROQ" -> groq.isNotBlank()
            "MISTRAL" -> mistral.isNotBlank()
            "NVIDIA" -> nvidia.isNotBlank()
            "KIMI" -> kimi.isNotBlank()
            "GLM" -> glm.isNotBlank()
            "OPENAI" -> openai.isNotBlank()
            else -> gemini.isNotBlank()
        }
    }.distinctUntilChanged()
        .stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val hasGeminiApiKey: StateFlow<Boolean> = aiPreferencesRepository.geminiApiKey
        .map { it.isNotBlank() }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks> = userPreferencesRepository.fullPlayerLoadingTweaksFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FullPlayerLoadingTweaks()
        )

    val showPlayerFileInfo: StateFlow<Boolean> = userPreferencesRepository.showPlayerFileInfoFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * Whether tapping the background of the player sheet toggles its state.
     * When disabled, users must use gestures or buttons to expand/collapse.
     */
    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hapticsEnabled: StateFlow<Boolean> = userPreferencesRepository.hapticsEnabledFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Lyrics source preference (API_FIRST, EMBEDDED_FIRST, LOCAL_FIRST)
    val lyricsSourcePreference: StateFlow<LyricsSourcePreference> = userPreferencesRepository.lyricsSourcePreferenceFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = LyricsSourcePreference.EMBEDDED_FIRST
        )

    val immersiveLyricsEnabled: StateFlow<Boolean> = userPreferencesRepository.immersiveLyricsEnabledFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val immersiveLyricsTimeout: StateFlow<Long> = userPreferencesRepository.immersiveLyricsTimeoutFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4000L
        )

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.MEDIUM)

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Last Library Tab Index
    val lastLibraryTabIndexFlow: StateFlow<Int> =
        userPreferencesRepository.lastLibraryTabIndexFlow.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Default to Songs tab
        )

    val libraryTabsFlow: StateFlow<List<String>> = userPreferencesRepository.libraryTabsOrderFlow
        .map { orderJson ->
            if (orderJson != null) {
                try {
                    Json.decodeFromString<List<String>>(orderJson)
                } catch (e: Exception) {
                    listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
                }
            } else {
                listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED"))

    // Custom Genres Names
    val customGenres: StateFlow<Set<String>> = userPreferencesRepository.customGenresFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val customGenreIcons: StateFlow<Map<String, Int>> = userPreferencesRepository.customGenreIconsFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val isGenreGridView: StateFlow<Boolean> = userPreferencesRepository.isGenreGridViewFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
}
