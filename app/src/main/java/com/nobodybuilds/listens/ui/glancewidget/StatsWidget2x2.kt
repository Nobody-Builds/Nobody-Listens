package com.nobodybuilds.listens.ui.glancewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.drop
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StatsWidgetEntryPoint {
    fun playbackStatsRepository(): PlaybackStatsRepository
}

internal data class ListeningToday(val todayMs: Long, val streakDays: Int)

internal object ListeningTodayCalculator {
    /** Today's listening time and the run of consecutive listening days ending today (or yesterday). */
    fun compute(events: List<PlaybackStatsRepository.PlaybackEvent>, zone: ZoneId = ZoneId.systemDefault()): ListeningToday {
        val byDay = HashMap<LocalDate, Long>()
        events.forEach { e ->
            val at = e.endTimestamp ?: e.timestamp
            val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            byDay[day] = (byDay[day] ?: 0L) + e.durationMs.coerceAtLeast(0L)
        }
        val today = LocalDate.now(zone)
        val listened = { d: LocalDate -> (byDay[d] ?: 0L) >= MIN_DAY_MS }
        var day = if (listened(today)) today else today.minusDays(1)
        var streak = 0
        while (listened(day) && streak < 3650) {
            streak++
            day = day.minusDays(1)
        }
        return ListeningToday(byDay[today] ?: 0L, streak)
    }

    fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000L
        return when {
            minutes < 1 -> "0 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    private const val MIN_DAY_MS = 60_000L
}

/** 2x2 stats: today's listening time and the current listening streak. */
class StatsWidget2x2 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, StatsWidgetEntryPoint::class.java)
                .playbackStatsRepository()
        }.getOrNull()
        val initial = loadSafely(repo)
        provideContent {
            // Recomputes whenever a play is recorded while the Glance session is open.
            val data by produceState(initial) {
                repo?.refreshFlow?.drop(1)?.collect { value = loadSafely(repo) }
            }
            GlanceTheme(colors = NbGlanceColors) { Content(data) }
        }
    }

    private suspend fun loadSafely(repo: PlaybackStatsRepository?): ListeningToday =
        runCatching { repo?.let { ListeningTodayCalculator.compute(it.exportEventsForBackup()) } }
            .onFailure { Timber.w(it, "Stats widget: load failed") }
            .getOrNull() ?: ListeningToday(0L, 0)

    @Composable
    private fun Content(data: ListeningToday) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(NbWidget.Radius)
                .padding(14.dp)
                .clickable(NbWidget.openApp()),
        ) {
            WidgetOverline("Today")
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = ListeningTodayCalculator.formatDuration(data.todayMs),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            WidgetMeta("listened", size = 11.sp)
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .background(if (data.streakDays > 0) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(999.dp)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = when (data.streakDays) {
                        0 -> "Start a streak today"
                        1 -> "1-day streak"
                        else -> "${data.streakDays}-day streak"
                    },
                    style = TextStyle(
                        color = if (data.streakDays > 0) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Refreshes the stats widget a few times a day (so "today" rolls over) — battery-not-low only. */
class StatsWidgetWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { StatsWidget2x2().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "lmusic_stats_widget_refresh"

        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<StatsWidgetWorker>(4, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }.onFailure { Timber.w(it, "Stats widget: schedule failed") }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
        }
    }
}

class StatsWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget2x2()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        StatsWidgetWorker.schedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        StatsWidgetWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        StatsWidgetWorker.cancel(context)
    }
}
