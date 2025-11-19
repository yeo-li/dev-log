package com.github.yeoli.devlog.domain.memo.service

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.ibm.icu.impl.IllegalIcuArgumentException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import java.awt.Point

class MemoService {

    private val logger = Logger.getInstance(MemoService::class.java)

    fun createMemo(content: String, project: Project): Memo? {
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
}