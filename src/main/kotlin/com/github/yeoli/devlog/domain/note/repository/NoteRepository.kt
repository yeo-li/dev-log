package com.github.yeoli.devlog.domain.note.repository

import com.github.yeoli.devlog.domain.note.domain.Note
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.time.LocalDateTime

@State(
    name = "DevLogNoteStorage",
    storages = [Storage("devlog-note.xml")]
)
@Service(Service.Level.PROJECT)
class NoteRepository : PersistentStateComponent<NoteState> {

    private var state: NoteState? = NoteState(
        content = "",
        updatedAt = LocalDateTime.now().toString()
    )

    override fun getState(): NoteState? = state

    override fun loadState(state: NoteState) {
        this.state = state
    }

    fun getNote(): Note {
        if (state == null) {
            state = Note(
                content = ""
            ).toState()
        }
        return state!!.toDomain()
    }

    fun updateNote(updatedNote: Note) {
        this.state = updatedNote.toState()
    }
}