package com.johnnyblabs.openspec.services;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.johnnyblabs.openspec.util.CliRunner;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.messages.Topic;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service(Service.Level.PROJECT)
public final class CliDetectionService {
    private static final Logger LOG = Logger.getInstance(CliDetectionService.class);
    private static final int TIMEOUT_MS = 10_000;
    private static final Duration DETECTION_STALENESS = Duration.ofSeconds(30);
    /** How long a resolved login-shell PATH is reused before a detection starts the shell again. */
    static final Duration SHELL_PATH_TTL = Duration.ofMinutes(10);

    /**
     * Fixed install locations for openspec on macOS/Linux that need no environment: Homebrew, the system paths, then
     * Homebrew on Linux (whose formula installs to {@code $(brew --prefix)/bin}).
     * {@link #unixCommonPaths} appends the standalone-binary installer's locations after these.
     */
    private static final List<String> UNIX_FIXED_PATHS = List.of(
            "/opt/homebrew/bin/openspec",
            "/usr/local/bin/openspec",
            "/usr/bin/openspec",
            "/home/linuxbrew/.linuxbrew/bin/openspec"
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
    /** When the login-shell PATH was last resolved (or last tried); null until the first try. */
    private volatile @Nullable Instant loginShellPathResolvedAt;
    private volatile Supplier<Instant> clock = Instant::now;
    private volatile Instant lastDetectionTime;
    private volatile @Nullable Boolean isWindowsOverride;
    private volatile ProcessRunner processRunner = CliDetectionService::defaultRunAndCapture;
    private volatile Function<String, String> envLookup = System::getenv;
    private volatile Supplier<String> userHome = () -> System.getProperty("user.home");
    private volatile @Nullable Predicate<String> executableCheckOverride;
    /** Finds a command on the IDE's own PATH (the environment it loaded from the user's shell); a seam for tests. */
    private volatile Function<String, File> idePathLookup = PathEnvironmentVariableUtil::findInPath;
    /** The PATH a child process inherits from the IDE when its command line sets none; a seam for tests. */
    private volatile Supplier<String> idePath = () -> EnvironmentUtil.getValue("PATH");

    public CliDetectionService(@Nullable Project project) {
        this.project = project;
    }

    @TestOnly
    public void setIsWindowsForTest(@Nullable Boolean override) {
        this.isWindowsOverride = override;
    }

    @TestOnly
    public void setClockForTest(Supplier<Instant> testClock) {
        this.clock = testClock;
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

    @TestOnly
    public void setIdePathForTest(Function<String, File> lookup, Supplier<String> path) {
        this.idePathLookup = lookup;
        this.idePath = path;
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
     * a detection in progress never reads as "unavailable". Reuses a login-shell PATH resolved in the last
     * {@link #SHELL_PATH_TTL}.
     */
    public void detect() {
        detect(false);
    }

    /**
     * Probes for the CLI. With {@code freshShellPath}, the login-shell PATH is resolved again whatever was resolved
     * before: the user asked to find the CLI, perhaps after changing their shell setup.
     */
    public void detect(boolean freshShellPath) {
        Detection found = null;
        try {
            // Read the PATH the user's interactive login shell sets (Unix; no-op on Windows). A GUI-launched IDE
            // may not have it, and every CLI run gets the entries the IDE's PATH lacks. Starting an interactive shell
            // can take seconds, so a recent result is reused.
            Instant resolvedAt = loginShellPathResolvedAt;
            if (freshShellPath || resolvedAt == null
                    || Duration.between(resolvedAt, clock.get()).compareTo(SHELL_PATH_TTL) >= 0) {
                resolveLoginShellPath();
                loginShellPathResolvedAt = clock.get();
            }

            // 1. Check settings path first
            if (project != null) {
                OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
                String settingsPath = settings.getCliPath();
                if (settingsPath != null && !settingsPath.isEmpty()) {
                    found = probe(settingsPath);
                }
            }

            // 2. Unix: the IDE's own PATH. A bare name below is looked up on the JVM's PATH, which for a
            // Dock-launched IDE is launchd's minimal one, so ask the platform for the absolute path first.
            if (found == null && !isWindows()) {
                File onIdePath = lookUpOnIdePath();
                if (onIdePath != null) {
                    found = probe(onIdePath.getAbsolutePath());
                }
            }

            // 3. Try bare "openspec" (works when the JVM's own PATH has it, e.g. a terminal launch or Windows)
            if (found == null) {
                found = probe("openspec");
            }

            // 4. The interactive login shell's PATH (Unix) or where.exe (Windows)
            if (found == null) {
                String shellPath = tryLoginShellWhich();
                if (shellPath != null) {
                    found = probe(shellPath);
                }
            }

            // 5. Try common install locations for the host OS, skipping any with no file to run
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
     * Finds openspec through the user's shell: on Unix, in the PATH the interactive login shell sets (read once per
     * detection by {@link #resolveLoginShellPath}, no extra process); on Windows, via where.exe. Returns the resolved
     * path, or null.
     */
    String tryLoginShellWhich() {
        if (!isWindows()) {
            String found = findOnPath(loginShellPath, executableCheck());
            if (found != null) {
                LOG.info("Found openspec on the login shell PATH: " + found);
            }
            return found;
        }
        try {
            GeneralCommandLine cmd = new GeneralCommandLine("where.exe", "openspec");
            cmd.setCharset(StandardCharsets.UTF_8);

            String output = processRunner.runAndCapture(cmd);
            if (output != null) {
                // where.exe may return multiple lines; take first non-empty.
                String firstLine = output.split("\\R", 2)[0].trim();
                if (!firstLine.isEmpty()) {
                    LOG.info("Found openspec via where.exe: " + firstLine);
                    return firstLine;
                }
            }
        } catch (Exception e) {
            LOG.debug("where.exe lookup failed", e);
        }
        return null;
    }

    /**
     * Reads the PATH the user's interactive login shell sets, once per detection. A login shell that isn't interactive
     * skips {@code ~/.zshrc}, where version managers (nvm, fnm, volta, asdf) and the standalone installer's PATH advice
     * usually go, so the shell runs interactively, the way the IDE's own environment loading does. rc files may print
     * anything, so the PATH is read between markers. No-op on Windows, where GUI apps inherit PATH.
     */
    private void resolveLoginShellPath() {
        if (isWindows()) return;

        String shell = envLookup.apply("SHELL");
        if (shell == null || shell.isEmpty()) {
            shell = "/bin/zsh";
        }
        try {
            List<String> argv = new ArrayList<>();
            argv.add(shell);
            argv.addAll(shellFlags(shell));
            argv.add(PRINT_PATH);
            GeneralCommandLine cmd = new GeneralCommandLine(argv);
            cmd.setCharset(StandardCharsets.UTF_8);
            // Ask rc files and oh-my-zsh to skip prompts and updates, as the IDE's own environment reader does.
            cmd.getEnvironment().put("INTELLIJ_ENVIRONMENT_READER", "true");
            cmd.getEnvironment().put("DISABLE_AUTO_UPDATE", "true");
            cmd.withInput(new File("/dev/null"));

            String path = parseMarkedPath(processRunner.runAndCapture(cmd));
            if (path != null) {
                loginShellPath = path;
                LOG.info("Resolved login shell PATH: " + path);
            }
        } catch (Exception e) {
            LOG.debug("Failed to resolve login shell PATH", e);
        }
    }

    static final String PATH_BEGIN = "__OPENSPEC_PATH_BEGIN__";
    static final String PATH_END = "__OPENSPEC_PATH_END__";
    /** Prints PATH between markers, on a line of its own, so rc-file output before it can't run into it. */
    static final String PRINT_PATH = "printf '\\n%s%s%s\\n' '" + PATH_BEGIN + "' \"$PATH\" '" + PATH_END + "'";

    /**
     * The flags that make {@code shell} an interactive login shell, as the IDE's own environment loading uses them:
     * {@code -l -i -c}, without {@code -i} for fish and without {@code -l} for csh and tcsh.
     */
    static List<String> shellFlags(String shell) {
        String name = shell.substring(shell.lastIndexOf('/') + 1);
        return switch (name) {
            case "fish" -> List.of("-l", "-c");
            case "csh", "tcsh" -> List.of("-i", "-c");
            default -> List.of("-l", "-i", "-c");
        };
    }

    /** The PATH between the last begin marker and the end marker after it, or null when the markers aren't there. */
    static @Nullable String parseMarkedPath(@Nullable String output) {
        if (output == null) return null;
        int begin = output.lastIndexOf(PATH_BEGIN);
        if (begin < 0) return null;
        int start = begin + PATH_BEGIN.length();
        int end = output.indexOf(PATH_END, start);
        if (end < 0) return null;
        String path = output.substring(start, end).strip();
        return path.isEmpty() ? null : path;
    }

    /** The first {@code dir/openspec} on {@code path} that the check accepts, or null. */
    static @Nullable String findOnPath(@Nullable String path, Predicate<String> check) {
        if (path == null) return null;
        for (String dir : path.split(":")) {
            if (dir.isEmpty()) continue;
            String candidate = (dir.endsWith("/") ? dir : dir + "/") + "openspec";
            if (check.test(candidate)) return candidate;
        }
        return null;
    }

    /** {@code base} followed by every {@code extra} entry it doesn't already contain, in order. */
    static String mergePath(@Nullable String base, @Nullable String extra) {
        if (base == null || base.isEmpty()) return extra == null ? "" : extra;
        if (extra == null || extra.isEmpty()) return base;
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>(List.of(base.split(":")));
        StringBuilder merged = new StringBuilder(base);
        for (String dir : extra.split(":")) {
            if (!dir.isEmpty() && seen.add(dir)) {
                merged.append(':').append(dir);
            }
        }
        return merged.toString();
    }

    private @Nullable File lookUpOnIdePath() {
        try {
            return idePathLookup.apply("openspec");
        } catch (RuntimeException e) {
            LOG.debug("IDE PATH lookup failed", e);
            return null;
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
    static String defaultRunAndCapture(GeneralCommandLine cmd) throws Exception {
        return runAndCapture(cmd, TIMEOUT_MS);
    }

    /**
     * Runs a command and returns trimmed stdout if it exits 0 within {@code timeoutMs}, or null otherwise. Stdout goes
     * to a temporary file, not a pipe: a pipe ends only when every process holding it exits, so a background process
     * an rc file starts (an agent, a tmux server) would keep a pipe read waiting after the shell itself has exited.
     * Package-private for tests.
     */
    static String runAndCapture(GeneralCommandLine cmd, long timeoutMs) throws Exception {
        Path out = Files.createTempFile("openspec-detect", ".out");
        try {
            // toProcessBuilder() keeps the command line's input file (the shell's /dev/null); a test pins that.
            ProcessBuilder builder = cmd.toProcessBuilder()
                    .redirectOutput(out.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = builder.start();
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;
            String output = Files.readString(out, StandardCharsets.UTF_8).trim();
            return output.isEmpty() ? null : output;
        } finally {
            Files.deleteIfExists(out);
        }
    }

    /**
     * Adds the login shell's PATH entries to a command's PATH, so {@code #!/usr/bin/env node} shebangs resolve. The
     * command keeps its own PATH (or the IDE's, which it would inherit) and gains only the entries it lacks; replacing
     * it would drop what the IDE loaded from {@code ~/.zshrc}. No-op on Windows (loginShellPath stays null there).
     */
    public void applyLoginShellPath(GeneralCommandLine cmd) {
        if (loginShellPath != null) {
            Map<String, String> env = cmd.getEnvironment();
            String base = env.containsKey("PATH") ? env.get("PATH") : idePath.get();
            env.put("PATH", mergePath(base, loginShellPath));
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
