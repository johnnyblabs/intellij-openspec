package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.services.CliDetectionService;
import com.johnnyb.openspec.toolwindow.OpenSpecConsolePanel;
import com.johnnyb.openspec.toolwindow.OpenSpecConsoleService;
import com.johnnyb.openspec.util.CliRunner;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for actions that delegate to the OpenSpec CLI for <b>read-only</b> operations.
 *
 * <p>This class implements CLI-first execution with a built-in fallback. It should only
 * be used for read operations (e.g., List) where the CLI can provide richer output than
 * the built-in implementation. Write operations (Init, Propose, Apply, Archive) should
 * extend {@link OpenSpecBaseAction} directly and use built-in logic exclusively.</p>
 *
 * @see OpenSpecBaseAction for the overall CLI/built-in strategy
 */
public abstract class OpenSpecCliAction extends OpenSpecBaseAction {

    protected abstract String[] getCliArgs();

    protected abstract String getCommandLabel();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null || !detection.isAvailable()) {
            if (!handleCliMissing(project, e)) {
                OpenSpecNotifier.warn(project,
                        "OpenSpec CLI not available. Cannot run '" + getCommandLabel() + "'. " +
                                "Install with: npm i -g openspec-dev");
            }
            return;
        }

        new Task.Backgroundable(project, "Running openspec " + getCommandLabel(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    CliRunner.CliResult result = CliRunner.run(project, getCliArgs());
                    ApplicationManager.getApplication()
                            .invokeLater(() -> showOutput(project, result));
                } catch (Exception ex) {
                    ApplicationManager.getApplication()
                            .invokeLater(() -> showError(project, ex));
                }
            }
        }.queue();
    }

    /**
     * Called when CLI is not available. Subclasses can override to provide fallback behavior.
     * @return true if fallback was handled, false to show default CLI-missing warning
     */
    protected boolean handleCliMissing(Project project, AnActionEvent event) {
        return false;
    }

    protected void showOutput(Project project, CliRunner.CliResult result) {
        OpenSpecConsoleService consoleService = project.getService(OpenSpecConsoleService.class);
        OpenSpecConsolePanel console = consoleService != null ? consoleService.getAndActivate() : null;

        if (console == null) {
            OpenSpecNotifier.info(project, "openspec " + getCommandLabel() +
                    (result.isSuccess() ? " completed" : " failed (exit " + result.exitCode() + ")"));
            return;
        }

        console.clear();
        console.printCommand("openspec " + getCommandLabel());

        if (!result.stdout().isEmpty()) {
            console.printOutput(result.stdout());
        }
        if (!result.stderr().isEmpty()) {
            console.printError(result.stderr());
        }

        if (result.isSuccess()) {
            console.printSystem("Process finished with exit code 0");
            refreshToolWindow(project);
        } else {
            console.printError("\nProcess finished with exit code " + result.exitCode() + "\n");
        }
    }

    private void showError(Project project, Exception ex) {
        OpenSpecNotifier.error(project, "Failed to run openspec " + getCommandLabel() + ": " + ex.getMessage());
    }
}
