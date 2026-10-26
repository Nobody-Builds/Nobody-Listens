package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.preferences.AlbumArtColorAccuracy
import kotlin.math.roundToInt

@Composable
internal fun MiniFullPlayerSkeletonPreview(
    scheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val sizeFactor = 0.7f
    fun scaled(dp: Dp): Dp = (dp.value * sizeFactor).dp
    val cardWidthFraction = 0.72f * sizeFactor

    val placeholderColor = scheme.onPrimaryContainer.copy(alpha = 0.1f)
    val placeholderOnColor = scheme.onPrimaryContainer.copy(alpha = 0.2f)
    val chipColor = scheme.onPrimary.copy(alpha = 0.8f)
    val topBarButtonColor = scheme.onPrimary.copy(alpha = 0.7f)
    val inactiveTrackColor = scheme.onPrimaryContainer.copy(alpha = 0.2f)
    val toggleRowColor = scheme.surfaceContainerLowest.copy(alpha = 0.7f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            color = scheme.primaryContainer,
            shape = RoundedCornerShape(scaled(34.dp)),
            modifier = Modifier.fillMaxWidth(cardWidthFraction)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(scaled(14.dp)),
                verticalArrangement = Arrangement.spacedBy(scaled(10.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(scaled(42.dp))
                            .clip(CircleShape)
                            .background(topBarButtonColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(scaled(92.dp))
                            .height(scaled(42.dp))
                            .clip(RoundedCornerShape(scaled(50.dp)))
                            .background(topBarButtonColor)
                    )
                }

//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth(0.5f)
//                        .height(scaled(12.dp))
//                        .clip(RoundedCornerShape(scaled(6.dp)))
//                        .background(placeholderOnColor)
//                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(190.dp))
                        .clip(RoundedCornerShape(scaled(22.dp)))
                        .background(placeholderColor)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(scaled(8.dp)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .height(scaled(18.dp))
                                .clip(RoundedCornerShape(scaled(6.dp)))
                                .background(placeholderColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.46f)
                                .height(scaled(12.dp))
                                .clip(RoundedCornerShape(scaled(6.dp)))
                                .background(placeholderOnColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(scaled(12.dp)))
                    Box(
                        modifier = Modifier
                            .size(scaled(40.dp))
                            .clip(CircleShape)
                            .background(chipColor)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(6.dp))
                        .clip(RoundedCornerShape(scaled(99.dp)))
                        .background(inactiveTrackColor)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.12f)
                            .height(scaled(6.dp))
                            .clip(RoundedCornerShape(scaled(99.dp)))
                            .background(scheme.primary.copy(alpha = 0.35f))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.12f)
                            .height(scaled(6.dp))
                            .clip(RoundedCornerShape(scaled(99.dp)))
                            .background(scheme.primary.copy(alpha = 0.35f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(CircleShape)
                            .background(scheme.onPrimary)
                    )
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(CircleShape)
                            .background(scheme.onPrimary)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(48.dp))
                        .padding(horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scaled(48.dp))
                            .clip(RoundedCornerShape(scaled(50.dp)))
                            .background(toggleRowColor)
                            .padding(
                                horizontal = scaled(6.dp),
                                vertical = scaled(6.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(scaled(6.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.secondary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.tertiary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PaletteSwatchSquare(
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
    ) {
        val circleRadius = maxWidth / 2
        val innerCorner by animateDpAsState(
            targetValue = if (selected) 12.dp else circleRadius,
            label = "paletteInnerCorner"
        )
        val outerCorner by animateDpAsState(
            targetValue = if (selected) 16.dp else circleRadius,
            label = "paletteOuterCorner"
        )
        val outlinePadding by animateDpAsState(
            targetValue = if (selected) 4.dp else 0.dp,
            label = "paletteOutlinePadding"
        )
        val borderWidth by animateDpAsState(
            targetValue = if (selected) 2.dp else 0.dp,
            label = "paletteBorderWidth"
        )

        Surface(
            color = scheme.surfaceContainerHighest,
            shape = RoundedCornerShape(outerCorner),
            border = if (borderWidth > 0.dp) BorderStroke(borderWidth, scheme.primary) else null,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(outlinePadding)
            ) {
                Surface(
                    color = scheme.surface,
                    shape = RoundedCornerShape(innerCorner),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.secondary)
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.tertiary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.surfaceContainerHighest)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaletteAccuracySlider(
    scheme: ColorScheme,
    value: Int,
    hapticsEnabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    val clampedValue = AlbumArtColorAccuracy.clamp(value)
    val hapticFeedback = LocalHapticFeedback.current
    var lastDispatchedValue by remember { mutableIntStateOf(clampedValue) }

    LaunchedEffect(clampedValue) {
        lastDispatchedValue = clampedValue
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.presentation_batch_f_color_accuracy_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.presentation_batch_f_color_accuracy_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .background(
                        color = scheme.primaryContainer,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    text = accuracySummaryText(clampedValue),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Slider(
            value = clampedValue.toFloat(),
            onValueChange = {
                val nextValue = AlbumArtColorAccuracy.clamp(it.roundToInt())
                if (nextValue == lastDispatchedValue) return@Slider

                if (hapticsEnabled) {
                    com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().selection()
                }
                lastDispatchedValue = nextValue
                onValueChange(nextValue)
            },
            valueRange = AlbumArtColorAccuracy.MIN.toFloat()..AlbumArtColorAccuracy.MAX.toFloat(),
            steps = AlbumArtColorAccuracy.STEPS,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.surfaceContainerHighest,
                activeTickColor = scheme.primary,
                inactiveTickColor = scheme.onSurfaceVariant.copy(alpha = 0.22f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_f_accuracy_current),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.presentation_batch_f_accuracy_more_accurate),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun accuracySummaryText(value: Int): String {
    val clamped = AlbumArtColorAccuracy.clamp(value)
    return when {
        clamped == 0 -> stringResource(R.string.presentation_batch_f_accuracy_summary_current)
        clamped in 1..3 -> stringResource(R.string.presentation_batch_f_accuracy_summary_subtle, clamped)
        clamped in 4..7 -> stringResource(R.string.presentation_batch_f_accuracy_summary_balanced, clamped)
        else -> stringResource(R.string.presentation_batch_f_accuracy_summary_precise, clamped)
    }
}
