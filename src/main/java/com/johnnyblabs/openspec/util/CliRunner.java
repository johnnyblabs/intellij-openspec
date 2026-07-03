package com.johnnyblabs.openspec.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        String exePath = resolveCliPath(project);

        GeneralCommandLine cmd = new GeneralCommandLine();
        cmd.setExePath(exePath);
        cmd.addParameters(args);
        cmd.setCharset(StandardCharsets.UTF_8);

        // Opt this process out of the CLI's telemetry notice. On its first run in a fresh
        // environment the CLI prepends a one-time "Note: OpenSpec collects anonymous usage stats"
        // line to stdout, which corrupts --json parsing (see CliJson). Setting this keeps stdout
        // clean for every invocation.
        cmd.getEnvironment().put("OPENSPEC_TELEMETRY", "0");

        if (project.getBasePath() != null) {
            cmd.setWorkDirectory(project.getBasePath());
        }

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

            return new CliResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join());
        } catch (CliException e) {
            throw e;
        } catch (Exception e) {
            throw new CliException("Failed to run OpenSpec CLI: " + e.getMessage(), e);
        }
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
