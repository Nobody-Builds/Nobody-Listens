package com.nobodybuilds.listens.presentation.screens

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.CollapsibleCommonTopBar
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.viewmodel.AudioOutputCategory
import com.nobodybuilds.listens.presentation.viewmodel.DeviceCapabilitiesState
import com.nobodybuilds.listens.presentation.viewmodel.DeviceCapabilitiesViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun DeviceCapabilitiesScreen(
    navController: NavController,
    viewModel: DeviceCapabilitiesViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 188.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }

    // Derived, not a LaunchedEffect keyed on the height: reading the height here recomposed the
    // whole screen on every frame of the top bar collapsing.
    val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
        derivedStateOf {
            1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx
            if (topBarHeight.value != targetValue) {
                coroutineScope.launch { topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium)) }
            }
        }
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = currentTopBarHeightDp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            DeviceCapabilitiesContent(
                state = state,
                lazyListState = lazyListState,
                topPadding = currentTopBarHeightDp,
                modifier = Modifier.fillMaxSize()
            )
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_category_device_capabilities_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = { navController.popBackStack() },
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp,
            maxLines = 2
        )
    }
}

@Composable
private fun DeviceCapabilitiesContent(
    state: DeviceCapabilitiesState,
    lazyListState: LazyListState,
    topPadding: Dp,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(
            top = topPadding + 8.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        item {
            PlaybackReadinessCard(
                deviceInfo = state.deviceInfo,
                audioCapabilities = state.audioCapabilities,
                storageSummary = state.storageSummary,
                playbackCompatibility = state.playbackCompatibility
            )
        }

        state.storageSummary?.let { storage ->
            item {
                LocalMusicStorageCard(storageSummary = storage)
            }
        }

        state.audioCapabilities?.let { audio ->
            item {
                PlaybackPathCard(
                    audioCapabilities = audio,
                    memorySummary = state.memorySummary,
                    exoPlayerInfo = state.exoPlayerInfo
                )
            }
        }

        if (state.formatSupport.isNotEmpty()) {
            item {
                FormatCompatibilityCard(
                    formats = state.formatSupport,
                    playbackCompatibility = state.playbackCompatibility
                )
            }
        }

        state.playbackCompatibility?.let { compatibility ->
            item {
                PlaybackFindingsCard(compatibility = compatibility)
            }
        }

        item {
            DeviceInfoPanel(deviceInfo = state.deviceInfo)
        }
    }
}

@Composable
internal fun yesNo(value: Boolean): String {
    return if (value) {
        stringResource(R.string.presentation_batch_g_yes)
    } else {
        stringResource(R.string.presentation_batch_g_no)
    }
}

@Composable
internal fun audioOutputCategoryLabel(category: AudioOutputCategory): String {
    return when (category) {
        AudioOutputCategory.BuiltIn -> stringResource(R.string.device_capabilities_output_builtin)
        AudioOutputCategory.Bluetooth -> stringResource(R.string.device_capabilities_output_bluetooth)
        AudioOutputCategory.Usb -> stringResource(R.string.device_capabilities_output_usb)
        AudioOutputCategory.Wired -> stringResource(R.string.device_capabilities_output_wired)
        AudioOutputCategory.Cast -> stringResource(R.string.device_capabilities_output_digital)
        AudioOutputCategory.Other -> stringResource(R.string.device_capabilities_output_other)
    }
}

@Composable
internal fun localizedDeviceInfoEntries(entries: List<Pair<String, String>>): List<Pair<String, String>> {
    val lManufacturer = stringResource(R.string.presentation_batch_g_device_key_manufacturer)
    val lModel = stringResource(R.string.presentation_batch_g_device_key_model)
    val lBrand = stringResource(R.string.presentation_batch_g_device_key_brand)
    val lDevice = stringResource(R.string.presentation_batch_g_device_key_device)
    val lAndroid = stringResource(R.string.presentation_batch_g_device_key_android_version)
    val lSdk = stringResource(R.string.presentation_batch_g_device_key_sdk_version)
    val lHardware = stringResource(R.string.presentation_batch_g_device_key_hardware)

    return entries.map { (key, value) ->
        val label = when (key) {
            "Manufacturer" -> lManufacturer
            "Model" -> lModel
            "Brand" -> lBrand
            "Device" -> lDevice
            "Android Version" -> lAndroid
            "SDK Version" -> lSdk
            "Hardware" -> lHardware
            else -> key
        }
        label to value
    }
}

internal fun orderedDeviceInfoEntries(deviceInfo: Map<String, String>): List<Pair<String, String>> {
    val preferredOrder = listOf(
        "Manufacturer",
        "Model",
        "Brand",
        "Device",
        "Android Version",
        "SDK Version",
        "Hardware"
    )
    val orderedEntries = mutableListOf<Pair<String, String>>()
    val seenKeys = mutableSetOf<String>()

    preferredOrder.forEach { key ->
        deviceInfo[key]?.let { value ->
            orderedEntries += key to value
            seenKeys += key
        }
    }

    deviceInfo.forEach { (key, value) ->
        if (key !in seenKeys) {
            orderedEntries += key to value
        }
    }
    return orderedEntries
}
