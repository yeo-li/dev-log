package com.github.yeoli.devlog.domain.memo.domain

import com.github.yeoli.devlog.domain.memo.repository.MemoState
import java.time.LocalDateTime

class Memo(
    val id: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,

    val content: String,

    val commitHash: String? = null,
    val filePath: String? = null,
    val selectedCodeSnippet: String? = null,

    val selectionStart: Int? = null,
    val selectionEnd: Int? = null,

    val visibleStart: Int? = null,
    val visibleEnd: Int? = null
) {
    init {
        validate()
    }

    constructor(
        content: String,
        commitHash: String?,
        filePath: String?,
        selectedCodeSnippet: String?,
        selectionStart: Int?,
        selectionEnd: Int?,
        visibleStart: Int?,
        visibleEnd: Int?
    ) : this(
        id = System.currentTimeMillis(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        content = content,
        commitHash = commitHash,
        filePath = filePath,
        selectedCodeSnippet = selectedCodeSnippet,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        visibleStart = visibleStart,
        visibleEnd = visibleEnd
    )

    private fun validate() {
        if (selectionStart != null && selectionEnd != null) {
            require(selectionStart <= selectionEnd) {
                "selectionStart는 selectionEnd 보다 작아야합니다."
            }
        }

        if (visibleStart != null && visibleEnd != null) {
            require(visibleStart <= visibleEnd) {
                "visibleStart는 visibleEnd 보다 작아야합니다."
            }
        }
    }

    fun toState(): MemoState =
        MemoState(
            id = this.id,
            createdAt = this.createdAt.toString(),
            updatedAt = this.updatedAt.toString(),
            content = this.content,
            commitHash = this.commitHash,
            filePath = this.filePath,
            selectedCodeSnippet = this.selectedCodeSnippet,
            selectionStart = this.selectionStart,
            selectionEnd = this.selectionEnd,
            visibleStart = this.visibleStart,
            visibleEnd = this.visibleEnd
        )

    fun update(
        content: String = this.content
    ): Memo {
        return Memo(
            id = this.id,
            createdAt = this.createdAt,
            updatedAt = LocalDateTime.now(),
            content = content,
            commitHash = this.commitHash,
            filePath = this.filePath,
            selectedCodeSnippet = this.selectedCodeSnippet,
            selectionStart = this.selectionStart,
            selectionEnd = this.selectionEnd,
            visibleStart = this.visibleStart,
            visibleEnd = this.visibleEnd
        )
    }
}
