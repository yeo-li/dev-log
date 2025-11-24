package com.github.yeoli.devlog.ui

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.intellij.icons.AllIcons
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.text.JTextComponent

class MemoDetailPanel(
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
