package com.nobodybuilds.listens.presentation.screens

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.viewmodel.SettingsUiState
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel

/** Settings > Behavior: folders, navigation, player gestures and haptics. */
@Composable
internal fun SettingsBehaviorCategoryContent(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel
) {
    SettingsSubsection(
        title = stringResource(R.string.setcat_folders)
    ) {
        SwitchSettingItem(
            title = stringResource(R.string.setcat_folder_back_gesture_title),
            subtitle = stringResource(R.string.setcat_folder_back_gesture_subtitle),
            checked = uiState.folderBackGestureNavigation,
            onCheckedChange = { settingsViewModel.setFolderBackGestureNavigation(it) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.rounded_touch_app_24),
                    null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        )
    }
    SettingsSubsection(title = "Navigation") {
        com.nobodybuilds.listens.presentation.navigation.SwipeBetweenPagesSetting()
    }
    SettingsSubsection(
        title = stringResource(R.string.setcat_player_gestures)
    ) {
        SwitchSettingItem(
            title = stringResource(R.string.setcat_tap_bg_closes_title),
            subtitle = stringResource(R.string.setcat_tap_bg_closes_subtitle),
            checked = uiState.tapBackgroundClosesPlayer,
            onCheckedChange = { settingsViewModel.setTapBackgroundClosesPlayer(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_touch_app_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }
    SettingsSubsection(title = "Gestures") {
        com.nobodybuilds.listens.presentation.gestures.GestureSettingsSection()
    }
    SettingsSubsection(
        title = stringResource(R.string.setcat_haptics),
        addBottomSpace = false
    ) {
        // Off / Subtle / Full + seek ticks (Off also clears the legacy haptics switch).
        com.nobodybuilds.listens.presentation.haptics.HapticsSettingsSection()
    }
}
