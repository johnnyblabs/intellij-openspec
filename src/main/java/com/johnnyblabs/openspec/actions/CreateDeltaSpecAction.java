package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.scaffolding.ScaffoldingService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

public class CreateDeltaSpecAction extends AnAction {

    private final String changePath;

    public CreateDeltaSpecAction(String changePath) {
        super("Create Spec...");
        this.changePath = changePath;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String domain = Messages.showInputDialog(project,
                "Enter the domain name for the spec:",
                "Create Spec", Messages.getQuestionIcon());
        if (domain == null || domain.isBlank()) return;

        VirtualFile changeDir = LocalFileSystem.getInstance().findFileByPath(changePath);
        if (changeDir == null) {
            OpenSpecNotifier.error(project, "Delta Spec", "Change directory not found");
            return;
        }

        try {
            ScaffoldingService scaffolding = project.getService(ScaffoldingService.class);
            scaffolding.createDeltaSpec(changeDir, domain);
            OpenSpecNotifier.info(project, "Delta Spec", "Spec created for domain: " + domain);
        } catch (Exception ex) {
            OpenSpecNotifier.error(project, "Delta Spec", "Failed to create spec: " + ex.getMessage());
        }
    }
}
