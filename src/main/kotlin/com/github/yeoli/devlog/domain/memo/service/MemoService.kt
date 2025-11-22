package com.github.yeoli.devlog.domain.memo.service

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.github.yeoli.devlog.domain.memo.repository.MemoRepository
import com.ibm.icu.impl.IllegalIcuArgumentException
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import java.awt.Point
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class MemoService(private val project: Project) {

    private val memoRepository = project.getService(MemoRepository::class.java)

    private val logger = Logger.getInstance(MemoService::class.java)

    fun createMemo(content: String): Memo? {
        val editor = getActiveEditor(project)
        if (editor == null) {
            logger.warn("[createMemo] editor가 null이므로 null을 반환합니다.")
            return null
        }

        val selectionModel = editor.selectionModel
        val document = editor.document

        val selectedCodeSnippet = selectionModel.selectedText

        val selectionStart = selectionModel.selectionStart
        val selectionEnd = selectionModel.selectionEnd

        val visibleArea = editor.scrollingModel.visibleAreaOnScrollingFinished
        val visibleStartLine = editor.xyToLogicalPosition(visibleArea.location).line
        val visibleEndLine = editor.xyToLogicalPosition(
            Point(visibleArea.x, visibleArea.y + visibleArea.height)
        ).line

        val virtualFile = FileDocumentManager.getInstance().getFile(document)
        val filePath = virtualFile?.path

        val commitHash = getCurrentCommitHash(project)

        if (content.isBlank()) {
            logger.warn("[createMemo] content가 blanck 이므로 null을 반환합니다.")
            return null
        }

        val memo: Memo
        try {
            memo = Memo(
                content = content,
                commitHash = commitHash,
                filePath = filePath,
                selectedCodeSnippet = selectedCodeSnippet,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
                visibleStart = visibleStartLine,
                visibleEnd = visibleEndLine
            )
        } catch (e: IllegalIcuArgumentException) {
            logger.warn("[createMemo] Memo 생성에 실패하여 null을 반환합니다.(사유: " + e.message + ")")
            return null;
        }

        return memo
    }

    private fun getActiveEditor(project: Project): Editor? {
        return FileEditorManager.getInstance(project).selectedTextEditor
    }

    private fun getCurrentCommitHash(project: Project): String? {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repo = repoManager.repositories.firstOrNull() ?: return null
        return repo.currentRevision
    }

    fun saveMemo(memo: Memo) {
        try {
            memoRepository.save(memo)
        } catch (e: Exception) {
            logger.warn("[saveMemo] 메모 저장 중 알 수 없는 에러가 발생했습니다. ${e.message}", e)
        }
    }

    fun getAllMemos(): List<Memo> {
        try {
            return memoRepository.getAll()
        } catch (e: Exception) {
            logger.warn("[getAllMemos] 메모 조회 중 알 수 없는 에러가 발생했습니다. ${e.message}", e)
        }

        return mutableListOf()
    }

    fun removeMemos(memos: List<Memo>) {
        if (memos.isEmpty()) return

        try {
            val ids: List<Long> = memos.map { it.id }
            memoRepository.removeMemosById(ids)
        } catch (e: Exception) {
            logger.warn("[removeMemos] 메모 삭제 중 알 수 없는 에러가 발생했습니다. ${e.message}", e)
        }
    }

    fun updateMemo(memoId: Long, newContent: String) {
        try {
            val memo: Memo = memoRepository.findMemoById(memoId) ?: return
            val updated = memo.update(newContent)
            memoRepository.removeMemoById(memoId)
            memoRepository.save(updated)
        } catch (e: Exception) {
            logger.warn("[updateMemo] 메모 수정 중 알 수 없는 에러가 발생했습니다. ${e.message}", e)
        }
    }

    fun buildHeader(): String {
        val projectName = project.name
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        return """
            ========== DEV LOG ==========
            
            # 요약 정보
            💻 프로젝트 명: $projectName
            ⏰ 추출 시간: $now
            
            ---------------------------------------
        """.trimIndent()
    }
}