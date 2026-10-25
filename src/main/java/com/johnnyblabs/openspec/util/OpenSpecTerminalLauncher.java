package com.johnnyblabs.openspec.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

public final class OpenSpecTerminalLauncher {

    private static final Logger LOG = Logger.getInstance(OpenSpecTerminalLauncher.class);

    private OpenSpecTerminalLauncher() {
    }

    /**
     * Opens (or focuses) the IntelliJ Terminal tool window in the given project's working
     * directory and runs the supplied command. Returns {@code true} if the command was
     * dispatched to the terminal, {@code false} if the Terminal tool window plugin is
     * unavailable or any other failure prevented the launch — the caller is expected to
     * surface a fallback (e.g. clipboard copy + notification).
     *
     * <p>Wraps {@link Throwable} so callers don't need to know about
     * {@code NoClassDefFoundError} when the terminal plugin is disabled or absent. The plugin declares it as an
     * optional dependency; without that declaration its classes are never visible, and every launch failed.
     *
     * <p>The command is sent through {@link TerminalWidget#sendCommandToExecute}, which queues it until the shell
     * has started. {@code createShellWidget} returns a {@link TerminalWidget} bridge, never a
     * {@code ShellTerminalWidget}, on every supported IDE. Call on the EDT.
     */
    public static boolean launchCommand(@NotNull Project project, @NotNull String command, @NotNull String tabName) {
        try {
            TerminalToolWindowManager manager = TerminalToolWindowManager.getInstance(project);
            if (manager == null) return false;
            String workingDir = project.getBasePath();
            TerminalWidget widget = manager.createShellWidget(workingDir, tabName, true, false);
            if (widget == null) return false;
            widget.sendCommandToExecute(command);
            return true;
        } catch (NoClassDefFoundError e) {
            // The terminal plugin is disabled or not installed: expected, and the caller falls back.
            LOG.info("Terminal plugin unavailable, not launching: " + command, e);
            return false;
        } catch (Throwable t) {
            LOG.warn("Terminal launch failed for command: " + command, t);
            return false;
        }
    }

    /**
     * Canonical fallback body paired with a clipboard copy of {@code command} when the
     * terminal tool window isn't available.
     */
    @NotNull
    public static String fallbackMessage(@NotNull String command) {
        return "Command copied to clipboard: " + command
                + ". Open a terminal in your project directory and paste to continue.";
    }
}
