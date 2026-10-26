package com.nobodybuilds.listens.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.session.MediaSession
import timber.log.Timber

/** The sleep timer: an alarm after a number of minutes, or a pause at the end of the current song. */
internal class SleepTimerScheduler(
    private val context: Context,
    private val sessionProvider: () -> MediaSession?
) {
    private val mediaSession: MediaSession? get() = sessionProvider()
    private val packageName: String get() = context.packageName
    private val alarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    var endOfTrackTimerSongId: String? = null

    private fun createSleepTimerPendingIntent(): PendingIntent {
        val intent = Intent(context, SleepTimerReceiver::class.java).apply {
            setPackage(packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun cancelDurationSleepTimerInternal() {
        alarmManager.cancel(createSleepTimerPendingIntent())
    }

    fun setDurationSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimers()
            return
        }
        endOfTrackTimerSongId = null
        val triggerAtMillis = System.currentTimeMillis() + (minutes * 60_000L)
        val pendingIntent = createSleepTimerPendingIntent()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
            } else
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            Timber.tag(TAG).d("Sleep timer set from Wear for %d minutes", minutes)
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "Exact alarm denied; using inexact sleep timer")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun setEndOfTrackSleepTimer(enabled: Boolean) {
        if (!enabled) {
            endOfTrackTimerSongId = null
            Timber.tag(TAG).d("End-of-track timer disabled from Wear")
            return
        }
        cancelDurationSleepTimerInternal()
        val currentSongId = mediaSession?.player?.currentMediaItem?.mediaId
        if (currentSongId.isNullOrBlank()) {
            endOfTrackTimerSongId = null
            Timber.tag(TAG).d("End-of-track timer ignored: no active song")
            return
        }
        endOfTrackTimerSongId = currentSongId
        Timber.tag(TAG).d("End-of-track timer set from Wear for mediaId=%s", currentSongId)
    }

    fun cancelSleepTimers() {
        cancelDurationSleepTimerInternal()
        endOfTrackTimerSongId = null
        Timber.tag(TAG).d("Sleep timers cancelled from Wear")
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
    }
}
