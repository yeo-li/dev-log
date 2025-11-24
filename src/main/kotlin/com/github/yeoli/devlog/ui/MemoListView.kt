package com.github.yeoli.devlog.ui

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*

private const val MAX_SNIPPET_LENGTH = 400
private val ACTIVATED_ROW_COLOR = JBColor(Color(0x2e, 0x43, 0x6e), Color(0x2e, 0x43, 0x6e))
private val SELECTED_ROW_COLOR = JBColor(Color(0x43, 0x45, 0x4a), Color(0x43, 0x45, 0x4a))
private val TITLE_MUTED_COLOR = JBColor(Color(0x88, 0x88, 0x88), Color(0xc0, 0xc0, 0xc0))
private val DATE_LABEL_BORDER = JBColor(Color(0xe3, 0xe6, 0xed), Color(0x33, 0x34, 0x38))
private val DATE_LABEL_TEXT = JBColor(Color(0x4d5160), Color(0xb8bcc9))

class RetrospectTimelineView(
    private val palette: UiPalette,
    private val interactions: Interactions
) {

    data class Interactions(
        val onEdit: (Memo, Int) -> Unit,
        val onSnapshot: (Memo) -> Unit,
        val onOpenDetail: (Memo) -> Unit,
        val onDelete: (Int, Memo) -> Unit,
        val onSelectionChanged: (Set<Long>) -> Unit
    )

    private val listPanel = object : JBPanel<JBPanel<*>>(GridBagLayout()), Scrollable {
        init {
            border = JBUI.Borders.empty(8)
            isOpaque = true
            background = JBColor.PanelBackground
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int
        ): Int =
            24

        override fun getScrollableBlockIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int
        ): Int =
            visibleRect.height

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false
    }
    private val scrollPane = JScrollPane(listPanel).apply {
        border = JBUI.Borders.empty()
        background = JBColor.PanelBackground
        viewport.background = JBColor.PanelBackground
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = 24
    }
    val component: JComponent = scrollPane

    private val rowByIndex = mutableMapOf<Int, ListRow>()
    private var selectedIndex: Int? = null
    private var records: List<Memo> = emptyList()
    private val selectedRecordIds = linkedSetOf<Long>()
    private var activatedRecordId: Long? = null

    fun render(records: List<Memo>) {
        this.records = records
        listPanel.removeAll()
        rowByIndex.clear()
        selectedIndex = null
        val existingIds = records.map { it.id }.toSet()
        selectedRecordIds.retainAll(existingIds)

        if (records.isEmpty()) {
            val placeholder = JBLabel("No DevLog entries recorded yet.").apply {
                border = JBUI.Borders.empty(32)
                horizontalAlignment = JBLabel.CENTER
                foreground = JBColor.gray
            }
            listPanel.add(
                placeholder,
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                }
            )
        } else {
            var gridY = 0
            var currentDate: String? = null
            records.forEachIndexed { index, record ->
                val recordDate = formatDate(record.updatedAt.toString())
                if (recordDate != currentDate) {
                    currentDate = recordDate
                    listPanel.add(
                        createDateLabel(recordDate),
                        GridBagConstraints().apply {
                            gridx = 0
                            gridy = gridY++
                            weightx = 1.0
                            fill = GridBagConstraints.HORIZONTAL
                            insets = Insets(12, 0, 6, 0)
                        }
                    )
                }
                val row = ListRow(record, index)
                rowByIndex[index] = row
                listPanel.add(
                    row,
                    GridBagConstraints().apply {
                        gridx = 0
                        gridy = gridY++
                        weightx = 1.0
                        fill = GridBagConstraints.HORIZONTAL
                        insets = Insets(2, 0, 2, 0)
                    }
                )
            }
            listPanel.add(
                JPanel(),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = gridY
                    weightx = 1.0
                    weighty = 1.0
                    fill = GridBagConstraints.BOTH
                }
            )
        }

        listPanel.revalidate()
        listPanel.repaint()
        interactions.onSelectionChanged(selectedRecordIds.toSet())
    }

    fun selectAllRecords() {
        selectedRecordIds.clear()
        selectedRecordIds.addAll(records.map { it.id })
        rowByIndex.values.forEach { it.setChecked(true) }
        interactions.onSelectionChanged(selectedRecordIds.toSet())
    }

    fun clearSelection() {
        if (selectedRecordIds.isEmpty()) return
        selectedRecordIds.clear()
        rowByIndex.values.forEach { it.setChecked(false) }
        interactions.onSelectionChanged(emptySet())
    }

    private fun selectRow(index: Int, ensureVisible: Boolean = false) {
        if (selectedIndex == index) return
        selectedIndex?.let { rowByIndex[it]?.setSelected(false) }
        selectedIndex = index
        rowByIndex[index]?.let {
            it.setSelected(true)
            if (ensureVisible) {
                SwingUtilities.invokeLater { it.scrollIntoView() }
            }
        }
    }

    fun navigateToRecord(recordId: Long): Boolean {
        val index = records.indexOfFirst { it.id == recordId }
        if (index == -1) return false
        selectRow(index, ensureVisible = true)
        return true
    }

    private fun createDateLabel(date: String): JComponent =
        JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = true
            background = Color(0x25, 0x26, 0x2A)
            border = JBUI.Borders.empty(6, 0, 6, 0)
            add(JBLabel(date).apply {
                font = JBFont.medium()
                foreground = DATE_LABEL_TEXT
                border = JBUI.Borders.empty(0, 12, 0, 12)
            }, BorderLayout.WEST)
        }

    private inner class ListRow(
        private val record: Memo,
        private val recordIndex: Int
    ) : JBPanel<JBPanel<*>>(BorderLayout()) {

        private val defaultBorder = JBUI.Borders.empty(10, 12)
        private var isRowSelected = false
        private lateinit var rowContainer: JBPanel<JBPanel<*>>
        private lateinit var timeLabel: JBLabel
        private lateinit var titleLabel: JBLabel
        private val checkBox: JCheckBox
        private var suppressToggle = false

        init {
            isOpaque = true
            background = palette.listRowBg
            border = defaultBorder

            checkBox = JCheckBox().apply {
                isOpaque = false
                isSelected = selectedRecordIds.contains(record.id)
                addActionListener {
                    if (!suppressToggle) {
                        toggleSelection(record.id, isSelected)
                    }
                }
            }

            val content = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
            }

            val fileDisplayName =
                record.filePath?.takeIf { it.isNotBlank() }?.substringAfterLast('/')
            val timePanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
            }
            timeLabel = JBLabel(formatTime(record.createdAt.toString())).apply {
                foreground = JBColor.gray
                font = JBFont.small()
            }
            timePanel.add(timeLabel)
            if (record.fullCodeSnapshot != null && record.fullCodeSnapshot.isNotBlank()) {
                timePanel.add(JBLabel(AllIcons.Actions.Preview).apply {
                    toolTipText = "Snapshot included"
                })
            }
            val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
                titleLabel = JBLabel(fileDisplayName ?: "").apply {
                    font = JBFont.medium()
                    foreground = TITLE_MUTED_COLOR
                    border = JBUI.Borders.empty(0, 8, 0, 0)
                }
                add(timePanel, BorderLayout.WEST)
                if (fileDisplayName != null) {
                    add(titleLabel, BorderLayout.CENTER)
                }
            }

            val snippet = createSnippetComponent(record.content)

            val meta = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
                isOpaque = false
                foreground = JBColor.gray
            }

            val footer = meta
            content.add(header, BorderLayout.NORTH)
            content.add(snippet, BorderLayout.CENTER)
            content.add(footer, BorderLayout.SOUTH)

            rowContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = true
                background = palette.listRowBg
                add(checkBox, BorderLayout.WEST)
                add(content, BorderLayout.CENTER)
            }

            add(rowContainer, BorderLayout.CENTER)
            updateBackground()
            propagateClicks(content)
            propagateClicks(snippet)
            propagateClicks(meta)

            componentPopupMenu = createPopupMenu()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        selectRow(recordIndex)
                        if (e.clickCount == 2) {
                            interactions.onSnapshot(record)
                            interactions.onOpenDetail(record)
                            markActivated(record.id)
                        }
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        selectRow(recordIndex)
                    }
                }

            })
        }

        fun setSelected(selected: Boolean) {
            isRowSelected = selected
            border = defaultBorder
            updateBackground()
        }

        private fun createPopupMenu() = JPopupMenu().apply {
            add(JMenuItem("Edit Comment").apply {
                addActionListener {
                    selectRow(recordIndex, ensureVisible = true)
                    interactions.onEdit(record, recordIndex)
                }
            })
            add(JMenuItem("Delete").apply {
                addActionListener { interactions.onDelete(recordIndex, record) }
            })
        }

        fun scrollIntoView() {
            SwingUtilities.invokeLater {
                val container = parent as? JComponent ?: return@invokeLater
                container.scrollRectToVisible(bounds)
            }
        }

        private fun updateBackground() {
            val base = when {
                record.id == activatedRecordId -> ACTIVATED_ROW_COLOR
                isRowSelected -> SELECTED_ROW_COLOR
                else -> palette.listRowBg
            }
            background = base
            rowContainer.background = base
        }

        fun setChecked(checked: Boolean) {
            suppressToggle = true
            checkBox.isSelected = checked
            suppressToggle = false
        }

        fun refreshVisualState() = updateBackground()

        private fun propagateClicks(component: JComponent) {
            component.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    when {
                        SwingUtilities.isLeftMouseButton(e) -> {
                            this@ListRow.dispatchEvent(
                                SwingUtilities.convertMouseEvent(
                                    component,
                                    e,
                                    this@ListRow
                                )
                            )
                        }

                        SwingUtilities.isRightMouseButton(e) -> {
                            selectRow(recordIndex)
                            showPopup(e)
                        }
                    }
                }

                override fun mousePressed(e: MouseEvent) = showPopup(e)

                override fun mouseReleased(e: MouseEvent) = showPopup(e)

                private fun showPopup(e: MouseEvent) {
                    if (e.isPopupTrigger) {
                        selectRow(recordIndex)
                        this@ListRow.componentPopupMenu?.show(e.component, e.x, e.y)
                    }
                }
            })
        }

        private fun createSnippetComponent(comment: String): JBTextArea =
            object : JBTextArea(truncate(extractTitleLine(comment))) {
                init {
                    lineWrap = true
                    wrapStyleWord = true
                    isEditable = false
                    isOpaque = false
                    border = JBUI.Borders.empty(6, 0, 4, 0)
                    foreground = palette.editorFg
                }

                override fun getMinimumSize(): Dimension {
                    val size = super.getPreferredSize()
                    return Dimension(0, size.height)
                }
            }
    }

    private fun extractTitleLine(comment: String): String {
        val firstLine = comment.lineSequence().firstOrNull() ?: ""
        val normalized = firstLine.trim()
        return normalized.ifEmpty { "(No comment provided)" }
    }

    private fun truncate(value: String): String =
        StringUtil.shortenTextWithEllipsis(value, MAX_SNIPPET_LENGTH, 0)

    private fun toggleSelection(recordId: Long, selected: Boolean) {
        val changed = if (selected) {
            selectedRecordIds.add(recordId)
        } else {
            selectedRecordIds.remove(recordId)
        }
        if (changed) {
            interactions.onSelectionChanged(selectedRecordIds.toSet())
        }
    }

    private fun markActivated(recordId: Long) {
        if (activatedRecordId == recordId) return
        activatedRecordId = recordId
        rowByIndex.values.forEach { it.refreshVisualState() }
    }

    private fun formatDate(timestamp: String): String =
        runCatching { LocalDate.parse(timestamp.substring(0, 10)) }
            .getOrNull()
            ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ?: timestamp.substringBefore('T', timestamp)

    private fun formatTime(timestamp: String): String =
        runCatching { LocalDateTime.parse(timestamp) }
            .getOrNull()
            ?.format(DateTimeFormatter.ofPattern("HH:mm"))
            ?: timestamp.take(5)
}
