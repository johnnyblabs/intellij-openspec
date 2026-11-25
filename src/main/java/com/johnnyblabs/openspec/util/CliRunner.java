package com.johnnyblabs.openspec.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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

    /** Runs {@code args} against the installed CLI, with the project's timeout. */
    public static CliResult run(Project project, CliArgs args) throws CliException {
        int timeoutMs = OpenSpecSettings.getInstance(project).getCliTimeoutSeconds() * 1000;
        return run(project, timeoutMs, args);
    }

    /**
     * Runs {@code args} against the installed CLI. The invocation is first fitted to the detected release
     * ({@link CliArgs#resolve}); one that release can't run is refused with {@link UnsupportedCliArgumentException}
     * before any process starts.
     */
    public static CliResult run(Project project, int timeoutMs, CliArgs args) throws CliException {
        GeneralCommandLine cmd = prepare(project.getService(CliDetectionService.class), project.getBasePath(), args);

        try {
            try {
                return runProcess(cmd, timeoutMs);
            } finally {
                afterFinishedRun(project, args);
            }
        } catch (CliException e) {
            throw e;
        } catch (Exception e) {
            throw new CliException("Failed to run OpenSpec CLI: " + e.getMessage(), e);
        }
    }

    /**
     * Starts {@code cmd}, closes its standard input at once (so a prompt reads end-of-input instead of waiting), drains
     * both output streams and waits up to {@code timeoutMs}. Package-private for tests.
     */
    static CliResult runProcess(GeneralCommandLine cmd, int timeoutMs) throws Exception {
        Process process = cmd.createProcess();
        try {
            process.getOutputStream().close();
        } catch (java.io.IOException ignored) {
            // The process may already have exited; nothing waits on its input then.
        }

        // Drain both streams concurrently to avoid deadlock on full buffers
        CompletableFuture<String> stdoutFuture = drainStreamAsync(process.getInputStream());
        CompletableFuture<String> stderrFuture = drainStreamAsync(process.getErrorStream());

        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw CliException.timeout("OpenSpec CLI command timed out after " + (timeoutMs / 1000) + "s");
        }
        return capturedResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join());
    }

    /**
     * What follows a finished run that may have written skills: the machine-wide skills cache is cleared and the
     * skills folders refreshed ({@code SkillsWritten}). A failure here never turns a finished run into a failed one.
     */
    private static void afterFinishedRun(Project project, CliArgs args) {
        try {
            com.johnnyblabs.openspec.services.SkillsWritten.afterRun(project, args);
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(CliRunner.class)
                    .warn("Refreshing skills after " + args.command() + " failed", e);
        }
    }

    /**
     * The command line for {@code args} on the installed CLI: fitted to its release, with the extra environment from
     * {@code args} on top of the plain-output one, and the login-shell PATH so a node-based CLI finds its interpreter.
     * A refused invocation throws here, so no process is ever started for it.
     */
    static GeneralCommandLine prepare(@Nullable CliDetectionService detection, @Nullable String workDir, CliArgs args)
            throws UnsupportedCliArgumentException {
        CliArgs.Resolved resolved = resolveFor(detection, args);
        GeneralCommandLine cmd = buildCommandLine(resolveCliPath(detection), workDir,
                resolved.argv().toArray(String[]::new));
        cmd.getEnvironment().putAll(args.env());
        if (detection != null) {
            detection.applyLoginShellPath(cmd);
        }
        return cmd;
    }

    /**
     * {@code args} fitted to the installed release. Until the first detection has finished the version is unknown,
     * and an early run (a tool-window refresh at startup) would be fitted to the target release instead of, say, an
     * installed 1.4; so a first run off the EDT joins the project's first detection. On the EDT it never waits, since
     * detection can take seconds; it resolves as the target release, as before detection existed.
     */
    static CliArgs.Resolved resolveFor(@Nullable CliDetectionService detection, CliArgs args)
            throws UnsupportedCliArgumentException {
        Application app = ApplicationManager.getApplication();
        return resolveFor(detection, args, app != null && app.isDispatchThread());
    }

    static CliArgs.Resolved resolveFor(@Nullable CliDetectionService detection, CliArgs args, boolean onEdt)
            throws UnsupportedCliArgumentException {
        if (detection != null && !onEdt) {
            detection.ensureDetected();
        }
        String version = detection != null && detection.isAvailable() ? detection.getDetectedVersion() : null;
        return args.resolve(version);
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
     * before the JSON), no update notice (`update` prints it on stdout), and no colour codes in text output. It also
     * turns prompts off ({@code OPEN_SPEC_INTERACTIVE=0}): no background run can wait for an answer, whatever its stdin.
     */
    public static void applyPlainOutputEnvironment(GeneralCommandLine cmd) {
        Map<String, String> env = cmd.getEnvironment();
        env.put(CliCommands.NON_INTERACTIVE, "0");
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

    private static String resolveCliPath(@Nullable CliDetectionService detection) {
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
        private final boolean timedOut;

        public CliException(String message) {
            this(message, null, false);
        }

        public CliException(String message, Throwable cause) {
            this(message, cause, false);
        }

        private CliException(String message, Throwable cause, boolean timedOut) {
            super(message, cause);
            this.timedOut = timedOut;
        }

        /** The CLI started but didn't finish within the timeout, as opposed to not running at all. */
        public static CliException timeout(String message) {
            return new CliException(message, null, true);
        }

        /** Whether the CLI started but was stopped for taking too long. */
        public boolean timedOut() {
            return timedOut;
        }
    }
}
