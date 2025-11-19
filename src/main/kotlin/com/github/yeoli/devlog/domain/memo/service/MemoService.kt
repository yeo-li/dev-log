package com.github.yeoli.devlog.domain.memo.service

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import java.awt.Point

class MemoService {

    fun createMemo(content: String, project: Project): Memo? {
        val editor = getActiveEditor(project) ?: return null

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

        return Memo(
            content = content,
            commitHash = commitHash,
            filePath = filePath,
            selectedCodeSnippet = selectedCodeSnippet,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            visibleStart = visibleStartLine,
            visibleEnd = visibleEndLine
        )
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