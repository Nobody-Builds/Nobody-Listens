package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Curve
import com.nobodybuilds.listens.data.model.TransitionSettings
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.concurrent.TimeUnit
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransitionDurationSection(
    settings: TransitionSettings,
    onDurationChange: (Int) -> Unit
) {
    val durationInSeconds = TimeUnit.MILLISECONDS.toSeconds(settings.durationMs.toLong()).toInt()

    // Tarjeta limpia, sin bordes innecesarios, usando el fondo para agrupar
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(stringResource(R.string.presentation_batch_d_transition_duration_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.presentation_batch_d_transition_duration_subtitle_format, durationInSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FilledIconButton(
                onClick = { onDurationChange(TransitionSettings().durationMs) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.presentation_batch_d_transition_reset_cd))
            }
        }

        // Visualizador contextual de canciones
        CrossfadeVisualizer(durationMs = settings.durationMs)

        Slider(
            value = settings.durationMs.toFloat(),
            onValueChange = { onDurationChange(it.toInt()) },
            valueRange = 0f..12000f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(height = 36.dp, width = 8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        //.border(4.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                )
            }
        )
    }
}

@Composable
private fun CrossfadeVisualizer(durationMs: Int) {
    val maxDuration = 12000f
    val normalized = durationMs.coerceIn(0, 12000)
    // Porcentaje de la superposición respecto al máximo
    val overlapFactor by animateFloatAsState(targetValue = normalized / maxDuration, label = "width")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Labels de canciones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.presentation_batch_d_transition_visualizer_current),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                stringResource(R.string.presentation_batch_d_transition_visualizer_next),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Barra Canción 1 (Izquierda -> Derecha)
                // Se extiende hasta la mitad + la mitad del overlap
                Box(
                    modifier = Modifier
                        .weight(1f) // Base width
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        )
                ) {
                    // Extensión visual de la barra superior (Song 1 Ending)
                    // Esta lógica es visual para representar el "overlap"
                }

                // Barra Canción 2 (Derecha -> Izquierda)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                        )
                )
            }

            // Área de superposición dinámica (El "Crossfade")
            // Representa el tiempo compartido
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.1f + (overlapFactor * 0.4f)) // Mínimo visual + factor
                    .background(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow//.copy(alpha = 0.8f) // Masking effect
                    )
                    .height(32.dp)
//                    .border(
//                        1.dp,
//                        MaterialTheme.colorScheme.outlineVariant,
//                        RoundedCornerShape(8.dp)
//                    )
            ) {
                // Representación interna del cruce
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 0.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusTL = 50.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusBR = 0.dp,
                                    smoothnessAsPercentBL = 60,
                                    cornerRadiusBL = 50.dp,
                                    smoothnessAsPercentBR = 60
                                )
                            )
                    )
                    //Spacer(modifier = Modifier.width(1.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 50.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusTL = 0.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusBR = 50.dp,
                                    smoothnessAsPercentBL = 60,
                                    cornerRadiusBL = 0.dp,
                                    smoothnessAsPercentBR = 60
                                )
                            )
                    )
                }
                // Icono central
                Icon(
                    Icons.Rounded.AutoAwesomeMotion,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Explicación textual dinámica
        Text(
            text = stringResource(
                R.string.presentation_batch_d_transition_overlap_explanation_format,
                TimeUnit.MILLISECONDS.toSeconds(durationMs.toLong()).toInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun TransitionCurvesSection(
    settings: TransitionSettings,
    onCurveInSelected: (Curve) -> Unit,
    onCurveOutSelected: (Curve) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.presentation_batch_d_transition_curves_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.presentation_batch_d_transition_curves_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            // Usamos colores terciarios para la salida (Fade Out)
            CurveSelectionColumn(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.presentation_batch_d_transition_fade_out),
                selected = settings.curveOut,
                onCurveSelected = onCurveOutSelected,
                activeColor = MaterialTheme.colorScheme.tertiaryContainer,
                onActiveColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            // Usamos colores secundarios para la entrada (Fade In)
            CurveSelectionColumn(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.presentation_batch_d_transition_fade_in),
                selected = settings.curveIn,
                onCurveSelected = onCurveInSelected,
                activeColor = MaterialTheme.colorScheme.secondaryContainer,
                onActiveColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun CurveSelectionColumn(
    modifier: Modifier,
    title: String,
    selected: Curve,
    onCurveSelected: (Curve) -> Unit,
    activeColor: Color,
    onActiveColor: Color
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp, top = 4.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Curve.entries.forEach { curve ->
                    val isSelected = selected == curve

                    // Diseño Expressive: La selección es una forma, no solo un check
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) activeColor else Color.Transparent)
                            .clickable { onCurveSelected(curve) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = curve.name.lowercase().replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) onActiveColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = onActiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
