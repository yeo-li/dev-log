package com.github.yeoli.devlog.ui

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.Timer
import javax.swing.text.JTextComponent

class NotePanel(
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