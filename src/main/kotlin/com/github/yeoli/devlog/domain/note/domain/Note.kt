package com.github.yeoli.devlog.domain.note.domain

import java.time.LocalDateTime

class Note constructor(
    val content: String,
    val updatedAt: LocalDateTime
) {

    constructor(content: String) : this(content, LocalDateTime.now())

    fun update(content: String): Note {
        return Note(
            content = content
        )
    }
}