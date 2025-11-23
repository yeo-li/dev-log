package com.github.yeoli.devlog.domain.note.domain

import com.github.yeoli.devlog.domain.note.repository.NoteState
import java.time.LocalDateTime

class Note(
    val content: String,
    val updatedAt: LocalDateTime
) {

    constructor(content: String) : this(content, LocalDateTime.now())

    fun update(content: String): Note {
        return Note(
            content = content
        )
    }

    fun toState(): NoteState {
        return NoteState(
            content = this.content,
            updatedAt = this.updatedAt.toString()
        )
    }
}