package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.ManageAiToolsDialog;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the Manage AI Tools dialog for adding, configuring, and updating
 * AI tool integrations in the project.
 *
 * <p>Unlike other OpenSpec actions, this is always enabled when a project is open —
 * users should be able to manage AI tools before initializing OpenSpec.</p>
 */
public class OpenSpecManageToolsAction extends OpenSpecBaseAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Always available when a project is open — don't require openspec/ to exist
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

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
