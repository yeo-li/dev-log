package com.github.yeoli.devlog.ui.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ToggleSelectionAction(
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