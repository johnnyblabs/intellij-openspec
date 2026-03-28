package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.BulkArchiveDialog;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OpenSpecBulkArchiveAction extends OpenSpecBaseAction {

    @Override
    protected String getWorkflowId() { return "bulk-archive"; }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> active = changeService.getActiveChanges();

        if (active.isEmpty()) {
            OpenSpecNotifier.warn(project, "Bulk Archive", "No active changes to archive");
            return;
        }

        BulkArchiveDialog dialog = new BulkArchiveDialog(project, active);
        dialog.show();

        // Refresh tool window after dialog closes
        refreshToolWindow(project);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (!e.getPresentation().isEnabled()) return;
        Project project = e.getProject();
        if (project == null) return;
        // Only enable when 2+ active changes exist
        ChangeService changeService = project.getService(ChangeService.class);
        e.getPresentation().setEnabled(changeService.getActiveChanges().size() >= 2);
    }
}
