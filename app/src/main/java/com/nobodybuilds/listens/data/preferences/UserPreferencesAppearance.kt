package com.nobodybuilds.listens.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository.Companion.DEFAULT_ALBUM_ART_CACHE_LIMIT_MB
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Player and navigation appearance settings and developer options behind [UserPreferencesRepository]. */
internal class UserPreferencesAppearance(
    private val dataStore: DataStore<Preferences>
) {

    val showPlayerFileInfoFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.FULL_PLAYER_SHOW_FILE_INFO] ?: true
            }

    suspend fun setShowPlayerFileInfo(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_SHOW_FILE_INFO] = show
        }
    }

    val fullPlayerLoadingTweaksFlow: Flow<FullPlayerLoadingTweaks> = dataStore.data
        .map { preferences ->
            val delayAlbum = preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_ALBUM] ?: true
            val delayMetadata = preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_METADATA] ?: true
            val delayProgress = preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] ?: true
            val delayControls = preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] ?: true

            val delayAll = delayAlbum && delayMetadata && delayProgress && delayControls

            FullPlayerLoadingTweaks(
                delayAll = delayAll,
                delayAlbumCarousel = delayAlbum,
                delaySongMetadata = delayMetadata,
                delayProgressBar = delayProgress,
                delayControls = delayControls,
                showPlaceholders = preferences[UserPreferencesKeys.FULL_PLAYER_PLACEHOLDERS] ?: true,
                transparentPlaceholders = preferences[UserPreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] ?: false,
                applyPlaceholdersOnClose = preferences[UserPreferencesKeys.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] ?: false,
                switchOnDragRelease = preferences[UserPreferencesKeys.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] ?: true,
                contentAppearThresholdPercent = preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] ?: 98,
                contentCloseThresholdPercent = preferences[UserPreferencesKeys.FULL_PLAYER_CLOSE_THRESHOLD] ?: 0
            )
        }

    val navBarCornerRadiusFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                sanitizeNavBarCornerRadius(preferences[UserPreferencesKeys.NAV_BAR_CORNER_RADIUS] ?: 32)
            }

    suspend fun setNavBarCornerRadius(radius: Int) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.NAV_BAR_CORNER_RADIUS] = sanitizeNavBarCornerRadius(radius)
        }
    }

    val navBarStyleFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.NAV_BAR_STYLE] ?: NavBarStyle.DEFAULT
            }

    suspend fun setNavBarStyle(style: String) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.NAV_BAR_STYLE] = style }
    }

    val navBarCompactModeFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.NAV_BAR_COMPACT_MODE] ?: false
            }

    suspend fun setNavBarCompactMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.NAV_BAR_COMPACT_MODE] = enabled
        }
    }

    val libraryNavigationModeFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.LIBRARY_NAVIGATION_MODE]
                        ?: LibraryNavigationMode.TAB_ROW
            }

    suspend fun setLibraryNavigationMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.LIBRARY_NAVIGATION_MODE] = mode
        }
    }

    val carouselStyleFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.CAROUSEL_STYLE] ?: CarouselStyle.NO_PEEK
            }

    suspend fun setCarouselStyle(style: String) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.CAROUSEL_STYLE] = style }
    }

    val launchTabFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.LAUNCH_TAB] ?: LaunchTab.HOME
            }

    suspend fun setLaunchTab(tab: String) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.LAUNCH_TAB] = tab }
    }

    suspend fun setDelayAllFullPlayerContent(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_ALL] = enabled

            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_ALBUM] = enabled
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_METADATA] = enabled
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = enabled
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = enabled
        }
    }

    suspend fun setDelayAlbumCarousel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_ALBUM] = enabled
        }
    }

    suspend fun setDelaySongMetadata(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_METADATA] = enabled
        }
    }

    suspend fun setDelayProgressBar(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = enabled
        }
    }

    suspend fun setDelayControls(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = enabled
        }
    }

    suspend fun setFullPlayerPlaceholders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_PLACEHOLDERS] = enabled
            if (!enabled) {
                preferences[UserPreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = false
            }
        }
    }

    suspend fun setTransparentPlaceholders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = enabled
        }
    }

    suspend fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] = enabled
        }
    }

    suspend fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] = enabled
        }
    }

    suspend fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        val coercedValue = thresholdPercent.coerceIn(0, 100)
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] = coercedValue
        }
    }

    suspend fun setFullPlayerCloseThreshold(thresholdPercent: Int) {
        val coercedValue = thresholdPercent.coerceIn(0, 100)
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FULL_PLAYER_CLOSE_THRESHOLD] = coercedValue
        }
    }

    suspend fun clearDeprecatedPlayerSheetPreference() {
        dataStore.edit { preferences ->
            preferences.remove(UserPreferencesKeys.USE_PLAYER_SHEET_V2)
        }
    }

    val useSmoothCornersFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.USE_SMOOTH_CORNERS] ?: false
        }

    suspend fun setUseSmoothCorners(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.USE_SMOOTH_CORNERS] = enabled
        }
    }

    // ===== Developer Options =====

    /**
     * Album art quality for player view.
     * Controls the maximum resolution for album artwork displayed in the full player.
     * Thumbnails in lists always use low resolution (256px) for optimal performance.
     */
    val albumArtQualityFlow: Flow<AlbumArtQuality> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.ALBUM_ART_QUALITY]
                ?.let {
                    try { AlbumArtQuality.valueOf(it) }
                    catch (e: Exception) { AlbumArtQuality.MEDIUM }
                }
                ?: AlbumArtQuality.MEDIUM
        }

    suspend fun setAlbumArtQuality(quality: AlbumArtQuality) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ALBUM_ART_QUALITY] = quality.name
        }
    }

    val albumArtCacheLimitMbFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.ALBUM_ART_CACHE_LIMIT_MB] ?: DEFAULT_ALBUM_ART_CACHE_LIMIT_MB
        }

    suspend fun setAlbumArtCacheLimitMb(limitMb: Int) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ALBUM_ART_CACHE_LIMIT_MB] = limitMb.coerceIn(50, 1500)
        }
    }

    /**
     * Whether tapping the background area of the player sheet closes it.
     * Default is false to avoid accidental dismissals while interacting with the full player.
     */
    val tapBackgroundClosesPlayerFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] ?: false
        }

    suspend fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] = enabled
        }
    }

    val hapticsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.HAPTICS_ENABLED] ?: true
        }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.HAPTICS_ENABLED] = enabled
        }
    }

    // --- Collage Pattern ---

    val collagePatternFlow: Flow<CollagePattern> =
        dataStore.data.map { preferences ->
            CollagePattern.fromStorageKey(preferences[UserPreferencesKeys.COLLAGE_PATTERN])
        }

    suspend fun setCollagePattern(pattern: CollagePattern) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.COLLAGE_PATTERN] = pattern.storageKey
        }
    }

    val collageAutoRotateFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.COLLAGE_AUTO_ROTATE] ?: false
        }

    suspend fun setCollageAutoRotate(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.COLLAGE_AUTO_ROTATE] = enabled
        }
    }
}
