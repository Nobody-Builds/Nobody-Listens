package com.nobodybuilds.listens.presentation.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.nobodybuilds.listens.presentation.viewmodel.BluetoothAudioDeviceState
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastBottomSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit = {}
) {
    val routes by playerViewModel.castRoutes.collectAsStateWithLifecycle()
    val selectedRoute by playerViewModel.selectedRoute.collectAsStateWithLifecycle()
    val routeVolume by playerViewModel.routeVolume.collectAsStateWithLifecycle()
    val isRefreshing by playerViewModel.isRefreshingRoutes.collectAsStateWithLifecycle()
    val isWifiEnabled by playerViewModel.isWifiEnabled.collectAsStateWithLifecycle()
    val isWifiRadioOn by playerViewModel.isWifiRadioOn.collectAsStateWithLifecycle()
    val wifiName by playerViewModel.wifiName.collectAsStateWithLifecycle()
    val isBluetoothEnabled by playerViewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val bluetoothName by playerViewModel.bluetoothName.collectAsStateWithLifecycle()
    val bluetoothAudioDeviceStates by playerViewModel.bluetoothAudioDeviceStates.collectAsStateWithLifecycle()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsStateWithLifecycle()
    val isCastConnecting by playerViewModel.isCastConnecting.collectAsStateWithLifecycle()
    val trackVolume by playerViewModel.trackVolume.collectAsStateWithLifecycle()
    val isPlaying = playerViewModel.stablePlayerState.collectAsStateWithLifecycle().value.isPlaying
    val context = LocalContext.current

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
    var missingPermissions by remember { mutableStateOf(missingCastPermissions(context, requiredPermissions)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    LaunchedEffect(Unit) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    val activeRoute = selectedRoute?.takeUnless { it.isDefault }
    val isRemoteSession = (isRemotePlaybackActive || isCastConnecting) && activeRoute != null

    val availableRoutes = if (isWifiEnabled) {
        routes.filterNot { it.isDefault }
    } else {
        emptyList()
    }
    val bluetoothDevices = bluetoothAudioDeviceStates
        .map { state -> state.copy(name = state.name.trim()) }
        .filter { it.name.isNotEmpty() }
        .distinctBy { it.stableId() }
    val activeBluetoothName = bluetoothName
        ?.trim()
        ?.takeIf { activeName ->
            activeName.isNotEmpty() && bluetoothDevices.any { it.name == activeName }
        }

    val devices = buildList {
        if (isWifiEnabled) {
            addAll(
                availableRoutes.map { route ->
                    val isRouteActive = activeRoute?.id == route.id
                    val normalizedConnectionState = when {
                        isRouteActive && isCastConnecting ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                        isRouteActive && isRemoteSession ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                        route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED
                        else -> route.connectionState
                    }

                    CastDeviceUi(
                        id = route.id,
                        name = route.name,
                        deviceType = route.deviceType,
                        playbackType = route.playbackType,
                        connectionState = normalizedConnectionState,
                        volumeHandling = route.volumeHandling,
                        volume = route.volume,
                        volumeMax = route.volumeMax,
                        isSelected = isRouteActive
                    )
                }
            )
        }

        if (isBluetoothEnabled) {
            bluetoothDevices.forEach { bluetoothDevice ->
                val isConnected = bluetoothDevice.name == activeBluetoothName
                add(
                    CastDeviceUi(
                        id = "bluetooth_${bluetoothDevice.stableId()}",
                        name = bluetoothDevice.name,
                        deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP,
                        playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL,
                        connectionState = if (isConnected) {
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                        } else {
                            MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED
                        },
                        volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
                        volume = if (isConnected) (trackVolume * 100).toInt() else 0,
                        volumeMax = 100,
                        isSelected = isConnected && !isRemoteSession,
                        batteryPercent = bluetoothDevice.batteryPercent,
                        isBluetooth = true
                    )
                )
            }
        }
    }

    val activeDevice = if (isRemoteSession) {
        val remoteRoute = checkNotNull(activeRoute)
        ActiveDeviceUi(
            id = remoteRoute.id,
            title = remoteRoute.name,
            subtitle = stringResource(R.string.presentation_batch_g_cast_subtitle_session),
            isRemote = true,
            icon = when (remoteRoute.deviceType) {
                MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Rounded.Tv
                MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
                MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> Icons.Rounded.Bluetooth
                else -> Icons.Rounded.Cast
            },
            isConnecting = isCastConnecting,
            volume = routeVolume.toFloat().coerceAtLeast(0f),
            volumeRange = 0f..remoteRoute.volumeMax.toFloat().coerceAtLeast(1f),
            connectionLabel = if (isCastConnecting) stringResource(R.string.presentation_batch_g_cast_connecting) else stringResource(R.string.presentation_batch_g_cast_connected)
        )
    } else {
        val isBluetoothAudio = isBluetoothEnabled && !activeBluetoothName.isNullOrEmpty()
        ActiveDeviceUi(
            id = "phone",
            title = if (isBluetoothAudio) activeBluetoothName!! else stringResource(R.string.presentation_batch_g_cast_this_phone),
            subtitle = if (isBluetoothAudio) stringResource(R.string.presentation_batch_g_cast_bluetooth_audio) else stringResource(R.string.presentation_batch_g_cast_local_playback),
            isRemote = false,
            icon = if (isBluetoothAudio) Icons.Rounded.Bluetooth else Icons.Rounded.Headphones,
            isConnecting = false,
            volume = trackVolume,
            volumeRange = 0f..1f,
            connectionLabel = if (isPlaying) stringResource(R.string.presentation_batch_g_cast_playing) else stringResource(R.string.presentation_batch_g_cast_paused)
        )
    }

    val uiState = CastSheetUiState(
        wifiRadioOn = isWifiRadioOn,
        wifiEnabled = isWifiEnabled,
        wifiSsid = wifiName,
        isScanning = isRefreshing && availableRoutes.isEmpty(),
        isRefreshing = isRefreshing,
        devices = devices,
        activeDevice = activeDevice,
        isBluetoothEnabled = isBluetoothEnabled,
        bluetoothName = activeBluetoothName
    )

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    DisposableEffect(Unit) {
        onExpansionChanged(1f)
        onDispose { onExpansionChanged(0f) }
    }

    com.nobodybuilds.listens.presentation.haptics.HapticBottomSheetEffect(sheetState)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 12.dp
    ) {
        // AQUÍ APLICAMOS EL FIX: Anulamos la fábrica de overscroll para todo lo que esté aquí adentro
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 18.dp)
            ) {
                if (missingPermissions.isNotEmpty()) {
                    CastPermissionStep(
                        missingPermissions = missingPermissions,
                        onRequestPermissions = {
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        }
                    )
                } else {
                    CastSheetContent(
                        state = uiState,
                        onSelectDevice = { id ->
                            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().toggleOn()
                            when {
                                id.startsWith("bluetooth_") -> {
                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                                else -> routes.firstOrNull { it.id == id }?.let { playerViewModel.selectRoute(it) }
                            }
                        },
                        onDisconnect = {
                            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().toggleOff()
                            playerViewModel.disconnect()
                            onDismiss()
                        },
                        onVolumeChange = { value ->
                            if (uiState.activeDevice.isRemote) {
                                playerViewModel.setRouteVolume(value.toInt())
                            } else {
                                playerViewModel.setTrackVolume(value)
                            }
                        },
                        onTurnOnWifi = {
                            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        onOpenBluetoothSettings = {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        onRefresh = {
                            playerViewModel.refreshCastRoutes()
                            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
                        },
                        startWithControls = isRemoteSession
                    )
                }
            }
        }
    }
}

internal data class CastDeviceUi(
    val id: String,
    val name: String,
    val deviceType: Int,
    val playbackType: Int,
    val connectionState: Int,
    val volumeHandling: Int,
    val volume: Int,
    val volumeMax: Int,
    val isSelected: Boolean,
    val batteryPercent: Int? = null,
    val isBluetooth: Boolean = false
)

internal data class ActiveDeviceUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val isRemote: Boolean,
    val icon: ImageVector,
    val isConnecting: Boolean,
    val volume: Float,
    val volumeRange: ClosedFloatingPointRange<Float>,
    val connectionLabel: String
)

internal data class CastSheetUiState(
    val wifiRadioOn: Boolean,
    val wifiEnabled: Boolean,
    val wifiSsid: String? = null,
    val isScanning: Boolean,
    val isRefreshing: Boolean,
    val devices: List<CastDeviceUi>,
    val activeDevice: ActiveDeviceUi,
    val isBluetoothEnabled: Boolean,
    val bluetoothName: String? = null
)

private fun BluetoothAudioDeviceState.stableId(): String {
    return address ?: name.lowercase()
}
