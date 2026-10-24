package com.johnnyblabs.openspec.services;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.johnnyblabs.openspec.util.CliRunner;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.messages.Topic;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service(Service.Level.PROJECT)
public final class CliDetectionService {
    private static final Logger LOG = Logger.getInstance(CliDetectionService.class);
    private static final int TIMEOUT_MS = 10_000;
    private static final Duration DETECTION_STALENESS = Duration.ofSeconds(30);

    /**
     * Fixed install locations for openspec on macOS/Linux that need no environment: Homebrew, then the system paths.
     * {@link #unixCommonPaths} appends the standalone-binary installer's locations after these.
     */
    private static final List<String> UNIX_FIXED_PATHS = List.of(
            "/opt/homebrew/bin/openspec",
            "/usr/local/bin/openspec",
            "/usr/bin/openspec"
    );

    /** The standalone-binary installers' overrides (upstream {@code install.sh} / {@code install.ps1}). */
    private static final String INSTALL_DIR_ENV = "OPENSPEC_INSTALL_DIR";
    private static final String INSTALL_NAME_ENV = "OPENSPEC_INSTALL_NAME";

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

    /**
     * Announced on the project message bus after every completed detection (CLI found or not), from whichever thread
     * ran it. Subscribers that touch UI must hop to the EDT themselves.
     */
    public static final Topic<Listener> TOPIC = Topic.create("OpenSpec CLI detection", Listener.class);

    public interface Listener {
        void detectionCompleted(boolean available, @Nullable String version);
    }

    /** One detection result. Published as a whole, so readers never see a mix of two results or a cleared state. */
    private record Detection(boolean available, @Nullable String path, @Nullable String version) {
        static final Detection NONE = new Detection(false, null, null);
    }

    private final @Nullable Project project;
    private volatile Detection current = Detection.NONE;
    private volatile boolean detectedOnce;
    private volatile String loginShellPath;
    private volatile Instant lastDetectionTime;
    private volatile @Nullable Boolean isWindowsOverride;
    private volatile ProcessRunner processRunner = CliDetectionService::defaultRunAndCapture;
    private volatile Function<String, String> envLookup = System::getenv;
    private volatile Supplier<String> userHome = () -> System.getProperty("user.home");
    private volatile @Nullable Predicate<String> executableCheckOverride;

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

    @TestOnly
    public void setEnvForTest(Function<String, String> lookup) {
        this.envLookup = lookup;
    }

    @TestOnly
    public void setUserHomeForTest(@Nullable String home) {
        this.userHome = () -> home;
    }

    /** Replaces the file check that gates fixed install locations, so tests can drive {@code detect()} with fake paths. */
    @TestOnly
    public void setExecutableCheckForTest(@Nullable Predicate<String> check) {
        this.executableCheckOverride = check;
    }

    /** Sets when detection last ran, so tests can exercise the 30s staleness throttle without sleeping. */
    @TestOnly
    public void setLastDetectionTimeForTest(@Nullable Instant time) {
        this.lastDetectionTime = time;
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

    /**
     * Probes for the CLI and publishes the result when done. The previous result stays visible to readers until then:
     * a detection in progress never reads as "unavailable".
     */
    public void detect() {
        Detection found = null;
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
                    found = probe(settingsPath);
                }
            }

            // 2. Try bare "openspec" via GeneralCommandLine (uses IntelliJ's env resolution)
            if (found == null) {
                found = probe("openspec");
            }

            // 3. Try via user's login shell (Unix) or where.exe (Windows)
            if (found == null) {
                String shellPath = tryLoginShellWhich();
                if (shellPath != null) {
                    found = probe(shellPath);
                }
            }

            // 4. Try common install locations for the host OS, skipping any with no file to run
            if (found == null) {
                Predicate<String> exists = executableCheck();
                for (String path : commonPathsForCurrentOs()) {
                    if (!exists.test(path)) continue;
                    found = probe(path);
                    if (found != null) break;
                }
            }
        } finally {
            publish(found != null ? found : Detection.NONE);
        }
    }

    // Public so cross-package integration tests (e.g. the Windows `.cmd` shim invocation test in the
    // coordination package) can exercise the real suffix-fallback + process-spawn end-to-end. Publishes on success.
    public boolean tryPath(String path) {
        Detection found = probe(path);
        if (found == null) return false;
        publish(found);
        return true;
    }

    /** The detection at {@code path} (with Windows suffix fallback), or {@code null}; changes no state. */
    private @Nullable Detection probe(String path) {
        Detection found = probeDirect(path);
        if (found == null && isWindows() && !hasRecognizedWindowsSuffix(path)) {
            for (String suffix : WINDOWS_EXE_SUFFIXES) {
                found = probeDirect(path + suffix);
                if (found != null) break;
            }
        }
        return found;
    }

    private @Nullable Detection probeDirect(String path) {
        try {
            GeneralCommandLine cmd = new GeneralCommandLine(path, "--version");
            cmd.setCharset(StandardCharsets.UTF_8);
            CliRunner.applyPlainOutputEnvironment(cmd);
            applyLoginShellPath(cmd);

            String output = processRunner.runAndCapture(cmd);
            if (output != null) {
                String version = output.replaceAll("(?i)openspec\\s*v?", "").trim();
                if (version.isEmpty()) {
                    version = output;
                }
                LOG.info("OpenSpec CLI detected at: " + path + " version: " + version);
                return new Detection(true, path, version);
            }
        } catch (Exception e) {
            LOG.debug("CLI not available at: " + path, e);
        }
        return null;
    }

    /** Makes {@code result} the current detection in one step, then announces it. */
    private void publish(Detection result) {
        current = result;
        lastDetectionTime = Instant.now();
        detectedOnce = true;
        if (project != null && !project.isDisposed()) {
            project.getMessageBus().syncPublisher(TOPIC).detectionCompleted(result.available(), result.version());
        }
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
        return isWindows() ? windowsCommonPaths(envLookup) : unixCommonPaths(envLookup, userHome.get());
    }

    /**
     * The fixed paths, then the standalone installer's override location, then its default {@code ~/.local/bin}.
     * The installer leaves PATH alone, so a GUI-launched IDE only finds the binary here.
     */
    static List<String> unixCommonPaths(Function<String, String> envLookup, @Nullable String home) {
        List<String> paths = new ArrayList<>(UNIX_FIXED_PATHS);
        String override = installOverride(envLookup, "/", "openspec");
        if (override != null) {
            paths.add(override);
        }
        if (home != null && !home.isBlank()) {
            paths.add(home + "/.local/bin/openspec");
        }
        return paths;
    }

    static List<String> windowsCommonPaths(Function<String, String> envLookup) {
        List<String> paths = new ArrayList<>(5);
        String appData = envLookup.apply("APPDATA");
        if (appData != null && !appData.isEmpty()) {
            paths.add(appData + "\\npm\\openspec.cmd");
        }
        String localAppData = envLookup.apply("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isEmpty()) {
            paths.add(localAppData + "\\npm\\openspec.cmd");
            paths.add(localAppData + "\\Microsoft\\WinGet\\Links\\openspec.cmd");
        }
        // The standalone installer adds its directory to the user PATH, which an already-running IDE doesn't see.
        String override = installOverride(envLookup, "\\", "openspec.exe");
        if (override != null) {
            paths.add(override);
        }
        if (localAppData != null && !localAppData.isEmpty()) {
            paths.add(localAppData + "\\Programs\\openspec\\openspec.exe");
        }
        return paths;
    }

    /** {@code OPENSPEC_INSTALL_DIR} joined with {@code OPENSPEC_INSTALL_NAME} (or {@code defaultName}), or null when the dir is unset. */
    private static @Nullable String installOverride(Function<String, String> envLookup, String separator, String defaultName) {
        String dir = envLookup.apply(INSTALL_DIR_ENV);
        if (dir == null || dir.isEmpty()) return null;
        String name = envLookup.apply(INSTALL_NAME_ENV);
        return dir + separator + (name != null && !name.isEmpty() ? name : defaultName);
    }

    Predicate<String> executableCheck() {
        Predicate<String> override = executableCheckOverride;
        if (override != null) return override;
        return isWindows() ? CliDetectionService::isRegularFile : CliDetectionService::isExecutableFile;
    }

    /** Whether {@code path} is a regular, executable file; an invalid path counts as missing. */
    static boolean isExecutableFile(String path) {
        try {
            Path p = Path.of(path);
            return Files.isRegularFile(p) && Files.isExecutable(p);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** Whether {@code path} is a regular file; on Windows the execute bit means nothing. */
    static boolean isRegularFile(String path) {
        try {
            return Files.isRegularFile(Path.of(path));
        } catch (InvalidPathException e) {
            return false;
        }
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
        return current.available();
    }

    public String getDetectedPath() {
        return current.path();
    }

    public String getDetectedVersion() {
        return current.version();
    }

    /** Whether any detection has completed yet; until then the CLI's availability is simply unknown. */
    public boolean isDetectedOnce() {
        return detectedOnce;
    }
}
