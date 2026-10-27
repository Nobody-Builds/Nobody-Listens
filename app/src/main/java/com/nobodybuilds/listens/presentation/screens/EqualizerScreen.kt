package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState // Added
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf // Added
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.equalizer.EqualizerPreset
import com.nobodybuilds.listens.presentation.components.CollapsibleCommonTopBar
import com.nobodybuilds.listens.presentation.viewmodel.EqualizerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Edit // Added
import androidx.compose.material.icons.rounded.Star // Added
import com.nobodybuilds.listens.presentation.components.CustomPresetsSheet
import com.nobodybuilds.listens.presentation.components.ReorderPresetsSheet
import com.nobodybuilds.listens.presentation.components.SavePresetDialog
import com.nobodybuilds.listens.presentation.components.RenamePresetDialog
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository.EqualizerViewMode
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.ui.res.stringResource

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    equalizerViewModel: EqualizerViewModel = hiltViewModel()
) {
    val uiState by equalizerViewModel.uiState.collectAsStateWithLifecycle()

    // Sheet States
    var showCustomPresetsSheet by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<EqualizerPreset?>(null) }
    
    // Handlers
    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name -> equalizerViewModel.saveCurrentAsCustomPreset(name) }
        )
    }
    
    renameTarget?.let { preset ->
        RenamePresetDialog(
            currentName = preset.displayName,
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                equalizerViewModel.renameCustomPreset(preset.name, newName)
            }
        )
    }
    
    if (showCustomPresetsSheet) {
        CustomPresetsSheet(
            presets = uiState.customPresets,
            pinnedPresetsNames = uiState.pinnedPresetsNames,
            onPresetSelected = { equalizerViewModel.selectPreset(it) },
            onPinToggled = { equalizerViewModel.togglePinPreset(it.name) },
            onRename = { renameTarget = it },
            onDelete = { equalizerViewModel.deleteCustomPreset(it) },
            onDismiss = { showCustomPresetsSheet = false }
        )
    }
    
    ReorderPresetsSheet(
        visible = showReorderSheet,
        allAvailablePresets = uiState.allAvailablePresets,
        pinnedPresetsNames = uiState.pinnedPresetsNames,
        onSave = { newOrder -> equalizerViewModel.updatePinnedPresetsOrder(newOrder) },
        onReset = { equalizerViewModel.resetPinnedPresetsToDefault() },
        onDismiss = { showReorderSheet = false }
    )
    
    // Transition animations
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { transitionState.targetState = true }
    
    val transition = rememberTransition(transitionState, label = "EqualizerAppearTransition")
    
    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) }
    ) { if (it) 1f else 0f }
    
    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
    ) { if (it) 0.dp else 40.dp }
    
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp
    
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
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            }
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp
            ),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Preset Tabs
            item(key = "preset_tabs") {
                val visiblePresets = remember(uiState.accessiblePresets) {
                    val defaultPresets = uiState.accessiblePresets.filter { !it.isCustom }
                    defaultPresets + EqualizerPreset.custom(List(10) { 0 }) // Always show "Custom" tab at end
                }
                
                PresetTabsRow(
                    presets = visiblePresets,
                    selectedPreset = uiState.currentPreset,
                    onPresetSelected = {
                        equalizerViewModel.selectPreset(it) 
                    },
                    onEditClick = { showReorderSheet = true }
                )
            }
            
            // Band Sliders
            item(key = "band_sliders") {
                BandSlidersSection(
                    bandLevels = uiState.bandLevels,
                    isEnabled = uiState.isEnabled,
                    currentPreset = uiState.currentPreset,
                    editingPresetName = uiState.editingPresetName,
                    onBandLevelChanged = { bandId, level ->
                        equalizerViewModel.setBandLevel(bandId, level)
                    },
                    viewMode = uiState.viewMode,
                    onSaveClick = { showSaveDialog = true },
                    onUpdateClick = {
                        uiState.editingPresetName?.let { equalizerViewModel.updateCustomPresetBands(it) }
                    },
                    onPresetsListClick = { showCustomPresetsSheet = true },
                    onUnpinClick = { }
                )
            }
            
            // Effect Controls
            item(key = "effect_controls") {
                EffectControlsSection(
                    bassBoostEnabled = uiState.bassBoostEnabled,
                    bassBoostStrength = uiState.bassBoostStrength, // Now Float
                    virtualizerEnabled = uiState.virtualizerEnabled,
                    virtualizerStrength = uiState.virtualizerStrength, // Now Float
                    loudnessEnabled = uiState.loudnessEnhancerEnabled,
                    loudnessStrength = uiState.loudnessEnhancerStrength, // Now Float
                    isBassBoostSupported = uiState.isBassBoostSupported,
                    isVirtualizerSupported = uiState.isVirtualizerSupported,
                    isLoudnessEnhancerSupported = uiState.isLoudnessEnhancerSupported,
                    isBassBoostDismissed = uiState.isBassBoostDismissed,
                    isVirtualizerDismissed = uiState.isVirtualizerDismissed,
                    isLoudnessDismissed = uiState.isLoudnessDismissed,
                    onBassBoostEnabledChange = { equalizerViewModel.setBassBoostEnabled(it) },
                    onBassBoostStrengthChange = { equalizerViewModel.setBassBoostStrength(it.roundToInt()) },
                    onVirtualizerEnabledChange = { equalizerViewModel.setVirtualizerEnabled(it) },
                    onVirtualizerStrengthChange = { equalizerViewModel.setVirtualizerStrength(it.roundToInt()) },
                    onLoudnessEnabledChange = { equalizerViewModel.setLoudnessEnhancerEnabled(it) },
                    onLoudnessStrengthChange = { equalizerViewModel.setLoudnessEnhancerStrength(it.roundToInt()) },
                    onDismissBassBoost = { equalizerViewModel.setBassBoostDismissed(true) },
                    onDismissVirtualizer = { equalizerViewModel.setVirtualizerDismissed(true) },
                    onDismissLoudness = { equalizerViewModel.setLoudnessDismissed(true) }
                )
            }
            
            // Volume Control
            item(key = "volume_control") {
                val volume by equalizerViewModel.systemVolume.collectAsStateWithLifecycle()
                VolumeControlCard(
                    volume = volume,
                    onVolumeChange = { equalizerViewModel.setSystemVolume(it) }
                )
            }
        }
        
        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_category_equalizer_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = { navController.popBackStack() },
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 72.dp,
            actions = {
                // View Mode Toggle
                FilledIconButton(
                    onClick = { equalizerViewModel.cycleViewMode() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = when(uiState.viewMode) {
                            EqualizerViewMode.SLIDERS -> Icons.Rounded.GraphicEq
                            EqualizerViewMode.GRAPH -> Icons.AutoMirrored.Rounded.ShowChart
                            EqualizerViewMode.HYBRID -> Icons.AutoMirrored.Rounded.ViewQuilt
                        },
                        contentDescription = stringResource(R.string.presentation_batch_d_eq_change_view_mode_cd)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Power toggle
                val isEnabled = uiState.isEnabled
                val powerButtonCorner by animateIntAsState(
                    targetValue = if (isEnabled) 50 else 12,
                    label = "PowerButtonShape"
                )

                FilledIconToggleButton(
                    checked = isEnabled,
                    onCheckedChange = { equalizerViewModel.toggleEqualizer() },
                    shape = RoundedCornerShape(powerButtonCorner),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = if (isEnabled) {
                            stringResource(R.string.presentation_batch_d_eq_disable_cd)
                        } else {
                            stringResource(R.string.presentation_batch_d_eq_enable_cd)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
            }
        )
    }
}

// EqualizerTopBar removed, replaced by CollapsibleCommonTopBar

@Composable
private fun PresetTabsRow(
    presets: List<EqualizerPreset>,
    selectedPreset: EqualizerPreset,
    onPresetSelected: (EqualizerPreset) -> Unit,
    onEditClick: () -> Unit
) {
    val showTabIndicator = false
    val selectedIndex = remember(presets, selectedPreset) {
        if (selectedPreset.isCustom || selectedPreset.name == "custom") {
             presets.indexOfLast { it.name == "Custom" || it.name == "custom" } // Match the placeholder
        } else {
             presets.indexOfFirst { it.name == selectedPreset.name }.coerceAtLeast(0)
        }
    }.coerceAtLeast(0)
    val coroutineScope = rememberCoroutineScope()
    
    // We don't use a Pager, so we need a manual scroll state if we wanted to auto-scroll.
    // Standard ScrollableTabRow handles scrolling to selected index automatically.
    
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 12.dp,
        containerColor = Color.Transparent,
        divider = {},
        indicator = {
            if (showTabIndicator) {
                 TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTabIndex = selectedIndex),
                    height = 3.dp,
                    width = 20.dp, // Fixed width for expressive dot? Or default width? Library used default.
                    // Library code: Modifier.tabIndicatorOffset(selectedTabIndex = pagerState.currentPage), height = 3.dp
                    // Let's stick to default width (match content) but custom height/color.
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.primary
                 )
            }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp) // Reduced height? Standard is often 48-64. 56 is good.
    ) {
        presets.forEachIndexed { index, preset ->
            val isPinnedCustom = preset.isCustom
            
            TabAnimation(
                index = index,
                title = preset.name,
                unselectedColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                selectedIndex = selectedIndex,
                onClick = { onPresetSelected(preset) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isPinnedCustom) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = stringResource(R.string.presentation_batch_d_eq_custom_preset_cd),
                            modifier = Modifier.size(10.dp), // Slightly smaller
                            tint = if (selectedIndex == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary 
                            // Note: TabAnimation handles content color usually, but Icon tint might need explicit handling or use LocalContentColor.
                            // TabAnimation uses: selectedContentColor = contentColor.
                            // So if I don't set tint, it will use LocalContentColor which is animated. 
                            // So remove manual tint or use LocalContentColor.current.
                        )
                    }
                }
            }
        }
        
        // Edit Button as a specific Tab (unselectable)
        TabAnimation(
            index = -1,
            title = stringResource(R.string.presentation_batch_d_eq_edit_tab_title),
            unselectedColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            selectedIndex = selectedIndex,
            onClick = onEditClick 
        ) {
             Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.presentation_batch_d_eq_edit_presets_cd),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
