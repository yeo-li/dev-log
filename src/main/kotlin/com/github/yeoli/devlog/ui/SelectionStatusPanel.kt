package com.github.yeoli.devlog.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent

class SelectionStatusPanel(
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