package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.johnnyblabs.openspec.toolwindow.WorkflowActionPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class OpenSpecFfAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
        if (toolWindow == null) return;

        toolWindow.activate(() -> {
            // Find the WorkflowActionPanel in the Browse tab
            var content = toolWindow.getContentManager().findContent("Browse");
            if (content == null) return;

            Component component = content.getComponent();
            WorkflowActionPanel panel = findWorkflowPanel(component);
            if (panel != null) {
                panel.activateFfInput();
            }
        });
    }

    private static WorkflowActionPanel findWorkflowPanel(Component root) {
        if (root instanceof WorkflowActionPanel wap) return wap;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                WorkflowActionPanel found = findWorkflowPanel(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
