package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.johnnyblabs.openspec.dialogs.SyncPreviewDialog;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.SpecSyncService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class OpenSpecSyncAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> active = changeService.getActiveChanges();

        if (active.isEmpty()) {
            OpenSpecNotifier.warn(project, "Sync Specs", "No active changes");
            return;
        }

        if (active.size() == 1) {
            syncChange(project, active.getFirst().getName());
        } else {
            List<String> names = active.stream().map(Change::getName).toList();
            JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(names)
                    .setTitle("Sync Specs for Change")
                    .setItemChosenCallback(name -> syncChange(project, name))
                    .createPopup()
                    .showInFocusCenter();
        }
    }

    private void syncChange(Project project, String changeName) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Computing spec sync...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                SpecSyncService syncService = project.getService(SpecSyncService.class);
                List<SpecSyncResult> results = syncService.computeSync(changeName);

                if (results.isEmpty()) {
                    OpenSpecNotifier.info(project, "Sync Specs", "No delta specs found for \"" + changeName + "\"");
                    return;
                }

                ApplicationManager.getApplication().invokeLater(() -> {
                    SyncPreviewDialog dialog = new SyncPreviewDialog(project, results);
                    if (dialog.showAndGet()) {
                        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Applying spec sync...", false) {
                            @Override
                            public void run(@NotNull ProgressIndicator ind) {
                                try {
                                    syncService.applySync(results);
                                    OpenSpecNotifier.info(project, "Sync Specs",
                                            "Specs synced for \"" + changeName + "\"");
                                    refreshToolWindow(project);
                                } catch (IOException ex) {
                                    OpenSpecNotifier.error(project, "Sync Specs",
                                            "Failed to apply sync: " + ex.getMessage());
                                }
                            }
                        });
                    }
                });
            }
        });
    }
}
