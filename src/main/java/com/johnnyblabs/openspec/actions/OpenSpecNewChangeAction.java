package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.NewChangeDialog;
import com.johnnyblabs.openspec.scaffolding.ScaffoldingService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.toolwindow.OpenSpecToolWindowPanel;
import com.johnnyblabs.openspec.util.CliCommands;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Function;

/**
 * Does what OpenSpec's {@code new} workflow does: creates a change without any artifact. The user then creates the
 * artifacts one at a time, the schema's first (the proposal, for spec-driven) first, from the workflow panel or with
 * Continue. Gated on {@code new}, which is not in the core profile.
 */
public class OpenSpecNewChangeAction extends OpenSpecBaseAction implements DumbAware {

    static final String TITLE = "New Change";

    @Override
    protected String getWorkflowId() { return "new"; }

    private static final Function<Project, NewChangeDialog> REAL_DIALOG = NewChangeDialog::new;
    private static volatile Function<Project, NewChangeDialog> dialogFactory = REAL_DIALOG;

    @TestOnly
    static void setDialogFactoryForTest(@Nullable Function<Project, NewChangeDialog> factory) {
        dialogFactory = factory == null ? REAL_DIALOG : factory;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        NewChangeDialog dialog = dialogFactory.apply(project);
        if (!dialog.showAndGet()) return;
        create(project, dialog.getChangeName(), dialog.getSelectedSchema());
    }

    /**
     * Creates {@code changeName} as {@code openspec new change} does: through the CLI, off the EDT, when it is
     * available (never with {@code --description}, which writes a README), else with the same scaffold in a write
     * action. Then shows it in the tool window. Call on the EDT.
     */
    static void create(Project project, String changeName, @Nullable String schema) {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null || !detection.isAvailable()) {
            try {
                project.getService(ScaffoldingService.class).createEmptyChange(changeName, schema);
            } catch (java.io.IOException ex) {
                OpenSpecNotifier.error(project, TITLE, "Couldn't create change '" + changeName + "'. " + ex.getMessage());
                return;
            }
            created(project, changeName);
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Creating change: " + changeName, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                String error;
                try {
                    CliRunner.CliResult result = CliRunner.run(project, CliCommands.newChange(changeName, schema));
                    error = result.isSuccess() ? null : result.stderr();
                } catch (CliRunner.CliException ex) {
                    error = ex.getMessage();
                }
                String failure = error;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (failure != null) {
                        OpenSpecNotifier.error(project, TITLE, "Couldn't create change '" + changeName + "'. " + failure);
                    } else {
                        created(project, changeName);
                    }
                }, project.getDisposed());
            }
        });
    }

    private static void created(Project project, String changeName) {
        OpenSpecNotifier.info(project, TITLE, "Created change " + changeName
                + ". Create its artifacts from the workflow panel, or with Continue.");
        OpenSpecToolWindowPanel.withPanel(project, panel -> {
            panel.refresh();
            panel.selectChange(changeName);
        });
    }
}
