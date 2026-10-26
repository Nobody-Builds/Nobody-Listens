package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.data.preferences.AlbumArtColorAccuracy
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemePair
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel
import com.nobodybuilds.listens.ui.theme.LocalNobodyListensDarkTheme
import com.nobodybuilds.listens.ui.theme.generateColorSchemeFromSeed
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteStyleSettingsScreen(
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isDarkTheme = LocalNobodyListensDarkTheme.current
    val albumSchemePair by playerViewModel.currentAlbumArtColorSchemePair.collectAsStateWithLifecycle()

    val baseScheme = MaterialTheme.colorScheme
    val albumScheme = remember(albumSchemePair, isDarkTheme, baseScheme) {
        albumSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light } ?: baseScheme
    }

    val currentSong = stablePlayerState.currentSong
    val isMiniPlayerVisible = currentSong != null
    val seedKey = currentSong?.albumArtUriString ?: currentSong?.id?.toString() ?: "system"
    var seedColor by remember(seedKey) { mutableStateOf<Color?>(null) }

    LaunchedEffect(seedKey, albumSchemePair, baseScheme.primary) {
        if (currentSong == null) {
            seedColor = baseScheme.primary
        } else if (seedColor == null && albumSchemePair != null) {
            seedColor = albumScheme.primary
        }
    }

    val resolvedSeed = seedColor ?: if (currentSong != null) albumScheme.primary else baseScheme.primary

    val styleSchemes = remember(resolvedSeed, isDarkTheme) {
        AlbumArtPaletteStyle.entries.associateWith { style ->
            val pair = generateColorSchemeFromSeed(
                seedColor = resolvedSeed,
                paletteStyle = style
            )
            if (isDarkTheme) pair.dark else pair.light
        }
    }

    var pendingStyle by remember { mutableStateOf(uiState.albumArtPaletteStyle) }
    var pendingAccuracy by remember { mutableStateOf(uiState.albumArtColorAccuracy) }
    LaunchedEffect(uiState.albumArtPaletteStyle) {
        pendingStyle = uiState.albumArtPaletteStyle
    }
    LaunchedEffect(uiState.albumArtColorAccuracy) {
        pendingAccuracy = uiState.albumArtColorAccuracy
    }

    var previewSchemePair by remember(currentSong?.albumArtUriString) {
        mutableStateOf<ColorSchemePair?>(albumSchemePair)
    }
    LaunchedEffect(
        currentSong?.albumArtUriString,
        pendingStyle,
        pendingAccuracy,
        uiState.albumArtPaletteStyle,
        uiState.albumArtColorAccuracy,
        albumSchemePair
    ) {
        val artUriString = currentSong?.albumArtUriString
        if (artUriString.isNullOrBlank()) {
            previewSchemePair = albumSchemePair
            return@LaunchedEffect
        }

        val matchesApplied =
            pendingStyle == uiState.albumArtPaletteStyle &&
                pendingAccuracy == uiState.albumArtColorAccuracy
        previewSchemePair = if (matchesApplied && albumSchemePair != null) {
            albumSchemePair
        } else {
            settingsViewModel.getAlbumArtPalettePreview(
                uriString = artUriString,
                style = pendingStyle,
                accuracyLevel = pendingAccuracy
            ) ?: albumSchemePair
        }
    }

    val hasPendingChanges =
        pendingStyle != uiState.albumArtPaletteStyle ||
            pendingAccuracy != uiState.albumArtColorAccuracy
    val previewScheme = previewSchemePair?.let { pair ->
        if (isDarkTheme) pair.dark else pair.light
    } ?: styleSchemes[pendingStyle] ?: albumScheme
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = bottomInset + if (isMiniPlayerVisible) MiniPlayerHeight + 12.dp else 16.dp

    Scaffold(
        containerColor = previewScheme.background,
        topBar = {
            PaletteStyleHeader(
                scheme = previewScheme,
                onBackClick = onBackClick,
                onApplyClick = {
                    settingsViewModel.setAlbumArtPaletteSettings(
                        style = pendingStyle,
                        accuracyLevel = pendingAccuracy
                    )
                },
                applyEnabled = hasPendingChanges
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniFullPlayerSkeletonPreview(
                scheme = previewScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )

            Surface(
                color = previewScheme.surfaceContainer,
                shape = RoundedCornerShape(34.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_f_palette_style_heading),
                        style = MaterialTheme.typography.titleLarge,
                        color = previewScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_f_palette_style_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = previewScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AlbumArtPaletteStyle.entries.forEach { style ->
                            PaletteSwatchSquare(
                                scheme = styleSchemes[style] ?: previewScheme,
                                selected = style == pendingStyle,
                                onClick = { pendingStyle = style },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = albumArtPaletteStyleLabel(pendingStyle),
                        style = MaterialTheme.typography.titleMedium,
                        color = previewScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = previewScheme.tertiaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            modifier = Modifier
                                .padding(8.dp)
                                .padding(start = 4.dp),
                            text = albumArtPaletteStyleDescription(pendingStyle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = previewScheme.onTertiaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    PaletteAccuracySlider(
                        scheme = previewScheme,
                        value = pendingAccuracy,
                        hapticsEnabled = uiState.hapticsEnabled,
                        onValueChange = { pendingAccuracy = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(contentBottomPadding))
        }
    }
}

@Composable
private fun PaletteStyleHeader(
    scheme: ColorScheme,
    onBackClick: () -> Unit,
    onApplyClick: () -> Unit,
    applyEnabled: Boolean
) {
    Surface(color = scheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.surfaceContainerLow,
                    contentColor = scheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.presentation_batch_f_cd_close)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.presentation_batch_f_colors_title),
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            FilledTonalButton(
                onClick = onApplyClick,
                enabled = applyEnabled,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    disabledContainerColor = scheme.surfaceContainerHigh,
                    disabledContentColor = scheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.presentation_batch_f_apply),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun albumArtPaletteStyleDescription(style: AlbumArtPaletteStyle): String =
    stringResource(
        when (style) {
            AlbumArtPaletteStyle.TONAL_SPOT -> R.string.presentation_batch_f_palette_style_tonal_spot_desc
            AlbumArtPaletteStyle.VIBRANT -> R.string.presentation_batch_f_palette_style_vibrant_desc
            AlbumArtPaletteStyle.EXPRESSIVE -> R.string.presentation_batch_f_palette_style_expressive_desc
            AlbumArtPaletteStyle.FRUIT_SALAD -> R.string.presentation_batch_f_palette_style_fruit_salad_desc
        }
    )

@Composable
private fun albumArtPaletteStyleLabel(style: AlbumArtPaletteStyle): String =
    stringResource(
        when (style) {
            AlbumArtPaletteStyle.TONAL_SPOT -> R.string.presentation_batch_f_palette_label_tonal_spot
            AlbumArtPaletteStyle.VIBRANT -> R.string.presentation_batch_f_palette_label_vibrant
            AlbumArtPaletteStyle.EXPRESSIVE -> R.string.presentation_batch_f_palette_label_expressive
            AlbumArtPaletteStyle.FRUIT_SALAD -> R.string.presentation_batch_f_palette_label_fruit_salad
        }
    )
