package com.github.yeoli.devlog.toolWindow


import com.github.yeoli.devlog.ui.DevLogPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.Color

/**
 * RetrospectPanel을 IntelliJ ToolWindow 영역에 붙여주는 팩토리.
 * ToolWindow 생성 시 UI와 경계선 스타일을 세팅한다.
 */
class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().info("Yeoli Retrospect ToolWindow ready.")
    }

    /**
     * ToolWindow가 초기화될 때 호출되어 컨텐츠와 테두리 스타일을 구성한다.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        toolWindow.setIcon(AllIcons.Actions.Annotate)
        val panel = DevLogPanel(project, toolWindow.disposable)
        val content = ContentFactory.getInstance().createContent(panel.component, null, false);

        toolWindow.contentManager.addContent(content)
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "false")
        applyDockBorder(toolWindow)
        project.messageBus.connect(toolWindow.disposable).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(toolWindowManager: ToolWindowManager) {
                    val updated = toolWindowManager.getToolWindow(toolWindow.id) ?: return
                    applyDockBorder(updated)
                }
            }
        )
    }

    override fun shouldBeAvailable(project: Project) = true

    /**
     * 도킹 상태/위치에 맞춰 ToolWindow 테두리를 설정한다.
     */
    private fun applyDockBorder(toolWindow: ToolWindow) {
        if (toolWindow.type == ToolWindowType.FLOATING ||
            toolWindow.type == ToolWindowType.WINDOWED ||
            toolWindow.type == ToolWindowType.SLIDING
        ) {
            toolWindow.component.border = JBUI.Borders.empty()
            toolWindow.component.repaint()
            return
        }

        val sides = when (toolWindow.anchor) {
            ToolWindowAnchor.LEFT -> BorderSides(0, 0, 0, BORDER_WIDTH)
            ToolWindowAnchor.RIGHT -> BorderSides(0, BORDER_WIDTH, 0, 0)
            ToolWindowAnchor.TOP -> BorderSides(0, 0, BORDER_WIDTH, 0)
            ToolWindowAnchor.BOTTOM -> BorderSides(BORDER_WIDTH, 0, 0, 0)
            else -> BorderSides(0, 0, 0, 0)
        }

        val border = if (sides.isEmpty()) {
            JBUI.Borders.empty()
        } else {
            JBUI.Borders.customLine(
                DOCK_BORDER_COLOR,
                sides.top,
                sides.left,
                sides.bottom,
                sides.right
            )
        }
        toolWindow.component.border = border
        toolWindow.component.revalidate()
        toolWindow.component.repaint()
    }

    companion object {
        private val DOCK_BORDER_COLOR = JBColor(Color(0x2b, 0x2d, 0x30), Color(0x2b, 0x2d, 0x30))
        private const val BORDER_WIDTH = 1
    }

    private data class BorderSides(val top: Int, val left: Int, val bottom: Int, val right: Int) {
        fun isEmpty() = top + left + bottom + right == 0
    }
}
