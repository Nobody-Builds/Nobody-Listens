@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import com.nobodybuilds.listens.utils.formatListeningDurationCompact
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.stats.displayNameRes
import androidx.compose.material.icons.rounded.Album

internal enum class TimelineMetric(
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
    val extractValue: (PlaybackStatsRepository.TimelineEntry) -> Double,
) {
    ListeningTime(
        displayNameRes = R.string.presentation_batch_g_stats_timeline_metric_listening_time,
        descriptionRes = R.string.presentation_batch_g_stats_timeline_metric_listening_time_desc,
        extractValue = { it.totalDurationMs.toDouble() },
    ),
    PlayCount(
        displayNameRes = R.string.presentation_batch_g_stats_timeline_metric_play_count,
        descriptionRes = R.string.presentation_batch_g_stats_timeline_metric_play_count_desc,
        extractValue = { it.playCount.toDouble() },
    ),
    AverageSession(
        displayNameRes = R.string.presentation_batch_g_stats_timeline_metric_avg_session,
        descriptionRes = R.string.presentation_batch_g_stats_timeline_metric_avg_session_desc,
        extractValue = { entry ->
            if (entry.playCount > 0) entry.totalDurationMs.toDouble() / entry.playCount.toDouble() else 0.0
        },
    )
}

@Composable
internal fun TimelineMetric.formatEntryValue(entry: PlaybackStatsRepository.TimelineEntry): String = when (this) {
    TimelineMetric.ListeningTime -> formatListeningDurationCompact(entry.totalDurationMs)
    TimelineMetric.PlayCount -> stringResource(R.string.presentation_batch_g_stats_n_plays, entry.playCount)
    TimelineMetric.AverageSession -> {
        val average = if (entry.playCount > 0) entry.totalDurationMs / entry.playCount else 0L
        formatListeningDurationCompact(average)
    }
}

internal enum class CategoryDimension(
    @StringRes val displayNameRes: Int,
    @StringRes val cardTitleRes: Int,
) {
    Genre(
        displayNameRes = R.string.presentation_batch_g_stats_dim_genre,
        cardTitleRes = R.string.presentation_batch_g_stats_card_listening_by_genre,
    ),
    Artist(
        displayNameRes = R.string.presentation_batch_g_stats_dim_artist,
        cardTitleRes = R.string.presentation_batch_g_stats_card_listening_by_artist,
    ),
    Album(
        displayNameRes = R.string.presentation_batch_g_stats_dim_album,
        cardTitleRes = R.string.presentation_batch_g_stats_card_listening_by_album,
    ),
    Song(
        displayNameRes = R.string.presentation_batch_g_stats_dim_song,
        cardTitleRes = R.string.presentation_batch_g_stats_card_listening_by_song,
    )
}

internal data class CategoryMetricEntry(
    val label: String,
    val durationMs: Long,
    val supporting: String
)

internal enum class TimelineChartLayout {
    Vertical,
    Horizontal
}

internal data class TimelineChartSpec(
    val layout: TimelineChartLayout,
    val minItemWidth: Dp,
    val maxItemWidth: Dp,
    val maxVisibleItems: Int,
    val chartHeight: Dp,
    val labelMaxLines: Int,
    val horizontalContentPadding: Dp
)

internal data class CategoryChartPalette(
    val containerColor: Color,
    val contentColor: Color,
    val accentColor: Color,
    val accentOnColor: Color
)

internal data class TrackShareSlice(
    val label: String,
    val durationMs: Long,
    val color: Color
)

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun rememberStatsSectionTitleStyle(): TextStyle {
    return remember {
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(570)
                    )
                )
            ),
            fontWeight = FontWeight(570),
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun rememberStatsAxisLabelStyle(range: StatsTimeRange): TextStyle {
    val fontSize = when (range) {
        StatsTimeRange.DAY, StatsTimeRange.WEEK -> 10.sp
        StatsTimeRange.MONTH, StatsTimeRange.ALL -> 11.sp
        StatsTimeRange.YEAR, StatsTimeRange.CUSTOM -> 10.sp
    }
    return remember(range, fontSize) {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(520)
                    )
                )
            ),
            fontWeight = FontWeight(520),
            fontSize = fontSize,
            lineHeight = if (range == StatsTimeRange.YEAR) 12.sp else 11.sp,
            letterSpacing = 0.sp
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun rememberStatsMetricValueStyle(compact: Boolean): TextStyle {
    return remember(compact) {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.genre_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(650),
                        FontVariation.width(95f),
                        FontVariation.grade(30)
                    )
                )
            ),
            fontWeight = FontWeight(650),
            fontSize = if (compact) 10.sp else 12.sp,
            lineHeight = if (compact) 12.sp else 14.sp,
            letterSpacing = 0.sp
        )
    }
}
