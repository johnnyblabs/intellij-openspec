package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.toolwindow.WorkflowActionPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class OpenSpecFfAction extends OpenSpecBaseAction {

    @Override
    protected String getWorkflowId() { return "ff"; }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (!e.getPresentation().isEnabled()) return;

        Project project = e.getProject();
        if (project == null) return;

        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService == null || !apiService.isConfigured()) {
            e.getPresentation().setEnabled(false);
            e.getPresentation().setDescription(
                    "Requires AI provider. Configure in Settings \u2192 Tools \u2192 OpenSpec.");
        }
    }

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
