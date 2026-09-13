package com.leanbitlab.lwidget.desk

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.leanbitlab.lwidget.R

/** Refresh hub for the fold desk widgets (Today, Notes, Tasks, Search). */
object DeskWidgets {

    /** Everything on the desk; cheap when none of them are placed. */
    fun updateAll(context: Context) {
        updateToday(context)
        updateNotes(context)
        updateTasks(context)
        updateSearch(context)
    }

    fun updateToday(context: Context) = update(context, TodayWidgetProvider::class.java) { TodayWidgetProvider.build(it) }
    fun updateNotes(context: Context) {
        update(context, NotesWidgetProvider::class.java) { NotesWidgetProvider.build(it) }
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NotesWidgetProvider::class.java))
        @Suppress("DEPRECATION")
        if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.notes_grid)
    }
    fun updateTasks(context: Context) = update(context, TasksWidgetProvider::class.java) { TasksWidgetProvider.build(it) }
    fun updateSearch(context: Context) = update(context, SearchWidgetProvider::class.java) { SearchWidgetProvider.build(it) }

    private fun update(context: Context, provider: Class<*>, build: (Context) -> RemoteViews) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, build(context))
    }

    internal fun activity(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Launch intent for the first installed package, else the fallback if anything handles it. */
    internal fun firstAvailable(context: Context, packages: List<String>, fallback: Intent?): Intent? {
        val pm = context.packageManager
        packages.forEach { pkg -> pm.getLaunchIntentForPackage(pkg)?.let { return it } }
        return fallback?.takeIf { it.resolveActivity(pm) != null }
    }
}
