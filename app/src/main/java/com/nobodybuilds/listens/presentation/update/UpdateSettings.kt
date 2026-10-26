package com.nobodybuilds.listens.presentation.update

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.BuildConfig
import com.nobodybuilds.listens.presentation.screens.SettingsItem
import com.nobodybuilds.listens.presentation.screens.SwitchSettingItem
import java.text.DateFormat
import java.util.Date

/** Settings rows for the update checker: weekly switch, "Check now" and "Skip this version". */
@Composable
fun UpdateSettings(viewModel: UpdateViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val enabled by viewModel.checksEnabled.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    SwitchSettingItem(
        title = "Check for updates",
        subtitle = "Once a week, on Wi-Fi or mobile data",
        checked = enabled,
        onCheckedChange = viewModel::setChecksEnabled,
        leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.secondary) }
    )

    val lastChecked = if (state.checkedAt > 0L) {
        "Last checked " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(state.checkedAt))
    } else {
        "Installed: ${BuildConfig.VERSION_NAME}"
    }
    SettingsItem(
        title = "Check now",
        subtitle = if (isChecking) "Checking…" else lastChecked,
        leadingIcon = { Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.secondary) },
        trailingIcon = {
            if (isChecking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        },
        onClick = viewModel::checkNow
    )

    val available = state.availableVersion
    if (available != null) {
        SettingsItem(
            title = "Skip this version",
            subtitle = "Stop reminders for Nobody Listens $available",
            leadingIcon = { Icon(Icons.Rounded.SkipNext, null, tint = MaterialTheme.colorScheme.secondary) },
            onClick = {
                viewModel.skipVersion(available)
                Toast.makeText(context, "Skipped Nobody Listens $available", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
