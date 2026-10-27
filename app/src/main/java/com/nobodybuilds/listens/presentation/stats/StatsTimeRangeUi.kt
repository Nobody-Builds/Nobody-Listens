package com.nobodybuilds.listens.presentation.stats

import androidx.annotation.StringRes
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.StatsTimeRange

@StringRes
fun StatsTimeRange.displayNameRes(): Int = when (this) {
    StatsTimeRange.DAY -> R.string.presentation_batch_g_stats_range_today
    StatsTimeRange.WEEK -> R.string.presentation_batch_g_stats_range_week_to_date
    StatsTimeRange.MONTH -> R.string.presentation_batch_g_stats_range_month_to_date
    StatsTimeRange.YEAR -> R.string.presentation_batch_g_stats_range_year_to_date
    StatsTimeRange.ALL -> R.string.presentation_batch_g_stats_range_all_time
    StatsTimeRange.CUSTOM -> R.string.nobody_listens_stats_range_custom
}
