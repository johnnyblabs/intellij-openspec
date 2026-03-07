package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeStatus;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Marks a change as "applied" by updating its {@code .openspec.yaml} status.
 *
 * <p><b>Strategy: Built-in only.</b> Apply is a write operation that updates
 * change metadata. The CLI has no equivalent {@code apply} command, so this
 * is inherently built-in. Updates the status field in {@code .openspec.yaml}
 * and refreshes the tree view.</p>
 */
public class OpenSpecApplyAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> active = changeService.getActiveChanges();

        if (active.isEmpty()) {
            OpenSpecNotifier.warn(project, "No active changes to apply");
            return;
        }

        // If multiple changes, let user pick
        Change target;
        if (active.size() == 1) {
            target = active.get(0);
        } else {
            String[] names = active.stream().map(Change::getName).toArray(String[]::new);
            int choice = Messages.showChooseDialog(project,
                    "Select a change to apply:",
                    "Apply Change", Messages.getQuestionIcon(), names, names[0]);
            if (choice < 0) return;
            target = active.get(choice);
        }

        try {
            changeService.updateStatus(target, ChangeStatus.APPLIED);
            OpenSpecNotifier.info(project, "Change applied: " + target.getName());
            refreshToolWindow(project);
        } catch (Exception ex) {
            OpenSpecNotifier.error(project, "Failed to apply change: " + ex.getMessage());
        }
    }
}
