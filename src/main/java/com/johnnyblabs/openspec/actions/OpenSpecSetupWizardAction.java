package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.SetupWizardDialog;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the Setup Wizard dialog from the toolbar or menu.
 */
public class OpenSpecSetupWizardAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        SetupWizardDialog dialog = new SetupWizardDialog(project);
        dialog.show();
    }
}
