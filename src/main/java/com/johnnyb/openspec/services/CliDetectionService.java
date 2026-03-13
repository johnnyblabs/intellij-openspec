package com.johnnyb.openspec.services;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.johnnyb.openspec.settings.OpenSpecSettings;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class CliDetectionService {
    private static final Logger LOG = Logger.getInstance(CliDetectionService.class);
    private static final int TIMEOUT_MS = 10_000;

    /**
     * Common install locations for openspec on macOS/Linux.
     */
    private static final List<String> COMMON_PATHS = List.of(
            "/opt/homebrew/bin/openspec",
            "/usr/local/bin/openspec",
            "/usr/bin/openspec"
    );

    private final Project project;
    private volatile boolean available;
    private volatile String detectedPath;
    private volatile String detectedVersion;
    private volatile String loginShellPath;

    public CliDetectionService(Project project) {
        this.project = project;
    }

    public void detect() {
        available = false;
        detectedPath = null;
        detectedVersion = null;

        // Resolve login shell PATH first — needed on macOS where GUI apps
        // don't inherit terminal PATH (so node/openspec can't be found)
        resolveLoginShellPath();

        // 1. Check settings path first
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String settingsPath = settings.getCliPath();
        if (settingsPath != null && !settingsPath.isEmpty()) {
            if (tryPath(settingsPath)) return;
        }

        // 2. Try bare "openspec" via GeneralCommandLine (uses IntelliJ's env resolution)
        if (tryPath("openspec")) return;

        // 3. Try via user's login shell
        String shellPath = tryLoginShellWhich();
        if (shellPath != null && tryPath(shellPath)) return;

        // 4. Try common install locations
        for (String path : COMMON_PATHS) {
            if (tryPath(path)) return;
        }
    }

    private boolean tryPath(String path) {
        try {
            GeneralCommandLine cmd = new GeneralCommandLine(path, "--version");
            cmd.setCharset(StandardCharsets.UTF_8);
            applyLoginShellPath(cmd);

            OSProcessHandler handler = new OSProcessHandler(cmd);
            StringBuilder stdout = new StringBuilder();

            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@org.jetbrains.annotations.NotNull ProcessEvent event, @org.jetbrains.annotations.NotNull Key outputType) {
                    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
                        stdout.append(event.getText());
                    }
                }
            });

            handler.startNotify();
            if (!handler.waitFor(TIMEOUT_MS)) {
                handler.destroyProcess();
                return false;
            }

            Integer exitCode = handler.getExitCode();
            if (exitCode != null && exitCode == 0) {
                available = true;
                detectedPath = path;
                String output = stdout.toString().trim();
                detectedVersion = output.replaceAll("(?i)openspec\\s*v?", "").trim();
                if (detectedVersion.isEmpty()) {
                    detectedVersion = output;
                }
                LOG.info("OpenSpec CLI detected at: " + path + " version: " + detectedVersion);
                return true;
            }
        } catch (Exception e) {
            LOG.debug("CLI not available at: " + path, e);
        }
        return false;
    }

    /**
     * Tries to find openspec via the user's login shell, which has the full PATH.
     * This handles macOS where GUI apps don't inherit terminal PATH.
     */
    private String tryLoginShellWhich() {
        String shell = System.getenv("SHELL");
        if (shell == null || shell.isEmpty()) {
            shell = "/bin/zsh"; // default on macOS
        }
        try {
            GeneralCommandLine cmd = new GeneralCommandLine(shell, "-lc", "which openspec");
            cmd.setCharset(StandardCharsets.UTF_8);

            OSProcessHandler handler = new OSProcessHandler(cmd);
            StringBuilder stdout = new StringBuilder();

            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@org.jetbrains.annotations.NotNull ProcessEvent event, @org.jetbrains.annotations.NotNull Key outputType) {
                    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
                        stdout.append(event.getText());
                    }
                }
            });

            handler.startNotify();
            if (!handler.waitFor(TIMEOUT_MS)) {
                handler.destroyProcess();
                return null;
            }

            Integer exitCode = handler.getExitCode();
            if (exitCode != null && exitCode == 0) {
                String path = stdout.toString().trim();
                if (!path.isEmpty()) {
                    LOG.info("Found openspec via login shell: " + path);
                    return path;
                }
            }
        } catch (Exception e) {
            LOG.debug("Login shell which failed", e);
        }
        return null;
    }

    /**
     * Resolves the PATH from the user's login shell and caches it.
     * On macOS, GUI apps don't inherit the terminal PATH, so tools like
     * node, openspec, etc. can't be found without this.
     */
    private void resolveLoginShellPath() {
        if (loginShellPath != null) return;

        String shell = System.getenv("SHELL");
        if (shell == null || shell.isEmpty()) {
            shell = "/bin/zsh";
        }
        try {
            GeneralCommandLine cmd = new GeneralCommandLine(shell, "-lc", "echo $PATH");
            cmd.setCharset(StandardCharsets.UTF_8);

            OSProcessHandler handler = new OSProcessHandler(cmd);
            StringBuilder stdout = new StringBuilder();

            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@org.jetbrains.annotations.NotNull ProcessEvent event, @org.jetbrains.annotations.NotNull Key outputType) {
                    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
                        stdout.append(event.getText());
                    }
                }
            });

            handler.startNotify();
            if (!handler.waitFor(TIMEOUT_MS)) {
                handler.destroyProcess();
                return;
            }

            Integer exitCode = handler.getExitCode();
            if (exitCode != null && exitCode == 0) {
                String path = stdout.toString().trim();
                if (!path.isEmpty()) {
                    loginShellPath = path;
                    LOG.info("Resolved login shell PATH: " + path);
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to resolve login shell PATH", e);
        }
    }

    /**
     * Applies the login shell PATH to a GeneralCommandLine so that
     * scripts with #!/usr/bin/env shebangs can resolve their interpreters.
     */
    public void applyLoginShellPath(GeneralCommandLine cmd) {
        if (loginShellPath != null) {
            Map<String, String> env = cmd.getEnvironment();
            env.put("PATH", loginShellPath);
        }
    }

    /**
     * Returns the cached login shell PATH, or null if not yet resolved.
     * Used by CliRunner to set up command environments.
     */
    public String getLoginShellPath() {
        return loginShellPath;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDetectedPath() {
        return detectedPath;
    }

    public String getDetectedVersion() {
        return detectedVersion;
    }
}
