package com.github.yeoli.devlog.ui

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.text.JTextComponent

class MemoInputComposer(
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
