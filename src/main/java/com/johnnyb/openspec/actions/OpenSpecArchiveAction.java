package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.tracking.IssueLifecycleService;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Archives a completed change by moving it from {@code changes/} to {@code archive/}.
 *
 * <p><b>Strategy: Built-in only.</b> Archive is a write operation that moves
 * directories on disk. The CLI's {@code openspec archive} may use date-prefixed
 * naming or different directory conventions. Using built-in ensures the tree view,
 * status tracking, and directory layout are always consistent — regardless of
 * whether the CLI is installed.</p>
 */
public class OpenSpecArchiveAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> active = changeService.getActiveChanges();

        if (active.isEmpty()) {
            OpenSpecNotifier.warn(project, "No active changes to archive");
            return;
        }

        Change target;
        if (active.size() == 1) {
            target = active.getFirst();
        } else {
            String[] names = active.stream().map(Change::getName).toArray(String[]::new);
            int choice = Messages.showChooseDialog(project,
                    "Select a change to archive:",
                    "Archive Change", Messages.getQuestionIcon(), names, names[0]);
            if (choice < 0) return;
            target = active.get(choice);
        }

        try {
            String changeName = target.getName();
            String changeDir = target.getPath();

            // Trigger issue close in configured trackers (before move, while metadata is accessible)
            IssueLifecycleService lifecycle = project.getService(IssueLifecycleService.class);
            if (lifecycle != null) {
                lifecycle.onArchive(changeName, changeDir);
            }

            changeService.archiveChange(target);
            OpenSpecNotifier.info(project, "Change archived: " + changeName);
            refreshToolWindow(project);
        } catch (Exception ex) {
            OpenSpecNotifier.error(project, "Failed to archive change: " + ex.getMessage());
        }
    }
}
