package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.johnnyblabs.openspec.toolwindow.OpenSpecToolWindowPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Refreshes the tool window tree view by re-scanning the VFS.
 *
 * <p><b>Strategy: Built-in only.</b> Refresh is a UI operation that re-reads
 * the {@code openspec/} directory tree from the IntelliJ VFS. No CLI
 * interaction is needed.</p>
 */
public class OpenSpecRefreshAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
        if (toolWindow == null) return;

        // Find the Browse tab (first content, or any content that is an OpenSpecToolWindowPanel)
        for (Content content : toolWindow.getContentManager().getContents()) {
            Component component = content.getComponent();
            if (component instanceof OpenSpecToolWindowPanel panel) {
                panel.refresh();
                break;
            }
        }
    }
}
