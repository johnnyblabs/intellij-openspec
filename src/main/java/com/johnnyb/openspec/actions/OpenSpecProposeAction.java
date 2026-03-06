package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.johnnyb.openspec.util.CliRunner;
import org.jetbrains.annotations.NotNull;

public class OpenSpecProposeAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String description = Messages.showInputDialog(
                project,
                "Enter a description for the proposed change:",
                "OpenSpec Propose",
                Messages.getQuestionIcon());

        if (description == null || description.isBlank()) return;

        try {
            CliRunner.CliResult result = CliRunner.run(project, "propose", description);
            showOutput(project, result);
        } catch (Exception ex) {
            Messages.showErrorDialog(project,
                    "Failed to run openspec propose: " + ex.getMessage(),
                    "OpenSpec Error");
        }
    }

    private void showOutput(Project project, CliRunner.CliResult result) {
        if (result.isSuccess()) {
            Messages.showInfoMessage(project, result.stdout(), "OpenSpec Propose");
        } else {
            Messages.showErrorDialog(project,
                    result.stderr().isEmpty() ? result.stdout() : result.stderr(),
                    "OpenSpec Propose Failed");
        }
    }
}
