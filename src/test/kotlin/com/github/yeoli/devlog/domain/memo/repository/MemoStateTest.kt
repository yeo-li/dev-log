package com.github.yeoli.devlog.domain.memo.repository

import com.github.yeoli.devlog.domain.memo.domain.Memo
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoStateTest {

    @Test
    fun `test 도메인 변환 성공`() {
        // given
        val memoState = MemoState(
            id = 0L,
            createdAt = LocalDateTime.now().toString(),
            updatedAt = LocalDateTime.now().toString(),
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
        val memo: Memo = memoState.toDomain()

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
