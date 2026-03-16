package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.VerifyReportDialog;
import com.johnnyblabs.openspec.model.VerificationReport;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.VerificationService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OpenSpecVerifyAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> activeChanges = changeService.getActiveChanges();
        if (activeChanges.isEmpty()) {
            OpenSpecNotifier.warn(project, "No active changes",
                    "Create a change first to verify.");
            return;
        }

        String changeName = activeChanges.getFirst().getName();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Verifying: " + changeName, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Checking completeness...");
                indicator.setFraction(0.0);

                VerificationService verificationService = project.getService(VerificationService.class);
                VerificationReport report = verificationService.verify(changeName);

                ApplicationManager.getApplication().invokeLater(() ->
                        new VerifyReportDialog(project, report).show());
            }
        });
    }
}
