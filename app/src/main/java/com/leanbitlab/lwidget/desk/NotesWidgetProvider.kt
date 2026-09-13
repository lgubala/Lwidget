package com.leanbitlab.lwidget.desk

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.WidgetPalette
import com.leanbitlab.lwidget.notes.Note
import com.leanbitlab.lwidget.notes.NoteEditorActivity
import com.leanbitlab.lwidget.notes.NotesStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Right page of the fold desk: a grid of sticky notes. */
class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        private data class Cell(val cell: Int, val text: Int, val meta: Int, val pin: Int)

        private val CELLS = listOf(
            Cell(R.id.note_cell_0, R.id.note_text_0, R.id.note_meta_0, R.id.note_pin_0),
            Cell(R.id.note_cell_1, R.id.note_text_1, R.id.note_meta_1, R.id.note_pin_1),
            Cell(R.id.note_cell_2, R.id.note_text_2, R.id.note_meta_2, R.id.note_pin_2),
            Cell(R.id.note_cell_3, R.id.note_text_3, R.id.note_meta_3, R.id.note_pin_3)
        )

        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_desk_notes)
            val palette = WidgetPalette.resolve(context)
            val notes = NotesStore.all(context)

            views.setTextColor(R.id.notes_label, palette.label)
            views.setTextColor(R.id.notes_add, palette.secondary)
            views.setOnClickPendingIntent(R.id.notes_add, editor(context, null))

            views.setViewVisibility(R.id.notes_empty, if (notes.isEmpty()) View.VISIBLE else View.GONE)
            views.setTextColor(R.id.notes_empty, palette.secondary)
            views.setOnClickPendingIntent(R.id.notes_empty, editor(context, null))

            views.setViewVisibility(R.id.notes_row_0, if (notes.isEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.notes_row_1, if (notes.size > 2) View.VISIBLE else View.GONE)

            CELLS.forEachIndexed { i, cell ->
                val note = notes.getOrNull(i)
                // An empty slot keeps its half of the row so a lone note doesn't stretch across
                views.setViewVisibility(cell.cell, if (note == null) View.INVISIBLE else View.VISIBLE)
                if (note == null) return@forEachIndexed
                views.setTextViewText(cell.text, note.text)
                views.setTextColor(cell.text, palette.primary)
                views.setTextViewText(cell.meta, meta(context, note))
                views.setTextColor(cell.meta, palette.label)
                views.setViewVisibility(cell.pin, if (note.pinned) View.VISIBLE else View.GONE)
                views.setInt(cell.pin, "setColorFilter", palette.secondary)
                views.setOnClickPendingIntent(cell.cell, editor(context, note))
            }

            val hidden = notes.size - CELLS.size
            views.setViewVisibility(R.id.notes_more, if (hidden > 0) View.VISIBLE else View.GONE)
            if (hidden > 0) {
                views.setTextViewText(R.id.notes_more, context.getString(R.string.notes_more, hidden))
                views.setTextColor(R.id.notes_more, palette.label)
            }
            return views
        }

        private fun meta(context: Context, note: Note): String {
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

        private fun editor(context: Context, note: Note?) = DeskWidgets.activity(
            context,
            Intent(context, NoteEditorActivity::class.java)
                // A distinct URI per note keeps each cell's PendingIntent from overwriting the others
                .setData(Uri.parse("lwidget://note/${note?.id ?: "new"}"))
                .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note?.id ?: -1L)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK),
            if (note == null) 200 else 201
        )
    }
}
