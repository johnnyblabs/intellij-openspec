package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeArtifactDag;
import com.johnnyb.openspec.services.ArtifactOrchestrationService;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.toolwindow.OpenSpecToolWindowPanel;
import com.johnnyb.openspec.tracking.IssueLifecycleService;
import com.johnnyb.openspec.util.ApplyPromptBuilder;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Assembles a full-context implementation prompt from a change's design,
 * specs, and tasks, then delivers it via the workflow panel's tool selector.
 */
public class OpenSpecApplyAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> active = changeService.getActiveChanges();

        if (active.isEmpty()) {
            OpenSpecNotifier.warn(project, "Apply", "No active changes to apply");
            return;
        }

        // If multiple changes, let user pick via popup
        if (active.size() == 1) {
            applyChange(project, active.getFirst());
        } else {
            List<String> names = active.stream().map(Change::getName).toList();
            JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(names)
                    .setTitle("Apply Change")
                    .setItemChosenCallback(name -> {
                        Change selected = active.stream()
                                .filter(c -> c.getName().equals(name))
                                .findFirst().orElse(null);
                        if (selected != null) {
                            applyChange(project, selected);
                        }
                    })
                    .createPopup()
                    .showInFocusCenter();
        }
    }

    private void applyChange(Project project, Change target) {
        String changeName = target.getName();
        String changeDir = target.getPath();

        // Run file I/O checks on background thread, then continue on EDT
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            // Check artifact completion
            ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
            ChangeArtifactDag dag = orchestration.getArtifactStatus(changeName);
            if (dag != null && !dag.isComplete()) {
                OpenSpecNotifier.warn(project, "Apply",
                        "Not all artifacts are complete for \"" + changeName + "\". Generate artifacts first.");
                return;
            }

            // Check if tasks exist
            Path tasksPath = Path.of(changeDir, "tasks.md");
            if (!Files.exists(tasksPath)) {
                OpenSpecNotifier.warn(project, "Apply", "No tasks.md found for \"" + changeName + "\"");
                return;
            }

            // Check if all tasks are already complete
            try {
                String tasksContent = Files.readString(tasksPath);
                int[] counts = ApplyPromptBuilder.countTasks(tasksContent);
                if (counts[1] > 0 && counts[0] == counts[1]) {
                    OpenSpecNotifier.info(project, "Apply",
                            "All tasks complete for \"" + changeName + "\". Consider archiving.");
                    return;
                }
            } catch (IOException ignored) {
            }

            // Focus the workflow panel and trigger apply from there
            focusAndApply(project, changeName);

            // Trigger issue status updates in configured trackers
            IssueLifecycleService lifecycle = project.getService(IssueLifecycleService.class);
            if (lifecycle != null) {
                lifecycle.onApply(changeName, changeDir);
            }
        });
    }

    private void focusAndApply(Project project, String changeName) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
            if (toolWindow == null) return;
            for (Content content : toolWindow.getContentManager().getContents()) {
                Component component = content.getComponent();
                if (component instanceof OpenSpecToolWindowPanel panel) {
                    panel.selectChangeAndApply(changeName);
                    return;
                }
            }
            // Fallback if panel not found — just notify
            OpenSpecNotifier.info(project, "Apply",
                    "Open the OpenSpec tool window to apply tasks for \"" + changeName + "\"");
        });
    }
}
