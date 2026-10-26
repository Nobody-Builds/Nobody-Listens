@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.utils.formatListeningDurationCompact
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.stats.displayNameRes
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryMetricsSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    selectedDimension: CategoryDimension,
    onDimensionSelected: (CategoryDimension) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = categoryPaletteFor(selectedDimension)
    val sectionTitleStyle = rememberStatsSectionTitleStyle()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_g_stats_section_top_categories),
                style = sectionTitleStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.presentation_batch_g_stats_top_categories_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            CategoryDimension.entries.reversed().forEach { dimension ->
                val isSelected = dimension == selectedDimension
                val chipPalette = categoryPaletteFor(dimension)
                FilterChip(
                    selected = isSelected,
                    onClick = { if (!isSelected) com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().selection(); onDimensionSelected(dimension) },
                    label = {
                        Text(
                            text = stringResource(dimension.displayNameRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipPalette.accentColor,
                        selectedLabelColor = chipPalette.accentOnColor,
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

        val entries = when (selectedDimension) {
            CategoryDimension.Genre -> summary?.topGenres.orEmpty().map {
                CategoryMetricEntry(
                    label = it.genre,
                    durationMs = it.totalDurationMs,
                    supporting = stringResource(
                        R.string.presentation_batch_g_stats_plays_artists,
                        it.playCount,
                        it.uniqueArtists
                    )
                )
            }

            CategoryDimension.Artist -> summary?.topArtists.orEmpty().map {
                CategoryMetricEntry(
                    label = it.artist,
                    durationMs = it.totalDurationMs,
                    supporting = stringResource(
                        R.string.presentation_batch_g_stats_plays_tracks,
                        it.playCount,
                        it.uniqueSongs
                    )
                )
            }

            CategoryDimension.Album -> summary?.topAlbums.orEmpty().map {
                CategoryMetricEntry(
                    label = it.album,
                    durationMs = it.totalDurationMs,
                    supporting = stringResource(
                        R.string.presentation_batch_g_stats_plays_tracks,
                        it.playCount,
                        it.uniqueSongs
                    )
                )
            }

            CategoryDimension.Song -> summary?.topSongs.orEmpty().map {
                val supportingParts = buildList {
                    add(stringResource(R.string.presentation_batch_g_stats_n_plays, it.playCount))
                    if (it.artist.isNotBlank()) {
                        add(it.artist)
                    }
                }
                CategoryMetricEntry(
                    label = it.title,
                    durationMs = it.totalDurationMs,
                    supporting = supportingParts.joinToString(separator = " • ")
                )
            }
        }.filter { it.durationMs > 0L }

        if (entries.isEmpty()) {
            StatsEmptyState(
                icon = Icons.Rounded.MusicNote,
                title = stringResource(R.string.presentation_batch_g_stats_empty_no_category_title),
                subtitle = stringResource(R.string.presentation_batch_g_stats_empty_no_category_subtitle)
            )
        } else {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = palette.containerColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = stringResource(selectedDimension.cardTitleRes),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = palette.contentColor
                    )
                    CategoryHorizontalBarChart(entries = entries, palette = palette)
                }
            }
        }
    }
}

@Composable
internal fun CategoryHorizontalBarChart(
    entries: List<CategoryMetricEntry>,
    palette: CategoryChartPalette,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val maxDuration = entries.maxOf { it.durationMs }.coerceAtLeast(1L)
    val metricStyle = rememberStatsMetricValueStyle(compact = false)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            val progress = (entry.durationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
            val isTop = index == 0
            val rowColor = if (isTop) {
                palette.accentColor.copy(alpha = 0.16f)
            } else {
                palette.contentColor.copy(alpha = 0.06f)
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = rowColor
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryRankBadge(
                            rank = index + 1,
                            accentColor = palette.accentColor,
                            accentOnColor = palette.accentOnColor,
                            highlighted = isTop
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.contentColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.supporting.isNotBlank()) {
                                Text(
                                    text = entry.supporting,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contentColor.copy(alpha = 0.76f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = formatListeningDurationCompact(entry.durationMs),
                            style = metricStyle,
                            fontWeight = if (isTop) FontWeight.SemiBold else FontWeight.Medium,
                            color = palette.contentColor
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (isTop) palette.accentColor else palette.accentColor.copy(alpha = 0.74f),
                        trackColor = palette.contentColor.copy(alpha = 0.18f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CategoryRankBadge(
    rank: Int,
    accentColor: Color,
    accentOnColor: Color,
    highlighted: Boolean
) {
    val metricStyle = rememberStatsMetricValueStyle(compact = false)
    val containerColor = if (highlighted) {
        accentColor
    } else {
        accentColor.copy(alpha = 0.24f)
    }
    val contentColor = if (highlighted) {
        accentOnColor
    } else {
        accentColor
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = metricStyle,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun categoryPaletteFor(dimension: CategoryDimension): CategoryChartPalette {
    return when (dimension) {
        CategoryDimension.Genre -> CategoryChartPalette(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            accentColor = MaterialTheme.colorScheme.tertiary,
            accentOnColor = MaterialTheme.colorScheme.onTertiary
        )

        CategoryDimension.Artist -> CategoryChartPalette(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            accentColor = MaterialTheme.colorScheme.primary,
            accentOnColor = MaterialTheme.colorScheme.onPrimary
        )

        CategoryDimension.Album -> CategoryChartPalette(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            accentColor = MaterialTheme.colorScheme.secondary,
            accentOnColor = MaterialTheme.colorScheme.onSecondary
        )

        CategoryDimension.Song -> CategoryChartPalette(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            accentColor = MaterialTheme.colorScheme.primary,
            accentOnColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}
