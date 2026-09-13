package com.leanbitlab.lwidget.media

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** The fold desk's player: same sessions and controls as the Blueprint one, softer layout. */
class SoftPlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MediaWidgetProvider.updateAll(context)
    }
}
