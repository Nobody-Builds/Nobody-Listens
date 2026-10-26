package com.twilighty.lmusic.presentation.lofi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fireplace
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.twilighty.lmusic.data.lofi.AmbienceKind
import com.twilighty.lmusic.data.lofi.AmbienceLayerState
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbSettingRow
import com.twilighty.lmusic.presentation.nb.NbSwitch
import com.twilighty.lmusic.presentation.nb.NbType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Labelled Daylight-styled slider with a value readout. */
@Composable
fun LofiSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueText: String = "${(value * 100).toInt()}%",
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = NbType.label, color = if (enabled) Nb.text else Nb.textMuted, modifier = Modifier.weight(1f))
            Text(valueText, style = NbType.caption, color = Nb.textMuted)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Nb.ink,
                activeTrackColor = Nb.ink,
                inactiveTrackColor = Nb.emphasis,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

fun AmbienceKind.icon(): ImageVector = when (this) {
    AmbienceKind.Rain -> Icons.Rounded.WaterDrop
    AmbienceKind.Cafe -> Icons.Rounded.LocalCafe
    AmbienceKind.Fireplace -> Icons.Rounded.Fireplace
    AmbienceKind.Waves -> Icons.Rounded.Waves
}

/** Switch + volume for each ambient layer. */
@Composable
fun AmbienceLayerControls(
    layers: Map<AmbienceKind, AmbienceLayerState>,
    onEnabled: (AmbienceKind, Boolean) -> Unit,
    onVolume: (AmbienceKind, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (kind in AmbienceKind.entries) {
            val state = layers[kind] ?: AmbienceLayerState()
            NbSettingRow(
                title = kind.label,
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(kind.icon()),
                trailing = { NbSwitch(checked = state.enabled, onCheckedChange = { onEnabled(kind, it) }) },
                onClick = { onEnabled(kind, !state.enabled) },
            )
            if (state.enabled) {
                LofiSlider(
                    label = "Volume",
                    value = state.volume,
                    onValueChange = { onVolume(kind, it) },
                    modifier = Modifier.padding(start = 54.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * Rings and radial bars driven by the generator's meters. Animates only while [active];
 * reads plain floats once per frame (no allocations, no work when idle).
 */
@Composable
fun LofiVisualizer(
    meters: () -> FloatArray?,
    active: Boolean,
    modifier: Modifier = Modifier,
    ink: Color = Nb.ink,
    accent: Color = Nb.accent,
    track: Color = Nb.emphasis,
) {
    val smooth = remember { FloatArray(6) }
    val bars = remember { FloatArray(BAR_COUNT) }
    var frame by remember { mutableLongStateOf(0L) }
    var rotation by remember { mutableLongStateOf(0L) }

    LaunchedEffect(active) {
        if (!active) {
            // Settle to rest over a few frames, then stop.
            repeat(30) {
                withFrameNanos { }
                for (i in smooth.indices) smooth[i] *= 0.85f
                for (i in bars.indices) bars[i] *= 0.85f
                frame++
            }
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { now -> rotation = now / 16_000_000L }
            val m = meters()
            if (m != null) {
                for (i in smooth.indices) {
                    val target = m.getOrElse(i) { 0f }
                    val k = if (target > smooth[i]) 0.5f else 0.12f
                    smooth[i] += (target - smooth[i]) * k
                }
            }
            for (i in bars.indices) {
                // Each bar listens to a different instrument blend.
                val t = (i % 6)
                val target = when (t) {
                    0 -> smooth[5]
                    1 -> smooth[4]
                    2 -> smooth[3] * 1.4f
                    3 -> smooth[5] * 0.8f + smooth[2] * 0.4f
                    4 -> smooth[4] * 0.7f
                    else -> smooth[0]
                }.coerceIn(0f, 1f)
                bars[i] += (target - bars[i]) * 0.35f
            }
            frame++
        }
    }

    Canvas(modifier) {
        if (frame < 0L) return@Canvas // read the frame counter so each tick redraws
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * 0.24f
        val kick = smooth[1].coerceIn(0f, 1f)
        val stroke = 3.dp.toPx()
        drawCircle(track, radius = base * 1.55f, center = c, style = Stroke(1.dp.toPx()))
        drawCircle(track, radius = base * 1.95f, center = c, style = Stroke(1.dp.toPx()))
        drawCircle(accent, radius = base * (0.92f + kick * 0.16f), center = c)
        drawCircle(ink.copy(alpha = 0.12f + 0.2f * smooth[0]), radius = base * (1.12f + smooth[0] * 0.2f), center = c, style = Stroke(stroke))
        val spin = (rotation % 3600L) / 3600f * 2f * PI.toFloat()
        val inner = base * 1.3f
        val maxLen = base * 0.75f
        for (i in 0 until BAR_COUNT) {
            val a = spin + i * (2f * PI.toFloat() / BAR_COUNT)
            val len = max(stroke, bars[i] * maxLen)
            val dx = cos(a)
            val dy = sin(a)
            drawLine(
                color = ink.copy(alpha = 0.55f + 0.45f * bars[i]),
                start = Offset(c.x + dx * inner, c.y + dy * inner),
                end = Offset(c.x + dx * (inner + len), c.y + dy * (inner + len)),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val BAR_COUNT = 30
