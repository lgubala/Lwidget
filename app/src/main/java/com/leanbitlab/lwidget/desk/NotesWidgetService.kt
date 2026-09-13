package com.leanbitlab.lwidget.desk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.WidgetPalette
import com.leanbitlab.lwidget.notes.Note
import com.leanbitlab.lwidget.notes.NoteEditorActivity
import com.leanbitlab.lwidget.notes.NotesStore

/** Feeds the Notes widget's scrolling grid. */
class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var notes: List<Note> = emptyList()
        private var palette = WidgetPalette.resolve(context)

        override fun onCreate() = Unit
        override fun onDestroy() = Unit

        override fun onDataSetChanged() {
            notes = NotesStore.all(context)
            palette = WidgetPalette.resolve(context)
        }

        override fun getCount() = notes.size
        override fun getItemId(position: Int) = notes.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true
        override fun getViewTypeCount() = 1
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_desk_note_item)
            val note = notes.getOrNull(position) ?: return views
            views.setTextViewText(R.id.note_text, note.text)
            views.setTextColor(R.id.note_text, palette.primary)
            views.setTextViewText(R.id.note_meta, NotesWidgetProvider.meta(context, note))
            views.setTextColor(R.id.note_meta, palette.label)
            views.setViewVisibility(R.id.note_pin, if (note.pinned) View.VISIBLE else View.GONE)
            views.setInt(R.id.note_pin, "setColorFilter", palette.secondary)
            views.setOnClickFillInIntent(
                R.id.note_item,
                Intent()
                    .setData(Uri.parse("lwidget://note/${note.id}"))
                    .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id)
            )
            return views
        }
    }
}
