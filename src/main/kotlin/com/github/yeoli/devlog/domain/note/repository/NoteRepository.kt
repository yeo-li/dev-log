package com.github.yeoli.devlog.domain.note.repository

import com.github.yeoli.devlog.domain.note.domain.Note
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "DevLogNoteStorage",
    storages = [Storage("devlog-note.xml")]
)
@Service(Service.Level.PROJECT)
class NoteRepository : PersistentStateComponent<NoteStorageState> {

    private var state: NoteStorageState = NoteStorageState()

    override fun getState(): NoteStorageState? = state

    override fun loadState(state: NoteStorageState) {
        this.state = state
    }

    fun getNote(): Note {
        return state.noteState.toDomain()
    }

    fun updateNote(updatedNote: Note) {
        this.state.noteState = updatedNote.toState()
    }
}
