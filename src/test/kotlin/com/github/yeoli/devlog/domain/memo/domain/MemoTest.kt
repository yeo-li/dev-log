package com.github.yeoli.devlog.domain.memo.domain

import com.github.yeoli.devlog.domain.memo.repository.MemoState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
            fullCodeSnapshot = "full code",
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
                fullCodeSnapshot = "full code",
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
                fullCodeSnapshot = "full code",
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
            fullCodeSnapshot = "full code",
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

    @Test
    fun `test buildMemoBlock - 정상적으로 문자열 생성`() {
        val created = LocalDateTime.of(2025, 11, 22, 13, 11, 10)
        val updated = LocalDateTime.of(2025, 11, 22, 13, 12, 0)

        val memo = Memo(
            id = 1L,
            createdAt = created,
            updatedAt = updated,
            content = "메모 내용입니다.",
            commitHash = "abc123",
            filePath = "/path/to/file",
            selectedCodeSnippet = "println(\"Hello\")",
            selectionStart = 0,
            selectionEnd = 5,
            visibleStart = 1,
            visibleEnd = 10
        )

        val block = memo.buildMemoBlock(1)

        assertTrue(block.contains("# Memo 1"))
        assertTrue(block.contains("📅 생성 시간 : ${created.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}"))
        assertTrue(block.contains("📅 수정 시간 : ${updated.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}"))
        assertTrue(block.contains("📌 Content"))
        assertTrue(block.contains("메모 내용입니다."))
        assertTrue(block.contains("Commit: abc123"))
        assertTrue(block.contains("File Path: /path/to/file"))
        assertTrue(block.contains("Visible Lines: 1 ~ 10"))
        assertTrue(block.contains("println(\"Hello\")"))
    }

    @Test
    fun `test buildMemoBlock - 모든 값이 있을 때 정상적으로 표시되는지`() {
        val created = LocalDateTime.of(2025, 11, 22, 13, 11, 10)
        val updated = LocalDateTime.of(2025, 11, 22, 13, 15, 30)

        val memo = Memo(
            id = 2L,
            createdAt = created,
            updatedAt = updated,
            content = "전체 필드 테스트",
            commitHash = "ff12aa",
            filePath = "/full/path/file.kt",
            selectedCodeSnippet = "val x = 10",
            fullCodeSnapshot = "full code",
            selectionStart = 3,
            selectionEnd = 9,
            visibleStart = 2,
            visibleEnd = 12
        )

        val block = memo.buildMemoBlock(2)

        assertTrue(block.contains("# Memo 2"))
        assertTrue(block.contains("📅 생성 시간 : ${created.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}"))
        assertTrue(block.contains("📅 수정 시간 : ${updated.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}"))
        assertTrue(block.contains("전체 필드 테스트"))
        assertTrue(block.contains("Commit: ff12aa"))
        assertTrue(block.contains("File Path: /full/path/file.kt"))
        assertTrue(block.contains("Visible Lines: 2 ~ 12"))
        assertTrue(block.contains("val x = 10"))
    }

    @Test
    fun `test buildMemoBlock - null 값들이 기본값으로 표시되는지`() {
        val created = LocalDateTime.of(2025, 11, 22, 13, 11, 10)

        val memo = Memo(
            id = 1L,
            createdAt = created,
            updatedAt = created,
            content = "내용",
            commitHash = null,
            filePath = null,
            selectedCodeSnippet = null,
            fullCodeSnapshot = null,
            selectionStart = null,
            selectionEnd = null,
            visibleStart = null,
            visibleEnd = null
        )

        val block = memo.buildMemoBlock(0)

        assertTrue(block.contains("Commit: N/A"))
        assertTrue(block.contains("File Path: N/A"))
        assertTrue(block.contains("Visible Lines: ? ~ ?"))
        assertTrue(block.contains("(no selected code)"))
    }
}
