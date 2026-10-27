package com.nobodybuilds.listens.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel

/** Settings > Developer: experiments, maintenance (Daily Mix, stats, palettes) and diagnostics. */
@Composable
internal fun SettingsDeveloperCategoryContent(
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    paletteRegenerateTargets: List<Song>,
    paletteRegenerateState: SettingsPaletteRegenerateState,
    onRegenerateDailyMixClick: () -> Unit,
    onRegenerateStatsClick: () -> Unit
) {
    with(paletteRegenerateState) {
        SettingsSubsection(title = stringResource(R.string.setcat_experiments)) {
            SettingsItem(
                title = stringResource(R.string.setcat_experimental_title),
                subtitle = stringResource(R.string.setcat_experimental_subtitle),
                leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.secondary) },
                trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { navController.navigateSafely(Screen.Experimental.route) }
            )
            SettingsItem(
                title = "Smart features (experimental)",
                subtitle = "On-device song radio, mood mixes and insights",
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary) },
                trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { navController.navigateSafely(Screen.SmartFeatures.route) }
            )
            SettingsItem(
                title = stringResource(R.string.setcat_test_setup_title),
                subtitle = stringResource(R.string.setcat_test_setup_subtitle),
                leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.tertiary) },
                onClick = {
                    settingsViewModel.resetSetupFlow()
                }
            )
        }

        SettingsSubsection(title = stringResource(R.string.setcat_maintenance)) {
            ActionSettingsItem(
                title = stringResource(R.string.setcat_force_daily_mix_title),
                subtitle = stringResource(R.string.setcat_force_daily_mix_subtitle),
                icon = { Icon(painterResource(R.drawable.rounded_instant_mix_24), null, tint = MaterialTheme.colorScheme.secondary) },
                primaryActionLabel = stringResource(R.string.setcat_regenerate_daily_mix_action),
                onPrimaryAction = onRegenerateDailyMixClick
            )
            ActionSettingsItem(
                title = stringResource(R.string.setcat_force_stats_title),
                subtitle = stringResource(R.string.setcat_force_stats_subtitle),
                icon = { Icon(painterResource(R.drawable.rounded_monitoring_24), null, tint = MaterialTheme.colorScheme.secondary) },
                primaryActionLabel = stringResource(R.string.setcat_regenerate_stats_action),
                onPrimaryAction = onRegenerateStatsClick
            )
            ActionSettingsItem(
                title = stringResource(R.string.setcat_force_palette_title),
                subtitle = if (paletteRegenerateTargets.isEmpty()) {
                    stringResource(R.string.setcat_force_palette_empty)
                } else {
                    stringResource(R.string.setcat_force_palette_subtitle)
                },
                icon = { Icon(Icons.Rounded.Style, null, tint = MaterialTheme.colorScheme.secondary) },
                primaryActionLabel = if (isPaletteBulkRegenerateRunning) stringResource(R.string.setcat_regenerating) else stringResource(R.string.setcat_regenerate_all),
                onPrimaryAction = { showRegenerateAllPalettesDialog = true },
                secondaryActionLabel = stringResource(R.string.setcat_choose_song),
                onSecondaryAction = { showPaletteRegenerateSheet = true },
                enabled = paletteRegenerateTargets.isNotEmpty() && !isAnyPaletteRegenerateRunning
            )
        }

        SettingsSubsection(
            title = stringResource(R.string.setcat_diagnostics),
            addBottomSpace = false
        ) {
            SettingsItem(
                title = stringResource(R.string.setcat_trigger_crash_title),
                subtitle = stringResource(R.string.setcat_trigger_crash_subtitle),
                leadingIcon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { settingsViewModel.triggerTestCrash() }
            )
        }
    }
}
