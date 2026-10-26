package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.TransitionMode
import com.nobodybuilds.listens.presentation.viewmodel.TransitionViewModel
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransitionScreen(
    navController: NavController,
    viewModel: TransitionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val displayedSettings = if (uiState.useGlobalDefaults) {
        uiState.globalSettings
    } else {
        uiState.rule?.settings ?: uiState.globalSettings
    }
    val isPlaylistScope = uiState.playlistId != null
    val hasCustomRule = uiState.rule != null && !uiState.useGlobalDefaults
    val isCrossfadeEnabled = displayedSettings.mode != TransitionMode.NONE

    // Configuración para el comportamiento de la TopBar colapsable (Material 3)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(uiState.isSaved, isPlaylistScope, uiState.useGlobalDefaults) {
        if (uiState.isSaved) {
            val messageRes = if (isPlaylistScope && uiState.useGlobalDefaults) {
                R.string.presentation_batch_d_transition_snackbar_using_global
            } else {
                R.string.presentation_batch_d_transition_snackbar_saved
            }
            snackbarHostState.showSnackbar(message = context.getString(messageRes))
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = if (isPlaylistScope) {
                            stringResource(R.string.presentation_batch_d_transition_title_playlist_rules)
                        } else {
                            stringResource(R.string.presentation_batch_d_transition_title_global)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier
                            .padding(start = 10.dp),
                        onClick = { navController.navigateUp() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Icon(painterResource(R.drawable.rounded_arrow_back_24), contentDescription = stringResource(R.string.auth_cd_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(end = 10.dp),
                        onClick = { viewModel.saveSettings() },
                        enabled = !uiState.isLoading,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                    ){
                        Icon(Icons.Rounded.Save, contentDescription = stringResource(R.string.presentation_batch_d_transition_save_cd))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
//            if (!uiState.isLoading) {
//                ExtendedFloatingActionButton(
//                    modifier = Modifier.padding(),
//                    onClick = { viewModel.saveSettings() },
//                    icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
//                    text = { Text("Save changes") },
//                    containerColor = MaterialTheme.colorScheme.primaryContainer,
//                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
//                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp) // Flat style for consistency
//                )
//            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        text = if (isPlaylistScope) {
                            stringResource(R.string.presentation_batch_d_transition_intro_playlist)
                        } else {
                            stringResource(R.string.presentation_batch_d_transition_intro_global)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                item {
                    TransitionSummaryCard(
                        isPlaylistScope = isPlaylistScope,
                        hasCustomRule = hasCustomRule,
                        followingGlobal = uiState.useGlobalDefaults,
                        onResetToGlobal = { viewModel.useGlobalDefaults() },
                        onEnableOverride = { viewModel.enablePlaylistOverride() },
                        enabled = isPlaylistScope
                    )
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                item {
                    TransitionModeSection(
                        selected = displayedSettings.mode,
                        onModeSelected = viewModel::updateMode
                    )
                }

                // Animación de visibilidad: Oculta controles complejos si no hay transición
                item {
                    AnimatedVisibility(
                        visible = isCrossfadeEnabled,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            TransitionDurationSection(
                                settings = displayedSettings,
                                onDurationChange = viewModel::updateDuration
                            )

                            TransitionCurvesSection(
                                settings = displayedSettings,
                                onCurveInSelected = viewModel::updateCurveIn,
                                onCurveOutSelected = viewModel::updateCurveOut
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionSummaryCard(
    isPlaylistScope: Boolean,
    hasCustomRule: Boolean,
    followingGlobal: Boolean,
    onResetToGlobal: () -> Unit,
    onEnableOverride: () -> Unit,
    enabled: Boolean
) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.presentation_batch_d_transition_active_status),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when {
                            !isPlaylistScope -> stringResource(R.string.presentation_batch_d_transition_status_global_default)
                            followingGlobal -> stringResource(R.string.presentation_batch_d_transition_status_following_global)
                            hasCustomRule -> stringResource(R.string.presentation_batch_d_transition_status_custom_override)
                            else -> stringResource(R.string.presentation_batch_d_transition_status_playlist_default)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            AnimatedVisibility(visible = isPlaylistScope) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)) {
                            Text(stringResource(R.string.presentation_batch_d_transition_custom_override_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.presentation_batch_d_transition_custom_override_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = !followingGlobal,
                            onCheckedChange = { isEnabled -> if (isEnabled) onEnableOverride() else onResetToGlobal() },
                            enabled = enabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionModeSection(
    selected: TransitionMode,
    onModeSelected: (TransitionMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.presentation_batch_d_transition_style_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.presentation_batch_d_transition_style_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Componente Toggle rediseñado: Plano, simétrico, sin sombras raras
        ExpressiveMorphingToggle(
            options = listOf(TransitionMode.NONE, TransitionMode.OVERLAP),
            selectedOption = selected,
            onOptionSelected = onModeSelected
        )
    }
}

@Composable
private fun ExpressiveMorphingToggle(
    options: List<TransitionMode>,
    selectedOption: TransitionMode,
    onOptionSelected: (TransitionMode) -> Unit
) {
    val selectedIndex = if (selectedOption == TransitionMode.OVERLAP) 1 else 0
    val shape = CircleShape //RoundedCornerShape(16.dp) // Menos redondeado para más estructura, o 50 para capsula
    val crossfadeLabel = stringResource(R.string.presentation_batch_d_transition_mode_crossfade)
    val noneLabel = stringResource(R.string.presentation_batch_d_transition_mode_none)

    // Contenedor Plano con borde sutil
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            //.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp)
    ) {
        val maxWidth = maxWidth
        val indicatorWidth = maxWidth / 2

        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedIndex == 1) indicatorWidth else 0.dp,
            animationSpec = tween(durationMillis = 300),
            label = "offset"
        )

        // El indicador se mueve detrás del texto
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .fillMaxSize()
                .offset(x = indicatorOffset)
                .clip(CircleShape) // Un poco más pequeño que el contenedor
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { mode ->
                val isSelected = selectedOption == mode
                val title = if (mode == TransitionMode.OVERLAP) crossfadeLabel else noneLabel

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable { onOptionSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
