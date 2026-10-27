package com.nobodybuilds.listens.data.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives focus phase-end alarms. A broadcast (unlike a foreground-service PendingIntent) is
 * delivered even when the app is in the background or the process was killed; the controller
 * then advances the phase, chimes and posts the notification without starting a foreground service.
 */
@AndroidEntryPoint
class FocusAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: FocusController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PHASE_ALARM) return
        val pending = goAsync()
        controller.onAlarmBroadcast { runCatching { pending.finish() } }
    }

    companion object {
        const val ACTION_PHASE_ALARM = "com.nobodybuilds.listens.focus.PHASE_ALARM"
    }
}
