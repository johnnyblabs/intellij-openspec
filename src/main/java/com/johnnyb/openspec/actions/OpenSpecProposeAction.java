package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.johnnyb.openspec.dialogs.ProposeChangeDialog;
import com.johnnyb.openspec.scaffolding.ScaffoldingService;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.toolwindow.OpenSpecToolWindowPanel;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

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
        autoFocusChange(project, changeName);
    }

    private void createChangeBuiltIn(Project project, String changeName, String why, String whatChanges) {
        try {
            ScaffoldingService scaffolding = project.getService(ScaffoldingService.class);
            VirtualFile changeDir = scaffolding.createChange(changeName, why, whatChanges);
            OpenSpecNotifier.info(project, "Propose", "Change proposed: " + changeDir.getName());

            // Fire "What's Next" notification on first proposal
            OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
            if (!settings.isFirstProposalCompleted()) {
                OpenSpecNotifier.info(project, "What's Next",
                        "Your change is created! Next, generate artifacts (design, specs, tasks) " +
                        "from the workflow panel, then implement the tasks.");
                settings.setFirstProposalCompleted(true);
            }
        } catch (Exception ex) {
            OpenSpecNotifier.error(project, "Propose", "Failed to create change: " + ex.getMessage());
        }
    }

    private void autoFocusChange(Project project, String changeName) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
            if (toolWindow == null) return;
            for (Content content : toolWindow.getContentManager().getContents()) {
                Component component = content.getComponent();
                if (component instanceof OpenSpecToolWindowPanel panel) {
                    panel.selectChange(changeName);
                    break;
                }
            }
        });
    }
}
