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
import java.time.LocalDateTime
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
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
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
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
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

    // ========= 메모 삭제 기능 =========
    fun `test 메모 삭제 기능 - 정상 삭제`() {
        val now = LocalDateTime.now()
        val memo1 = Memo(
            id = 1L,
            createdAt = now,
            updatedAt = now,
            content = "a",
            commitHash = null,
            filePath = "/path/to/file1",
            selectedCodeSnippet = null,
            selectionStart = null,
            selectionEnd = null,
            visibleStart = null,
            visibleEnd = null
        )
        val memo2 = Memo(
            id = 2L,
            createdAt = now,
            updatedAt = now,
            content = "b",
            commitHash = null,
            filePath = "/path/to/file2",
            selectedCodeSnippet = null,
            selectionStart = null,
            selectionEnd = null,
            visibleStart = null,
            visibleEnd = null
        )
        val memos = listOf(memo1, memo2)

        MemoService(project).removeMemos(memos)

        org.mockito.kotlin.verify(memoRepository)
            .removeMemosById(listOf(1L, 2L))
    }

    fun `test 메모 삭제 기능 - 빈 리스트는 Repository를 호출하지 않음`() {
        MemoService(project).removeMemos(emptyList())

        org.mockito.kotlin.verify(memoRepository, org.mockito.kotlin.never())
            .removeMemosById(org.mockito.kotlin.any())
    }

    fun `test 메모 삭제 기능 - Repository 예외 발생해도 서비스는 throw 하지 않음`() {
        val now = LocalDateTime.now()
        val memo = Memo(
            id = 10L,
            createdAt = now,
            updatedAt = now,
            content = "x",
            commitHash = null,
            filePath = "/path/to/file",
            selectedCodeSnippet = null,
            selectionStart = null,
            selectionEnd = null,
            visibleStart = null,
            visibleEnd = null
        )

        whenever(
            memoRepository.removeMemosById(listOf(10L))
        ).thenThrow(RuntimeException("DB error"))

        val result = runCatching {
            MemoService(project).removeMemos(listOf(memo))
        }
        assertTrue(result.isSuccess, "예외가 발생하면 안 됩니다.")
    }

    // ========= 메모 수정 기능 =========
    fun `test 메모 수정 성공`() {
        // given
        val now = LocalDateTime.now()
        val original = Memo(
            id = 1L,
            createdAt = now,
            updatedAt = now,
            content = "old",
            commitHash = null,
            filePath = "/path/file",
            selectedCodeSnippet = "snippet",
            selectionStart = 0,
            selectionEnd = 5,
            visibleStart = 1,
            visibleEnd = 3
        )
        whenever(memoRepository.findMemoById(1L)).thenReturn(original)

        val updated = original.update(content = "new")
        whenever(memoRepository.save(updated)).thenAnswer {}

        // when
        MemoService(project).updateMemo(1L, "new")

        // then
        org.mockito.kotlin.verify(memoRepository).removeMemoById(1L)
        org.mockito.kotlin.verify(memoRepository).save(org.mockito.kotlin.check {
            assertEquals("new", it.content)
        })
    }

    fun `test 조회 실패 시 아무 것도 하지 않음`() {
        // given
        whenever(memoRepository.findMemoById(999L)).thenReturn(null)

        // when
        MemoService(project).updateMemo(999L, "new")

        // then
        org.mockito.kotlin.verify(memoRepository, org.mockito.kotlin.never())
            .removeMemoById(org.mockito.kotlin.any())
        org.mockito.kotlin.verify(memoRepository, org.mockito.kotlin.never())
            .save(org.mockito.kotlin.any())
    }

    fun `test udpate 적용된 필드 검증`() {
        // given
        val createdAt = LocalDateTime.now().minusDays(1)
        val original = Memo(
            id = 1L,
            createdAt = createdAt,
            updatedAt = createdAt,
            content = "before",
            commitHash = "abc",
            filePath = "/path",
            selectedCodeSnippet = "code",
            selectionStart = 10,
            selectionEnd = 20,
            visibleStart = 5,
            visibleEnd = 15
        )
        whenever(memoRepository.findMemoById(1L)).thenReturn(original)

        val service = MemoService(project)

        // when
        service.updateMemo(1L, "after")

        // then
        org.mockito.kotlin.verify(memoRepository).save(org.mockito.kotlin.check { updated ->
            assertEquals(1L, updated.id)
            assertEquals("after", updated.content)

            assertEquals(original.createdAt, updated.createdAt)
            assertEquals(original.commitHash, updated.commitHash)
            assertEquals(original.filePath, updated.filePath)
            assertEquals(original.selectedCodeSnippet, updated.selectedCodeSnippet)
            assertEquals(original.selectionStart, updated.selectionStart)
            assertEquals(original.selectionEnd, updated.selectionEnd)
            assertEquals(original.visibleStart, updated.visibleStart)
            assertEquals(original.visibleEnd, updated.visibleEnd)
        })
    }

    // ========= 메모 추출 기능 =========

    fun `test buildHeader - 프로젝트명과 시간 포함`() {
        // given
        val service = MemoService(project)

        // when
        val header = service.buildHeader()

        // then
        assertTrue(header.contains("========== DEV LOG =========="))
        assertTrue(header.contains("💻 프로젝트 명: ${project.name}"))
        assertTrue(header.contains("⏰ 추출 시간:"))
        val regex = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
        assertTrue(regex.containsMatchIn(header), "날짜 포맷이 잘못되었습니다.")
        assertTrue(header.contains("---------------------------------------"))
    }

    fun `test buildExportText - 빈 메모 리스트`() {
        // given
        val service = MemoService(project)

        // when
        val export = service.buildExportText(emptyList())

        // then
        assertTrue(export.contains("========== DEV LOG =========="))
        assertTrue(export.contains("(내보낼 메모가 없습니다.)"))
    }

    fun `test buildExportText - 단일 메모 포함`() {
        val now = LocalDateTime.of(2025, 1, 1, 10, 20, 30)

        val memo = Memo(
            id = 1L,
            createdAt = now,
            updatedAt = now,
            content = "hello",
            commitHash = "abc123",
            filePath = "/path/file",
            selectedCodeSnippet = "val a = 1",
            selectionStart = 0,
            selectionEnd = 10,
            visibleStart = 3,
            visibleEnd = 5
        )

        val service = MemoService(project)

        // when
        val export = service.buildExportText(listOf(memo))

        // then: 헤더 포함
        assertTrue(export.contains("========== DEV LOG =========="))
        assertTrue(export.contains("💻 프로젝트 명: ${project.name}"))

        // 메모 블록 정보 포함 확인
        assertTrue(export.contains("# Memo 1"))
        assertTrue(export.contains("📌 Content"))
        assertTrue(export.contains("hello"))
        assertTrue(export.contains("abc123"))
        assertTrue(export.contains("/path/file"))
        assertTrue(export.contains("val a = 1"))
        assertTrue(export.contains("3 ~ 5"))
    }

    fun `test buildExportText - 여러 메모 순서대로 출력`() {
        val now = LocalDateTime.of(2025, 1, 1, 9, 0, 0)

        val memo1 = Memo(
            id = 1L,
            createdAt = now,
            updatedAt = now,
            content = "first",
            commitHash = null,
            filePath = "/f1",
            selectedCodeSnippet = null,
            selectionStart = 0,
            selectionEnd = 0,
            visibleStart = 1,
            visibleEnd = 1
        )

        val memo2 = Memo(
            id = 2L,
            createdAt = now.plusHours(1),
            updatedAt = now.plusHours(1),
            content = "second",
            commitHash = null,
            filePath = "/f2",
            selectedCodeSnippet = null,
            selectionStart = 10,
            selectionEnd = 10,
            visibleStart = 2,
            visibleEnd = 2
        )

        val service = MemoService(project)

        // when
        val export = service.buildExportText(listOf(memo1, memo2))

        // then
        assertTrue(export.contains("# Memo 1"))
        assertTrue(export.contains("# Memo 2"))

        // 순서 보장
        val index1 = export.indexOf("# Memo 1")
        val index2 = export.indexOf("# Memo 2")
        assertTrue(index1 < index2, "Memo 1이 Memo 2보다 먼저 출력되어야 합니다.")
    }

    fun `test buildExportText - memoBlock 필드 검증`() {
        val now = LocalDateTime.of(2025, 1, 1, 12, 34, 56)

        val memo = Memo(
            id = 10L,
            createdAt = now,
            updatedAt = now,
            content = "내용입니다",
            commitHash = null,
            filePath = null,
            selectedCodeSnippet = null,
            selectionStart = 100,
            selectionEnd = 200,
            visibleStart = 3,
            visibleEnd = 8
        )

        val service = MemoService(project)

        // when
        val export = service.buildExportText(listOf(memo))

        // then
        // 날짜 체크
        assertTrue(export.contains("2025-01-01 12:34:56"))
        // content
        assertTrue(export.contains("내용입니다"))
        // null 매핑
        assertTrue(export.contains("- Commit: N/A"))
        assertTrue(export.contains("- File Path: N/A"))
        assertTrue(export.contains("(no selected code)"))
        // visible range
        assertTrue(export.contains("3 ~ 8"))
    }

    fun `test exportToTxt - 파일 생성 및 내용 저장`() {
        // given
        val service = MemoService(project)
        val text = "Hello DevLog Export Test"

        // IntelliJ 테스트용 임시 디렉토리 생성 (VirtualFile)
        val exportDir = myFixture.tempDirFixture.findOrCreateDir("exports")

        // when
        val exported = service.exportToTxt(text, exportDir)

        // then
        assertNotNull(exported)
        assertTrue(exported!!.exists())
        assertTrue(exported.name.startsWith("devlog-${project.name}-"))

        val content = String(exported.contentsToByteArray(), Charsets.UTF_8)
        assertEquals(text, content)
    }

}
