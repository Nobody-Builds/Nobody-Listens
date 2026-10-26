package com.nobodybuilds.listens.data.garden

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nobodybuilds.listens.ui.glancewidget.GardenWidget2x2
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** Redraws every Garden widget once a day, just after midnight, so "Day N" moves on. */
class GardenWidgetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { GardenWidget2x2().updateAll(applicationContext) }
            .onFailure { Timber.w(it, "Garden widget: daily update failed") }
        return Result.success()
    }
}

object GardenWidgetScheduler {
    private const val WORK_NAME = "lmusic_garden_widget_daily"

    /** Unique + KEEP, so calling this from every widget update is cheap and idempotent. */
    fun schedule(context: Context) {
        runCatching {
            val now = LocalDateTime.now()
            val nextRun = now.toLocalDate().plusDays(1).atTime(0, 10)
            val delayMs = Duration.between(now, nextRun).toMillis().coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
            val request = PeriodicWorkRequestBuilder<GardenWidgetWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }.onFailure { Timber.w(it, "Garden widget: schedule failed") }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    }
}

/**
 * Called after each recorded play. Refreshes the Garden widget only when today has just become
 * a listening day (and only if a Garden widget is on the home screen) — at most once a day.
 */
@Singleton
class GardenWidgetRefresher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: GardenRepository,
) {
    suspend fun onListeningRecorded() {
        // Stats and recently played widgets change with every recorded play (throttled there).
        runCatching { com.nobodybuilds.listens.ui.glancewidget.ExtraWidgetUpdater.onListeningRecorded(context) }
        runCatching {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(GardenWidget2x2::class.java)
            if (ids.isEmpty()) return
            val today = LocalDate.now().toEpochDay()
            val prefs = repository.preferences
            if (prefs.widgetListeningDay() == today) return
            val snapshot = repository.snapshot()
            if (!snapshot.todayCounted) return
            prefs.setWidgetListeningDay(today)
            GardenWidget2x2().updateAll(context)
        }.onFailure { Timber.w(it, "Garden widget: refresh failed") }
    }
}
