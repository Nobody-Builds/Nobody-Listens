package com.nobodybuilds.listens.data.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nobodybuilds.listens.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/**
 * Keeps a focus session alive while it runs and shows its countdown notification.
 *
 * Why a `specialUse` foreground service: a session lasts up to hours and is often silent, so
 * `mediaPlayback` doesn't fit (music, when chosen, is played by the main player or the lofi
 * service, which have their own media notifications), `shortService` is capped at ~3 minutes and
 * `dataSync` is wrong and time-limited on Android 15. The service holds no wake lock: the
 * countdown is drawn by the system chronometer (no per-second updates) and phase ends are
 * delivered by an AlarmManager alarm (exact when allowed), so the CPU can sleep in between.
 * The timer state is saved with wall-clock end times, so a killed process can catch up.
 */
@AndroidEntryPoint
class FocusService : Service() {

    @Inject lateinit var controller: FocusController

    private var inForeground = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val handle = object : FocusServiceHandle {
        override fun refresh() = updateNotification()
        override fun finish(summary: String?) = finishSession(summary)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        controller.service = handle
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Satisfy the startForegroundService() contract first; without the foreground, stop.
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val action = intent?.action
        scope.launch {
            // A cold-started process (alarm / notification action) restores the saved session
            // before acting on it; a no-op once restored.
            runCatching { controller.restore() }.onFailure { Timber.tag(TAG).w(it, "Focus restore failed") }
            when (action) {
                ACTION_TOGGLE -> controller.togglePause()
                ACTION_QUICK_START -> controller.quickStart() // home-screen widget
                ACTION_SKIP -> controller.skip()
                ACTION_STOP -> controller.stop()
                ACTION_ALARM -> controller.onAlarm() // alarms armed by older versions
                else -> Unit
            }
            if (!controller.state.value.phase.isActive) finishSession(null)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (controller.service === handle) controller.service = null
        scope.cancel()
        super.onDestroy()
    }

    /** Returns false when the service couldn't enter the foreground (the caller then stops). */
    private fun goForeground(): Boolean {
        if (inForeground) return true
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            inForeground = true
        }.onFailure { Timber.tag(TAG).w(it, "startForeground failed, stopping") }
        return inForeground
    }

    private fun updateNotification() {
        if (!inForeground) return
        val s = controller.state.value
        if (!s.phase.isActive) return
        runCatching { nm().notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun finishSession(summary: String?) {
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        if (summary != null) runCatching { nm().notify(DONE_NOTIFICATION_ID, buildDoneNotification(summary)) }
        stopSelf()
    }

    private fun buildNotification(): Notification = buildSessionNotification(this, controller.state.value)

    private fun buildDoneNotification(summary: String): Notification = buildDoneNotification(this, summary)

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() = ensureChannel(nm())

    companion object {
        private const val TAG = "FocusService"
        const val CHANNEL_ID = "lmusic_focus"
        private const val NOTIFICATION_ID = 0x0F0C
        private const val DONE_NOTIFICATION_ID = 0x0F0D
        const val ACTION_SHOW = "com.nobodybuilds.listens.focus.SHOW"
        const val ACTION_TOGGLE = "com.nobodybuilds.listens.focus.TOGGLE"
        const val ACTION_SKIP = "com.nobodybuilds.listens.focus.SKIP"
        const val ACTION_STOP = "com.nobodybuilds.listens.focus.STOP"
        const val ACTION_ALARM = "com.nobodybuilds.listens.focus.ALARM"
        /** Starts a session with the saved length and music (widget); resumes a paused one. */
        const val ACTION_QUICK_START = "com.nobodybuilds.listens.focus.QUICK_START"

        /** The session countdown notification (also posted without the service after a background alarm). */
        internal fun buildSessionNotification(context: Context, s: FocusTimerState): Notification {
            val now = System.currentTimeMillis()
            val title = when (s.phase) {
                FocusPhase.Focus -> "Focus · round ${s.round} of ${s.config.rounds}"
                FocusPhase.ShortBreak -> "Short break"
                FocusPhase.LongBreak -> "Long break"
                else -> "Focus"
            }
            val endsAt = s.phaseEndsAt
            val text = when {
                endsAt != null -> "Until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endsAt))}"
                s.isPaused -> "Paused · ${formatClock(s.remainingMs(now))} left"
                else -> "Getting ready"
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.nobodybuilds.listens.shared.R.drawable.nl_mark_mono)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(launchIntent(context))
            if (endsAt != null) {
                builder.setWhen(endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            } else {
                builder.setShowWhen(false).setUsesChronometer(false)
            }
            builder.addAction(
                if (s.isRunning) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24,
                if (s.isRunning) "Pause" else "Resume",
                action(context, ACTION_TOGGLE, 1)
            )
            builder.addAction(R.drawable.rounded_skip_next_24, if (s.phase.isBreak) "Skip break" else "Skip", action(context, ACTION_SKIP, 2))
            builder.addAction(R.drawable.rounded_close_24, "Stop", action(context, ACTION_STOP, 3))
            return builder.build()
        }

        internal fun buildDoneNotification(context: Context, summary: String): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.nobodybuilds.listens.shared.R.drawable.nl_mark_mono)
                .setContentTitle("Nice work")
                .setContentText(summary)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(launchIntent(context))
                .build()

        /**
         * Posts the session (or "done") notification while [FocusService] isn't running, e.g. after a
         * phase alarm in a cold-started process, where starting a foreground service isn't allowed.
         */
        internal fun notifyWithoutService(context: Context, s: FocusTimerState, summary: String?) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            runCatching {
                ensureChannel(nm)
                if (s.phase.isActive) {
                    nm.notify(NOTIFICATION_ID, buildSessionNotification(context, s))
                } else {
                    nm.cancel(NOTIFICATION_ID)
                    if (summary != null) nm.notify(DONE_NOTIFICATION_ID, buildDoneNotification(context, summary))
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not post focus notification") }
        }

        private fun launchIntent(context: Context): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }

        private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode,
                Intent(context, FocusService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        private fun ensureChannel(nm: NotificationManager) {
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Focus sessions", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Focus timer countdown and controls"
                    setShowBadge(false)
                }
            )
        }

        fun formatClock(ms: Long): String {
            val totalSec = (ms + 999) / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val sec = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
        }
    }
}
