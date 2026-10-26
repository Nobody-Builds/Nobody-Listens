package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.viewmodel.SettingsUiState
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel

/** Experimental › Player UI tweaks: animated lyrics and step 1 (what loads late) of the full player. */
@Composable
internal fun ColumnScope.ExperimentalPlayerTweaksSettings(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel
) {
    val loadingTweaks = uiState.fullPlayerLoadingTweaks
    val delayAllEnabled = loadingTweaks.delayAll
    val appearThresholdPercent = loadingTweaks.contentAppearThresholdPercent
    val closeThresholdPercent = loadingTweaks.contentCloseThresholdPercent
    val switchOnDragRelease = loadingTweaks.switchOnDragRelease
    val placeholdersEnabled = loadingTweaks.showPlaceholders
    val isAnyDelayEnabled = loadingTweaks.let {
        it.delayAll || it.delayAlbumCarousel || it.delaySongMetadata || it.delayProgressBar || it.delayControls
    }
    val canUseTriggerMode = isAnyDelayEnabled && placeholdersEnabled

    SwitchSettingItem(
        title = stringResource(R.string.presentation_batch_f_exp_animated_lyrics_title),
        subtitle = stringResource(R.string.presentation_batch_f_exp_animated_lyrics_subtitle),
        checked = uiState.useAnimatedLyrics,
        onCheckedChange = settingsViewModel::setUseAnimatedLyrics,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    )

    AnimatedVisibility(
        visible = uiState.useAnimatedLyrics,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SwitchSettingItem(
                title = stringResource(R.string.presentation_batch_f_exp_lyric_blur_title),
                subtitle = stringResource(R.string.presentation_batch_f_exp_lyric_blur_subtitle),
                checked = uiState.animatedLyricsBlurEnabled,
                onCheckedChange = settingsViewModel::setAnimatedLyricsBlurEnabled,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.BlurOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            AnimatedVisibility(
                visible = uiState.animatedLyricsBlurEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LinearScale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.presentation_batch_f_exp_blur_strength),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        val strengthText = stringResource(
                                            R.string.presentation_batch_f_exp_blur_strength_value,
                                            uiState.animatedLyricsBlurStrength
                                        )
                                        Text(
                                            text = strengthText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.presentation_batch_f_exp_blur_strength_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Slider(
                            value = uiState.animatedLyricsBlurStrength,
                            onValueChange = { settingsViewModel.setAnimatedLyricsBlurStrength(it) },
                            valueRange = 0.1f..2.0f,
                            steps = 10
                        )
                    }
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        Text(
            text = stringResource(R.string.presentation_batch_f_exp_step1_delay_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    SwitchSettingItem(
        title = stringResource(R.string.presentation_batch_f_exp_delay_everything_title),
        subtitle = stringResource(R.string.presentation_batch_f_exp_delay_everything_subtitle),
        checked = delayAllEnabled,
        onCheckedChange = settingsViewModel::setDelayAllFullPlayerContent,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    )

    AnimatedVisibility(
        visible = !delayAllEnabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SwitchSettingItem(
                title = stringResource(R.string.presentation_batch_f_exp_album_carousel_title),
                subtitle = stringResource(R.string.presentation_batch_f_exp_album_carousel_subtitle),
                checked = loadingTweaks.delayAlbumCarousel,
                onCheckedChange = settingsViewModel::setDelayAlbumCarousel,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.ViewCarousel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            SwitchSettingItem(
                title = stringResource(R.string.presentation_batch_f_exp_song_metadata_title),
                subtitle = stringResource(R.string.presentation_batch_f_exp_song_metadata_subtitle),
                checked = loadingTweaks.delaySongMetadata,
                onCheckedChange = settingsViewModel::setDelaySongMetadata,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.LinearScale,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            SwitchSettingItem(
                title = stringResource(R.string.presentation_batch_f_exp_progress_bar_title),
                subtitle = stringResource(R.string.presentation_batch_f_exp_progress_bar_subtitle),
                checked = loadingTweaks.delayProgressBar,
                onCheckedChange = settingsViewModel::setDelayProgressBar,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.LinearScale,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            SwitchSettingItem(
                title = stringResource(R.string.presentation_batch_f_exp_playback_controls_title),
                subtitle = stringResource(R.string.presentation_batch_f_exp_playback_controls_subtitle),
                checked = loadingTweaks.delayControls,
                onCheckedChange = settingsViewModel::setDelayControls,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
    }

    AnimatedVisibility(
        visible = delayAllEnabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_f_exp_delay_all_active_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }

    ExperimentalPlaceholderSettings(
        loadingTweaks = loadingTweaks,
        placeholdersEnabled = placeholdersEnabled,
        canUseTriggerMode = canUseTriggerMode,
        switchOnDragRelease = switchOnDragRelease,
        appearThresholdPercent = appearThresholdPercent,
        closeThresholdPercent = closeThresholdPercent,
        isAnyDelayEnabled = isAnyDelayEnabled,
        settingsViewModel = settingsViewModel
    )
}
