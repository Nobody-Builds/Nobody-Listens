package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import androidx.compose.ui.platform.LocalHapticFeedback
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActiveDeviceHero(
    device: ActiveDeviceUi,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    var sliderValue by remember(device.id, device.volume) { mutableFloatStateOf(device.volume) }
    LaunchedEffect(device.volume) { sliderValue = device.volume }
    val haptics = LocalHapticFeedback.current

    val discreteSteps = remember(device.volumeRange) {
        val span = device.volumeRange.endInclusive - device.volumeRange.start
        if (span <= 1f) 20 else span.toInt().coerceAtLeast(0) - 1
    }.coerceAtLeast(0)
    var lastStep by remember(device.id) { mutableIntStateOf(-1) }

    val heroShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 42.dp,
        cornerRadiusTR = 20.dp,
        cornerRadiusBL = 20.dp,
        cornerRadiusBR = 42.dp,
        smoothnessAsPercentTL = 70,
        smoothnessAsPercentTR = 70,
        smoothnessAsPercentBL = 70,
        smoothnessAsPercentBR = 70
    )

    Card(
        shape = heroShape,
        colors = CardDefaults.cardColors(
            containerColor = if (device.isRemote) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(62.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                    alpha = if (device.isConnecting) 0.18f else 0.12f
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (device.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f),
                                strokeWidth = 4.dp,
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            Icon(
                                imageVector = device.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // La columna de texto dicta la altura de la Row
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 2
                    )

                    val statusText = buildString {
                        append(device.subtitle)
                        append(" • ")
                        append(device.connectionLabel)
                    }

                    Text(
                        text = if (device.isConnecting && device.isRemote) stringResource(R.string.presentation_batch_g_cast_connecting_ellipsis) else statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1
                    )
                    if (device.isRemote) {
                        Button(
                            onClick = onDisconnect,
                            enabled = !device.isConnecting,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .height(46.dp)
                                .padding(top = 4.dp)
                        ) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                painter = painterResource(R.drawable.rounded_mimo_disconnect_24),
                                contentDescription = stringResource(R.string.presentation_batch_g_cast_cd_disconnect_icon),
                            )
                            Spacer(
                                modifier = Modifier.width(6.dp)
                            )
                            Text(stringResource(R.string.presentation_batch_g_cast_disconnect))
                        }
                    }
                }
            }

            // Sección de Volumen (Sin cambios)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (device.isRemote) stringResource(R.string.presentation_batch_g_cast_volume_device) else stringResource(R.string.presentation_batch_g_cast_volume_phone),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = buildVolumeLabel(sliderValue, device.volumeRange.endInclusive),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                val interactionSource = remember { MutableInteractionSource() }
                Slider(
                    value = sliderValue.coerceIn(device.volumeRange.start, device.volumeRange.endInclusive),
                    onValueChange = { newValue ->
                        sliderValue = newValue
                        val quantized = if (device.volumeRange.endInclusive <= 1f) {
                            (newValue * 20).toInt()
                        } else {
                            newValue.toInt()
                        }
                        if (quantized != lastStep) {
                            lastStep = quantized
                            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().volumeStep()
                        }
                        onVolumeChange(newValue)
                    },
                    valueRange = device.volumeRange,
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                inactiveTrackColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f),
                                thumbColor = MaterialTheme.colorScheme.onTertiary
                            )
                        )
                    },
                    thumb = { sliderState ->
                        SliderDefaults.Thumb(
                            modifier = Modifier
                                .height(36.dp),
                            interactionSource = interactionSource,
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        )
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        inactiveTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        thumbColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildVolumeLabel(value: Float, max: Float): String {
    return if (max <= 1f) {
        "${(value * 100).toInt()}%"
    } else {
        "${value.toInt()} / ${max.toInt()}"
    }
}

@Composable
internal fun EmptyDeviceState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.presentation_batch_g_cast_searching),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.presentation_batch_g_cast_searching_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
