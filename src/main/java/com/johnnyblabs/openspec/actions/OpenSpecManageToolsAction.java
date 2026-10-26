package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.ManageAiToolsDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Consumer;

/**
 * Opens the Manage AI Tools dialog for adding, configuring, and updating
 * AI tool integrations in the project.
 *
 * <p>Unlike other OpenSpec actions, this is always enabled when a project is open —
 * users should be able to manage AI tools before initializing OpenSpec.</p>
 */
public class OpenSpecManageToolsAction extends OpenSpecBaseAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Always available when a project is open — don't require openspec/ to exist
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        showFor(project);
    }

    /** Shows Manage AI Tools for {@code project}, then refreshes the tool window. Call on the EDT. */
    public static void showFor(@NotNull Project project) {
        dialogOpener.accept(project);

        // Refresh tool window after dialog closes (tools may have changed)
        refreshToolWindow(project);
    }

    /**
     * Shows Manage AI Tools from inside another modal dialog (the Settings page's link). The tool window is refreshed
     * from a pooled thread, which takes {@link #refreshToolWindow}'s asynchronous path: a synchronous VFS refresh on the
     * EDT would stall the Settings dialog, and the dialog has already refreshed the project directory itself.
     */
    public static void showFromModal(@NotNull Project project) {
        dialogOpener.accept(project);
        ApplicationManager.getApplication().executeOnPooledThread(() -> refreshToolWindow(project));
    }

    private static final Consumer<Project> SHOW_DIALOG = project -> new ManageAiToolsDialog(project).show();

    /** Shows the dialog; replaced in tests, which can't show a modal dialog. */
    private static volatile Consumer<Project> dialogOpener = SHOW_DIALOG;

    @TestOnly
    public static void setDialogOpenerForTest(@Nullable Consumer<Project> opener) {
        dialogOpener = opener == null ? SHOW_DIALOG : opener;
    }
}
