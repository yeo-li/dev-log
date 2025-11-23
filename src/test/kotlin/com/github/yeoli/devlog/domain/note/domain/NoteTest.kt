package com.github.yeoli.devlog.domain.note.domain

import org.junit.jupiter.api.Assertions.*
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

    @Test
    fun `test 동일한 콘텐츠로 업데이트할 때 updatedAt이 갱신되지 않아야 한다`() {
        val now = java.time.LocalDateTime.now()
        val original = Note(
            content = "same"
        )

        val updated = original.update("same")

        assertEquals(original.updatedAt, updated.updatedAt)
    }
}