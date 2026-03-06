package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.johnnyb.openspec.toolwindow.OpenSpecToolWindowPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class OpenSpecRefreshAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
        if (toolWindow == null) return;

        Content content = toolWindow.getContentManager().getSelectedContent();
        if (content != null) {
            Component component = content.getComponent();
            if (component instanceof OpenSpecToolWindowPanel panel) {
                panel.refresh();
            }
        }
    }
}
