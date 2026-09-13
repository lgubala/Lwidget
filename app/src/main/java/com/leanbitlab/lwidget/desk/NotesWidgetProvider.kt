package com.leanbitlab.lwidget.desk

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.WidgetPalette
import com.leanbitlab.lwidget.notes.Note
import com.leanbitlab.lwidget.notes.NoteEditorActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Right page of the fold desk: a scrolling grid of sticky notes. */
class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_desk_notes)
            val palette = WidgetPalette.resolve(context)

            views.setTextColor(R.id.notes_label, palette.label)
            views.setTextColor(R.id.notes_add, palette.secondary)
            views.setOnClickPendingIntent(R.id.notes_add, newNote(context))

            views.setTextColor(R.id.notes_empty, palette.secondary)
            views.setOnClickPendingIntent(R.id.notes_empty, newNote(context))

            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.notes_grid, Intent(context, NotesWidgetService::class.java))
            views.setEmptyView(R.id.notes_grid, R.id.notes_empty)

            // Each tile fills in its own note id; the template has no data so the tile's URI applies
            val template = Intent(context, NoteEditorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(
                R.id.notes_grid,
                PendingIntent.getActivity(context, 201, template, mutable or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            return views
        }

        internal fun meta(context: Context, note: Note): String {
            if (note.pinned) return context.getString(R.string.note_meta_pinned)
            val date = Instant.ofEpochMilli(note.updated).atZone(ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now()
            return when {
                date == today -> context.getString(R.string.note_meta_today)
                date == today.minusDays(1) -> context.getString(R.string.note_meta_yesterday)
                date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()
                else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())).uppercase()
            }
        }

        private fun newNote(context: Context) = DeskWidgets.activity(
            context,
            Intent(context, NoteEditorActivity::class.java)
                .setData(Uri.parse("lwidget://note/new"))
                .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, -1L)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK),
            200
        )
    }
}
