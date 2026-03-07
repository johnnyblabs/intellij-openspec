package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyb.openspec.dialogs.ProposeChangeDialog;
import com.johnnyb.openspec.scaffolding.ScaffoldingService;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a new change proposal with all required artifacts.
 *
 * <p><b>Strategy: Built-in only.</b> Propose is a write operation that creates
 * the full artifact set (proposal.md, design.md, tasks.md, specs/). The CLI's
 * {@code openspec new change} only creates {@code .openspec.yaml}, which is too
 * minimal. Built-in scaffolding produces a complete, predictable structure every
 * time.</p>
 */
public class OpenSpecProposeAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ProposeChangeDialog dialog = new ProposeChangeDialog(project);
        if (!dialog.showAndGet()) return;

        String changeName = dialog.getChangeName();
        String why = dialog.getWhy();
        String whatChanges = dialog.getWhatChanges();

        // Always use built-in scaffolding — it creates the full artifact set
        // (proposal.md, design.md, tasks.md, specs/) that both the plugin and CLI expect.
        // The CLI's "new change" only creates .openspec.yaml.
        createChangeBuiltIn(project, changeName, why, whatChanges);
        refreshToolWindow(project);
    }

    private void createChangeBuiltIn(Project project, String changeName, String why, String whatChanges) {
        try {
            ScaffoldingService scaffolding = project.getService(ScaffoldingService.class);
            VirtualFile changeDir = scaffolding.createChange(changeName, why, whatChanges);
            OpenSpecNotifier.info(project, "Change proposed: " + changeDir.getName());
        } catch (Exception ex) {
            OpenSpecNotifier.error(project, "Failed to create change: " + ex.getMessage());
        }
    }
}
