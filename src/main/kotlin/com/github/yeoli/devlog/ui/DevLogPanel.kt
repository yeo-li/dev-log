package com.github.yeoli.devlog.ui

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.github.yeoli.devlog.domain.memo.service.MemoService
import com.github.yeoli.devlog.domain.note.service.NoteService
import com.github.yeoli.devlog.event.MemoChangedEvent
import com.github.yeoli.devlog.event.MemoListener
import com.github.yeoli.devlog.ui.action.DeleteSelectedAction
import com.github.yeoli.devlog.ui.action.MemoExportAction
import com.github.yeoli.devlog.ui.action.ToggleSelectionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.StatusBar
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JComponent

/**
 * Retrospect DevLog UI의 메인 컨테이너.
 * 타임라인·작성기·공유 메모/상세보기 카드 전환을 모두 여기서 제어한다.
 */
internal class DevLogPanel(
    private val project: Project,
    parentDisposable: Disposable
) {

    private val memoService = project.getService<MemoService>(MemoService::class.java)
    private val noteService = project.getService<NoteService>(NoteService::class.java)

    private val palette = UiPalette()

    // 작성기 영역 (분리 후보)
    private val composer = MemoInputComposer(palette, ::handleSaveRequest)

    // 공유 노트 영역 (분리 후보)
    private val sharedNotes = NotePanel(palette, ::handleSharedNotesSave)

    // 선택 상태 배너 (분리 후보)
    private val selectionStatusPanel = SelectionStatusPanel(palette)

    private val selectedRecordIds = linkedSetOf<Long>()

    // 상세 편집기 (분리 후보)
    private val recordDetailPanel = MemoDetailPanel(
        palette = palette,
        onRequestSave = ::handleDetailSave,
        onRequestBack = ::handleDetailBack
    )

    // 타임라인 뷰 (분리 후보: MemoListView)
    private val timeline = MemoListView(
        palette,
        MemoListView.Interactions(
            onEdit = { record, index -> openEditDialog(record) },
            onSnapshot = { openSnapshotInEditor(it) },
            onOpenDetail = { record -> openRecordDetail(record) },
            onDelete = { index, record -> deleteMemo(record) },
            onSelectionChanged = ::handleSelectionChanged
        )
    )

    // shared notes / detail view 등을 전환하기 위한 카드 레이아웃.
    private val cards = CardLayout()
    private val contentStack = JBPanel<JBPanel<*>>().apply {
        layout = cards
        background = palette.editorBg
    }
    private val viewToggleAction = SharedNotesToggleAction()
    private val selectionToggleAction = ToggleSelectionAction(::selectAllRecords, ::clearSelection)
    private val exportSelectedAction = MemoExportAction(
        project = project,
        recordsProvider = ::getSelectedRecords,
        actionText = "Export Selected Logs",
        actionDescription = "Export checked DevLog entries as Markdown text",
        icon = AllIcons.ToolbarDecorator.Export
    )
    private val deleteSelectedAction =
        DeleteSelectedAction(project, ::getSelectedRecords, ::deleteSelectedRecords)
    private var currentCard: String? = null

    // 사용자가 특정 레코드에 연결하기 위해 선택해 둔 대상.
    private var totalRecordCount: Int = 0
    private var navigationToolbar: ActionToolbar? = null

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = palette.editorBg
        border = JBUI.Borders.empty()
        add(createNavigationBar(), BorderLayout.NORTH)
        contentStack.add(createMainView(), RETROSPECTS_CARD)
        contentStack.add(sharedNotes.component, SHARED_NOTES_CARD)
        contentStack.add(recordDetailPanel.component, RECORD_DETAIL_CARD)
        add(contentStack, BorderLayout.CENTER)
        switchCard(RETROSPECTS_CARD)
    }

    init {
        refreshMemos()
        loadSharedNotes()
        setupSelectionTracking(parentDisposable)
        project.messageBus.connect(parentDisposable).subscribe(
            MemoChangedEvent.TOPIC,
            MemoListener {
                refreshMemos()
                loadSharedNotes()
            }
        )
    }

    // ---------- UI 빌더 ----------
    private fun createNavigationBar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(viewToggleAction)
            add(selectionToggleAction)
            add(exportSelectedAction)
            add(deleteSelectedAction)
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("RetrospectNavToolbar", group, true)
            .apply {
                targetComponent = null
                component.background = palette.editorBg
                component.border = JBUI.Borders.empty()
            }
        navigationToolbar = toolbar
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = palette.editorBg
            border = JBUI.Borders.empty(6, 12, 4, 12)
            add(toolbar.component, BorderLayout.CENTER)
        }
    }

    private fun createMainView(): JComponent {
        val timelineStack = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = palette.editorBg
            add(timeline.component, BorderLayout.CENTER)
            add(selectionStatusPanel.component, BorderLayout.SOUTH)
        }
        val lowerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = palette.editorBg
            add(composer.component, BorderLayout.CENTER)
        }
        return JBSplitter(true, 0.8f).apply {
            dividerWidth = 5
            isOpaque = false
            firstComponent = timelineStack
            secondComponent = lowerPanel
            setHonorComponentsMinimumSize(true)
        }
    }

    private fun switchCard(card: String) {
        if (currentCard == card) return
        currentCard = card
        cards.show(contentStack, card)
        refreshToolbarActions()
    }

    // ---------- 데이터 동기화 ----------
    private fun refreshMemos() {
        val memos = memoService.getAllMemos()
        totalRecordCount = memos.size
        timeline.render(memos)
        refreshToolbarActions()
    }

    private fun loadSharedNotes() {
        sharedNotes.setContent(noteService.getNote().content)
    }

    private fun notifyChange() {
        project.messageBus.syncPublisher(MemoChangedEvent.TOPIC).onChanged()
    }

    // ---------- 메모/노트 액션 ----------
    private fun handleSaveRequest(rawBody: String) {
        val body = rawBody.trim()
        if (body.isEmpty()) {
            composer.showEmptyBodyMessage()
            return
        }
        val memo = memoService.createMemo(rawBody) ?: return
        memoService.saveMemo(memo)
        notifyChange()
        composer.clear()
        composer.updateStatus("Saved at ${currentTimeString()}")
    }

    private fun handleSharedNotesSave(rawNotes: String) {
        noteService.updateNote(rawNotes)
        notifyChange()
        sharedNotes.markSaved(currentTimeString())
    }

    private fun openRecordDetail(record: Memo) {
        recordDetailPanel.displayRecord(record)
        switchCard(RECORD_DETAIL_CARD)
    }

    private fun handleDetailSave(memoId: Long, updatedContent: String) {
        val memo = memoService.findMemoById(memoId)
        if (memo == null || memo.content == updatedContent) return
        memoService.updateMemo(memoId, updatedContent)
        notifyChange()
    }

    private fun handleDetailBack() {
        switchCard(RETROSPECTS_CARD)
    }

    private fun openEditDialog(memo: Memo) {
        val editorArea = JBTextArea(memo.content, 10, 50).apply {
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8)
        }
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(JBLabel("Update content:"), BorderLayout.NORTH)
            add(JBScrollPane(editorArea), BorderLayout.CENTER)
        }
        val dialog = object : DialogWrapper(project) {
            init {
                title = "Edit Memo"
                init()
            }

            override fun createCenterPanel(): JComponent = panel
        }
        if (dialog.showAndGet()) {
            val newContent = editorArea.text.trim()
            if (newContent != memo.content) {
                memoService.updateMemo(memo.id, newContent)
                notifyChange()
            }
        }
    }

    private fun deleteMemo(memo: Memo) {
        val confirm = Messages.showYesNoDialog(
            project,
            "삭제 후엔 복구가 불가능 합니다.\n이 메모를 지우시겠습니까?",
            "Delete",
            Messages.getQuestionIcon()
        )
        if (confirm == Messages.YES) {
            memoService.removeMemos(listOf(memo))
            notifyChange()
        }
    }

    private fun deleteSelectedRecords(records: List<Memo>) {
        if (records.isEmpty()) return
        val ids = records.map { it.id }.toSet()
        if (ids.isEmpty()) return
        val memos = memoService.getAllMemos().filter { ids.contains(it.id) }
        memoService.removeMemos(memos)
        notifyChange()
        selectedRecordIds.removeAll(ids)
        refreshToolbarActions()
    }

    private fun getSelectedRecords(): List<Memo> {
        if (selectedRecordIds.isEmpty()) return emptyList()
        val selected = selectedRecordIds.toSet()
        return memoService.getAllMemos()
            .sortedBy { it.createdAt }.filter { it.id in selected }
    }

    // ---------- 선택/툴바 상태 ----------
    private fun handleSelectionChanged(ids: Set<Long>) {
        selectedRecordIds.clear()
        selectedRecordIds.addAll(ids)
        refreshToolbarActions()
    }

    private fun selectAllRecords() {
        timeline.selectAllRecords()
    }

    private fun clearSelection() {
        timeline.clearSelection()
    }

    private fun refreshToolbarActions() {
        val timelineActive = currentCard == RETROSPECTS_CARD
        selectionToggleAction.setControlsEnabled(timelineActive)
        selectionToggleAction.updateState(totalRecordCount, selectedRecordIds.size)
        exportSelectedAction.setForceDisabled(!timelineActive)
        deleteSelectedAction.setForceDisabled(!timelineActive)
        navigationToolbar?.updateActionsImmediately()
    }

    private fun setupSelectionTracking(parentDisposable: Disposable) {
        EditorFactory.getInstance().eventMulticaster.addSelectionListener(object :
            SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                if (e.editor.project != project) return
                updateSelectionBanner()
            }
        }, parentDisposable)

        project.messageBus.connect(parentDisposable).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    updateSelectionBanner()
                }
            }
        )

        updateSelectionBanner()
    }

    private fun updateSelectionBanner() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val selectionModel = editor?.selectionModel
        val hasMeaningfulSelection = selectionModel?.hasSelection() == true &&
                selectionModel.selectionStart != selectionModel.selectionEnd
        if (hasMeaningfulSelection) {
            val fileName = editor?.virtualFile?.name?.takeIf { it.isNotBlank() } ?: "current file"
            selectionStatusPanel.showSelectionActive(fileName)
        } else {
            selectionStatusPanel.showIdleState()
        }
    }

    // ---------- 카드 전환 액션 ----------
    private inner class SharedNotesToggleAction :
        ToggleAction("Memos", "Toggle Memo List / Notes", AllIcons.General.History) {

        override fun isSelected(e: AnActionEvent): Boolean =
            currentCard == SHARED_NOTES_CARD

        override fun setSelected(
            e: AnActionEvent,
            state: Boolean
        ) {
            val target = if (state) SHARED_NOTES_CARD else RETROSPECTS_CARD
            switchCard(target)
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            val showingShared = currentCard == SHARED_NOTES_CARD
            if (showingShared) {
                e.presentation.text = "Show Memos"
                e.presentation.description = "Switch to memo list"
                e.presentation.icon = AllIcons.General.History
            } else {
                e.presentation.text = "Show Notes"
                e.presentation.description = "Switch to shared notes"
                e.presentation.icon = AllIcons.Toolwindows.Documentation
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private fun currentTimeString(): String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))


    /**
     * 레코드에 저장된 코드 스냅샷을 읽기 전용 가상 파일로 열고,
     * 당시에 선택했던 영역을 하이라이트한다.
     */
    private fun openSnapshotInEditor(record: Memo) {
        val fileManager = FileEditorManager.getInstance(project)
        val existingSnapshot = fileManager.openFiles.firstOrNull {
            it.getUserData(SNAPSHOT_FILE_KEY) == record.id
        }
        if (existingSnapshot != null) {
            // 이미 열린 가상 파일이 있으면 그대로 포커스만 맞춘다.
            fileManager.openFile(existingSnapshot, true)
            return
        }
        if (record.fullCodeSnapshot == null || record.fullCodeSnapshot.isBlank()) {
            // 일반 로그에는 snapshot이 없으므로 사용자에게 안내.
            StatusBar.Info.set("This entry does not have a captured snapshot.", project)
            return
        }
        val baseNameRaw = record.filePath
            ?.takeIf { it.isNotBlank() }
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "GeneralLog.txt"
        val sanitizedBaseName = baseNameRaw.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val extension = sanitizedBaseName.substringAfterLast('.', "txt")
        val fileName = if (sanitizedBaseName.contains('.')) {
            "DevLog_${sanitizedBaseName}"
        } else {
            "DevLog_${sanitizedBaseName}.$extension"
        }
        val header = buildString {
            appendLine("// DevLog entry captured ${record.createdAt}")
            appendLine("// Commit: ${record.commitHash ?: "N/A"}")
            appendLine("// File: ${record.filePath}")
            appendLine("// Comment: ${record.content.replace("\n", " ")}")
            appendLine("// Visible lines: ${record.visibleStart}-${record.visibleEnd}")
            appendLine()
        }
        val content = header + record.fullCodeSnapshot
        val headerLength = header.length

        val virtualFile = LightVirtualFile(fileName, content).apply {
            isWritable = false
            putUserData(SNAPSHOT_FILE_KEY, record.id)
        }

        val descriptor = OpenFileDescriptor(
            project,
            virtualFile,
            (headerLength).coerceIn(0, content.length)
        )

        val editor = fileManager.openTextEditor(descriptor, true) ?: return
        val docLength = editor.document.textLength
        val rawSelectionStart = record.selectionStart ?: 0
        val rawSelectionEnd = record.selectionEnd ?: 0
        val selectionStart = (headerLength + rawSelectionStart).coerceIn(0, docLength)
        val selectionEnd = (headerLength + rawSelectionEnd).coerceIn(0, docLength)
        // 당시 선택 영역을 복원해 맥락을 쉽게 파악할 수 있게 한다.
        editor.selectionModel.setSelection(
            minOf(selectionStart, selectionEnd),
            maxOf(selectionStart, selectionEnd)
        )
        // 자동으로 중앙에 스크롤하여 스냅샷 포커스를 맞춘다.
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        highlightCapturedSelection(editor, selectionStart, selectionEnd)
    }

    private fun highlightCapturedSelection(editor: Editor, start: Int, end: Int) {
        if (start == end) return
        val attributes = TextAttributes().apply {
            backgroundColor = JBColor(Color(198, 239, 206, 170), Color(60, 96, 66, 150))
        }
        editor.markupModel.addRangeHighlighter(
            start,
            end,
            HighlighterLayer.SELECTION - 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE
        )
    }

    companion object {
        private const val RETROSPECTS_CARD = "retrospects"
        private const val SHARED_NOTES_CARD = "shared_notes"
        private const val RECORD_DETAIL_CARD = "record_detail"
        private val SNAPSHOT_FILE_KEY = Key.create<Long>("YEOLI_RETROSPECT_SNAPSHOT_ID")
    }

}

data class UiPalette(
    val editorBg: JBColor = JBColor(Color(247, 248, 250), Color(0x1E, 0x1F, 0x22)),
    val editorFg: JBColor = JBColor(Color(32, 32, 32), Color(230, 230, 230)),
    val borderColor: JBColor = JBColor(Color(0x2b, 0x2d, 0x30), Color(0x2b, 0x2d, 0x30)),
    val listRowBg: JBColor = JBColor(Color(0xF7, 0xF8, 0xFA), Color(0x2B, 0x2D, 0x30)),
    val listRowSelectedBg: JBColor = JBColor(Color(0xE3, 0xF2, 0xFD), Color(0x1F, 0x3B, 0x4D))
)
