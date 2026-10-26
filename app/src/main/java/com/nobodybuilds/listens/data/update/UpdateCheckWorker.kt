package com.nobodybuilds.listens.data.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Weekly background check for a newer Nobody Listens release. */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateChecker: UpdateChecker,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (updateChecker.check(notify = true)) {
            is UpdateCheckResult.Failed -> if (runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "lmusic_update_check"
    }
}

@Singleton
class UpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Schedules (KEEP) or cancels the weekly check. */
    fun apply(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(UpdateCheckWorker.WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
