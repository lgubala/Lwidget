package com.leanbitlab.lwidget.desk

import android.app.SearchManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.WidgetPalette

/** A plain search pill for the fold desk. */
class SearchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_desk_search)
            val palette = WidgetPalette.resolve(context)
            views.setTextColor(R.id.search_hint, palette.secondary)
            views.setInt(R.id.search_icon, "setColorFilter", palette.secondary)

            // Whatever the phone uses for search (usually the Google app), else a browser web search
            DeskWidgets.firstAvailable(
                context, emptyList(),
                Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
            )?.let { views.setOnClickPendingIntent(R.id.search_root, DeskWidgets.activity(context, it, 400)) }
                ?: DeskWidgets.firstAvailable(context, emptyList(), Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, ""))
                    ?.let { views.setOnClickPendingIntent(R.id.search_root, DeskWidgets.activity(context, it, 400)) }
            return views
        }
    }
}
