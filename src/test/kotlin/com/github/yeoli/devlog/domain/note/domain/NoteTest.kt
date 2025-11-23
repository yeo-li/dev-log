package com.github.yeoli.devlog.domain.note.domain

import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime
import kotlin.test.Test

class NoteTest {

    @Test
    fun `test 업데이트 시 새로운 인스턴스를 반환해야 한다`() {
        val original = Note(
            content = "hello"
        )

        val updated = original.update("new content")

        assertNotSame(original, updated)
    }

    @Test
    fun `test 업데이트 시 콘텐츠가 변경되어야 한다`() {
        val original = Note(
            content = "old content"
        )

        val updated = original.update("new content")

        assertEquals("new content", updated.content)
        assertNotEquals(original.content, updated.content)
    }

    @Test
    fun `test 빈 문자열로 업데이트할 때 정상적으로 처리되어야 한다`() {
        val original = Note(
            content = "something"
        )

        val updated = original.update("")

        assertEquals("", updated.content)
    }

    // =========== toState 테스트 ===========
    @Test
    fun `test toState - content와 updatedAt이 올바르게 변환된다`() {
        // given
        val now = LocalDateTime.of(2025, 1, 1, 12, 0)
        val note = Note("Hello", now)

        // when
        val state = note.toState()

        // then
        assertEquals("Hello", state.content)
        assertEquals(now.toString(), state.updatedAt)
    }

    @Test
    fun `test toState - empty content도 정상 변환된다`() {
        // given
        val note = Note("")

        // when
        val state = note.toState()

        // then
        assertEquals("", state.content)
        assertNotNull(state.updatedAt)
    }

    @Test
    fun `test toState - updatedAt이 현재 시간이 아닐 수 있다`() {
        // given
        val customTime = LocalDateTime.of(2024, 12, 31, 23, 59)
        val note = Note("Time test", customTime)

        // when
        val state = note.toState()

        // then
        assertEquals(customTime.toString(), state.updatedAt)
    }
}
