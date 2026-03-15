package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.ManageAiToolsDialog;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the Manage AI Tools dialog for adding, configuring, and updating
 * AI tool integrations in the project.
 */
public class OpenSpecManageToolsAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ManageAiToolsDialog dialog = new ManageAiToolsDialog(project);
        dialog.show();

        // Refresh tool window after dialog closes (tools may have changed)
        refreshToolWindow(project);
    }
}
