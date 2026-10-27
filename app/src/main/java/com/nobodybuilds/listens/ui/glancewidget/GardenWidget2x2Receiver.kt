package com.nobodybuilds.listens.ui.glancewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.nobodybuilds.listens.data.garden.GardenWidgetScheduler

class GardenWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GardenWidget2x2()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GardenWidgetScheduler.schedule(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        GardenWidgetScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        GardenWidgetScheduler.cancel(context)
    }
}
