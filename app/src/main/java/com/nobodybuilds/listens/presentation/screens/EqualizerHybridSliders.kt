package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset // Added
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign // Added
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
internal fun HybridBandSliders(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    frequencies: List<String>,
    onBandLevelChanged: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val bandBass = stringResource(R.string.presentation_batch_d_eq_band_bass)
    val bandLowMids = stringResource(R.string.presentation_batch_d_eq_band_low_mids)
    val bandHighMids = stringResource(R.string.presentation_batch_d_eq_band_high_mids)
    val bandTreble = stringResource(R.string.presentation_batch_d_eq_band_treble)
    val bandBassLow = stringResource(R.string.presentation_batch_d_eq_band_bass_low)
    val bandMidHigh = stringResource(R.string.presentation_batch_d_eq_band_mid_high)
    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Static Compact Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp) // Enlarged from 140.dp
                .padding(horizontal = 4.dp) // Reduced outer padding
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp)
        ) {
             Text(
                text = stringResource(R.string.presentation_batch_d_eq_frequency_response),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(bottom = 8.dp)
             )
             
             Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
                 HybridFrequencyResponseGraph(bandLevels, isEnabled)
                 
                 // Draw simplistic X axis labels
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).offset(y = 4.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val labels = if (bandLevels.size > 5) {
                        listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
                    } else {
                         listOf("60", "230", "910", "4k", "14k")
                    }
                    labels.forEach { 
                        Text(it, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f))
                    }
                }
             }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Tabs & Sliders
        // Calculate pages (max 3 per page)
        val itemsPerPage = 3
        val pageCount = (bandLevels.size + itemsPerPage - 1) / itemsPerPage
        
        // Dynamic Tabs
        val tabs = if (pageCount == 4) {
            listOf(bandBass, bandLowMids, bandHighMids, bandTreble)
        } else if (pageCount == 2) {
            listOf(bandBassLow, bandMidHigh)
        } else {
            (1..pageCount).map { context.getString(R.string.presentation_batch_d_eq_page_n, it) }
        }

        val pagerState = rememberPagerState(pageCount = { pageCount })
        val coroutineScope = rememberCoroutineScope()
        
        // Use pagerState.currentPage as the source of truth to avoid feedback loops
        val selectedTabIndex = pagerState.currentPage
        val showBandPageTabIndicator = false

        Column(modifier = Modifier.padding(horizontal = 0.dp)) {
            // Tabs Row (Matching PresetTabsRow style)
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 12.dp,
                divider = {},
                indicator = {
                    if (showBandPageTabIndicator) {
                         TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTabIndex = selectedTabIndex),
                            height = 3.dp,
                            width = 20.dp,
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { 
                            Text(
                                title, 
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                            ) 
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

             Spacer(modifier = Modifier.height(24.dp))

             HorizontalPager(
                 state = pagerState, 
                 modifier = Modifier.fillMaxWidth(),
                 userScrollEnabled = true,
                 verticalAlignment = Alignment.Top
             ) { page ->
                 // Content for this page
                 val start = page * itemsPerPage
                 val end = minOf(start + itemsPerPage, bandLevels.size)
                 val indices = start until end
                 
                 Column(
                     verticalArrangement = Arrangement.spacedBy(16.dp), // Reduced from 24.dp
                     modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                 ) {
                     indices.forEach { index ->
                         if (index < bandLevels.size) {
                            HybridHorizontalSlider(
                                frequency = frequencies.getOrElse(index) { "" },
                                level = bandLevels[index],
                                isEnabled = isEnabled,
                                onLevelChanged = { onBandLevelChanged(index, it) }
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
private fun HybridHorizontalSlider(
    frequency: String,
    level: Int,
    isEnabled: Boolean,
    onLevelChanged: (Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticValue by remember { mutableIntStateOf(level) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Frequency Label
        Column(modifier = Modifier.width(36.dp)) {
            val freqVal = frequency.replace("Hz", "").replace("k", "k")
            Text(
                text = freqVal,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.presentation_batch_d_eq_unit_hz),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Horizontal Slider (Thick Track)
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Slider(
                value = level.toFloat(),
                onValueChange = { 
                    val intVal = it.roundToInt()
                    if (intVal != lastHapticValue) {
                        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().selection()
                        lastHapticValue = intVal
                    }
                    onLevelChanged(intVal) 
                },
                valueRange = -15f..15f,
                steps = 0,
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

        // Value Label
        Text(
            text = (if (level > 0) "+$level" else "$level") + "dB",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (level != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun HybridFrequencyResponseGraph(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    frequencies: List<String>? = null
) {
     val primaryColor = MaterialTheme.colorScheme.primary
     val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
     
     androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val widthPerBand = size.width / bandLevels.size
        val path = Path()
        
        // Graph Metrics
        val trackHeight = size.height * 0.7f 
        val topOffset = size.height * 0.15f
        
        // Draw Grid Lines (Horizontal)
        // Range -15 to +15. Grid at -10, -5, 0, 5, 10
        val gridLevels = listOf(-10, -5, 0, 5, 10)
        gridLevels.forEach { lvl ->
            val normalized = ((lvl - (-15f)) / (15f - -15f)).coerceIn(0f, 1f)
            val yNormalized = 1f - normalized
            val y = topOffset + (yNormalized * trackHeight)
            
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }
        
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
                color = if (isEnabled) primaryColor else primaryColor.copy(alpha=0.5f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Draw Dots
            points.forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = point
                )
            }
        }
     }
}
