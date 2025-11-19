package com.github.yeoli.devlog.domain.memo.domain

import java.time.LocalDateTime

class Memo(
    val content: String,

    val commitHash: String? = null,
    val filePath: String? = null,
    val selectedCodeSnippet: String? = null,

    val selectionStart: Int? = null,
    val selectionEnd: Int? = null,

    val visibleStart: Int? = null,
    val visibleEnd: Int? = null
) {
    val id: Long = generateId()
    val createdAt: LocalDateTime = LocalDateTime.now()
    val updatedAt: LocalDateTime = LocalDateTime.now()

    init {
        validate()
    }

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

    private fun generateId(): Long {
        return System.currentTimeMillis()
    }
}