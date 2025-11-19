package com.github.yeoli.devlog.domain.memo.service

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.intellij.mock.MockFileDocumentManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.Function
import java.awt.Point

class MemoServiceTest : BasePlatformTestCase() {

    fun test_메모_생성_성공() {
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
        val memo: Memo? = MemoService().createMemo(memoContent, project)
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

    fun test_메모_생성_선택없음() {
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
        val memo: Memo? = MemoService().createMemo(memoContent, project)
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

    fun test_메모_생성_에디터없음_예외() {
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
        val memo: Memo? = MemoService().createMemo("에디터 없음", project)
        assertNull(memo);
    }

    fun test_메모_생성_파일경로없음() {
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
        assertTrue("선택할 코드 스니펫을 찾지 못했습니다.", selectionStart >= 0)
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
            val memo: Memo? = MemoService().createMemo(memoContent, project)

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
}
