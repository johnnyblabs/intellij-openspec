package com.johnnyb.openspec.actions;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.johnnyb.openspec.util.CliRunner;
import org.jetbrains.annotations.NotNull;

public abstract class OpenSpecCliAction extends OpenSpecBaseAction {

    protected abstract String[] getCliArgs();

    protected abstract String getCommandLabel();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        try {
            CliRunner.CliResult result = CliRunner.run(project, getCliArgs());
            showOutput(project, result);
        } catch (Exception ex) {
            showError(project, ex);
        }
    }

    protected void showOutput(Project project, CliRunner.CliResult result) {
        ConsoleView console = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project).getConsole();

        console.print("$ openspec " + getCommandLabel() + "\n",
                ConsoleViewContentType.SYSTEM_OUTPUT);

        if (!result.stdout().isEmpty()) {
            console.print(result.stdout(), ConsoleViewContentType.NORMAL_OUTPUT);
        }
        if (!result.stderr().isEmpty()) {
            console.print(result.stderr(), ConsoleViewContentType.ERROR_OUTPUT);
        }

        if (result.isSuccess()) {
            console.print("\nProcess finished with exit code 0\n",
                    ConsoleViewContentType.SYSTEM_OUTPUT);
        } else {
            console.print("\nProcess finished with exit code " + result.exitCode() + "\n",
                    ConsoleViewContentType.ERROR_OUTPUT);
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Run");
        if (toolWindow != null) {
            toolWindow.activate(() -> {
                var content = toolWindow.getContentManager()
                        .getFactory().createContent(console.getComponent(), "OpenSpec: " + getCommandLabel(), true);
                toolWindow.getContentManager().addContent(content);
                toolWindow.getContentManager().setSelectedContent(content);
            });
        }
    }

    private void showError(Project project, Exception ex) {
        ConsoleView console = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project).getConsole();
        console.print("Failed to run openspec " + getCommandLabel() + ": " + ex.getMessage() + "\n",
                ConsoleViewContentType.ERROR_OUTPUT);
    }
}
