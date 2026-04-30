package com.johnnyblabs.openspec.services;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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

    /**
     * Windows executable extensions, in fallback order. Java's ProcessBuilder
     * does not consult PATHEXT, so a bare name like "openspec" will not resolve
     * to "openspec.cmd"; we must try the suffixes explicitly.
     */
    private static final List<String> WINDOWS_EXE_SUFFIXES = List.of(".cmd", ".bat", ".exe");

    @FunctionalInterface
    interface ProcessRunner {
        @Nullable String runAndCapture(GeneralCommandLine cmd) throws Exception;
    }

    private final @Nullable Project project;
    private volatile boolean available;
    private volatile String detectedPath;
    private volatile String detectedVersion;
    private volatile String loginShellPath;
    private volatile Instant lastDetectionTime;
    private volatile @Nullable Boolean isWindowsOverride;
    private volatile ProcessRunner processRunner = CliDetectionService::defaultRunAndCapture;

    public CliDetectionService(@Nullable Project project) {
        this.project = project;
    }

    @TestOnly
    public void setIsWindowsForTest(@Nullable Boolean override) {
        this.isWindowsOverride = override;
    }

    @TestOnly
    public void setProcessRunnerForTest(ProcessRunner runner) {
        this.processRunner = runner;
    }

    private boolean isWindows() {
        Boolean override = isWindowsOverride;
        return override != null ? override : SystemInfo.isWindows;
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
            // don't inherit terminal PATH (so node/openspec can't be found).
            // No-op on Windows.
            resolveLoginShellPath();

            // 1. Check settings path first
            if (project != null) {
                OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
                String settingsPath = settings.getCliPath();
                if (settingsPath != null && !settingsPath.isEmpty()) {
                    if (tryPath(settingsPath)) return;
                }
            }

            // 2. Try bare "openspec" via GeneralCommandLine (uses IntelliJ's env resolution)
            if (tryPath("openspec")) return;

            // 3. Try via user's login shell (Unix) or where.exe (Windows)
            String shellPath = tryLoginShellWhich();
            if (shellPath != null && tryPath(shellPath)) return;

            // 4. Try common install locations for the host OS
            for (String path : commonPathsForCurrentOs()) {
                if (tryPath(path)) return;
            }
        } finally {
            lastDetectionTime = Instant.now();
        }
    }

    boolean tryPath(String path) {
        if (tryPathDirect(path)) return true;
        if (isWindows() && !hasRecognizedWindowsSuffix(path)) {
            for (String suffix : WINDOWS_EXE_SUFFIXES) {
                if (tryPathDirect(path + suffix)) return true;
            }
        }
        return false;
    }

    private boolean tryPathDirect(String path) {
        try {
            GeneralCommandLine cmd = new GeneralCommandLine(path, "--version");
            cmd.setCharset(StandardCharsets.UTF_8);
            applyLoginShellPath(cmd);

            String output = processRunner.runAndCapture(cmd);
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

    private static boolean hasRecognizedWindowsSuffix(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String suffix : WINDOWS_EXE_SUFFIXES) {
            if (lower.endsWith(suffix)) return true;
        }
        return false;
    }

    /**
     * Tries to find openspec via the user's login shell on Unix
     * (handles macOS where GUI apps don't inherit terminal PATH),
     * or via where.exe on Windows. Returns the resolved path, or null.
     */
    String tryLoginShellWhich() {
        try {
            GeneralCommandLine cmd;
            if (isWindows()) {
                cmd = new GeneralCommandLine("where.exe", "openspec");
            } else {
                String shell = System.getenv("SHELL");
                if (shell == null || shell.isEmpty()) {
                    shell = "/bin/zsh";
                }
                cmd = new GeneralCommandLine(shell, "-lc", "which openspec");
            }
            cmd.setCharset(StandardCharsets.UTF_8);

            String output = processRunner.runAndCapture(cmd);
            if (output != null) {
                // where.exe may return multiple lines; take first non-empty.
                String firstLine = output.split("\\R", 2)[0].trim();
                if (!firstLine.isEmpty()) {
                    LOG.info("Found openspec via " + (isWindows() ? "where.exe" : "login shell") + ": " + firstLine);
                    return firstLine;
                }
            }
        } catch (Exception e) {
            LOG.debug("which/where lookup failed", e);
        }
        return null;
    }

    /**
     * Resolves the PATH from the user's login shell and caches it.
     * On macOS, GUI apps don't inherit the terminal PATH, so tools like
     * node, openspec, etc. can't be found without this. No-op on Windows
     * where GUI apps inherit PATH normally and shebang interpreter
     * resolution doesn't apply to .cmd shims.
     */
    private void resolveLoginShellPath() {
        if (loginShellPath != null) return;
        if (isWindows()) return;

        String shell = System.getenv("SHELL");
        if (shell == null || shell.isEmpty()) {
            shell = "/bin/zsh";
        }
        try {
            GeneralCommandLine cmd = new GeneralCommandLine(shell, "-lc", "echo $PATH");
            cmd.setCharset(StandardCharsets.UTF_8);

            String path = processRunner.runAndCapture(cmd);
            if (path != null) {
                loginShellPath = path;
                LOG.info("Resolved login shell PATH: " + path);
            }
        } catch (Exception e) {
            LOG.debug("Failed to resolve login shell PATH", e);
        }
    }

    private List<String> commonPathsForCurrentOs() {
        return isWindows() ? windowsCommonPaths() : COMMON_PATHS;
    }

    static List<String> windowsCommonPaths() {
        return windowsCommonPaths(System::getenv);
    }

    static List<String> windowsCommonPaths(Function<String, String> envLookup) {
        List<String> paths = new ArrayList<>(3);
        String appData = envLookup.apply("APPDATA");
        if (appData != null && !appData.isEmpty()) {
            paths.add(appData + "\\npm\\openspec.cmd");
        }
        String localAppData = envLookup.apply("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isEmpty()) {
            paths.add(localAppData + "\\npm\\openspec.cmd");
            paths.add(localAppData + "\\Microsoft\\WinGet\\Links\\openspec.cmd");
        }
        return paths;
    }

    /**
     * Runs a command and returns trimmed stdout if exit code is 0, or null otherwise.
     * Uses Process directly instead of OSProcessHandler to avoid ReadAction threading checks.
     * Drains stdout asynchronously so the timeout on waitFor is effective.
     */
    private static String defaultRunAndCapture(GeneralCommandLine cmd) throws Exception {
        Process process = cmd.createProcess();

        // Drain both streams async — readAllBytes blocks until EOF, so we must not call it
        // before waitFor or the timeout becomes dead code when the process hangs.
        // Stderr must also be drained to prevent the process blocking on a full pipe buffer.
        CompletableFuture<String> stdoutFuture = drainAsync(process.getInputStream());
        drainAsync(process.getErrorStream());

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
     * No-op on Windows (loginShellPath stays null there).
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
