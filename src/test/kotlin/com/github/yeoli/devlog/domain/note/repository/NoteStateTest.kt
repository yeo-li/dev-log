package com.github.yeoli.devlog.domain.note.repository

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.LocalDateTime
import kotlin.test.Test

class NoteStateTest {
    @Test
    fun `test toDomain - content와 updatedAt이 올바르게 변환된다`() {
        // given
        val nowString = "2025-01-01T12:00:00"
        val state = NoteState(
            content = "Hello Note",
            updatedAt = nowString
        )

        // when
        val note = state.toDomain()

        // then
        assertEquals("Hello Note", note.content)
        assertEquals(LocalDateTime.parse(nowString), note.updatedAt)
    }

    @Test
    fun `test toDomain - 빈 content도 정상적으로 변환된다`() {
        // given
        val nowString = LocalDateTime.now().toString()
        val state = NoteState(
            content = "",
            updatedAt = nowString
        )

        // when
        val note = state.toDomain()

        // then
        assertEquals("", note.content)
        assertEquals(LocalDateTime.parse(nowString), note.updatedAt)
    }

    @Test
    fun `test toDomain - updatedAt 문자열 포맷이 LocalDateTime으로 파싱 가능해야 한다`() {
        // given
        val formattedTime = "2024-12-31T23:59:00"
        val state = NoteState("TimeCheck", formattedTime)

        // when & then
        assertDoesNotThrow {
            state.toDomain()
        }
    }
}