package com.github.yeoli.devlog.domain.note.service

import com.github.yeoli.devlog.domain.note.domain.Note
import com.github.yeoli.devlog.domain.note.repository.NoteRepository
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteServiceTest {

    private val project: Project = mock(Project::class.java)
    private val noteRepository: NoteRepository = mock(NoteRepository::class.java)

    private fun createService(): NoteService {
        val service = NoteService(project)
        val field = service.javaClass.getDeclaredField("noteRepository")
        field.isAccessible = true
        field.set(service, noteRepository)
        return service
    }

    @Test
    fun `test 레포지토리와 동일한 노트를 반환한다`() {
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
    fun `test 기본 빈 노트를 반환한다`() {
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

    // ========= updateNote 테스트 =========

    @Test
    fun `test 내용이 변경되면 업데이트한다`() {
        // given
        val oldNote = Note("old", LocalDateTime.now())
        whenever(noteRepository.getNote()).thenReturn(oldNote)

        val service = createService()

        val newContent = "new"

        // when
        service.updateNote(newContent)

        // then
        verify(noteRepository, times(1)).updateNote(
            check { updated ->
                assertEquals(newContent, updated.content)
            }
        )
    }

    @Test
    fun `test 내용이 동일하면 업데이트하지 않는다`() {
        // given
        val sameNote = Note("same", LocalDateTime.now())
        whenever(noteRepository.getNote()).thenReturn(sameNote)

        val service = createService()

        // when
        service.updateNote("same")

        // then
        verify(noteRepository, never()).updateNote(any())
    }
}
