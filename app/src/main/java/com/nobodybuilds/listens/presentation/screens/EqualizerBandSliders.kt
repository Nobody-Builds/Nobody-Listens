package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.equalizer.EqualizerPreset
import kotlin.math.roundToInt
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.rounded.ExpandMore // Added
import androidx.compose.material.icons.rounded.Save // Added
import androidx.compose.material3.Surface
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository.EqualizerViewMode
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BandSlidersSection(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    currentPreset: EqualizerPreset,
    editingPresetName: String?,
    onBandLevelChanged: (Int, Int) -> Unit,
    viewMode: EqualizerViewMode,
    onSaveClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onUnpinClick: () -> Unit,
    onPresetsListClick: () -> Unit
) {
    val frequencies = EqualizerPreset.BAND_FREQUENCIES
    
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
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                 val isCustomOrSaved = editingPresetName != null || currentPreset.name == "custom" || currentPreset.isCustom
                 val displayLabel = editingPresetName ?: currentPreset.displayName
                 
                 Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    onClick = onPresetsListClick,
                    enabled = isCustomOrSaved 
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (isCustomOrSaved) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = stringResource(R.string.presentation_batch_d_eq_presets_cd),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                if (currentPreset.name == "custom" && editingPresetName == null) {
                     Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = CircleShape,
                        onClick = onSaveClick
                    ) {
                         Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             Icon(
                                 imageVector = Icons.Rounded.Save,
                                 contentDescription = null,
                                 modifier = Modifier.size(20.dp),
                                 tint = MaterialTheme.colorScheme.onTertiaryContainer
                             )
                             Spacer(modifier = Modifier.width(6.dp))
                             Text(
                                 text = stringResource(R.string.action_save),
                                 color = MaterialTheme.colorScheme.onTertiaryContainer,
                                 fontWeight = FontWeight.Bold
                             )
                         }
                     }
                }
                
                if (editingPresetName != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        onClick = onUpdateClick
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.presentation_batch_d_action_update),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))

            when (viewMode) {
                EqualizerViewMode.GRAPH -> {
                    GraphBandSliders(
                        bandLevels = bandLevels,
                        isEnabled = isEnabled,
                        frequencies = frequencies,
                        onBandLevelChanged = onBandLevelChanged
                    )
                }
                EqualizerViewMode.HYBRID -> {
                    HybridBandSliders(
                        bandLevels = bandLevels,
                        isEnabled = isEnabled,
                        frequencies = frequencies,
                        onBandLevelChanged = onBandLevelChanged
                    )
                }
                EqualizerViewMode.SLIDERS -> {
                    val pagerState = rememberPagerState(pageCount = { 2 })

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(270.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val start = page * 5
                            val end = minOf(start + 5, bandLevels.size)

                            for (index in start until end) {
                                val level = bandLevels.getOrElse(index) { 0 }
                                VerticalBandSlider(
                                    frequency = frequencies.getOrElse(index) { "${index * 1000}Hz" },
                                    level = level,
                                    isEnabled = isEnabled,
                                    onLevelChanged = { newLevel -> onBandLevelChanged(index, newLevel) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Page Indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pagerState.pageCount) { iteration ->
                             val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(if (pagerState.currentPage == iteration) 10.dp else 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphBandSliders(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    frequencies: List<String>,
    onBandLevelChanged: (Int, Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp) // Slightly taller for graph headroom
    ) {
        val density = LocalDensity.current
        
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            bandLevels.forEachIndexed { index, level ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Value Text (Top)
                    Text(
                        text = if (level > 0) "+$level" else "$level",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(20.dp)
                    )
                    

                    CustomVerticalSlider(
                        value = level.toFloat(),
                        onValueChange = { onBandLevelChanged(index, it.roundToInt()) },
                        valueRange = -15f..15f,
                        enabled = isEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        activeTrackColor = Color.Transparent, 
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        thumbColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        trackThickness = 4.dp, 
                        thumbSize = 16.dp,
                        thumbShape = CircleShape
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Frequency
                    Text(
                        text = frequencies.getOrElse(index) { "" }.replace("Hz", "").replace("k", "k"),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Graph Canvas Overlay
        if (isEnabled) {
            val primaryColor = MaterialTheme.colorScheme.primary
            
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                val widthPerBand = size.width / bandLevels.size
                val path = Path()
                
                // Constants from CustomVerticalSlider
                val sliderTopPadding = 20.dp.toPx()
                val sliderBottomPadding = 24.dp.toPx()
                val thumbSize = 16.dp.toPx() // Updated to 16dp
                val verticalPadding = 4.dp.toPx()
                val availableHeight = size.height - sliderTopPadding - sliderBottomPadding
                val trackHeight = availableHeight - thumbSize - (verticalPadding * 2)
                val topOffset = sliderTopPadding + verticalPadding + (thumbSize / 2)
                
                val points = bandLevels.mapIndexed { index, level ->
                    val x = (widthPerBand * index) + (widthPerBand / 2)
                    val normalized = ((level - (-15f)) / (15f - -15f)).coerceIn(0f, 1f)
                    val yNormalized = 1f - normalized
                    val y = topOffset + (yNormalized * trackHeight)
                    Offset(x, y)
                }
                
                if (points.isNotEmpty()) {
                    path.moveTo(points[0].x, points[0].y)
                    
                    for (i in 0 until points.size - 1) {
                        val p0 = points[maxOf(0, i - 1)]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points[minOf(points.size - 1, i + 2)]
                        
                        val cp1X = p1.x + (p2.x - p0.x) * 0.2f
                        val cp1Y = p1.y + (p2.y - p0.y) * 0.2f
                        val cp2X = p2.x - (p3.x - p1.x) * 0.2f
                        val cp2Y = p2.y - (p3.y - p1.y) * 0.2f
                        
                        path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                    }
                    
                    // Draw Line
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    // Draw Fill
                    val fillPath = Path()
                    fillPath.addPath(path)
                    fillPath.lineTo(points.last().x, size.height - sliderBottomPadding)
                    fillPath.lineTo(points.first().x, size.height - sliderBottomPadding)
                    fillPath.close()
                    
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
            }
        }
    }
}
