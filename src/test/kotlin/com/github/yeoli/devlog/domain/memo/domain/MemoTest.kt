package com.github.yeoli.devlog.domain.memo.domain

import com.github.yeoli.devlog.domain.memo.repository.MemoState
import kotlin.test.*

class MemoTest {

    @Test
    fun `test Memo 생성 성공`() {
        // given & then
        val memo = Memo(
            content = "테스트 메모",
            commitHash = "abc123",
            filePath = "/path/SampleFile.kt",
            selectedCodeSnippet = "val selected = 42",
            selectionStart = 5,
            selectionEnd = 10,
            visibleStart = 1,
            visibleEnd = 20
        )

        // then
        assertEquals("테스트 메모", memo.content)
        assertEquals("abc123", memo.commitHash)
        assertEquals("/path/SampleFile.kt", memo.filePath)
        assertEquals("val selected = 42", memo.selectedCodeSnippet)
        assertEquals(5, memo.selectionStart)
        assertEquals(10, memo.selectionEnd)
        assertEquals(1, memo.visibleStart)
        assertEquals(20, memo.visibleEnd)
        assertTrue(memo.id > 0)
        assertNotNull(memo.createdAt)
        assertNotNull(memo.updatedAt)
    }

    @Test
    fun `test Memo 생성 실패 selection 범위`() {
        assertFailsWith<IllegalArgumentException> {
            Memo(
                content = "잘못된 메모",
                commitHash = "abc123",
                filePath = "/path/SampleFile.kt",
                selectedCodeSnippet = "val selected = 42",
                selectionStart = 10,
                selectionEnd = 5,
                visibleStart = null,
                visibleEnd = null
            )
        }
    }

    @Test
    fun `test Memo 생성 실패 visible 범위`() {
        // when & then
        assertFailsWith<IllegalArgumentException> {
            Memo(
                content = "잘못된 메모",
                commitHash = "abc123",
                filePath = "/path/SampleFile.kt",
                selectedCodeSnippet = "val selected = 42",
                selectionStart = 5,
                selectionEnd = 10,
                visibleStart = 20,
                visibleEnd = 10
            )
        }
    }

    @Test
    fun `test MemoState 변환 성공`() {
        // given
        val memo = Memo(
            content = "테스트 메모",
            commitHash = "abc123",
            filePath = "/path/SampleFile.kt",
            selectedCodeSnippet = "val selected = 42",
            selectionStart = 5,
            selectionEnd = 10,
            visibleStart = 1,
            visibleEnd = 20
        )

        // when
        val memoState: MemoState = memo.toState()

        // then
        assertEquals(memo.id, memoState.id)
        assertEquals(memo.createdAt.toString(), memoState.createdAt)
        assertEquals(memo.updatedAt.toString(), memoState.updatedAt)
        assertEquals("테스트 메모", memoState.content)
        assertEquals("abc123", memoState.commitHash)
        assertEquals("/path/SampleFile.kt", memoState.filePath)
        assertEquals("val selected = 42", memoState.selectedCodeSnippet)
        assertEquals(5, memoState.selectionStart)
        assertEquals(10, memoState.selectionEnd)
        assertEquals(1, memoState.visibleStart)
        assertEquals(20, memoState.visibleEnd)
        assertTrue(memoState.id > 0)
        assertNotNull(memoState.createdAt)
        assertNotNull(memoState.updatedAt)
    }
}
