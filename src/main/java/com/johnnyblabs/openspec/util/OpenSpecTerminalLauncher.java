package com.johnnyblabs.openspec.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
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
     * {@code NoClassDefFoundError} from a stripped IDE missing the terminal plugin.
     */
    public static boolean launchCommand(@NotNull Project project, @NotNull String command, @NotNull String tabName) {
        try {
            TerminalToolWindowManager manager = TerminalToolWindowManager.getInstance(project);
            if (manager == null) return false;
            String workingDir = project.getBasePath();
            Object widget = manager.createShellWidget(workingDir, tabName, true, false);
            if (widget instanceof ShellTerminalWidget shell) {
                shell.executeCommand(command);
                return true;
            }
            return false;
        } catch (Throwable t) {
            LOG.info("Terminal launch failed for command: " + command, t);
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
