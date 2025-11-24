package com.github.yeoli.devlog.ui.action

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class DeleteSelectedAction(
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