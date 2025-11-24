package com.github.yeoli.devlog.domain.note.repository

import com.github.yeoli.devlog.domain.note.domain.Note
import java.time.LocalDateTime

data class NoteState(
    var content: String = "",
    var updatedAt: String = LocalDateTime.now().toString()
) {

    fun toDomain(): Note {
        return Note(content, updatedAt = this.updatedAt.let { LocalDateTime.parse(it) })
    }
}