package com.johnnyblabs.openspec.services;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.PROJECT)
public final class CliDetectionService {
    private static final Logger LOG = Logger.getInstance(CliDetectionService.class);
    private static final int TIMEOUT_MS = 10_000;
    private static final Duration DETECTION_STALENESS = Duration.ofSeconds(30);

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
    private volatile Instant lastDetectionTime;

    public CliDetectionService(Project project) {
        this.project = project;
    }

    /**
     * Re-runs detection only if the last detection was more than 30 seconds ago.
     * Returns true if detection was actually performed.
     */
    public boolean detectIfStale() {
        Instant last = lastDetectionTime;
        if (last != null && Duration.between(last, Instant.now()).compareTo(DETECTION_STALENESS) < 0) {
            return false;
        }
        detect();
        return true;
    }

    public void detect() {
        available = false;
        detectedPath = null;
        detectedVersion = null;

        try {
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
        } finally {
            lastDetectionTime = Instant.now();
        }
    }

    private boolean tryPath(String path) {
        try {
            GeneralCommandLine cmd = new GeneralCommandLine(path, "--version");
            cmd.setCharset(StandardCharsets.UTF_8);
            applyLoginShellPath(cmd);

            String output = runAndCapture(cmd);
            if (output != null) {
                available = true;
                detectedPath = path;
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

            String path = runAndCapture(cmd);
            if (path != null) {
                LOG.info("Found openspec via login shell: " + path);
                return path;
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

            String path = runAndCapture(cmd);
            if (path != null) {
                loginShellPath = path;
                LOG.info("Resolved login shell PATH: " + path);
            }
        } catch (Exception e) {
            LOG.debug("Failed to resolve login shell PATH", e);
        }
    }

    /**
     * Runs a command and returns trimmed stdout if exit code is 0, or null otherwise.
     * Uses Process directly instead of OSProcessHandler to avoid ReadAction threading checks.
     * Drains stdout asynchronously so the timeout on waitFor is effective.
     */
    private static String runAndCapture(GeneralCommandLine cmd) throws Exception {
        Process process = cmd.createProcess();

        // Drain both streams async — readAllBytes blocks until EOF, so we must not call it
        // before waitFor or the timeout becomes dead code when the process hangs.
        // Stderr must also be drained to prevent the process blocking on a full pipe buffer.
        CompletableFuture<String> stdoutFuture = drainAsync(process.getInputStream());
        drainAsync(process.getErrorStream()); // discard stderr but prevent pipe stall

        if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return null;
        }

        if (process.exitValue() == 0) {
            String output = stdoutFuture.join().trim();
            return output.isEmpty() ? null : output;
        }
        return null;
    }

    private static CompletableFuture<String> drainAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        });
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
