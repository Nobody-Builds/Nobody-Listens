package com.nobodybuilds.listens.data.smart

import com.nobodybuilds.listens.data.crash.SafeMode
import android.content.Context
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nobodybuilds.listens.data.database.SongFeaturesDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Background indexer for smart features. Runs only while charging + idle + battery not low,
 * and additionally checks the battery is at least 50 %. Analyses up to [BATCH_SIZE] local songs
 * per run, one at a time, and chains another run (same constraints) while songs remain.
 */
@HiltWorker
class SmartIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: SongFeaturesDao,
    private val preferences: SmartPreferences
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!preferences.isEnabled()) return@withContext Result.success()
        if (SafeMode.isActive(applicationContext)) return@withContext Result.success()
        if (!batteryOk(applicationContext)) {
            Timber.d("Smart: battery below %d%%, skipping", MIN_BATTERY_PERCENT)
            return@withContext Result.success()
        }
        if (!runLock.tryLock()) return@withContext Result.success()
        try {
            dao.deleteOrphans()
            val retryFailedBefore = System.currentTimeMillis() - FAILED_RETRY_AFTER_MS
            val pending = dao.getPendingSongs(SmartModel.MODEL_VERSION, retryFailedBefore, BATCH_SIZE)
            if (pending.isEmpty()) {
                preferences.setLastIndexedAt(System.currentTimeMillis())
                return@withContext Result.success()
            }
            var done = 0
            var written = 0
            setProgress(workDataOf(KEY_DONE to 0, KEY_BATCH to pending.size))
            SongAnalyzer(applicationContext).use { analyzer ->
                for (song in pending) {
                    if (isStopped) break
                    if (done > 0 && done % 10 == 0 && !batteryOk(applicationContext)) break
                    val result = analyzer.analyze(song) { isStopped }
                    // null: cancelled or a transient error; the song stays pending for a later run.
                    if (result != null) {
                        dao.upsert(result)
                        written++
                    }
                    done++
                    setProgress(workDataOf(KEY_DONE to done, KEY_BATCH to pending.size))
                }
            }
            preferences.setLastIndexedAt(System.currentTimeMillis())
            // Chain only when this run made progress, so songs hitting transient errors don't loop.
            if (!isStopped && done == pending.size && written > 0 &&
                dao.countPendingSongs(SmartModel.MODEL_VERSION, retryFailedBefore) > 0
            ) {
                SmartIndexScheduler.enqueueContinuation(applicationContext)
            }
            Result.success()
        } catch (e: SmartAnalysisUnavailableException) {
            // The ML runtime couldn't load or run: nothing was marked failed, try again later.
            Timber.w(e, "Smart: analysis unavailable, retrying later")
            Result.retry()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Timber.e(t, "Smart: indexing run failed")
            Result.success()
        } finally {
            runLock.unlock()
        }
    }

    companion object {
        const val KEY_DONE = "done"
        const val KEY_BATCH = "batch"
        const val BATCH_SIZE = 40
        const val MIN_BATTERY_PERCENT = 50
        /** Songs whose decode failed are retried after this long. */
        val FAILED_RETRY_AFTER_MS = TimeUnit.DAYS.toMillis(14)
        private val runLock = Mutex()

        fun batteryOk(context: Context): Boolean {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return level <= 0 || level >= MIN_BATTERY_PERCENT // level <= 0: property unsupported
        }
    }
}

object SmartIndexScheduler {
    const val UNIQUE_PERIODIC = "lmusic_smart_index"
    const val UNIQUE_CONTINUATION = "lmusic_smart_index_next"

    private fun constraints() = Constraints.Builder()
        .setRequiresCharging(true)
        .setRequiresDeviceIdle(true)
        .setRequiresBatteryNotLow(true)
        .build()

    fun schedule(context: Context) {
        // Safe mode keeps Smart indexing off for this session; the saved setting is untouched.
        if (SafeMode.isActive(context)) return
        val request = PeriodicWorkRequestBuilder<SmartIndexWorker>(12, TimeUnit.HOURS, 3, TimeUnit.HOURS)
            .setConstraints(constraints())
            .addTag(UNIQUE_PERIODIC)
            .build()
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        // Kick off a first pass as soon as the device is charging + idle.
        enqueueContinuation(context, ExistingWorkPolicy.KEEP)
    }

    fun enqueueContinuation(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE) {
        if (SafeMode.isActive(context)) return
        val request = OneTimeWorkRequestBuilder<SmartIndexWorker>()
            .setConstraints(constraints())
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag(UNIQUE_PERIODIC)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_CONTINUATION, policy, request)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(UNIQUE_PERIODIC)
        wm.cancelUniqueWork(UNIQUE_CONTINUATION)
    }
}
