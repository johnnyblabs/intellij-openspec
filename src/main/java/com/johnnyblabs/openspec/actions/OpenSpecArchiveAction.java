package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.johnnyblabs.openspec.dialogs.VerifyDialog;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult;
import com.johnnyblabs.openspec.services.ArchiveDispatch;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.ArchiveReadinessService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Archives a completed change by moving it from {@code changes/} to {@code archive/}.
 * Runs a pre-flight Verify check before archiving.
 */
public class OpenSpecArchiveAction extends OpenSpecBaseAction {

    @Override
    protected String getWorkflowId() { return "archive"; }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> active = changeService.getActiveChanges();

        if (active.isEmpty()) {
            OpenSpecNotifier.warn(project, "Archive", "No active changes to archive");
            return;
        }

        if (active.size() == 1) {
            runVerifyAndArchive(project, changeService, active.getFirst());
        } else {
            List<String> names = active.stream().map(Change::getName).toList();
            JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(names)
                    .setTitle("Archive Change")
                    .setItemChosenCallback(name -> {
                        Change selected = active.stream()
                                .filter(c -> c.getName().equals(name))
                                .findFirst().orElse(null);
                        if (selected != null) {
                            runVerifyAndArchive(project, changeService, selected);
                        }
                    })
                    .createPopup()
                    .showInFocusCenter();
        }
    }

    private void runVerifyAndArchive(Project project, ChangeService changeService, Change target) {
        // Verify runs the CLI + a blocking AI call — keep it off the EDT, then show the pre-flight
        // dialog and archive on the EDT. The dialog's OK is enabled for READY and (bypassable)
        // IN_PROGRESS, and disabled only for a hard validation BLOCK.
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Verifying: " + target.getName(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ArchiveReadinessService readinessService = project.getService(ArchiveReadinessService.class);
                ArchiveReadinessResult result = readinessService.verify(target.getName());

                ApplicationManager.getApplication().invokeLater(() -> {
                    VerifyDialog dialog = new VerifyDialog(project, result);
                    if (dialog.showAndGet()) {
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
