package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.VerifyDialog;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ArchiveReadinessService;
import com.johnnyblabs.openspec.services.ArchiveDispatch;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The Verify menu action — the same pre-archive readiness check as the tool-window Verify button and
 * the Archive pre-flight, so "Verify" means one thing everywhere. Shows the three-state
 * {@link VerifyDialog}; its OK archives the change (enabled for READY and bypassable IN_PROGRESS,
 * disabled for a hard validation BLOCK).
 */
public class OpenSpecVerifyAction extends OpenSpecBaseAction {

    @Override
    protected String getWorkflowId() { return "verify"; }

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

        Change target = activeChanges.getFirst();
        String changeName = target.getName();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Verifying: " + changeName, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Checking readiness...");
                indicator.setFraction(0.0);

                ArchiveReadinessService readinessService = project.getService(ArchiveReadinessService.class);
                ArchiveReadinessResult result = readinessService.verify(changeName);

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (new VerifyDialog(project, result).showAndGet()) {
                        archiveChange(project, changeService, target);
                    }
                });
            }
        });
    }

    private void archiveChange(Project project, ChangeService changeService, Change target) {
        String changeName = target.getName();
        // In the background (it may run the OpenSpec CLI); merges the change's delta specs as `openspec archive` does.
        ArchiveDispatch.start(project, target, true,
                outcome -> {
                    OpenSpecNotifier.info(project, "Archive", ArchiveDispatch.summary(changeName, outcome));
                    refreshToolWindow(project);
                },
                error -> OpenSpecNotifier.error(project, "Archive",
                        "Failed to archive change: " + ArchiveDispatch.reason(error)));
    }
}
