package com.johnnyb.openspec.toolwindow;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

public class OpenSpecConsolePanel extends JPanel {

    private final ConsoleView consoleView;

    public OpenSpecConsolePanel(Project project) {
        super(new BorderLayout());
        consoleView = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project).getConsole();
        add(consoleView.getComponent(), BorderLayout.CENTER);
        consoleView.print("CLI output will appear here when commands are executed.\n",
                ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public void printCommand(String command) {
        consoleView.print("$ " + command + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public void printOutput(String text) {
        consoleView.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    public void printError(String text) {
        consoleView.print(text, ConsoleViewContentType.ERROR_OUTPUT);
    }

    public void printSystem(String text) {
        consoleView.print(text + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public void clear() {
        consoleView.clear();
    }

    public ConsoleView getConsoleView() {
        return consoleView;
    }
}
