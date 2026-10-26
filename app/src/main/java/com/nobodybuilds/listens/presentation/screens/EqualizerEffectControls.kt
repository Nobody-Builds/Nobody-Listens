package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset // Added
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize // Added
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton // Added
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf // Added
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale // Added
import androidx.compose.ui.text.style.TextAlign // Added
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import kotlin.math.roundToInt
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check // Added import for Switch check icon
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import com.nobodybuilds.listens.presentation.components.WavyArcSlider
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.ui.res.stringResource

@Composable
internal fun EffectControlsSection(
    bassBoostEnabled: Boolean,
    bassBoostStrength: Float, // Int -> Float
    virtualizerEnabled: Boolean,
    virtualizerStrength: Float, // Int -> Float
    loudnessEnabled: Boolean,
    loudnessStrength: Float, // Int -> Float
    isBassBoostSupported: Boolean,
    isVirtualizerSupported: Boolean,
    isLoudnessEnhancerSupported: Boolean,
    isBassBoostDismissed: Boolean = false,
    isVirtualizerDismissed: Boolean = false,
    isLoudnessDismissed: Boolean = false,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostStrengthChange: (Float) -> Unit, // Int -> Float
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerStrengthChange: (Float) -> Unit, // Int -> Float
    onLoudnessEnabledChange: (Boolean) -> Unit,
    onLoudnessStrengthChange: (Float) -> Unit, // Int -> Float
    onDismissBassBoost: () -> Unit,
    onDismissVirtualizer: () -> Unit,
    onDismissLoudness: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .height(androidx.compose.foundation.layout.IntrinsicSize.Max) // Ensure equal heights
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bass Boost
        if (isBassBoostSupported) {
            EffectCard(
                title = stringResource(R.string.presentation_batch_d_eq_bass_boost),
                value = bassBoostStrength, // Already Float
                valueRange = 0f..1000f, // Keeping range as is, assuming VM handles 0-100 normalization? 
                // Wait, if VM stores 0-100 Float, but repo uses 0-1000 Int.
                // If I incorrectly changed VM to store 0-100 Float, I must match UI ranges.
                // Originally ranges were 0..1000. 
                // Let's assume VM exposes raw 0..1000 as Float to match slider requirements.
                isEnabled = bassBoostEnabled,
                onValueChange = { onBassBoostStrengthChange(it) }, // Pass Float directly
                onEnabledChange = onBassBoostEnabledChange
            )
        } else if (!isBassBoostDismissed) {
             UnsupportedEffectCard(
                title = stringResource(R.string.presentation_batch_d_eq_bass_boost),
                onDismiss = onDismissBassBoost
            )
        }
        
        // Virtualizer
        if (isVirtualizerSupported) {
            EffectCard(
                title = stringResource(R.string.presentation_batch_d_eq_virtualizer),
                value = virtualizerStrength,
                valueRange = 0f..1000f,
                isEnabled = virtualizerEnabled,
                onValueChange = { onVirtualizerStrengthChange(it) },
                onEnabledChange = onVirtualizerEnabledChange
            )
        } else if (!isVirtualizerDismissed) {
             UnsupportedEffectCard(
                title = stringResource(R.string.presentation_batch_d_eq_virtualizer),
                onDismiss = onDismissVirtualizer
            )
        }

        // Loudness Enhancer
        if (isLoudnessEnhancerSupported) {
            EffectCard(
                title = stringResource(R.string.presentation_batch_d_eq_loudness),
                value = loudnessStrength,
                valueRange = 0f..1000f,
                isEnabled = loudnessEnabled,
                onValueChange = { onLoudnessStrengthChange(it) },
                onEnabledChange = onLoudnessEnabledChange
            )
        } else if (!isLoudnessDismissed) {
             UnsupportedEffectCard(
                title = stringResource(R.string.presentation_batch_d_eq_loudness),
                onDismiss = onDismissLoudness
            )
        }
    }
}

@Composable
private fun EffectCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isEnabled: Boolean,
    onValueChange: (Float) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.width(150.dp).fillMaxHeight(), // Match parent intrinsic height
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp) // Reduced spacing
        ) {

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(150.dp)
                    .height(110.dp) 
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(150.dp) // Force render size
                        .offset(y = (5).dp), // Shift UP slightly to center clearer
                    contentAlignment = Alignment.Center
                ) {
                    WavyArcSlider(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxSize(),
                        enabled = isEnabled,
                        valueRange = valueRange,
                        activeTrackColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        thumbColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        waveAmplitude = 3.dp
                    )
                    
                    // Percentage Text
                    val percentage = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start) * 100).toInt()
                    Text(
                        text = stringResource(R.string.ui_format_percent_d, percentage),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.scale(0.8f) 
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun UnsupportedEffectCard(
    title: String,
    onDismiss: () -> Unit
) {
     Card(
        modifier = Modifier.width(150.dp).fillMaxHeight(), // Match parent intrinsic height
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.presentation_batch_d_eq_effect_not_supported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun UnsupportedEffectRow(
    title: String,
    message: String = "",
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) // Subtle warning
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        val resolvedMessage = if (message.isNotEmpty()) {
            message
        } else {
            stringResource(R.string.presentation_batch_d_eq_effect_not_supported_device)
        }
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = resolvedMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndividualEffectRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    strength: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    maxStrength: Int = 1000
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                thumbContent = if (isEnabled) {
                    {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else null
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..maxStrength.toFloat(),
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth(),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(36.dp)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun VolumeControlCard(
    volume: Float, 
    onVolumeChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var lastHapticValue by remember { mutableStateOf(volume) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_d_eq_volume),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = volume,
                        onValueChange = { newValue ->
                            // Subtle haptic feedback on each 5% change
                            val currentPercent = (newValue * 100).roundToInt()
                            val lastPercent = (lastHapticValue * 100).roundToInt()
                            if (currentPercent / 5 != lastPercent / 5) {
                                com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().volumeStep()
                                lastHapticValue = newValue
                            }
                            onVolumeChange(newValue)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(36.dp)
                            )
                        }
                    )
                }
                
                Text(
                    modifier = Modifier.width(46.dp),
                    text = stringResource(
                        R.string.ui_format_percent_d,
                        (volume * 100).roundToInt()
                    ),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
