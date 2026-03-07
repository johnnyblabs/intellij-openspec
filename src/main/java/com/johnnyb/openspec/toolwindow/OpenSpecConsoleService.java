package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class OpenSpecConsoleService {

    private final Project project;
    private OpenSpecConsolePanel consolePanel;

    public OpenSpecConsoleService(Project project) {
        this.project = project;
    }

    public void register(OpenSpecConsolePanel panel) {
        this.consolePanel = panel;
    }

    @Nullable
    public OpenSpecConsolePanel getConsolePanel() {
        return consolePanel;
    }

    /**
     * Returns the console panel and activates the Console tab in the tool window.
     */
    @Nullable
    public OpenSpecConsolePanel getAndActivate() {
        if (consolePanel == null) return null;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
        if (toolWindow != null) {
            toolWindow.activate(() -> {
                var contentManager = toolWindow.getContentManager();
                Content consoleContent = contentManager.findContent("Console");
                if (consoleContent != null) {
                    contentManager.setSelectedContent(consoleContent);
                }
            });
        }
        return consolePanel;
    }
}
