package com.johnnyblabs.openspec.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class CliRunner {

    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private CliRunner() {
    }

    public static CliResult run(Project project, String... args) throws CliException {
        int timeoutMs = OpenSpecSettings.getInstance(project).getCliTimeoutSeconds() * 1000;
        return run(project, timeoutMs, args);
    }

    public static CliResult run(Project project, int timeoutMs, String... args) throws CliException {
        return run(project, timeoutMs, Map.of(), args);
    }

    /** Runs the CLI with {@code extraEnv} set on top of the plain-output environment. */
    public static CliResult run(Project project, Map<String, String> extraEnv, String... args) throws CliException {
        int timeoutMs = OpenSpecSettings.getInstance(project).getCliTimeoutSeconds() * 1000;
        return run(project, timeoutMs, extraEnv, args);
    }

    private static CliResult run(Project project, int timeoutMs, Map<String, String> extraEnv, String... args)
            throws CliException {
        GeneralCommandLine cmd = buildCommandLine(resolveCliPath(project), project.getBasePath(), args);
        cmd.getEnvironment().putAll(extraEnv);

        // Apply login shell PATH so node-based CLI scripts can resolve their interpreter
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null) {
            detection.applyLoginShellPath(cmd);
        }

        try {
            Process process = cmd.createProcess();

            // Drain both streams concurrently to avoid deadlock on full buffers
            CompletableFuture<String> stdoutFuture = drainStreamAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = drainStreamAsync(process.getErrorStream());

            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new CliException("OpenSpec CLI command timed out after " + (timeoutMs / 1000) + "s");
            }

            return capturedResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join());
        } catch (CliException e) {
            throw e;
        } catch (Exception e) {
            throw new CliException("Failed to run OpenSpec CLI: " + e.getMessage(), e);
        }
    }

    /**
     * The result of a finished run. Colour codes are removed here once, for every caller: the run asks for none, but
     * a wrapper can force them, and a raw escape byte never belongs to JSON (which encodes it as {@code \u001b}).
     */
    static CliResult capturedResult(int exitCode, String stdout, String stderr) {
        return new CliResult(exitCode, CliText.stripAnsi(stdout), CliText.stripAnsi(stderr));
    }

    /** The command line for one CLI run: the executable, its arguments, the plain-output environment and the project dir. */
    static GeneralCommandLine buildCommandLine(String exePath, @Nullable String workDir, String... args) {
        GeneralCommandLine cmd = new GeneralCommandLine();
        cmd.setExePath(exePath);
        cmd.addParameters(args);
        cmd.setCharset(StandardCharsets.UTF_8);
        applyPlainOutputEnvironment(cmd);
        if (workDir != null) {
            cmd.setWorkDirectory(workDir);
        }
        return cmd;
    }

    /**
     * Asks the CLI for output the plugin can read and show: no telemetry notice (CLI 1.3–1.9 printed it on stdout,
     * before the JSON), no update notice (`update` prints it on stdout), and no colour codes in text output.
     */
    public static void applyPlainOutputEnvironment(GeneralCommandLine cmd) {
        Map<String, String> env = cmd.getEnvironment();
        env.put("OPENSPEC_TELEMETRY", "0");
        env.put("OPENSPEC_NO_UPDATE_CHECK", "1");
        env.put("NO_COLOR", "1");
        env.put("FORCE_COLOR", "0");
    }

    private static CompletableFuture<String> drainStreamAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        });
    }

    private static String resolveCliPath(Project project) {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null && detection.isAvailable() && detection.getDetectedPath() != null) {
            return detection.getDetectedPath();
        }
        return "openspec";
    }

    public record CliResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static class CliException extends Exception {
        public CliException(String message) {
            super(message);
        }

        public CliException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
