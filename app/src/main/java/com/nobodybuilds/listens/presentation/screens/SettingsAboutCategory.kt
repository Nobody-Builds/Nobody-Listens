package com.nobodybuilds.listens.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.navigation.navigateSafely

/** Settings > About: updates, the About page and the last crash report. */
@Composable
internal fun SettingsAboutCategoryContent(navController: NavController) {
    SettingsSubsection(title = "Updates") {
        com.nobodybuilds.listens.presentation.update.UpdateSettings()
    }
    SettingsSubsection(
        title = stringResource(R.string.setcat_application),
        addBottomSpace = false
    ) {
        SettingsItem(
            title = stringResource(R.string.setcat_about_nobody_listens_title),
            subtitle = stringResource(R.string.setcat_about_nobody_listens_subtitle),
            leadingIcon = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely("about") }
        )
        // Shown only when a crash report is saved (view / share / clear).
        com.nobodybuilds.listens.presentation.crash.LastCrashReportSetting()
    }
}
