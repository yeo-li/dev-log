package com.github.yeoli.devlog.domain.memo.service

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.github.yeoli.devlog.domain.memo.repository.MemoRepository
import com.intellij.mock.MockFileDocumentManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.Function
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Point
import kotlin.test.assertTrue

class MemoServiceTest : BasePlatformTestCase() {

    private lateinit var memoRepository: MemoRepository

    override fun setUp() {
        super.setUp()

        // mock 생성
        memoRepository = mock()

        project.replaceService(
            MemoRepository::class.java,
            memoRepository,
            testRootDisposable
        )
    }

    fun `test 메모 생성 성공`() {
        // given
        val psiFile = myFixture.configureByText(
            "SampleFile.kt",
            """
            fun main() {
                val selected = 42
                println(selected)
            }
            """.trimIndent()
        )
        val memoContent = "테스트 메모"
        val editor = myFixture.editor
        val document = editor.document
        val targetSnippet = "val selected = 42"
        val selectionStart = document.text.indexOf(targetSnippet)
        assertTrue("선택할 코드 스니펫을 찾지 못했습니다.", selectionStart >= 0)
        val selectionEnd = selectionStart + targetSnippet.length
        editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val visibleArea = editor.scrollingModel.visibleAreaOnScrollingFinished
        val expectedVisibleStart = editor.xyToLogicalPosition(visibleArea.location).line
        val expectedVisibleEnd = editor.xyToLogicalPosition(
            Point(visibleArea.x, visibleArea.y + visibleArea.height)
        ).line

        // when
        val memo: Memo? = MemoService(project).createMemo(memoContent)
        // then
        if (memo != null) {
            assertEquals(memoContent, memo.content)
            assertEquals(targetSnippet, memo.selectedCodeSnippet)
            assertEquals(psiFile.virtualFile.path, memo.filePath)
            assertEquals(selectionStart, memo.selectionStart)
            assertEquals(selectionEnd, memo.selectionEnd)
            assertEquals(expectedVisibleStart, memo.visibleStart)
            assertEquals(expectedVisibleEnd, memo.visibleEnd)
            assertNull(memo.commitHash)
        } else {
            fail("memo가 null 입니다.")
        }

    }

    fun `test 메모 생성 선택없음`() {
        // given
        val psiFile = myFixture.configureByText(
            "SampleFile.kt",
            """
            fun main() {
                val selected = 42
                println(selected)
            }
            """.trimIndent()
        )
        val memoContent = "선택 없음 메모"
        val editor = myFixture.editor
        val document = editor.document
        val caretTarget = document.text.indexOf("println(selected)")
        assertTrue("커서를 이동할 코드를 찾지 못했습니다.", caretTarget >= 0)
        editor.caretModel.moveToOffset(caretTarget)
        editor.selectionModel.removeSelection()

        val visibleArea = editor.scrollingModel.visibleAreaOnScrollingFinished
        val expectedVisibleStart = editor.xyToLogicalPosition(visibleArea.location).line
        val expectedVisibleEnd = editor.xyToLogicalPosition(
            Point(visibleArea.x, visibleArea.y + visibleArea.height)
        ).line

        // when
        val memo: Memo? = MemoService(project).createMemo(memoContent)
        // then
        if (memo != null) {
            assertEquals(memoContent, memo.content)
            assertNull(memo.selectedCodeSnippet)
            assertEquals(caretTarget, memo.selectionStart)
            assertEquals(caretTarget, memo.selectionEnd)
            assertEquals(psiFile.virtualFile.path, memo.filePath)
            assertEquals(expectedVisibleStart, memo.visibleStart)
            assertEquals(expectedVisibleEnd, memo.visibleEnd)
            assertNull(memo.commitHash)
        } else {
            fail("memo가 null 입니다.")
        }
    }

    fun `test 메모 생성 에디터 없음 예외`() {
        // given
        val psiFile = myFixture.configureByText(
            "SampleFile.kt",
            """
            fun main() {}
            """.trimIndent()
        )
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.closeFile(psiFile.virtualFile)
        assertNull("선택된 에디터가 없어야 합니다.", fileEditorManager.selectedTextEditor)

        // expect
        val memo: Memo? = MemoService(project).createMemo("에디터 없음")
        assertNull(memo);
    }

    fun `test 메모 생성 파일경로 없음`() {
        // given
        myFixture.configureByText(
            "SampleFile.kt",
            """
            fun main() {
                val selected = 42
            }
            """.trimIndent()
        )
        val memoContent = "파일 경로 없음"
        val editor = myFixture.editor
        val document = editor.document
        val snippet = "val selected = 42"
        val selectionStart = document.text.indexOf(snippet)
        val selectionEnd = selectionStart + snippet.length
        editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val mockDisposable = Disposer.newDisposable()
        val mockFileDocumentManager = MockFileDocumentManagerImpl(
            null,
            Function { text -> EditorFactory.getInstance().createDocument(text) }
        )
        ApplicationManager.getApplication().replaceService(
            FileDocumentManager::class.java,
            mockFileDocumentManager,
            mockDisposable
        )

        try {
            // when
            val memo: Memo? = MemoService(project).createMemo(memoContent)

            // then
            if (memo != null) {
                assertEquals(memoContent, memo.content)
                assertEquals(snippet, memo.selectedCodeSnippet)
                assertNull("파일 경로가 null 이어야 합니다.", memo.filePath)
                assertEquals(selectionStart, memo.selectionStart)
                assertEquals(selectionEnd, memo.selectionEnd)
            } else {
                fail("memo가 null 입니다.")
            }

        } finally {
            Disposer.dispose(mockDisposable)
        }
    }

    // ========= 메모 조회 기능 =========
    fun `test 메모 전체 조회 기능 성공`() {
        // given
        val memo1 = Memo(
            id = System.currentTimeMillis(),
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now(),
            content = "메모1",
            commitHash = null,
            filePath = "/path/to/file1",
            selectedCodeSnippet = "snippet1",
            selectionStart = 0,
            selectionEnd = 5,
            visibleStart = 1,
            visibleEnd = 3
        )

        val memo2 = Memo(
            id = System.currentTimeMillis() + 1,
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now(),
            content = "메모2",
            commitHash = null,
            filePath = "/path/to/file2",
            selectedCodeSnippet = "snippet2",
            selectionStart = 10,
            selectionEnd = 20,
            visibleStart = 4,
            visibleEnd = 10
        )

        whenever(memoRepository.getAll()).thenReturn(listOf(memo1, memo2))

        // when
        val result = MemoService(project).getAllMemos()

        // then
        assertEquals(2, result.size)
        assertEquals("메모1", result[0].content)
        assertEquals("메모2", result[1].content)
        assertEquals("/path/to/file1", result[0].filePath)
        assertEquals("/path/to/file2", result[1].filePath)
    }

    fun `test 메모 전체 조회 기능 실패 - 예외 발생시 빈 리스트`() {
        // given
        whenever(memoRepository.getAll()).thenThrow(RuntimeException("DB error"))

        // when
        val result = MemoService(project).getAllMemos()

        // then
        assertTrue(result.isEmpty(), "예외 발생 시 빈 리스트를 반환해야 합니다.")
    }

}

