package com.leanbitlab.lwidget.desk

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.pm.PackageManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import com.leanbitlab.lwidget.AwidgetProvider
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.WidgetPalette

/** Right page of the fold desk: open Tasks.org tasks. Tapping opens Tasks.org to tick them off. */
class TasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        private val ROWS = listOf(
            R.id.task_row_0 to R.id.task_text_0, R.id.task_row_1 to R.id.task_text_1,
            R.id.task_row_2 to R.id.task_text_2, R.id.task_row_3 to R.id.task_text_3,
            R.id.task_row_4 to R.id.task_text_4, R.id.task_row_5 to R.id.task_text_5
        )
        private val BOXES = listOf(R.id.task_box_0, R.id.task_box_1, R.id.task_box_2, R.id.task_box_3, R.id.task_box_4, R.id.task_box_5)

        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_desk_tasks)
            val palette = WidgetPalette.resolve(context)
            views.setTextColor(R.id.tasks_label, palette.label)
            views.setTextColor(R.id.tasks_count, palette.label)
            views.setTextColor(R.id.tasks_empty, palette.secondary)

            val tasksApp = context.packageManager.getLaunchIntentForPackage("org.tasks")
            val canRead = tasksApp != null && (
                context.checkSelfPermission(AwidgetProvider.PERMISSION_READ_TASKS_ORG) == PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(AwidgetProvider.PERMISSION_READ_TASKS_ASTRID) == PackageManager.PERMISSION_GRANTED
                )
            tasksApp?.let { views.setOnClickPendingIntent(R.id.tasks_root, DeskWidgets.activity(context, it, 300)) }

            val tasks = if (canRead) AwidgetProvider.fetchActiveTasks(context, ROWS.size + 20) else emptyList()
            val message = when {
                tasksApp == null -> R.string.desk_tasks_install
                !canRead -> R.string.desk_tasks_permission
                tasks.isEmpty() -> R.string.desk_tasks_none
                else -> null
            }
            views.setViewVisibility(R.id.tasks_empty, if (message != null) View.VISIBLE else View.GONE)
            message?.let { views.setTextViewText(R.id.tasks_empty, context.getString(it)) }
            views.setTextViewText(R.id.tasks_count, if (tasks.isEmpty()) "" else context.getString(R.string.desk_tasks_open, tasks.size))

            ROWS.forEachIndexed { i, (rowId, textId) ->
                val task = tasks.getOrNull(i)
                views.setViewVisibility(rowId, if (task == null) View.GONE else View.VISIBLE)
                if (task == null) return@forEachIndexed
                val due = AwidgetProvider.formatDueSuffix(task.dueMillis)
                val text = SpannableStringBuilder(task.title)
                if (due.isNotBlank()) {
                    val start = text.length
                    text.append(due)
                    text.setSpan(ForegroundColorSpan(palette.label), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                views.setTextViewText(textId, text)
                views.setTextColor(textId, palette.primary)
                views.setInt(BOXES[i], "setColorFilter", palette.secondary)
            }
            return views
        }
    }
}
