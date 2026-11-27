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
import com.johnnyblabs.openspec.dialogs.ArchiveSyncPrompt;
import com.johnnyblabs.openspec.services.ArchiveDispatch;
import com.johnnyblabs.openspec.services.SpecSyncService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The Verify menu action — the same pre-archive readiness check as the tool-window Verify button and
 * the Archive pre-flight, so "Verify" means one thing everywhere. Shows the three-state
 * {@link VerifyDialog} as a report: its OK closes, as upstream's verify workflow never archives. When the profile
 * includes {@code archive}, its separate Archive… button hands off to the archive step. Outside Direct API, its
 * "Verify with <tool>…" button hands the change to the agent's verify skill ({@link VerifyHandoff}).
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

        ChangePicker.pick(activeChanges.stream().map(Change::getName).toList(), e.getDataContext(), "Verify",
                name -> verify(project, changeService, activeChanges.stream()
                        .filter(c -> c.getName().equals(name)).findFirst().orElseThrow()));
    }

    private void verify(Project project, ChangeService changeService, Change target) {
        String changeName = target.getName();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Verifying: " + changeName, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Checking readiness...");
                indicator.setFraction(0.0);

                ArchiveReadinessService readinessService = project.getService(ArchiveReadinessService.class);
                ArchiveReadinessResult result = readinessService.verify(changeName);
                boolean hasDeltaSpecs = project.getService(SpecSyncService.class).hasDeltaSpecs(changeName);
                java.util.List<String> retiring = hasDeltaSpecs
                        ? project.getService(SpecSyncService.class).retiringCapabilities(changeName) : java.util.List.of();
                boolean archiveOffered = VerifyDialog.archiveOffered(project);
                VerifyHandoff.Target agent = VerifyHandoff.resolve(project);

                ApplicationManager.getApplication().invokeLater(() -> {
                    VerifyDialog dialog = VerifyDialog.report(project, result, archiveOffered, agent.label());
                    dialog.show();
                    if (VerifyDialog.archiveRequested(dialog.getExitCode())) {
                        archiveChange(project, changeService, target, hasDeltaSpecs, retiring);
                    } else if (VerifyDialog.handoffRequested(dialog.getExitCode())) {
                        VerifyHandoff.deliver(project, changeName, agent);
                    }
                });
            }
        });
    }

    private void archiveChange(Project project, ChangeService changeService, Change target, boolean hasDeltaSpecs,
                               java.util.List<String> retiring) {
        String changeName = target.getName();
        Boolean sync = ArchiveSyncPrompt.choose(project, changeName, hasDeltaSpecs, retiring);
        if (sync == null) {
            return;
        }
        // In the background (it may run the OpenSpec CLI); merges the change's delta specs when the user asked to sync.
        ArchiveDispatch.start(project, target, sync,
                outcome -> {
                    OpenSpecNotifier.info(project, "Archive", ArchiveDispatch.summary(changeName, outcome));
                    refreshToolWindow(project);
                },
                error -> OpenSpecNotifier.error(project, "Archive",
                        "Failed to archive change: " + ArchiveDispatch.reason(error)));
    }
}
