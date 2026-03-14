package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Archives a completed change by moving it from {@code changes/} to {@code archive/}.
 */
public class OpenSpecArchiveAction extends OpenSpecBaseAction {

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
            archiveChange(project, changeService, active.getFirst());
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
                            archiveChange(project, changeService, selected);
                        }
                    })
                    .createPopup()
                    .showInFocusCenter();
        }
    }

    private void archiveChange(Project project, ChangeService changeService, Change target) {
        String changeName = target.getName();

        // Phase 1: Archive (filesystem)
        try {
            changeService.archiveChange(target);
            OpenSpecNotifier.info(project, "Archive", "Change archived: " + changeName);
        } catch (Exception ex) {
            // Archive failed — do NOT proceed to sync
            OpenSpecNotifier.error(project, "Archive", "Failed to archive change: " + ex.getMessage());
            return;
        }

        refreshToolWindow(project);
    }
}
