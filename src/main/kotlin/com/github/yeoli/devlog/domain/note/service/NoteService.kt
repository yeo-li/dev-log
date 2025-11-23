package com.github.yeoli.devlog.domain.note.service

import com.github.yeoli.devlog.domain.note.domain.Note
import com.github.yeoli.devlog.domain.note.repository.NoteRepository
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NoteService(private val project: Project) {

    private val noteRepository =
        project.getService<NoteRepository>(NoteRepository::class.java)

    fun getNote(): Note {
        return noteRepository.getNote()
    }
}

