package com.github.yeoli.devlog.domain.memo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoTest {

    @Test
    fun test_Memo_생성_성공() {
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
    fun test_Memo_생성_실패_selection_범위() {
        assertFailsWith<IllegalArgumentException> {
            Memo(
                content = "잘못된 메모",
                selectionStart = 10,
                selectionEnd = 5
            )
        }
    }

    @Test
    fun test_Memo_생성_실패_visible_범위() {
        assertFailsWith<IllegalArgumentException> {
            Memo(
                content = "보이는 영역 오류",
                visibleStart = 20,
                visibleEnd = 10
            )
        }
    }
}
