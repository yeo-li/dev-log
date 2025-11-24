package com.github.yeoli.devlog.ui

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.github.yeoli.devlog.domain.memo.service.MemoService
import com.github.yeoli.devlog.domain.note.service.NoteService
import com.github.yeoli.devlog.event.MemoChangedEvent
import com.github.yeoli.devlog.event.MemoListener
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.StatusBar
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.text.JTextComponent

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

    // 밝은/어두운 테마 모두에서 사용할 색상 팔레트.
    private val palette = UiPalette()

    // 하단 작성 영역(코멘트 입력 + Save 버튼).
    private val composer = RetrospectComposer(palette, ::handleSaveRequest)

    // 공유 메모 탭에서 사용되는 텍스트 영역.
    private val sharedNotes = SharedNotesPanel(palette, ::handleSharedNotesSave)

    // 에디터 선택 상태를 좌측 배너로 보여주는 컴포넌트.
    private val selectionStatusPanel = SelectionStatusPanel(palette)

    // 카드 전환으로 띄우는 상세 로그 편집기.
    private val recordDetailPanel = RecordDetailPanel(
        palette = palette,
        onRequestSave = ::handleDetailSave,
        onRequestBack = ::handleDetailBack
    )

    // 타임라인에서 체크된 레코드들의 ID 저장소.
    private val selectedRecordIds = linkedSetOf<Long>()

    // DevLog들을 시간순으로 보여주고 조작하는 메인 뷰.
    private val timeline = RetrospectTimelineView(
        palette,
        RetrospectTimelineView.Interactions(
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

    /**
     * 스토리지의 최신 데이터를 타임라인/툴바 상태에 반영한다.
     */
    private fun refreshMemos() {
        val memos = memoService.getAllMemos()
        totalRecordCount = memos.size
        timeline.render(memos)
        refreshToolbarActions()
    }

    /**
     * 타임라인(위)과 작성기(아래)를 포함하는 메인 분할 레이아웃을 구성한다.
     */
    private fun createMainView(): JComponent {
        val timelineStack = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = palette.editorBg
            // DevLog 목록 + 현재 에디터 선택 상태 배너를 하나의 패널로 묶는다.
            add(timeline.component, BorderLayout.CENTER)
            add(selectionStatusPanel.component, BorderLayout.SOUTH)
        }
        val lowerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = palette.editorBg
            // 선택한 레코드와 연결하려는 context 영역과 작성기 패널이 붙어 있다.
//            add(linkContextPanel.component, BorderLayout.NORTH)
            add(composer.component, BorderLayout.CENTER)
        }
        val splitter = JBSplitter(true, 0.8f).apply {
            dividerWidth = 5
            isOpaque = false
            // 상단에 타임라인, 하단에 작성기를 배치하고 상단에 비중을 둔다.
            firstComponent = timelineStack
            secondComponent = lowerPanel
            setHonorComponentsMinimumSize(true)
        }
        return splitter
    }

    /**
     * 카드 전환/선택/내보내기 등 액션이 모인 상단 툴바를 만든다.
     */
    private fun createNavigationBar(): JComponent {
        val group = DefaultActionGroup().apply {
            // 보기 전환 + 선택 토글 + 내보내기/삭제 액션을 순서대로 배치.
            add(viewToggleAction)
            add(selectionToggleAction)
            add(exportSelectedAction)
            add(deleteSelectedAction)
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("RetrospectNavToolbar", group, true)
            .apply {
                targetComponent = null
                // ToolWindow 배경과 자연스럽게 섞이도록 여백/색상을 정리.
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

    /**
     * 카드 레이아웃을 전환하고, 액션 활성화 상태를 재계산한다.
     */
    private fun switchCard(card: String) {
        if (currentCard == card) return
        currentCard = card
        cards.show(contentStack, card)
        refreshToolbarActions()
    }

    private fun loadSharedNotes() {
        sharedNotes.setContent(noteService.getNote().content)
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

    /**
     * 에디터 선택/포커스 변화 이벤트를 구독해 상태 배너를 갱신한다.
     */
    private fun setupSelectionTracking(parentDisposable: Disposable) {
        EditorFactory.getInstance().eventMulticaster.addSelectionListener(object :
            SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                if (e.editor.project != project) return
                // 에디터 선택 길이/파일명 변화를 즉시 배너에 반영.
                updateSelectionBanner()
            }
        }, parentDisposable)

        project.messageBus.connect(parentDisposable).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    // 다른 에디터로 전환되면 선택 상태가 달라질 수 있으므로 갱신.
                    updateSelectionBanner()
                }
            }
        )

        updateSelectionBanner()
    }

    /**
     * 현재 에디터의 선택 영역 유무에 따라 배너 문구를 바꾼다.
     */
    private fun updateSelectionBanner() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val selectionModel = editor?.selectionModel
        val hasMeaningfulSelection = selectionModel?.hasSelection() == true &&
                selectionModel.selectionStart != selectionModel.selectionEnd
        if (hasMeaningfulSelection) {
            // 파일명이 없으면 현재 파일이라는 문구로 대체.
            val fileName = editor?.virtualFile?.name?.takeIf { it.isNotBlank() } ?: "current file"
            selectionStatusPanel.showSelectionActive(fileName)
        } else {
            selectionStatusPanel.showIdleState()
        }
    }

    /**
     * 작성기의 Save 트리거를 처리한다. 선택 영역이 있으면 Draft로, 아니면 일반 로그로 저장한다.
     */
    // 저장되는 로직
    private fun handleSaveRequest(rawBody: String) {
        val body = rawBody.trim()
        if (body.isEmpty()) {
            composer.showEmptyBodyMessage()
            return
        }
        val memo = memoService.createMemo(rawBody)
        if (memo == null) return
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

    private fun notifyChange() {
        project.messageBus.syncPublisher(MemoChangedEvent.TOPIC).onChanged()
    }

    private fun handleSelectionChanged(ids: Set<Long>) {
        selectedRecordIds.clear()
        selectedRecordIds.addAll(ids)
        refreshToolbarActions()
    }

    private fun getSelectedRecords(): List<Memo> {
        if (selectedRecordIds.isEmpty()) return emptyList()
        val selected = selectedRecordIds.toSet()
        return memoService.getAllMemos()
            .sortedBy { it.createdAt }.filter { it.id in selected }
    }

    private fun deleteSelectedRecords(records: List<Memo>) {
        if (records.isEmpty()) return
        val ids = records.map { it.id }.toSet()

        // memoService에 removeMemosByIds로 바꾸면 될듯
        if (ids.isEmpty()) return
        val memos = memoService.getAllMemos().filter { ids.contains(it.id) }
        memoService.removeMemos(memos)

        notifyChange()
        selectedRecordIds.removeAll(ids)
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
        // 타임라인이 아닐 때는 선택 관련 컨트롤을 비활성화한다.
        selectionToggleAction.setControlsEnabled(timelineActive)
        selectionToggleAction.updateState(totalRecordCount, selectedRecordIds.size)
        exportSelectedAction.setForceDisabled(!timelineActive)
        deleteSelectedAction.setForceDisabled(!timelineActive)
        navigationToolbar?.updateActionsImmediately()
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
        val dialog = object : com.intellij.openapi.ui.DialogWrapper(project) {
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

private class RetrospectComposer(
    private val palette: UiPalette,
    private val onRequestSave: (String) -> Unit
) {
    private val descriptionArea = object : JBTextArea(6, 40) {
        override fun getLocationOnScreen(): Point =
            if (isShowing) super.getLocationOnScreen() else Point(0, 0)
    }.apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(8, 12, 8, 12)
        background = palette.editorBg
        foreground = palette.editorFg
        caretColor = palette.editorFg
        font = font.deriveFont(14f)
        emptyText.text = "Write log entry..."
    }
    private val statusLabel = JBLabel().apply {
        foreground = JBColor.gray
    }
    private val saveButton = JButton("Save Log").apply {
        isEnabled = false
        background = palette.editorBg
        foreground = palette.editorFg
        addActionListener { onRequestSave(descriptionArea.text) }
    }

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = palette.editorBg
        foreground = palette.editorFg
        border = JBUI.Borders.empty()
        add(descriptionArea, BorderLayout.CENTER)
        add(createControls(), BorderLayout.SOUTH)
    }

    init {
        descriptionArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                val hasText = descriptionArea.text.isNotBlank()
                saveButton.isEnabled = hasText
                statusLabel.text = if (hasText) "Unsaved changes" else ""
            }
        })
        registerSaveShortcut(descriptionArea) {
            if (saveButton.isEnabled) {
                saveButton.doClick()
            }
        }
    }

    private fun createControls(): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = palette.editorBg
        foreground = palette.editorFg
        border = JBUI.Borders.empty(0, 12, 8, 12)
        add(statusLabel, BorderLayout.WEST)
        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            background = palette.editorBg
            foreground = palette.editorFg
            add(saveButton)
        }
        add(buttonPanel, BorderLayout.EAST)
    }

    fun clear() {
        descriptionArea.text = ""
    }

    fun updateStatus(text: String) {
        statusLabel.text = text
    }

    fun showEmptyBodyMessage() {
        statusLabel.text = "Please enter a log entry."
    }

}

private class RecordDetailPanel(
    private val palette: UiPalette,
    private val onRequestSave: (Long, String) -> Unit,
    private val onRequestBack: () -> Unit
) {
    private val bodyArea = JBTextArea(12, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(8, 12, 8, 12)
        background = palette.editorBg
        foreground = palette.editorFg
        caretColor = palette.editorFg
        font = font.deriveFont(14f)
    }
    private val statusLabel = JBLabel("Autosave ready").apply {
        foreground = JBColor.gray
    }
    private val titleLabel = JBLabel("").apply {
        foreground = palette.editorFg
        border = JBUI.Borders.emptyLeft(4)
    }
    private val backButton = JButton("Back").apply {
        icon = AllIcons.Actions.Back
        addActionListener {
            triggerManualSave()
            onRequestBack()
        }
    }
    private val autoSaveTimer = Timer(AUTO_SAVE_DELAY_MS) {
        performAutoSave()
    }.apply {
        isRepeats = false
    }
    private var hasUnsavedChanges = false
    private var suppressEvent = false
    private var currentRecordId: Long? = null

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = palette.editorBg
        foreground = palette.editorFg
        add(createHeader(), BorderLayout.NORTH)
        add(JBScrollPane(bodyArea).apply {
            border = JBUI.Borders.empty()
            background = palette.editorBg
            viewport.background = palette.editorBg
        }, BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)
    }

    init {
        bodyArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                if (suppressEvent) return
                hasUnsavedChanges = true
                statusLabel.text = "Auto-saving..."
                scheduleAutoSave()
            }
        })
        registerSaveShortcut(bodyArea) {
            triggerManualSave()
        }
    }

    fun displayRecord(record: Memo) {
        currentRecordId = record.id
        titleLabel.text = buildTitle(record)
        suppressEvent = true
        bodyArea.text = record.content
        bodyArea.caretPosition = bodyArea.text.length
        suppressEvent = false
        hasUnsavedChanges = false
        cancelAutoSave()
        statusLabel.text = "Autosave ready"
        bodyArea.requestFocusInWindow()
    }

    private fun createHeader(): JComponent =
        JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = palette.editorBg
            border = JBUI.Borders.empty(8, 12, 4, 12)
            val leftGroup = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                background = palette.editorBg
                add(backButton)
                add(titleLabel)
            }
            add(leftGroup, BorderLayout.WEST)
        }

    private fun createFooter(): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = palette.editorBg
        border = JBUI.Borders.empty(8, 12, 12, 12)
        add(statusLabel, BorderLayout.WEST)
    }

    private fun buildTitle(record: Memo): String {
        val fileSegment = record.filePath
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "General Log"
        val formattedTime = runCatching { LocalDateTime.parse(record.createdAt.toString()) }
            .getOrNull()
            ?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            ?: record.createdAt.toString()
        return "$fileSegment · $formattedTime"
    }

    private fun scheduleAutoSave() {
        if (!hasUnsavedChanges) return
        autoSaveTimer.restart()
    }

    private fun cancelAutoSave() {
        if (autoSaveTimer.isRunning) {
            autoSaveTimer.stop()
        }
    }

    private fun performAutoSave() {
        if (!hasUnsavedChanges) return
        val recordId = currentRecordId ?: return
        hasUnsavedChanges = false
        onRequestSave(recordId, bodyArea.text)
        statusLabel.text = "Saved"
    }

    private fun triggerManualSave() {
        if (!hasUnsavedChanges) return
        cancelAutoSave()
        performAutoSave()
    }

    companion object {
        private const val AUTO_SAVE_DELAY_MS = 1500
    }
}

private class SharedNotesPanel(
    private val palette: UiPalette,
    private val onRequestSave: (String) -> Unit
) {
    private val autoSaveTimer = Timer(AUTO_SAVE_DELAY_MS) {
        performAutoSave()
    }.apply {
        isRepeats = false
    }
    private val notesArea = JBTextArea(6, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(8, 12, 8, 12)
        background = palette.editorBg
        foreground = palette.editorFg
        caretColor = palette.editorFg
    }
    private val statusLabel = JBLabel("Saved").apply {
        foreground = JBColor.gray
    }
    private var hasUnsavedChanges = false
    private var suppressEvent = false

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = palette.editorBg
        foreground = palette.editorFg
        border = JBUI.Borders.empty(12, 0, 0, 0)
        val header = JBLabel("Notes").apply {
            border = JBUI.Borders.empty(0, 12, 8, 12)
        }
        val scrollPane = JBScrollPane(notesArea).apply {
            border = JBUI.Borders.empty()
            background = palette.editorBg
            viewport.background = palette.editorBg
        }
        add(header, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(createControls(), BorderLayout.SOUTH)
    }

    init {
        notesArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                if (suppressEvent) return
                hasUnsavedChanges = true
                statusLabel.text = "Saving..."
                scheduleAutoSave()
            }
        })
        registerSaveShortcut(notesArea) {
            triggerManualSave()
        }
    }

    private fun createControls(): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = palette.editorBg
        border = JBUI.Borders.empty(0, 12, 12, 12)
        add(statusLabel, BorderLayout.WEST)
    }

    fun setContent(value: String) {
        suppressEvent = true
        notesArea.text = value
        suppressEvent = false
        hasUnsavedChanges = false
        cancelAutoSave()
        statusLabel.text = "Saved"
    }

    fun markSaved(timestamp: String) {
        cancelAutoSave()
        hasUnsavedChanges = false
        statusLabel.text = "Saved at $timestamp"
    }

    private fun scheduleAutoSave() {
        if (!hasUnsavedChanges) return
        autoSaveTimer.restart()
    }

    private fun cancelAutoSave() {
        if (autoSaveTimer.isRunning) {
            autoSaveTimer.stop()
        }
    }

    private fun performAutoSave() {
        if (!hasUnsavedChanges) return
        statusLabel.text = "Auto-saving..."
        hasUnsavedChanges = false
        onRequestSave(notesArea.text)
    }

    private fun triggerManualSave() {
        if (!hasUnsavedChanges) return
        cancelAutoSave()
        performAutoSave()
    }

    companion object {
        private const val AUTO_SAVE_DELAY_MS = 2000
    }
}

private class SelectionStatusPanel(
    private val palette: UiPalette
) {
    private val infoLabel = JBLabel().apply {
        foreground = JBColor.gray
        font = font.deriveFont(font.size2D - 1f)
        horizontalAlignment = JBLabel.LEFT
        verticalAlignment = JBLabel.CENTER
    }

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = palette.editorBg
        foreground = palette.editorFg
        border = JBUI.Borders.empty(4, 12, 4, 12)
        add(infoLabel, BorderLayout.CENTER)
        val fixedHeight = 30
        preferredSize = JBUI.size(0, fixedHeight)
        minimumSize = JBUI.size(0, fixedHeight)
    }

    init {
        showIdleState()
    }

    fun showSelectionActive(fileName: String) {
        infoLabel.text = "Selected $fileName"
        infoLabel.foreground = JBColor.gray
    }

    fun showIdleState() {
        infoLabel.text = "No code selected."
        infoLabel.foreground = JBColor.gray
    }
}

private fun registerSaveShortcut(component: JComponent, action: () -> Unit) {
    if (GraphicsEnvironment.isHeadless()) return
    val shortcutMask = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    val actionKey = "devlog.saveShortcut.${component.hashCode()}"
    val keyStrokes = listOf(
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, shortcutMask),
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, shortcutMask or KeyEvent.SHIFT_DOWN_MASK)
    )
    keyStrokes.forEach { keyStroke ->
        component.getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, actionKey)
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionKey)
    }
    component.actionMap.put(actionKey, object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
            action()
        }
    })
    if (component is JTextComponent) {
        component.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val metaCombo = e.isMetaDown || e.isControlDown
                if (metaCombo && e.keyCode == KeyEvent.VK_ENTER) {
                    e.consume()
                    action()
                }
            }
        })
    }
}

private class DeleteSelectedAction(
    private val project: Project,
    private val selectionProvider: () -> List<Memo>,
    private val onDelete: (List<Memo>) -> Unit
) : DumbAwareAction(
    "Delete Selected Logs",
    "Delete all checked DevLog entries",
    AllIcons.Actions.GC
) {

    private var forceDisabled = false

    fun setForceDisabled(disabled: Boolean) {
        forceDisabled = disabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (forceDisabled) return
        val selected = selectionProvider()
        if (selected.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("YeoliRetrospectNotifications")
                ?.createNotification(
                    "Delete Selected Logs",
                    "No DevLog entries are selected.",
                    NotificationType.WARNING
                )
                ?.notify(project)
            return
        }
        val confirmed = Messages.showYesNoDialog(
            project,
            "Delete ${selected.size} selected DevLog entries?",
            "Delete Selected Logs",
            Messages.getQuestionIcon()
        )
        if (confirmed == Messages.YES) {
            onDelete(selected)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = !forceDisabled && selectionProvider().isNotEmpty()
    }
}

private class ToggleSelectionAction(
    private val onSelectAll: () -> Unit,
    private val onClearSelection: () -> Unit
) : DumbAwareAction("Select All Logs", "Select every DevLog entry", AllIcons.Actions.Selectall) {

    private var totalItems: Int = 0
    private var selectedItems: Int = 0
    private var controlsEnabled: Boolean = true

    fun updateState(total: Int, selected: Int) {
        totalItems = total
        selectedItems = selected
    }

    fun setControlsEnabled(enabled: Boolean) {
        controlsEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (!controlsEnabled || totalItems == 0) return
        if (selectedItems == totalItems) {
            onClearSelection()
        } else {
            onSelectAll()
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val hasRecords = totalItems > 0
        val allSelected = hasRecords && selectedItems == totalItems
        e.presentation.isEnabled = controlsEnabled && hasRecords
        if (allSelected) {
            e.presentation.text = "Clear Selection"
            e.presentation.description = "Clear all DevLog selections"
            e.presentation.icon = AllIcons.Actions.Unselectall
        } else {
            e.presentation.text = "Select All"
            e.presentation.description = "Select every DevLog entry"
            e.presentation.icon = AllIcons.Actions.Selectall
        }
    }
}
