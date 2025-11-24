package com.github.yeoli.devlog.ui.action

import com.github.yeoli.devlog.domain.memo.domain.Memo
import com.github.yeoli.devlog.ui.MemoExportPipeline
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection
import javax.swing.Icon

class MemoExportAction(
    private val project: Project,
    private val recordsProvider: (() -> List<Memo>)? = null,
    actionText: String = "Download DevLog Logs",
    actionDescription: String = "Download DevLog entries as Markdown text",
    icon: Icon = AllIcons.Actions.Download
) : DumbAwareAction(actionText, actionDescription, icon) {

    private var forceDisabled = false

    fun setForceDisabled(disabled: Boolean) {
        forceDisabled = disabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (forceDisabled) return
        val recordsOverride = recordsProvider?.invoke()
        if (recordsProvider != null && (recordsOverride == null || recordsOverride.isEmpty())) {
            notify("No DevLog entries selected to export.", NotificationType.WARNING)
            return
        }
        val pipeline = MemoExportPipeline(project)
        val payload = pipeline.buildPayload(recordsOverride)

        CopyPasteManager.getInstance().setContents(StringSelection(payload.content))

        val descriptor = FileSaverDescriptor(
            "Save DevLog Export",
            "Choose where to save the DevLog log export.",
            payload.fileExtension
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = dialog.save(null as VirtualFile?, payload.defaultFileName)

        if (wrapper == null) {
            notify(
                "Export cancelled. Markdown remains copied to clipboard.",
                NotificationType.WARNING
            )
            return
        }

        try {
            FileUtil.writeToFile(wrapper.file, payload.content)
            wrapper.virtualFile?.refresh(false, false)
            notify("Saved DevLog log to ${wrapper.file.path} and copied it to the clipboard.")
        } catch (ex: Exception) {
            notify("Failed to save file: ${ex.message}", NotificationType.ERROR)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (forceDisabled) {
            e.presentation.isEnabled = false
            return
        }
        if (recordsProvider != null) {
            e.presentation.isEnabled = recordsProvider.invoke().isNotEmpty()
        }
    }

    private fun notify(message: String, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification("DevLog Export", message, type)
            ?.notify(project)
    }

    companion object {
        private const val NOTIFICATION_GROUP_ID = "YeoliRetrospectNotifications"
    }
}