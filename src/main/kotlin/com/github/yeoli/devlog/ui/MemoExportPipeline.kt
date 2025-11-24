package com.github.yeoli.devlog.ui

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.github.yeoli.devlog.domain.memo.service.MemoService
import com.intellij.openapi.project.Project
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MemoExportPipeline(
    private val project: Project
) {

    data class Payload(
        val content: String,
        val fileExtension: String,
        val defaultFileName: String
    )

    fun buildPayload(recordsOverride: List<Memo>? = null): Payload {
        val memoService = project.getService(MemoService::class.java)
        val memos: List<Memo> = recordsOverride
            ?: memoService.getAllMemosOrderByCreatedAt()

        val header = memoService.buildHeader()
        val body = if (memos.isEmpty()) {
            "(내보낼 메모가 없습니다.)"
        } else {
            memos.mapIndexed { index, memo -> memo.buildMemoBlock(index + 1) }
                .joinToString("\n")
        }
        val content = header + "\n\n" + body
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val defaultFileName = "devlog-${project.name}-$date.txt"

        return Payload(
            content = content,
            fileExtension = "txt",
            defaultFileName = defaultFileName
        )
    }
}