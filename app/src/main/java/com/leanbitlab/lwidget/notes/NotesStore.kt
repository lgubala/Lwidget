package com.leanbitlab.lwidget.notes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Note(val id: Long, val text: String, val pinned: Boolean, val updated: Long)

/** Sticky notes kept on the device, in Lwidget's own storage. */
object NotesStore {

    private const val PREFS = "lwidget_notes"
    private const val KEY = "notes"

    /** Pinned first, then most recently edited. */
    fun all(context: Context): List<Note> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Note(o.getLong("id"), o.getString("text"), o.optBoolean("pinned"), o.optLong("updated"))
            }.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(context: Context, id: Long): Note? = all(context).firstOrNull { it.id == id }

    /** Inserts or replaces by id, stamping the edit time. Returns the saved note. */
    fun save(context: Context, id: Long?, text: String, pinned: Boolean): Note {
        val now = System.currentTimeMillis()
        val note = Note(id ?: now, text, pinned, now)
        write(context, all(context).filterNot { it.id == note.id } + note)
        return note
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    private fun write(context: Context, notes: List<Note>) {
        val array = JSONArray()
        notes.forEach {
            array.put(JSONObject().put("id", it.id).put("text", it.text).put("pinned", it.pinned).put("updated", it.updated))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
