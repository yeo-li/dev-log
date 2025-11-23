package com.github.yeoli.devlog.domain.note.service

import com.github.yeoli.devlog.domain.note.domain.Note
import com.github.yeoli.devlog.domain.note.repository.NoteRepository
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteServiceTest {

    private val project: Project = mock(Project::class.java)
    private val noteRepository: NoteRepository = mock(NoteRepository::class.java)

    @Test
    fun test_레포지토리와_동일한_노트를_반환한다() {
        // given
        val now = LocalDateTime.now()
        val expectedNote = Note("Hello Test", now)

        whenever(noteRepository.getNote()).thenReturn(expectedNote)

        val service = NoteService(project)
        val noteServiceField = service.javaClass.getDeclaredField("noteRepository")
        noteServiceField.isAccessible = true
        noteServiceField.set(service, noteRepository)

        // when
        val actual = service.getNote()

        // then
        assertEquals(expectedNote, actual)
    }

    @Test
    fun test_기본_빈_노트를_반환한다() {
        // given
        val defaultNote = Note("")
        whenever(noteRepository.getNote()).thenReturn(defaultNote)

        val service = NoteService(project)
        val noteServiceField = service.javaClass.getDeclaredField("noteRepository")
        noteServiceField.isAccessible = true
        noteServiceField.set(service, noteRepository)

        // when
        val actual = service.getNote()

        // then
        assertEquals(defaultNote, actual)
    }
}