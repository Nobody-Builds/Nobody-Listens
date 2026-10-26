@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import com.nobodybuilds.listens.utils.formatListeningDurationCompact
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.stats.displayNameRes
import androidx.compose.material.icons.rounded.PlayCircleOutline

@Composable
internal fun ListeningTimelineSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    selectedMetric: TimelineMetric,
    onMetricSelected: (TimelineMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = summary?.range ?: StatsTimeRange.WEEK
    val timeline = summary?.timeline.orEmpty()
    val hasTimeline = timeline.isNotEmpty() && timeline.any { it.totalDurationMs > 0L || it.playCount > 0 }
    val sectionTitleStyle = rememberStatsSectionTitleStyle()
    val emDash = stringResource(R.string.presentation_batch_g_stats_em_dash)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_g_stats_section_listening_timeline),
                style = sectionTitleStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = timelineSupportingCopy(selectedMetric = selectedMetric, range = range),
                style = MaterialTheme.typography.bodyMedium, // Increased size
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            TimelineMetric.entries.forEach { metric ->
                val isSelected = metric == selectedMetric
                FilterChip(
                    selected = isSelected,
                    onClick = { if (!isSelected) com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().selection(); onMetricSelected(metric) },
                    label = {
                        Text(
                            text = stringResource(metric.displayNameRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape, // Fully rounded
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color.Transparent,
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        if (!hasTimeline) {
            StatsEmptyState(
                icon = Icons.Rounded.PlayCircleOutline,
                title = stringResource(R.string.presentation_batch_g_stats_empty_no_timeline_title),
                subtitle = stringResource(R.string.presentation_batch_g_stats_empty_no_timeline_subtitle)
            )
        } else {
            val cardColor = when (range) {
                StatsTimeRange.DAY, StatsTimeRange.WEEK -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                StatsTimeRange.MONTH, StatsTimeRange.YEAR -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
                StatsTimeRange.ALL, StatsTimeRange.CUSTOM -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.40f)
            }
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = when (range) {
                                    StatsTimeRange.DAY -> stringResource(R.string.presentation_batch_g_stats_rhythm_daily)
                                    StatsTimeRange.WEEK -> stringResource(R.string.presentation_batch_g_stats_rhythm_weekly)
                                    StatsTimeRange.MONTH -> stringResource(R.string.presentation_batch_g_stats_rhythm_monthly)
                                    StatsTimeRange.YEAR -> stringResource(R.string.presentation_batch_g_stats_rhythm_year)
                                    StatsTimeRange.ALL, StatsTimeRange.CUSTOM -> stringResource(R.string.presentation_batch_g_stats_rhythm_all_time)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (range) {
                                    StatsTimeRange.DAY -> stringResource(R.string.presentation_batch_g_stats_grouped_4h)
                                    StatsTimeRange.WEEK -> stringResource(R.string.presentation_batch_g_stats_grouped_dow)
                                    StatsTimeRange.MONTH -> stringResource(R.string.presentation_batch_g_stats_grouped_week_of_month)
                                    StatsTimeRange.YEAR -> stringResource(R.string.presentation_batch_g_stats_grouped_by_month)
                                    StatsTimeRange.ALL -> stringResource(R.string.presentation_batch_g_stats_grouped_by_year)
                                    StatsTimeRange.CUSTOM -> stringResource(R.string.nobody_listens_stats_range_custom)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TimelineMetricBadge(metric = selectedMetric)
                    }

                    TimelineBarChart(
                        entries = timeline,
                        metric = selectedMetric,
                        range = range,
                        blankLabel = emDash
                    )
                }
            }

            summary?.peakTimeline?.let { peak ->
                HighlightRow(
                    title = stringResource(R.string.presentation_batch_g_stats_peak_segment),
                    value = formatTimelineLabelForRange(peak.label, range, emDash),
                    supporting = when (selectedMetric) {
                        TimelineMetric.ListeningTime -> formatListeningDurationCompact(peak.totalDurationMs)
                        TimelineMetric.PlayCount -> stringResource(R.string.presentation_batch_g_stats_n_plays, peak.playCount)
                        TimelineMetric.AverageSession -> {
                            val average = if (peak.playCount > 0) peak.totalDurationMs / peak.playCount else 0L
                            formatListeningDurationCompact(average)
                        }
                    },
                    icon = Icons.Rounded.AutoGraph
                )
            }
        }
    }
}

@Composable
internal fun TimelineMetricBadge(
    metric: TimelineMetric,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (metric) {
        TimelineMetric.ListeningTime -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        TimelineMetric.PlayCount -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TimelineMetric.AverageSession -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = stringResource(metric.displayNameRes),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun TimelineBarChart(
    entries: List<PlaybackStatsRepository.TimelineEntry>,
    metric: TimelineMetric,
    range: StatsTimeRange,
    blankLabel: String,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val spec = timelineChartSpecFor(range = range, entryCount = entries.size)

    when (spec.layout) {
        TimelineChartLayout.Vertical -> VerticalTimelineBarChart(
            entries = entries,
            metric = metric,
            range = range,
            spec = spec,
            blankLabel = blankLabel,
            modifier = modifier
        )

        TimelineChartLayout.Horizontal -> HorizontalTimelineBarChart(
            entries = entries,
            metric = metric,
            range = range,
            spec = spec,
            blankLabel = blankLabel,
            modifier = modifier
        )
    }
}

@Composable
internal fun VerticalTimelineBarChart(
    entries: List<PlaybackStatsRepository.TimelineEntry>,
    metric: TimelineMetric,
    range: StatsTimeRange,
    spec: TimelineChartSpec,
    blankLabel: String,
    modifier: Modifier = Modifier
) {
    val maxMetricValue = entries.maxOfOrNull { metric.extractValue(it) }?.coerceAtLeast(0.0) ?: 0.0
    val hasNonZeroValues = maxMetricValue > 0.0
    val axisLabelStyle = rememberStatsAxisLabelStyle(range)
    val metricValueStyle = rememberStatsMetricValueStyle(compact = true)

    val highlightColor = when (range) {
        StatsTimeRange.DAY, StatsTimeRange.WEEK -> MaterialTheme.colorScheme.primary
        StatsTimeRange.MONTH, StatsTimeRange.YEAR -> MaterialTheme.colorScheme.secondary
        StatsTimeRange.ALL, StatsTimeRange.CUSTOM -> MaterialTheme.colorScheme.tertiary
    }
    val regularColor = highlightColor.copy(alpha = 0.72f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val spacing = 10.dp
            val itemCount = entries.size.coerceAtLeast(1)
            val innerWidth = (maxWidth - (spec.horizontalContentPadding * 2)).coerceAtLeast(0.dp)
            val spacingTotal = spacing * (itemCount - 1).coerceAtLeast(0)
            val minimumChartWidth = (spec.minItemWidth * itemCount) + spacingTotal
            val needsHorizontalScroll = minimumChartWidth > innerWidth || entries.size > spec.maxVisibleItems
            val fittedItemWidth = ((innerWidth - spacingTotal) / itemCount).coerceIn(spec.minItemWidth, spec.maxItemWidth)
            val itemWidth = if (needsHorizontalScroll) spec.minItemWidth else fittedItemWidth

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(spec.chartHeight),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.Bottom,
                    userScrollEnabled = needsHorizontalScroll,
                    contentPadding = PaddingValues(horizontal = spec.horizontalContentPadding)
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { index, entry -> "${entry.label}-$index" }
                    ) { _, entry ->
                        val value = metric.extractValue(entry).coerceAtLeast(0.0)
                        val progress = if (maxMetricValue > 0.0) (value / maxMetricValue).toFloat().coerceIn(0f, 1f) else 0f
                        val isPeak = hasNonZeroValues && (maxMetricValue - value) <= maxMetricValue * 0.01
                        val label = formatTimelineLabelForRange(entry.label, range, blankLabel)

                        Column(
                            modifier = Modifier.width(itemWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = metric.formatEntryValue(entry),
                                style = metricValueStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(trackColor),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(if (progress == 0f) 0f else progress)
                                        .clip(CircleShape)
                                        .background(if (isPeak) highlightColor else regularColor)
                                )
                            }
                            Text(
                                text = label,
                                style = axisLabelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = spec.labelMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HorizontalTimelineBarChart(
    entries: List<PlaybackStatsRepository.TimelineEntry>,
    metric: TimelineMetric,
    range: StatsTimeRange,
    spec: TimelineChartSpec,
    blankLabel: String,
    modifier: Modifier = Modifier
) {
    val maxMetricValue = entries.maxOfOrNull { metric.extractValue(it) }?.coerceAtLeast(0.0) ?: 0.0
    val hasNonZeroValues = maxMetricValue > 0.0
    val axisLabelStyle = rememberStatsAxisLabelStyle(range)
    val metricValueStyle = rememberStatsMetricValueStyle(compact = true)
    val highlightColor = when (range) {
        StatsTimeRange.DAY, StatsTimeRange.WEEK -> MaterialTheme.colorScheme.primary
        StatsTimeRange.MONTH, StatsTimeRange.YEAR -> MaterialTheme.colorScheme.secondary
        StatsTimeRange.ALL, StatsTimeRange.CUSTOM -> MaterialTheme.colorScheme.tertiary
    }
    val regularColor = highlightColor.copy(alpha = 0.72f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val edgeContentPadding = spec.horizontalContentPadding

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        entries.forEach { entry ->
            val value = metric.extractValue(entry).coerceAtLeast(0.0)
            val progress = if (maxMetricValue > 0.0) (value / maxMetricValue).toFloat().coerceIn(0f, 1f) else 0f
            val isPeak = hasNonZeroValues && (maxMetricValue - value) <= maxMetricValue * 0.01
            val label = formatTimelineLabelForRange(entry.label, range, blankLabel)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(edgeContentPadding))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.widthIn(min = 72.dp, max = 96.dp),
                        style = axisLabelStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(trackColor),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(if (progress == 0f) 0f else progress)
                                .clip(CircleShape)
                                .background(if (isPeak) highlightColor else regularColor)
                        )
                    }
                    Text(
                        text = metric.formatEntryValue(entry),
                        modifier = Modifier.widthIn(min = 58.dp),
                        style = metricValueStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(edgeContentPadding))
            }
        }
    }
}

@Composable
internal fun timelineSupportingCopy(
    selectedMetric: TimelineMetric,
    range: StatsTimeRange
): String {
    val rangeCopy = stringResource(
        when (range) {
            StatsTimeRange.DAY -> R.string.presentation_batch_g_stats_timeline_support_day
            StatsTimeRange.WEEK -> R.string.presentation_batch_g_stats_timeline_support_week
            StatsTimeRange.MONTH -> R.string.presentation_batch_g_stats_timeline_support_month
            StatsTimeRange.YEAR -> R.string.presentation_batch_g_stats_timeline_support_year
            StatsTimeRange.ALL, StatsTimeRange.CUSTOM -> R.string.presentation_batch_g_stats_timeline_support_all
        }
    )
    return stringResource(selectedMetric.descriptionRes) + " " + rangeCopy
}

internal fun formatTimelineLabelForRange(
    rawLabel: String,
    range: StatsTimeRange,
    blankLabel: String
): String {
    val label = rawLabel.trim()
    if (label.isBlank()) return blankLabel
    return when (range) {
        StatsTimeRange.DAY -> {
            val match = Regex("(?i)^(\\d{1,2})(am|pm)$").matchEntire(label)
            if (match != null) {
                "${match.groupValues[1]} ${match.groupValues[2].uppercase(Locale.US)}"
            } else {
                label
            }
        }

        StatsTimeRange.YEAR -> monthThreeLetters(label, blankLabel)
        else -> label
    }
}

internal fun monthThreeLetters(label: String, blankLabel: String): String {
    val cleaned = label.trim()
    if (cleaned.isEmpty()) return blankLabel
    return cleaned.take(3)
}

internal fun timelineChartSpecFor(
    range: StatsTimeRange,
    entryCount: Int
): TimelineChartSpec {
    return when (range) {
        StatsTimeRange.DAY -> TimelineChartSpec(
            layout = TimelineChartLayout.Vertical,
            minItemWidth = 52.dp,
            maxItemWidth = 72.dp,
            maxVisibleItems = 5,
            chartHeight = 224.dp,
            labelMaxLines = 1,
            horizontalContentPadding = 20.dp
        )

        StatsTimeRange.WEEK -> TimelineChartSpec(
            layout = TimelineChartLayout.Vertical,
            minItemWidth = 50.dp,
            maxItemWidth = 68.dp,
            maxVisibleItems = 6,
            chartHeight = 224.dp,
            labelMaxLines = 1,
            horizontalContentPadding = 20.dp
        )

        StatsTimeRange.MONTH -> TimelineChartSpec(
            layout = TimelineChartLayout.Vertical,
            minItemWidth = 62.dp,
            maxItemWidth = 82.dp,
            maxVisibleItems = 4,
            chartHeight = 232.dp,
            labelMaxLines = 1,
            horizontalContentPadding = 16.dp
        )

        StatsTimeRange.YEAR -> TimelineChartSpec(
            layout = TimelineChartLayout.Vertical,
            minItemWidth = 56.dp,
            maxItemWidth = 68.dp,
            maxVisibleItems = 5,
            chartHeight = 236.dp,
            labelMaxLines = 2,
            horizontalContentPadding = 16.dp
        )

        StatsTimeRange.ALL, StatsTimeRange.CUSTOM -> {
            val isSparse = entryCount <= 4
            TimelineChartSpec(
                layout = TimelineChartLayout.Vertical,
                minItemWidth = if (isSparse) 62.dp else 56.dp,
                maxItemWidth = if (isSparse) 78.dp else 66.dp,
                maxVisibleItems = if (isSparse) 4 else 6,
                chartHeight = 228.dp,
                labelMaxLines = 1,
                horizontalContentPadding = 16.dp
            )
        }
    }
}
