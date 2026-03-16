package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.FfDialog;
import org.jetbrains.annotations.NotNull;

public class OpenSpecFfAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        FfDialog dialog = new FfDialog(project);
        if (dialog.showAndGet() && dialog.isCompleted()) {
            refreshToolWindow(project);
        }
    }
}
