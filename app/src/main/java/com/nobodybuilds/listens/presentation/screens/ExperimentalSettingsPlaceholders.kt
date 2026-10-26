package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Rectangle
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
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

/** Experimental › Player UI tweaks: step 2 (placeholders) and step 3 (when content appears). */
@Composable
internal fun ColumnScope.ExperimentalPlaceholderSettings(
    loadingTweaks: FullPlayerLoadingTweaks,
    placeholdersEnabled: Boolean,
    canUseTriggerMode: Boolean,
    switchOnDragRelease: Boolean,
    appearThresholdPercent: Int,
    closeThresholdPercent: Int,
    isAnyDelayEnabled: Boolean,
    settingsViewModel: SettingsViewModel
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        Text(
            text = stringResource(R.string.presentation_batch_f_exp_step2_placeholders_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    SwitchSettingItem(
        title = stringResource(R.string.presentation_batch_f_exp_use_placeholders_title),
        subtitle = stringResource(R.string.presentation_batch_f_exp_use_placeholders_subtitle),
        checked = placeholdersEnabled,
        onCheckedChange = settingsViewModel::setFullPlayerPlaceholders,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Rectangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    )

    AnimatedVisibility(
        visible = placeholdersEnabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Text(
                        text = stringResource(R.string.presentation_batch_f_exp_step3_trigger_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (canUseTriggerMode) {
                            stringResource(R.string.presentation_batch_f_exp_trigger_mode_unlocked)
                        } else {
                            stringResource(R.string.presentation_batch_f_exp_trigger_mode_locked)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TriggerModeOptionCard(
                            title = stringResource(R.string.presentation_batch_f_exp_threshold_title),
                            subtitle = stringResource(R.string.presentation_batch_f_exp_threshold_subtitle),
                            selected = !switchOnDragRelease,
                            enabled = canUseTriggerMode,
                            onClick = { settingsViewModel.setFullPlayerSwitchOnDragRelease(false) },
                            modifier = Modifier.weight(1f)
                        )
                        TriggerModeOptionCard(
                            title = stringResource(R.string.presentation_batch_f_exp_drag_release_title),
                            subtitle = stringResource(R.string.presentation_batch_f_exp_drag_release_subtitle),
                            selected = switchOnDragRelease,
                            enabled = canUseTriggerMode,
                            onClick = { settingsViewModel.setFullPlayerSwitchOnDragRelease(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = canUseTriggerMode && !switchOnDragRelease,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    Text(
                                        text = stringResource(R.string.presentation_batch_f_exp_expand_threshold_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.presentation_batch_f_exp_expand_threshold_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Slider(
                                value = appearThresholdPercent.toFloat(),
                                onValueChange = { settingsViewModel.setFullPlayerAppearThreshold(it.roundToInt()) },
                                valueRange = 0f..100f,
                                steps = 99,
                                enabled = isAnyDelayEnabled
                            )

                            Text(
                                text = stringResource(
                                    R.string.presentation_batch_f_exp_content_appears_at,
                                    appearThresholdPercent
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SwitchSettingItem(
                        title = stringResource(R.string.presentation_batch_f_exp_apply_on_close_title),
                        subtitle = stringResource(R.string.presentation_batch_f_exp_apply_on_close_subtitle),
                        checked = loadingTweaks.applyPlaceholdersOnClose,
                        onCheckedChange = settingsViewModel::setFullPlayerPlaceholdersOnClose,
                        enabled = isAnyDelayEnabled,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Rectangle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )

                    AnimatedVisibility(
                        visible = loadingTweaks.applyPlaceholdersOnClose,
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
                                        Text(
                                            text = stringResource(R.string.presentation_batch_f_exp_close_threshold_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.presentation_batch_f_exp_close_threshold_subtitle),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Slider(
                                    value = closeThresholdPercent.toFloat(),
                                    onValueChange = { settingsViewModel.setFullPlayerCloseThreshold(it.roundToInt()) },
                                    valueRange = 0f..100f,
                                    steps = 99,
                                    enabled = isAnyDelayEnabled
                                )

                                Text(
                                    text = stringResource(
                                        R.string.presentation_batch_f_exp_placeholders_after_collapse,
                                        closeThresholdPercent
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = canUseTriggerMode && switchOnDragRelease,
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
                        text = stringResource(R.string.presentation_batch_f_exp_drag_release_bypass),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            SwitchSettingItem(
                title = stringResource(R.string.presentation_batch_f_exp_transparent_placeholders_title),
                subtitle = stringResource(R.string.presentation_batch_f_exp_transparent_placeholders_subtitle),
                checked = loadingTweaks.transparentPlaceholders,
                onCheckedChange = settingsViewModel::setTransparentPlaceholders,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
    }
}

@Composable
private fun TriggerModeOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 94.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        }
    }
}
