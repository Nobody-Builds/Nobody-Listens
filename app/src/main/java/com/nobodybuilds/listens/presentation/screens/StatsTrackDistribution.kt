@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.utils.formatListeningDurationCompact
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.PI
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

@Composable
internal fun TrackConcentrationCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val songs = summary?.songs.orEmpty()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.presentation_batch_g_stats_track_concentration_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.presentation_batch_g_stats_track_concentration_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )
            }

            if (songs.isEmpty()) {
                StatsEmptyState(
                    icon = Icons.Rounded.AutoGraph,
                    title = stringResource(R.string.presentation_batch_g_stats_empty_no_concentration_title),
                    subtitle = stringResource(R.string.presentation_batch_g_stats_empty_no_concentration_subtitle)
                )
            } else {
                val totalDuration = songs.sumOf { it.totalDurationMs }.coerceAtLeast(1L)
                val topOneDuration = songs.firstOrNull()?.totalDurationMs ?: 0L
                val topThreeDuration = songs.take(3).sumOf { it.totalDurationMs }
                val topThreeShare = (topThreeDuration.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                val averagePlaysPerTrack = (
                    (summary?.totalPlayCount ?: songs.sumOf { it.playCount }).toFloat() /
                        songs.size.coerceAtLeast(1).toFloat()
                    )
                val topOneColor = MaterialTheme.colorScheme.primary
                val topTwoThreeColor = MaterialTheme.colorScheme.secondary
                val othersColor = MaterialTheme.colorScheme.tertiary
                val slices = buildList {
                    if (topOneDuration > 0L) {
                        add(
                            TrackShareSlice(
                                label = stringResource(R.string.presentation_batch_g_stats_slice_top_1),
                                durationMs = topOneDuration,
                                color = topOneColor
                            )
                        )
                    }
                    val topTwoToThree = (topThreeDuration - topOneDuration).coerceAtLeast(0L)
                    if (topTwoToThree > 0L) {
                        add(
                            TrackShareSlice(
                                label = stringResource(R.string.presentation_batch_g_stats_slice_top_2_3),
                                durationMs = topTwoToThree,
                                color = topTwoThreeColor
                            )
                        )
                    }
                    val remaining = (totalDuration - topThreeDuration).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        add(
                            TrackShareSlice(
                                label = stringResource(R.string.presentation_batch_g_stats_slice_others),
                                durationMs = remaining,
                                color = othersColor
                            )
                        )
                    }
                }

                TrackDistributionOverview(
                    slices = slices,
                    totalDurationMs = totalDuration,
                    topThreeShare = topThreeShare,
                    averagePlaysPerTrack = averagePlaysPerTrack,
                    uniqueTracks = songs.size
                )
            }
        }
    }
}

@Composable
internal fun TrackDistributionOverview(
    slices: List<TrackShareSlice>,
    totalDurationMs: Long,
    topThreeShare: Float,
    averagePlaysPerTrack: Float,
    uniqueTracks: Int,
    modifier: Modifier = Modifier
) {
    val metricStyle = rememberStatsMetricValueStyle(compact = false)
    val onColor = MaterialTheme.colorScheme.onSurface
    val supporting = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            BoxWithConstraints(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                val compact = maxWidth < 420.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TrackDistributionDonut(
                            slices = slices,
                            totalDurationMs = totalDurationMs,
                            topThreeShare = topThreeShare,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        TrackDistributionStats(
                            metricStyle = metricStyle,
                            topThreeShare = topThreeShare,
                            averagePlaysPerTrack = averagePlaysPerTrack,
                            uniqueTracks = uniqueTracks
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackDistributionDonut(
                            slices = slices,
                            totalDurationMs = totalDurationMs,
                            topThreeShare = topThreeShare
                        )
                        TrackDistributionStats(
                            modifier = Modifier.weight(1f),
                            metricStyle = metricStyle,
                            topThreeShare = topThreeShare,
                            averagePlaysPerTrack = averagePlaysPerTrack,
                            uniqueTracks = uniqueTracks
                        )
                    }
                }
            }
        }

        if (slices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slices.forEach { slice ->
                    val share = (slice.durationMs.toFloat() / totalDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(slice.color)
                            )
                            Text(
                                text = slice.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = supporting,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(
                                    R.string.presentation_batch_g_stats_percent,
                                    (share * 100f).roundToInt()
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = onColor
                            )
                            Text(
                                text = formatListeningDurationCompact(slice.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = supporting
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrackDistributionStats(
    metricStyle: TextStyle,
    topThreeShare: Float,
    averagePlaysPerTrack: Float,
    uniqueTracks: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.presentation_batch_g_stats_listening_concentration_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(
                R.string.presentation_batch_g_stats_top_three_share_line,
                (topThreeShare * 100f).roundToInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", averagePlaysPerTrack),
                        style = metricStyle,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_stats_avg_plays_per_track),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = uniqueTracks.toString(),
                        style = metricStyle,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_stats_unique_tracks),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun TrackDistributionDonut(
    slices: List<TrackShareSlice>,
    totalDurationMs: Long,
    topThreeShare: Float,
    modifier: Modifier = Modifier
) {
    val strokePx = with(LocalDensity.current) { 18.dp.toPx() }
    val visualGapPx = with(LocalDensity.current) { 4.dp.toPx() }
    val trackColor = Color.Transparent
    Box(
        modifier = modifier.size(158.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val safeTotal = totalDurationMs.coerceAtLeast(1L).toFloat()
            val arcInset = (strokePx / 2f) + 1f
            val diameter = (size.minDimension - (arcInset * 2f)).coerceAtLeast(0f)
            val radius = (diameter - strokePx).coerceAtLeast(0f) / 2f
            val gapAngle = if (slices.size > 1 && radius > 0f) {
                // Round caps extend ~stroke/2 at both ends, so centerline gap must include that.
                val centerlineGapPx = visualGapPx + strokePx
                ((centerlineGapPx / radius) * (180f / PI.toFloat())).coerceIn(1f, 24f)
            } else {
                0f
            }
            val arcTopLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )

            slices.forEach { slice ->
                val rawSweep = (slice.durationMs.toFloat() / safeTotal) * 360f
                val sweep = (rawSweep - gapAngle).coerceAtLeast(0f)
                if (sweep > 0.2f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle + (gapAngle / 2f),
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokePx,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
                startAngle += rawSweep
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.presentation_batch_g_stats_percent,
                    (topThreeShare * 100f).roundToInt()
                ),
                style = rememberStatsMetricValueStyle(compact = false).copy(
                    fontSize = 24.sp,
                    lineHeight = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.presentation_batch_g_stats_top_three_share_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
