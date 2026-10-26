package com.nobodybuilds.listens.presentation.screens

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.preferences.AppLanguage
import com.nobodybuilds.listens.data.preferences.AppThemeMode
import com.nobodybuilds.listens.data.preferences.CarouselStyle
import com.nobodybuilds.listens.data.preferences.CollagePattern
import com.nobodybuilds.listens.data.preferences.LaunchTab
import com.nobodybuilds.listens.data.preferences.LibraryNavigationMode
import com.nobodybuilds.listens.data.preferences.NavBarStyle
import com.nobodybuilds.listens.data.preferences.ThemePreference
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.viewmodel.SettingsUiState
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel

/** Settings > Appearance: theme, now playing, Home, navigation bar, screensaver and lyrics screen. */
@Composable
internal fun SettingsAppearanceCategoryContent(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()
    val appColorStyle by settingsViewModel.appColorStyle.collectAsStateWithLifecycle()
    val appPitchBlack by settingsViewModel.appPitchBlack.collectAsStateWithLifecycle()
    val nbHomeSettings: com.nobodybuilds.listens.presentation.nb.NbHomeSettingsViewModel = hiltViewModel()
    val showClassicHomeSections by nbHomeSettings.showClassicSections.collectAsStateWithLifecycle()

    SettingsSubsection(title = stringResource(R.string.setcat_global_theme)) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_language_label),
            description = stringResource(R.string.setcat_language_desc),
            options = AppLanguage.getLanguageOptions(context),
            selectedKey = uiState.appLanguageTag,
            onSelectionChanged = {
                settingsViewModel.setAppLanguage(it)
                (context as? Activity)?.recreate()
            },
            leadingIcon = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_app_theme_label),
            description = stringResource(R.string.setcat_app_theme_desc),
            options = mapOf(
                AppThemeMode.LIGHT to stringResource(R.string.setcat_theme_light),
                AppThemeMode.DARK to stringResource(R.string.setcat_theme_dark),
                AppThemeMode.FOLLOW_SYSTEM to stringResource(R.string.setcat_theme_follow_system)
            ),
            selectedKey = uiState.appThemeMode,
            onSelectionChanged = { settingsViewModel.setAppThemeMode(it) },
            leadingIcon = { Icon(Icons.Rounded.LightMode, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        if (uiState.appThemeMode != AppThemeMode.LIGHT) {
            SwitchSettingItem(
                title = stringResource(R.string.nobody_listens_pitch_black_title),
                subtitle = stringResource(R.string.nobody_listens_pitch_black_subtitle),
                checked = appPitchBlack,
                onCheckedChange = settingsViewModel::setAppPitchBlack,
                leadingIcon = { Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.secondary) }
            )
        }
        ThemeSelectorItem(
            label = stringResource(R.string.nobody_listens_color_style_label),
            description = stringResource(R.string.nobody_listens_color_style_desc),
            options = mapOf(
                com.nobodybuilds.listens.data.preferences.AppColorStyle.DAYLIGHT to stringResource(R.string.nobody_listens_color_style_daylight),
                com.nobodybuilds.listens.data.preferences.AppColorStyle.MATERIAL_YOU to stringResource(R.string.nobody_listens_color_style_material_you)
            ),
            selectedKey = appColorStyle,
            onSelectionChanged = { settingsViewModel.setAppColorStyle(it) },
            leadingIcon = { Icon(Icons.Rounded.Style, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.setcat_smooth_corners_title),
            subtitle = stringResource(R.string.setcat_smooth_corners_subtitle),
            checked = useSmoothCorners,
            onCheckedChange = settingsViewModel::setUseSmoothCorners,
            leadingIcon = { Icon(painterResource(R.drawable.rounded_rounded_corner_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.setcat_now_playing)) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_player_theme_label),
            description = stringResource(R.string.setcat_player_theme_desc),
            options = mapOf(
                ThemePreference.ALBUM_ART to stringResource(R.string.setcat_player_theme_album_art),
                ThemePreference.DYNAMIC to stringResource(R.string.setcat_player_theme_dynamic)
            ),
            selectedKey = uiState.playerThemePreference,
            onSelectionChanged = { settingsViewModel.setPlayerThemePreference(it) },
            leadingIcon = { Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.setcat_show_player_file_info_title),
            subtitle = stringResource(R.string.setcat_show_player_file_info_subtitle),
            checked = uiState.showPlayerFileInfo,
            onCheckedChange = { settingsViewModel.setShowPlayerFileInfo(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_attach_file_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SettingsItem(
            title = stringResource(R.string.setcat_album_art_palette_title),
            subtitle = stringResource(R.string.setcat_album_art_palette_subtitle, uiState.albumArtPaletteStyle.label),
            leadingIcon = { Icon(Icons.Rounded.Style, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely(Screen.PaletteStyle.route) }
        )
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_carousel_style_label),
            description = stringResource(R.string.setcat_carousel_style_desc),
            options = mapOf(
                CarouselStyle.NO_PEEK to stringResource(R.string.setcat_carousel_no_peek),
                CarouselStyle.ONE_PEEK to stringResource(R.string.setcat_carousel_one_peek)
            ),
            selectedKey = uiState.carouselStyle,
            onSelectionChanged = { settingsViewModel.setCarouselStyle(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_view_carousel_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        val livingArtworkSettings: com.nobodybuilds.listens.presentation.components.player.LivingArtworkViewModel = hiltViewModel()
        val livingArtworkEnabled by livingArtworkSettings.enabled.collectAsStateWithLifecycle()
        SwitchSettingItem(
            title = "Living artwork",
            subtitle = "The cover breathes gently in time with the song",
            checked = livingArtworkEnabled,
            onCheckedChange = { livingArtworkSettings.setEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_album_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.setcat_home_collage)) {
        SwitchSettingItem(
            title = "Show classic Home sections",
            subtitle = "Album collage and Daily Mix below Your mix",
            checked = showClassicHomeSections,
            onCheckedChange = { nbHomeSettings.setShowClassicSections(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_view_column_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_collage_pattern_label),
            description = stringResource(R.string.setcat_collage_pattern_desc),
            options = CollagePattern.entries.associate { it.storageKey to it.label },
            selectedKey = uiState.collagePattern.storageKey,
            onSelectionChanged = { key ->
                settingsViewModel.setCollagePattern(CollagePattern.fromStorageKey(key))
            },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_view_column_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.setcat_auto_rotate_patterns_title),
            subtitle = stringResource(R.string.setcat_auto_rotate_patterns_subtitle),
            checked = uiState.collageAutoRotate,
            onCheckedChange = { settingsViewModel.setCollageAutoRotate(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_shuffle_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.setcat_navigation_bar)) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_navbar_style_label),
            description = stringResource(R.string.setcat_navbar_style_desc),
            options = mapOf(
                NavBarStyle.DEFAULT to stringResource(R.string.setcat_navbar_style_default),
                NavBarStyle.FULL_WIDTH to stringResource(R.string.setcat_navbar_style_full_width)
            ),
            selectedKey = uiState.navBarStyle,
            onSelectionChanged = { settingsViewModel.setNavBarStyle(it) },
            leadingIcon = { Icon(Icons.Rounded.Style, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.setcat_compact_mode_title),
            subtitle = stringResource(R.string.setcat_compact_mode_subtitle),
            checked = uiState.navBarCompactMode,
            onCheckedChange = { settingsViewModel.setNavBarCompactMode(it) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.rounded_view_week_24),
                    null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        )
        SettingsItem(
            title = stringResource(R.string.setcat_navbar_corner_title),
            subtitle = stringResource(R.string.setcat_navbar_corner_subtitle),
            leadingIcon = { Icon(painterResource(R.drawable.rounded_rounded_corner_24), null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely("nav_bar_corner_radius") }
        )
    }

    SettingsSubsection(title = "Screensaver") {
        com.nobodybuilds.listens.presentation.screensaver.ScreensaverSettingsSection()
    }

    SettingsSubsection(title = stringResource(R.string.setcat_lyrics_screen)) {
        SwitchSettingItem(
            title = stringResource(R.string.setcat_immersive_lyrics_title),
            subtitle = stringResource(R.string.setcat_immersive_lyrics_subtitle),
            checked = uiState.immersiveLyricsEnabled,
            onCheckedChange = { settingsViewModel.setImmersiveLyricsEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )

        if (uiState.immersiveLyricsEnabled) {
            ThemeSelectorItem(
                label = stringResource(R.string.setcat_auto_hide_delay_label),
                description = stringResource(R.string.setcat_auto_hide_delay_desc),
                options = mapOf(
                    "3000" to stringResource(R.string.setcat_lyrics_delay_3s),
                    "4000" to stringResource(R.string.setcat_lyrics_delay_4s),
                    "5000" to stringResource(R.string.setcat_lyrics_delay_5s),
                    "6000" to stringResource(R.string.setcat_lyrics_delay_6s)
                ),
                selectedKey = uiState.immersiveLyricsTimeout.toString(),
                onSelectionChanged = { settingsViewModel.setImmersiveLyricsTimeout(it.toLong()) },
                leadingIcon = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.secondary) }
            )
        }
    }

    SettingsSubsection(
        title = stringResource(R.string.setcat_app_navigation_section),
        addBottomSpace = false
    ) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_default_tab_label),
            description = stringResource(R.string.setcat_default_tab_desc),
            options = mapOf(
                LaunchTab.HOME to stringResource(R.string.tab_home),
                LaunchTab.SEARCH to stringResource(R.string.search),
                LaunchTab.LIBRARY to stringResource(R.string.tab_library),
            ),
            selectedKey = uiState.launchTab,
            onSelectionChanged = { settingsViewModel.setLaunchTab(it) },
            leadingIcon = { Icon(painterResource(R.drawable.tab_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_library_navigation_label),
            description = stringResource(R.string.setcat_library_navigation_desc),
            options = mapOf(
                LibraryNavigationMode.TAB_ROW to stringResource(R.string.setcat_library_nav_tab_row),
                LibraryNavigationMode.COMPACT_PILL to stringResource(R.string.setcat_library_nav_compact_pill)
            ),
            selectedKey = uiState.libraryNavigationMode,
            onSelectionChanged = { settingsViewModel.setLibraryNavigationMode(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_library_music_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }
}
