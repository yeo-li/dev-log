package com.github.yeoli.devlog.domain.note.repository

import com.github.yeoli.devlog.domain.note.domain.Note
import java.time.LocalDateTime

data class NoteState(
    val content: String,
    val updatedAt: String
) {

    fun toDomain(): Note {
        return Note(content, updatedAt = this.updatedAt.let { LocalDateTime.parse(it) })
    }
}