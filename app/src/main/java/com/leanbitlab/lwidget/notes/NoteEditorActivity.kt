package com.leanbitlab.lwidget.notes

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.desk.DeskWidgets

/** A small floating editor opened from the Notes widget. */
class NoteEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        val existing = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
            .takeIf { it > 0 }
            ?.let { NotesStore.get(this, it) }

        val title = findViewById<TextView>(R.id.note_editor_title)
        val text = findViewById<EditText>(R.id.note_editor_text)
        val pin = findViewById<MaterialSwitch>(R.id.note_editor_pin)
        val delete = findViewById<View>(R.id.note_editor_delete)

        title.setText(if (existing == null) R.string.note_new else R.string.note_edit)
        existing?.let {
            text.setText(it.text)
            text.setSelection(it.text.length)
            pin.isChecked = it.pinned
        }
        delete.visibility = if (existing == null) View.GONE else View.VISIBLE

        text.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        findViewById<View>(R.id.note_editor_cancel).setOnClickListener { finish() }

        delete.setOnClickListener {
            existing?.let { NotesStore.delete(this, it.id) }
            DeskWidgets.updateNotes(this)
            finish()
        }

        findViewById<View>(R.id.note_editor_save).setOnClickListener {
            val content = text.text.toString().trim()
            when {
                content.isNotEmpty() -> NotesStore.save(this, existing?.id, content, pin.isChecked)
                // Saving an emptied note removes it rather than leaving a blank card
                existing != null -> NotesStore.delete(this, existing.id)
            }
            DeskWidgets.updateNotes(this)
            finish()
        }
    }
}
