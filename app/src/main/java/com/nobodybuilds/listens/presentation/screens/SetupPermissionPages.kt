package com.nobodybuilds.listens.presentation.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.PermissionIconCollage
import com.nobodybuilds.listens.presentation.components.FileExplorerDialog
import com.nobodybuilds.listens.presentation.viewmodel.DirectoryEntry
import com.nobodybuilds.listens.presentation.viewmodel.SetupUiState
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import com.nobodybuilds.listens.utils.StorageInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorySelectionPage(
    uiState: SetupUiState,
    currentPath: File,
    directoryChildren: List<DirectoryEntry>,
    availableStorages: List<StorageInfo>,
    selectedStorageIndex: Int,
    isExplorerPriming: Boolean,
    isExplorerReady: Boolean,
    isCurrentDirectoryResolved: Boolean,
    isAtRoot: Boolean,
    explorerRoot: File,
    onOpenExplorer: () -> Unit,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onPrimeExplorer: () -> Unit,
    onSkip: () -> Unit,
    onToggleAllowed: (File) -> Unit,
    onSelectionFinished: () -> Unit,
    onStorageSelected: (Int) -> Unit
) {
    var showDirectoryPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val hasMediaPermission = uiState.mediaPermissionGranted
    val canOpenDirectoryPicker = hasMediaPermission

    LaunchedEffect(canOpenDirectoryPicker) {
        if (canOpenDirectoryPicker) {
            onPrimeExplorer()
        }
    }

    PermissionPageLayout(
        title = stringResource(R.string.setup_excluded_folders_title),
        description = stringResource(R.string.setup_excluded_folders_description),
        buttonText = stringResource(R.string.setup_choose_folders_ignore),
        buttonEnabled = canOpenDirectoryPicker,
        onGrantClicked = {
            if (canOpenDirectoryPicker) {
                showDirectoryPicker = true
                onOpenExplorer()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_grant_storage_first), Toast.LENGTH_SHORT).show()
            }
        },
        icons = persistentListOf(
            R.drawable.rounded_folder_24,
            R.drawable.rounded_music_note_24,
            R.drawable.rounded_create_new_folder_24,
            R.drawable.rounded_folder_open_24,
            R.drawable.rounded_audio_file_24
        )
    ) {
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.skip_for_now))
        }
    }

    FileExplorerDialog(
        visible = showDirectoryPicker,
        currentPath = currentPath,
        directoryChildren = directoryChildren,
        availableStorages = availableStorages,
        selectedStorageIndex = selectedStorageIndex,
        isLoading = uiState.isLoadingDirectories,
        isPriming = isExplorerPriming,
        isReady = isExplorerReady,
        isCurrentDirectoryResolved = isCurrentDirectoryResolved,
        isAtRoot = isAtRoot,
        rootDirectory = explorerRoot,
        onNavigateTo = onNavigateTo,
        onNavigateUp = onNavigateUp,
        onNavigateHome = { onNavigateTo(explorerRoot) },
        onToggleAllowed = onToggleAllowed,
        onRefresh = onRefresh,
        onStorageSelected = onStorageSelected,
        onDone = {
            onSelectionFinished()
            showDirectoryPicker = false
        },
        onDismiss = {
            onSelectionFinished()
            showDirectoryPicker = false
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPermissionPage(
    uiState: SetupUiState,
    onPermissionStateUpdated: () -> Unit
) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissions)
    val mediaIcons = persistentListOf(
        R.drawable.rounded_music_note_24,
        R.drawable.rounded_album_24,
        R.drawable.rounded_library_music_24,
        R.drawable.rounded_artist_24,
        R.drawable.rounded_playlist_play_24
    )

    // Sync the granted state with the ViewModel
    val isGranted = uiState.mediaPermissionGranted || permissionState.allPermissionsGranted

    LaunchedEffect(permissionState.allPermissionsGranted) {
        onPermissionStateUpdated()
    }

    PermissionPageLayout(
        title = stringResource(R.string.setup_permission_media_title),
        granted = isGranted,
        description = stringResource(R.string.setup_permission_media_description),
        buttonText = if (isGranted) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_grant_media_permission),
        buttonEnabled = !isGranted,
        icons = mediaIcons,
        onGrantClicked = {
            if (!isGranted) {
                permissionState.launchMultiplePermissionRequest()
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationsPermissionPage(
    uiState: SetupUiState,
    onPermissionStateUpdated: () -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val permissionState = rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.POST_NOTIFICATIONS))
    val notificationIcons = persistentListOf(
        R.drawable.rounded_circle_notifications_24,
        R.drawable.rounded_skip_next_24,
        R.drawable.rounded_play_arrow_24,
        R.drawable.rounded_pause_24,
        R.drawable.rounded_skip_previous_24
    )

    // Sync the granted state with the ViewModel
    val isGranted = uiState.notificationsPermissionGranted || permissionState.allPermissionsGranted

    LaunchedEffect(permissionState.allPermissionsGranted) {
        onPermissionStateUpdated()
    }

    PermissionPageLayout(
        title = stringResource(R.string.setup_permission_notifications_title),
        granted = isGranted,
        description = stringResource(R.string.setup_permission_notifications_description),
        buttonText = if (isGranted) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_enable_notifications),
        buttonEnabled = !isGranted,
        icons = notificationIcons,
        onGrantClicked = {
            if (!isGranted) {
                permissionState.launchMultiplePermissionRequest()
            }
        }
    )
}

@Composable
fun AlarmsPermissionPage(
    uiState: SetupUiState,
    onSkip: () -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val context = LocalContext.current
    val icons = persistentListOf(
        R.drawable.rounded_alarm_24,
        R.drawable.rounded_schedule_24,
        R.drawable.rounded_timer_24,
        R.drawable.rounded_hourglass_empty_24,
        R.drawable.rounded_notifications_active_24
    )

    val isGranted = uiState.alarmsPermissionGranted

    PermissionPageLayout(
        title = stringResource(R.string.setup_permission_alarms_title),
        granted = isGranted,
        description = stringResource(R.string.setup_permission_alarms_description),
        buttonText = if (isGranted) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_grant_permission_generic),
        buttonEnabled = !isGranted,
        icons = icons,
        onGrantClicked = {
            if (!isGranted) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                val uri = "package:${context.packageName}".toUri()
                intent.data = uri
                context.startActivity(intent)
            }
        }
    ) {
        if (!isGranted) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.skip_for_now))
            }
        }
    }
}

@Composable
fun BatteryOptimizationPage(
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
    
    // Track whether battery optimization is ignored
    var isIgnoringBatteryOptimizations by remember { 
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) 
    }
    
    // Re-check when resuming (user comes back from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val batteryIcons = persistentListOf(
        R.drawable.rounded_music_note_24,
        R.drawable.rounded_play_arrow_24,
        R.drawable.rounded_all_inclusive_24,
        R.drawable.rounded_pause_24,
        R.drawable.rounded_check_circle_24
    )

    PermissionPageLayout(
        title = stringResource(R.string.setup_battery_optimization_title),
        granted = isIgnoringBatteryOptimizations,
        description = stringResource(R.string.setup_battery_optimization_description),
        buttonText = if (isIgnoringBatteryOptimizations) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_disable_battery_optimization),
        buttonEnabled = !isIgnoringBatteryOptimizations,
        icons = batteryIcons,
        onGrantClicked = {
            if (!isIgnoringBatteryOptimizations) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general battery settings if direct intent fails
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(context, context.getString(R.string.toast_battery_settings_unavailable), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    ) {
        if (!isIgnoringBatteryOptimizations) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.skip_for_now))
            }
        }
    }
}

@Composable
fun PermissionPageLayout(
    title: String,
    granted: Boolean = false,
    description: String,
    buttonText: String,
    icons: ImmutableList<Int>,
    buttonEnabled: Boolean = true,
    onGrantClicked: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = GoogleSansRounded,
                    fontSize = 32.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            PermissionIconCollage(
                modifier = Modifier.height(220.dp),
                icons = icons
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGrantClicked,
                enabled = buttonEnabled,
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            ) {
                AnimatedContent(targetState = granted, label = "ButtonAnim") { isGranted ->
                    if (isGranted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(buttonText)
                        }
                    } else {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
