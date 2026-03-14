package com.johnnyblabs.openspec.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.nio.charset.StandardCharsets;

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

        if (project.getBasePath() != null) {
            cmd.setWorkDirectory(project.getBasePath());
        }

        // Apply login shell PATH so node-based CLI scripts can resolve their interpreter
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null) {
            detection.applyLoginShellPath(cmd);
        }

        try {
            OSProcessHandler handler = new OSProcessHandler(cmd);
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@org.jetbrains.annotations.NotNull ProcessEvent event, @org.jetbrains.annotations.NotNull Key outputType) {
                    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
                        stdout.append(event.getText());
                    } else if (ProcessOutputTypes.STDERR.equals(outputType)) {
                        stderr.append(event.getText());
                    }
                }
            });

            handler.startNotify();
            if (!handler.waitFor(timeoutMs)) {
                handler.destroyProcess();
                throw new CliException("OpenSpec CLI command timed out after " + (timeoutMs / 1000) + "s");
            }

            int exitCode = handler.getExitCode() != null ? handler.getExitCode() : -1;
            return new CliResult(exitCode, stdout.toString(), stderr.toString());
        } catch (CliException e) {
            throw e;
        } catch (Exception e) {
            throw new CliException("Failed to run OpenSpec CLI: " + e.getMessage(), e);
        }
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
