package com.github.yeoli.devlog.domain.memo.repository

import com.github.yeoli.devlog.domain.memo.domain.Memo
import java.time.LocalDateTime

data class MemoState(
    var id: Long = 0L,
    var createdAt: String = LocalDateTime.now().toString(),
    var updatedAt: String = LocalDateTime.now().toString(),
    var content: String = "",
    var commitHash: String? = null,
    var filePath: String? = null,
    var selectedCodeSnippet: String? = null,
    var fullCodeSnapshot: String? = null,
    var selectionStart: Int? = null,
    var selectionEnd: Int? = null,
    var visibleStart: Int? = null,
    var visibleEnd: Int? = null
) {
    fun toDomain(): Memo =
        Memo(
            id = this.id,
            createdAt = this.createdAt.let { LocalDateTime.parse(it) },
            updatedAt = this.updatedAt.let { LocalDateTime.parse(it) },
            content = content,
            commitHash = commitHash,
            filePath = filePath,
            selectedCodeSnippet = selectedCodeSnippet,
            fullCodeSnapshot = fullCodeSnapshot,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            visibleStart = visibleStart,
            visibleEnd = visibleEnd
        )
}
