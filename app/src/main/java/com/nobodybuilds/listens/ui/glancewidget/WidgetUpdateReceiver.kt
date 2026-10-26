package com.nobodybuilds.listens.ui.glancewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.nobodybuilds.listens.ACTION_WIDGET_UPDATE_PLAYBACK_STATE") return

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            try {
                val glanceAppWidgetManager = GlanceAppWidgetManager(context)

                PlayerWidgetUpdater.playerWidgets().forEach { widget ->
                    glanceAppWidgetManager.getGlanceIds(widget.javaClass).forEach { glanceId ->
                        widget.update(context, glanceId)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("WidgetUpdateReceiver").e(e, "Error updating widgets")
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
