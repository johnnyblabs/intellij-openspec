package com.johnnyblabs.openspec.services;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.util.CliVersion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CliDetectionServiceTest {

    /**
     * A service that can't see this machine's real CLI: the IDE-PATH lookup finds nothing and the IDE's PATH is empty,
     * so only what a test feeds through its seams is found.
     */
    static CliDetectionService hermetic() {
        CliDetectionService svc = new CliDetectionService((Project) null);
        svc.setIdePathForTest(name -> null, () -> "");
        return svc;
    }


    // ---------- Staleness check: the real detectIfStale(), not a test-local copy ----------
    // (These previously exercised a private isStale() mirror in this file, so deleting the service's throttle could
    // not fail them.) Each counts CLI process runs to prove whether detection actually ran.

    private static CliDetectionService serviceCountingRuns(java.util.concurrent.atomic.AtomicInteger runs) {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> {
            runs.incrementAndGet();
            return null;
        });
        return svc;
    }

    @Test
    void versionProbe_asksForPlainOutput() {
        List<Map<String, String>> probeEnvs = new ArrayList<>();
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> {
            if (cmd.getParametersList().getList().contains("--version")) {
                probeEnvs.add(Map.copyOf(cmd.getEnvironment()));
                return "openspec 1.14.3";
            }
            return null;
        });
        svc.detect();
        assertFalse(probeEnvs.isEmpty(), "a --version probe ran");
        for (Map<String, String> env : probeEnvs) {
            assertEquals("1", env.get("NO_COLOR"));
            assertEquals("0", env.get("FORCE_COLOR"));
            assertEquals("1", env.get("OPENSPEC_NO_UPDATE_CHECK"));
            assertEquals("0", env.get("OPENSPEC_TELEMETRY"));
        }
    }

    @Test
    void stalenessCheck_neverDetected_runsDetection() {
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        CliDetectionService svc = serviceCountingRuns(runs);
        assertTrue(svc.detectIfStale(), "never detected → stale → detection runs");
        assertTrue(runs.get() > 0);
    }

    @Test
    void stalenessCheck_recentDetection_isSkipped() {
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        CliDetectionService svc = serviceCountingRuns(runs);
        svc.setLastDetectionTimeForTest(Instant.now().minus(Duration.ofSeconds(10)));
        assertFalse(svc.detectIfStale(), "detection 10s ago is within the 30s window");
        assertEquals(0, runs.get());
    }

    @Test
    void stalenessCheck_justUnderThreshold_isSkipped() {
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        CliDetectionService svc = serviceCountingRuns(runs);
        svc.setLastDetectionTimeForTest(Instant.now().minus(Duration.ofSeconds(29)));
        assertFalse(svc.detectIfStale(), "detection 29s ago is still fresh");
        assertEquals(0, runs.get());
    }

    @Test
    void stalenessCheck_oldDetection_runsDetection() {
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        CliDetectionService svc = serviceCountingRuns(runs);
        svc.setLastDetectionTimeForTest(Instant.now().minus(Duration.ofSeconds(45)));
        assertTrue(svc.detectIfStale(), "detection 45s ago is stale");
        assertTrue(runs.get() > 0);
    }

    @Test
    void stalenessCheck_atThreshold_runsDetection() {
        // The service's check is "younger than 30s → skip", so a detection 30s (or more) ago is stale.
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        CliDetectionService svc = serviceCountingRuns(runs);
        svc.setLastDetectionTimeForTest(Instant.now().minus(Duration.ofSeconds(30)));
        assertTrue(svc.detectIfStale(), "detection 30s ago is stale");
        assertTrue(runs.get() > 0);
    }

    // ---------- Windows suffix fallback (task 2.1) ----------

    @Test
    void tryPath_windows_fallsBackThroughSuffixesInOrder_shortCircuitsOnFirstSuccess() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(true);

        List<String> attempted = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            attempted.add(cmd.getExePath());
            return cmd.getExePath().endsWith(".cmd") ? "openspec 1.0.0" : null;
        });

        assertTrue(svc.tryPath("openspec"));
        assertEquals(List.of("openspec", "openspec.cmd"), attempted,
                "Should try bare name then .cmd, stopping on first success");
        assertEquals("openspec.cmd", svc.getDetectedPath());
        assertEquals("1.0.0", svc.getDetectedVersion());
    }

    @Test
    void tryPath_windows_allSuffixesFail_returnsFalseAfterTryingAll() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(true);

        List<String> attempted = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            attempted.add(cmd.getExePath());
            return null;
        });

        assertFalse(svc.tryPath("openspec"));
        assertEquals(List.of("openspec", "openspec.cmd", "openspec.bat", "openspec.exe"), attempted,
                "Should try bare name then all three Windows suffixes in declared order");
    }

    // ---------- Settings path with extension (task 2.2) ----------

    @Test
    void tryPath_windows_pathWithCmdExtension_invokedVerbatimNoSuffixRetry() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(true);

        List<String> attempted = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            attempted.add(cmd.getExePath());
            return null;
        });

        svc.tryPath("C:\\Users\\me\\AppData\\Roaming\\npm\\openspec.cmd");

        assertEquals(1, attempted.size(), "Path already ending in .cmd should not be extended");
        assertEquals("C:\\Users\\me\\AppData\\Roaming\\npm\\openspec.cmd", attempted.get(0));
    }

    @Test
    void tryPath_windows_pathWithExeExtension_invokedVerbatim() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(true);

        List<String> attempted = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            attempted.add(cmd.getExePath());
            return null;
        });

        svc.tryPath("C:\\tools\\openspec.EXE");

        assertEquals(1, attempted.size(), "Suffix match is case-insensitive — .EXE should suppress fallback");
    }

    // ---------- where.exe vs zsh (task 2.3) ----------

    @Test
    void tryLoginShellWhich_windows_usesWhereExeNotZsh() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(true);

        List<String> exes = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            exes.add(cmd.getExePath());
            return "C:\\Users\\me\\AppData\\Roaming\\npm\\openspec.cmd\r\nC:\\other\\openspec.cmd\r\n";
        });

        String result = svc.tryLoginShellWhich();

        assertEquals("C:\\Users\\me\\AppData\\Roaming\\npm\\openspec.cmd", result,
                "Should return first non-empty line of where.exe output");
        assertEquals(List.of("where.exe"), exes, "Should run where.exe, never zsh");
    }

    @Test
    void tryLoginShellWhich_windows_emptyOutput_returnsNull() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(true);
        svc.setProcessRunnerForTest(cmd -> null);

        assertNull(svc.tryLoginShellWhich());
    }

    // ---------- Windows common paths (task 2.4) ----------

    @Test
    void windowsCommonPaths_bothEnvVarsPresent_returnsTheShimsThenTheStandaloneBinary() {
        Map<String, String> env = Map.of(
                "APPDATA", "C:\\Users\\me\\AppData\\Roaming",
                "LOCALAPPDATA", "C:\\Users\\me\\AppData\\Local"
        );
        List<String> paths = CliDetectionService.windowsCommonPaths(env::get);

        assertEquals(List.of(
                "C:\\Users\\me\\AppData\\Roaming\\npm\\openspec.cmd",
                "C:\\Users\\me\\AppData\\Local\\npm\\openspec.cmd",
                "C:\\Users\\me\\AppData\\Local\\Microsoft\\WinGet\\Links\\openspec.cmd",
                "C:\\Users\\me\\AppData\\Local\\Programs\\openspec\\openspec.exe"
        ), paths);
    }

    @Test
    void windowsCommonPaths_onlyAppDataSet_skipsLocalAppDataCandidates() {
        Map<String, String> env = Map.of("APPDATA", "C:\\Roaming");
        List<String> paths = CliDetectionService.windowsCommonPaths(env::get);
        assertEquals(List.of("C:\\Roaming\\npm\\openspec.cmd"), paths);
    }

    @Test
    void windowsCommonPaths_noEnvVarsSet_returnsEmptyList() {
        List<String> paths = CliDetectionService.windowsCommonPaths(name -> null);
        assertTrue(paths.isEmpty());
    }

    @Test
    void windowsCommonPaths_emptyStringEnvVar_treatedAsUnset() {
        Map<String, String> env = Map.of("APPDATA", "", "LOCALAPPDATA", "C:\\Local");
        List<String> paths = CliDetectionService.windowsCommonPaths(env::get);
        assertEquals(List.of(
                "C:\\Local\\npm\\openspec.cmd",
                "C:\\Local\\Microsoft\\WinGet\\Links\\openspec.cmd",
                "C:\\Local\\Programs\\openspec\\openspec.exe"
        ), paths);
    }

    @Test
    void detect_windows_aFailingShimFallsThroughToTheNext() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(true);
        svc.setEnvForTest(Map.of("APPDATA", "C:\\Roaming", "LOCALAPPDATA", "C:\\Local")::get);
        svc.setExecutableCheckForTest(path -> true);
        List<String> attempted = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            if (!cmd.getParametersList().getList().contains("--version")) return null;
            String exe = cmd.getExePath();
            attempted.add(exe);
            return exe.equals("C:\\Local\\npm\\openspec.cmd") ? "openspec 2.0.0" : null;
        });

        svc.detect();

        assertEquals("C:\\Local\\npm\\openspec.cmd", svc.getDetectedPath());
        assertTrue(attempted.contains("C:\\Roaming\\npm\\openspec.cmd"), "the first shim was tried and failed");
        assertFalse(attempted.contains("C:\\Local\\Microsoft\\WinGet\\Links\\openspec.cmd"),
                "detection stops at the first shim that runs");
    }

    // ---------- Non-Windows hosts: no Windows branches invoked (task 2.5) ----------

    @Test
    void tryPath_unix_noSuffixFallback() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);

        List<String> attempted = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            attempted.add(cmd.getExePath());
            return null;
        });

        svc.tryPath("openspec");

        assertEquals(List.of("openspec"), attempted,
                "On non-Windows, only the bare path is attempted — no .cmd/.bat/.exe variants");
    }

    @Test
    void tryLoginShellWhich_unix_searchesTheShellPathWithoutStartingAProcess() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);

        List<String> exes = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            exes.add(cmd.getExePath());
            return null;
        });

        assertNull(svc.tryLoginShellWhich(), "no shell PATH resolved yet, so nothing to search");
        assertTrue(exes.isEmpty(), "on Unix the shell's PATH is read once per detection, not per lookup: " + exes);
    }

    // ---------- Version detection (contract against captured `openspec --version`) ----------

    @Test
    void parsesRealVersionOutput_stripsToNumericAndClearsFloor() {
        // The captured `openspec --version` on 1.7 is bare "1.7.0". Feed the raw fixture through the
        // real detection: the recovered numeric version feeds every floor gate and must clear the
        // 1.3.0 floor. The gate is a floor (>= 1.3.0), never an allowlist/equality, so any future
        // version flows through — the broad no-upper-cap coverage lives in CliVersionAtLeastTest;
        // here it is tied to the actually-detected version.
        CliDetectionService svc = hermetic();
        svc.setProcessRunnerForTest(cmd -> fixture("1.7.0/version.txt"));

        assertTrue(svc.tryPath("openspec"));
        assertTrue(svc.isAvailable());
        assertEquals("1.7.0", svc.getDetectedVersion());
        assertTrue(CliVersion.atLeast(svc.getDetectedVersion(), "1.3.0"),
                "the detected version must clear the 1.3.0 floor");
    }

    @Test
    void stripsOpenspecVPrefixFromOlderVersionFormat() {
        // Only 1.7's bare "1.7.0" is capturable on the installed CLI, but older builds print
        // "openspec vX.Y.Z"; the strip (`(?i)openspec\s*v?`) must reduce that to the numeric version.
        // This exercises the prefix-removal branch the bare-fixture case leaves untouched.
        CliDetectionService svc = hermetic();
        svc.setProcessRunnerForTest(cmd -> "openspec v1.7.0");

        assertTrue(svc.tryPath("openspec"));
        assertEquals("1.7.0", svc.getDetectedVersion());
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (java.io.InputStream is = CliDetectionServiceTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- Re-detection picks up a mid-session CLI upgrade (redetect-cli-on-focus) ----------
    // The faked `openspec --version` output is the CLI's real captured output (fixtures/cli/<ver>/version.txt).

    private static String capturedVersionOutput(String version) {
        try (java.io.InputStream in = CliDetectionServiceTest.class.getResourceAsStream(
                "/fixtures/cli/" + version + "/version.txt")) {
            assertNotNull(in, "missing captured version.txt for " + version);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A runner that answers `openspec --version` with the given captured output, and nothing else. */
    private static void fakeInstalledCli(CliDetectionService svc, String version) {
        String output = capturedVersionOutput(version);
        svc.setProcessRunnerForTest(cmd -> cmd.getExePath().contains("openspec") ? output : null);
    }

    @Test
    void staleDetection_picksUpAnUpgradedCli() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        fakeInstalledCli(svc, "1.12.0");
        svc.detect();
        assertEquals("1.12.0", svc.getDetectedVersion());

        fakeInstalledCli(svc, "1.13.2"); // the CLI is upgraded underneath the running IDE
        svc.setLastDetectionTimeForTest(java.time.Instant.now().minusSeconds(31));

        assertTrue(svc.detectIfStale(), "a detection older than 30s must re-run");
        assertEquals("1.13.2", svc.getDetectedVersion(), "the upgraded CLI must be picked up");
    }

    @Test
    void freshDetection_isThrottled_soWindowSwitchingDoesNotRerunTheCli() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        fakeInstalledCli(svc, "1.12.0");
        svc.detect();

        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        String upgraded = capturedVersionOutput("1.13.2");
        svc.setProcessRunnerForTest(cmd -> {
            runs.incrementAndGet();
            return cmd.getExePath().contains("openspec") ? upgraded : null;
        });
        svc.setLastDetectionTimeForTest(java.time.Instant.now().minusSeconds(5));

        assertFalse(svc.detectIfStale(), "a detection within 30s must be skipped");
        assertEquals(0, runs.get(), "no CLI process may run inside the throttle window");
        assertEquals("1.12.0", svc.getDetectedVersion());
    }

    @Test
    void aDetectionInProgressNeverReadsAsUnavailable() throws Exception {
        // A re-run must not clear the previous result while it probes: readers (the tool window, validation's
        // CLI-or-fallback choice) would be told the CLI is missing for as long as the probe takes.
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> cmd.getParametersList().getList().contains("--version") ? "openspec 1.14.3" : null);
        svc.detect();
        assertTrue(svc.isAvailable());

        java.util.concurrent.CountDownLatch probing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        svc.setProcessRunnerForTest(cmd -> {
            probing.countDown();
            release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return null; // the re-run will find nothing
        });
        Thread rerun = new Thread(svc::detect, "re-detect");
        rerun.start();
        assertTrue(probing.await(5, java.util.concurrent.TimeUnit.SECONDS), "the re-run reached its probe");

        assertTrue(svc.isAvailable(), "mid-detection, readers still see the previous result");
        assertEquals("1.14.3", svc.getDetectedVersion());
        assertEquals("openspec", svc.getDetectedPath(), "path and version stay consistent with each other");

        release.countDown();
        rerun.join(5_000);
        assertFalse(svc.isAvailable(), "the new result replaces the old one once the re-run completes");
        assertNull(svc.getDetectedVersion());
        assertNull(svc.getDetectedPath());
    }

    @Test
    void detectedOnceIsFalseUntilTheFirstDetectionCompletes() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> null);
        assertFalse(svc.isDetectedOnce(), "nothing has been detected yet");
        svc.detect();
        assertTrue(svc.isDetectedOnce(), "a completed detection counts even when the CLI wasn't found");
        assertFalse(svc.isAvailable());
    }

    // ---------- 1.15 standalone binary: its captured --version output parses like the npm install ----------

    private static String loadCliFixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (java.io.InputStream is = CliDetectionServiceTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    private static CliDetectionService detectWithVersionOutput(String versionOutput) {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd ->
                cmd.getParametersList().getList().contains("--version") ? versionOutput : null);
        svc.tryPath("openspec");
        return svc;
    }

    @Test
    void standaloneBinary_versionOutput_detectsAs115() {
        // Real `openspec --version` stdout of the 1.15.0 standalone darwin-arm64 binary (see fixtures/cli/README.md).
        String captured = loadCliFixture("1.15.0/standalone/version.txt");
        CliDetectionService svc = detectWithVersionOutput(captured);

        assertTrue(svc.isAvailable(), "the standalone binary's --version output counts as a detected CLI");
        assertEquals("1.15.0", svc.getDetectedVersion(), "detected version is the bare version, without the trailing newline");
        assertTrue(CliVersion.atLeast(svc.getDetectedVersion(), "1.15.0"), "recognized as the 1.15 generation");
    }

    @Test
    void standaloneBinary_detectsTheSameVersionAsTheNpmInstall() {
        String standalone = detectWithVersionOutput(loadCliFixture("1.15.0/standalone/version.txt")).getDetectedVersion();
        String npm = detectWithVersionOutput(loadCliFixture("1.15.0/version.txt")).getDetectedVersion();
        assertEquals(npm, standalone, "the standalone binary and the npm install are the same generation to the plugin");
    }

    // ---------- 1.15 standalone binary: where its installers put it ----------

    private static final List<String> UNIX_FIXED = List.of(
            "/opt/homebrew/bin/openspec", "/usr/local/bin/openspec", "/usr/bin/openspec",
            "/home/linuxbrew/.linuxbrew/bin/openspec");

    @Test
    void unixCommonPaths_noOverrides_endWithLocalBin() {
        List<String> paths = CliDetectionService.unixCommonPaths(name -> null, "/home/me");
        List<String> expected = new ArrayList<>(UNIX_FIXED);
        expected.add("/home/me/.local/bin/openspec");
        assertEquals(expected, paths, "the existing locations keep their order; the installer's default comes last");
    }

    @Test
    void unixCommonPaths_installDirOverride_comesBeforeLocalBin() {
        Map<String, String> env = Map.of("OPENSPEC_INSTALL_DIR", "/opt/tools");
        List<String> paths = CliDetectionService.unixCommonPaths(env::get, "/home/me");
        assertEquals(List.of("/opt/tools/openspec", "/home/me/.local/bin/openspec"), paths.subList(4, 6));
    }

    @Test
    void unixCommonPaths_installDirAndNameOverrides_combine() {
        Map<String, String> env = Map.of("OPENSPEC_INSTALL_DIR", "/opt/tools", "OPENSPEC_INSTALL_NAME", "ospec");
        List<String> paths = CliDetectionService.unixCommonPaths(env::get, "/home/me");
        assertEquals("/opt/tools/ospec", paths.get(4));
    }

    @Test
    void unixCommonPaths_emptyDirOrNameAlone_addsNoOverride() {
        Map<String, String> env = Map.of("OPENSPEC_INSTALL_DIR", "", "OPENSPEC_INSTALL_NAME", "ospec");
        List<String> paths = CliDetectionService.unixCommonPaths(env::get, "/home/me");
        assertEquals(5, paths.size(), "a name without a dir is no candidate");
        assertFalse(paths.stream().anyMatch(p -> p.contains("ospec") && !p.endsWith("/openspec")));
    }

    @Test
    void unixCommonPaths_blankHome_addsNoLocalBin() {
        assertEquals(UNIX_FIXED, CliDetectionService.unixCommonPaths(name -> null, " "));
        assertEquals(UNIX_FIXED, CliDetectionService.unixCommonPaths(name -> null, null));
    }

    @Test
    void windowsCommonPaths_installDirOverride_comesBeforeTheStandaloneDefault() {
        Map<String, String> env = Map.of("LOCALAPPDATA", "C:\\Local", "OPENSPEC_INSTALL_DIR", "D:\\Tools");
        List<String> paths = CliDetectionService.windowsCommonPaths(env::get);
        assertEquals(List.of("D:\\Tools\\openspec.exe", "C:\\Local\\Programs\\openspec\\openspec.exe"),
                paths.subList(paths.size() - 2, paths.size()));
    }

    @Test
    void windowsCommonPaths_noLocalAppData_keepsASetOverride() {
        Map<String, String> env = Map.of("OPENSPEC_INSTALL_DIR", "D:\\Tools", "OPENSPEC_INSTALL_NAME", "os.exe");
        assertEquals(List.of("D:\\Tools\\os.exe"), CliDetectionService.windowsCommonPaths(env::get));
    }

    /** A service where every branch before the fixed locations misses, and the probe answers only for {@code hit}. */
    private static CliDetectionService serviceFindingOnly(boolean windows, String hit, String output,
                                                          List<String> probed) {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(windows);
        svc.setProcessRunnerForTest(cmd -> {
            if (!cmd.getParametersList().getList().contains("--version")) return null;
            probed.add(cmd.getExePath());
            return cmd.getExePath().equals(hit) ? output : null;
        });
        return svc;
    }

    @Test
    void detect_unix_findsTheStandaloneBinaryInLocalBin() {
        String standalone = "/home/me/.local/bin/openspec";
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = serviceFindingOnly(false, standalone,
                loadCliFixture("1.15.0/standalone/version.txt").trim(), probed);
        svc.setEnvForTest(name -> null);
        svc.setUserHomeForTest("/home/me");
        List<String> checked = new ArrayList<>();
        svc.setExecutableCheckForTest(path -> {
            checked.add(path);
            return path.equals(standalone);
        });

        List<String> otherCommands = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            if (!cmd.getParametersList().getList().contains("--version")) {
                otherCommands.add(cmd.getExePath());
                return null;
            }
            probed.add(cmd.getExePath());
            return cmd.getExePath().equals(standalone) ? loadCliFixture("1.15.0/standalone/version.txt").trim() : null;
        });

        svc.detect();

        assertFalse(otherCommands.contains("where.exe"), "no Windows lookup on Unix");
        assertTrue(svc.isAvailable());
        assertEquals(standalone, svc.getDetectedPath());
        assertEquals("1.15.0", svc.getDetectedVersion());
        List<String> expectedChecks = new ArrayList<>(UNIX_FIXED);
        expectedChecks.add(standalone);
        assertEquals(expectedChecks, checked, "Homebrew, the system paths and Linux Homebrew are checked first");
        assertEquals(List.of("openspec", standalone), probed, "only the bare name and the existing file are run");
    }

    @Test
    void detect_windows_findsTheStandaloneBinaryAfterTheShims() {
        String standalone = "C:\\Local\\Programs\\openspec\\openspec.exe";
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = serviceFindingOnly(true, standalone,
                loadCliFixture("1.15.0/standalone/version.txt").trim(), probed);
        svc.setEnvForTest(Map.of("APPDATA", "C:\\Roaming", "LOCALAPPDATA", "C:\\Local")::get);
        List<String> checked = new ArrayList<>();
        svc.setExecutableCheckForTest(path -> {
            checked.add(path);
            return path.equals(standalone);
        });

        svc.detect();

        assertEquals(standalone, svc.getDetectedPath());
        assertEquals("1.15.0", svc.getDetectedVersion());
        assertEquals(List.of(
                "C:\\Roaming\\npm\\openspec.cmd",
                "C:\\Local\\npm\\openspec.cmd",
                "C:\\Local\\Microsoft\\WinGet\\Links\\openspec.cmd",
                standalone), checked, "the npm and winget shims are checked first");
        assertEquals(standalone, probed.get(probed.size() - 1));
    }

    @Test
    void detect_anExistingInstallStillWinsOverTheStandaloneBinary() {
        String existing = "/usr/local/bin/openspec";
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> {
            if (!cmd.getParametersList().getList().contains("--version")) return null;
            probed.add(cmd.getExePath());
            return cmd.getExePath().startsWith("/") ? "openspec 1.15.0" : null;
        });
        svc.setEnvForTest(name -> null);
        svc.setUserHomeForTest("/home/me");
        svc.setExecutableCheckForTest(path -> path.equals(existing) || path.equals("/home/me/.local/bin/openspec"));

        svc.detect();

        assertEquals(existing, svc.getDetectedPath());
        assertFalse(probed.contains("/home/me/.local/bin/openspec"), "detection stops at the first install found");
    }

    @Test
    void detect_missingFixedLocationsAreNotRun() {
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = serviceFindingOnly(false, "none", "openspec 1.15.0", probed);
        svc.setEnvForTest(Map.of("OPENSPEC_INSTALL_DIR", "/opt/tools")::get);
        svc.setUserHomeForTest("/home/me");
        svc.setExecutableCheckForTest(path -> false);

        svc.detect();

        assertFalse(svc.isAvailable());
        assertEquals(List.of("openspec"), probed, "only the bare name runs; no fixed location starts a process");
    }

    @Test
    void detect_theFileCheckDoesNotGateTheBareNameButFindsTheShellPathEntry() {
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> {
            List<String> args = cmd.getParametersList().getList();
            if (args.contains(CliDetectionService.PRINT_PATH)) {
                return "noise\n" + CliDetectionService.PATH_BEGIN + "/usr/bin:/custom/bin" + CliDetectionService.PATH_END;
            }
            if (!args.contains("--version")) return null;
            probed.add(cmd.getExePath());
            return cmd.getExePath().equals("/custom/bin/openspec") ? "openspec 1.15.0" : null;
        });
        // Nothing passes the check except the shell PATH's copy; the bare name is probed regardless.
        svc.setExecutableCheckForTest(path -> path.equals("/custom/bin/openspec"));

        svc.detect();

        assertEquals(List.of("openspec", "/custom/bin/openspec"), probed);
        assertEquals("/custom/bin/openspec", svc.getDetectedPath());
    }

    // ---------- The real file check ----------

    @Test
    void isExecutableFile_acceptsOnlyARegularExecutableFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws java.io.IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "the execute bit needs a POSIX file system");
        java.nio.file.Path exe = java.nio.file.Files.createFile(dir.resolve("openspec"));
        java.nio.file.Files.setPosixFilePermissions(exe, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        java.nio.file.Path plain = java.nio.file.Files.createFile(dir.resolve("plain"));
        java.nio.file.Files.setPosixFilePermissions(plain, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));

        assertTrue(CliDetectionService.isExecutableFile(exe.toString()));
        assertFalse(CliDetectionService.isExecutableFile(plain.toString()), "not executable");
        assertFalse(CliDetectionService.isExecutableFile(dir.toString()), "a directory");
        assertFalse(CliDetectionService.isExecutableFile(dir.resolve("missing").toString()), "missing");
        assertFalse(CliDetectionService.isExecutableFile("bad\0path"), "an invalid path counts as missing");
    }

    @Test
    void isRegularFile_ignoresTheExecuteBit(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws java.io.IOException {
        java.nio.file.Path plain = java.nio.file.Files.createFile(dir.resolve("openspec.exe"));
        assertTrue(CliDetectionService.isRegularFile(plain.toString()));
        assertFalse(CliDetectionService.isRegularFile(dir.toString()), "a directory");
        assertFalse(CliDetectionService.isRegularFile(dir.resolve("missing.exe").toString()), "missing");
        assertFalse(CliDetectionService.isRegularFile("bad\0path"), "an invalid path counts as missing");
    }

    @Test
    void detect_theInstallDirOverrideWinsOverLocalBin() {
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> {
            if (!cmd.getParametersList().getList().contains("--version")) return null;
            probed.add(cmd.getExePath());
            return cmd.getExePath().startsWith("/") ? "1.15.0" : null;
        });
        svc.setEnvForTest(Map.of("OPENSPEC_INSTALL_DIR", "/opt/tools")::get);
        svc.setUserHomeForTest("/home/me");
        svc.setExecutableCheckForTest(path -> path.equals("/opt/tools/openspec")
                || path.equals("/home/me/.local/bin/openspec"));

        svc.detect();

        assertEquals("/opt/tools/openspec", svc.getDetectedPath());
        assertFalse(probed.contains("/home/me/.local/bin/openspec"));
    }

    @Test
    void theDefaultFileCheckFollowsTheOsAtEachCall(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws java.io.IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "the execute bit needs a POSIX file system");
        java.nio.file.Path file = java.nio.file.Files.createFile(dir.resolve("openspec.exe"));
        java.nio.file.Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        CliDetectionService svc = hermetic();

        svc.setIsWindowsForTest(false);
        assertFalse(svc.executableCheck().test(file.toString()), "Unix needs the execute bit");
        svc.setIsWindowsForTest(true);
        assertTrue(svc.executableCheck().test(file.toString()), "Windows needs only a regular file");
    }

    // ---------- The interactive login shell's PATH (fixtures/shell/) ----------

    private static String shellFixture(String name) {
        try (java.io.InputStream is = CliDetectionServiceTest.class.getResourceAsStream("/fixtures/shell/" + name)) {
            assertNotNull(is, name);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void theMarkedPathIsReadFromRealShellOutputIgnoringTheBanner() {
        for (String name : List.of("zsh-ilc-noisy.txt", "bash-ilc-noisy.txt")) {
            String raw = shellFixture(name);
            assertTrue(raw.contains("Welcome back!"), name + " capture precondition: the rc file printed a banner");
            String path = CliDetectionService.parseMarkedPath(raw);
            assertNotNull(path, name);
            assertTrue(path.startsWith("/fixture/home/rcbin:"), name + ": the rc-added entry comes first: " + path);
            assertFalse(path.contains("Welcome") || path.contains("\n"), name + ": no banner text: " + path);
        }
    }

    @Test
    void aLoginShellThatIsNotInteractiveMissesTheRcEntry() {
        String path = CliDetectionService.parseMarkedPath(shellFixture("zsh-lc-misses.txt"));
        assertNotNull(path);
        assertFalse(path.contains("/fixture/home/rcbin"), "the old -lc flags skip .zshrc: " + path);
    }

    @Test
    void markerParsingRejectsIncompleteOutputAndUsesTheLastBegin() {
        assertNull(CliDetectionService.parseMarkedPath(null));
        assertNull(CliDetectionService.parseMarkedPath("/usr/bin:/bin"), "no markers, nothing used");
        assertNull(CliDetectionService.parseMarkedPath(CliDetectionService.PATH_BEGIN + "/usr/bin"), "no end marker");
        assertNull(CliDetectionService.parseMarkedPath(CliDetectionService.PATH_BEGIN + CliDetectionService.PATH_END), "empty");
        String echoedEarlier = "echo " + CliDetectionService.PATH_BEGIN + "junk\n"
                + CliDetectionService.PATH_BEGIN + "/a:/b" + CliDetectionService.PATH_END + "\n";
        assertEquals("/a:/b", CliDetectionService.parseMarkedPath(echoedEarlier));
    }

    @Test
    void shellFlagsFollowTheShellLikeThePlatform() {
        assertEquals(List.of("-l", "-i", "-c"), CliDetectionService.shellFlags("/bin/zsh"));
        assertEquals(List.of("-l", "-i", "-c"), CliDetectionService.shellFlags("/usr/bin/bash"));
        assertEquals(List.of("-l", "-c"), CliDetectionService.shellFlags("/opt/homebrew/bin/fish"));
        assertEquals(List.of("-i", "-c"), CliDetectionService.shellFlags("/bin/tcsh"));
        assertEquals(List.of("-i", "-c"), CliDetectionService.shellFlags("/bin/csh"));
        assertEquals(List.of("-l", "-i", "-c"), CliDetectionService.shellFlags("/usr/local/bin/nu"));
    }

    @Test
    void mergeAppendsOnlyMissingEntriesInOrder() {
        assertEquals("/usr/bin:/bin:/home/me/.nvm/bin:/opt/x",
                CliDetectionService.mergePath("/usr/bin:/bin", "/home/me/.nvm/bin:/usr/bin:/opt/x"));
        assertEquals("/a:/b", CliDetectionService.mergePath("/a:/b", "/b:/a"), "nothing missing, unchanged");
        assertEquals("/a", CliDetectionService.mergePath(null, "/a"));
        assertEquals("/a", CliDetectionService.mergePath("", "/a"));
        assertEquals("/a", CliDetectionService.mergePath("/a", null));
    }

    @Test
    void detect_unix_runsOneInteractiveShellWithTheReaderEnvAndFindsAnRcOnlyEntry() {
        List<GeneralCommandLine> shells = new ArrayList<>();
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setEnvForTest(name -> name.equals("SHELL") ? "/bin/zsh" : null);
        svc.setUserHomeForTest(null);
        svc.setProcessRunnerForTest(cmd -> {
            List<String> args = cmd.getParametersList().getList();
            if (args.contains(CliDetectionService.PRINT_PATH)) {
                shells.add(cmd);
                return shellFixture("zsh-ilc-noisy.txt");
            }
            if (!args.contains("--version")) return null;
            probed.add(cmd.getExePath());
            return cmd.getExePath().equals("/fixture/home/rcbin/openspec") ? "1.15.0" : null;
        });
        svc.setExecutableCheckForTest(path -> path.equals("/fixture/home/rcbin/openspec"));

        svc.detect();

        assertEquals(1, shells.size(), "one shell call per detection, no separate which");
        GeneralCommandLine shell = shells.get(0);
        assertEquals("/bin/zsh", shell.getExePath());
        assertEquals(List.of("-l", "-i", "-c", CliDetectionService.PRINT_PATH), shell.getParametersList().getList());
        assertEquals("true", shell.getEnvironment().get("INTELLIJ_ENVIRONMENT_READER"));
        assertEquals("true", shell.getEnvironment().get("DISABLE_AUTO_UPDATE"));
        assertEquals("/fixture/home/rcbin/openspec", svc.getDetectedPath());
        assertEquals(List.of("openspec", "/fixture/home/rcbin/openspec"), probed, "bare name, then the shell PATH entry");
    }

    @Test
    void detect_unix_aShellWithNoMarkersFallsThroughToTheFixedLocations() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setEnvForTest(name -> null);
        svc.setUserHomeForTest(null);
        svc.setProcessRunnerForTest(cmd -> {
            if (cmd.getParametersList().getList().contains(CliDetectionService.PRINT_PATH)) return "only a banner";
            return cmd.getExePath().equals("/usr/local/bin/openspec") ? "1.15.0" : null;
        });
        svc.setExecutableCheckForTest(path -> path.equals("/usr/local/bin/openspec"));

        svc.detect();

        assertEquals("/usr/local/bin/openspec", svc.getDetectedPath());
        assertNull(svc.getLoginShellPath(), "unmarked output is never used as a PATH");
    }

    @Test
    void detect_unix_theIdePathComesAfterSettingsAndBeforeAnyShellOrBareProbe() {
        List<String> order = new ArrayList<>();
        List<String> lookedUp = new ArrayList<>();
        CliDetectionService svc = new CliDetectionService((Project) null);
        svc.setIsWindowsForTest(false);
        java.io.File hit = new java.io.File("/home/me/.nvm/versions/node/v22/bin/openspec");
        // The path detection probes, as the host's File reports it (a Windows runner adds a drive letter).
        String probedHit = hit.getAbsolutePath();
        svc.setIdePathForTest(name -> {
            lookedUp.add(name);
            return hit;
        }, () -> "/usr/bin");
        svc.setProcessRunnerForTest(cmd -> {
            List<String> args = cmd.getParametersList().getList();
            order.add(args.contains(CliDetectionService.PRINT_PATH) ? "shell" : cmd.getExePath());
            return args.contains("--version") && cmd.getExePath().contains(".nvm") ? "1.15.0" : null;
        });

        svc.detect();

        assertEquals(List.of("openspec"), lookedUp);
        assertEquals(probedHit, svc.getDetectedPath());
        assertEquals(List.of("shell", probedHit), order,
                "the IDE PATH hit is probed by its absolute path, before the bare name");
    }

    @Test
    void detect_unix_anIdePathMissGoesOnToTheBareName() {
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> {
            if (!cmd.getParametersList().getList().contains("--version")) return null;
            probed.add(cmd.getExePath());
            return "1.15.0";
        });

        svc.detect();

        assertEquals(List.of("openspec"), probed);
    }

    @Test
    void detect_windows_neverAsksTheIdePathOrStartsAShell() {
        List<String> lookedUp = new ArrayList<>();
        List<String> exes = new ArrayList<>();
        CliDetectionService svc = new CliDetectionService((Project) null);
        svc.setIsWindowsForTest(true);
        svc.setEnvForTest(name -> null);
        svc.setIdePathForTest(name -> {
            lookedUp.add(name);
            return null;
        }, () -> "");
        svc.setProcessRunnerForTest(cmd -> {
            exes.add(cmd.getExePath());
            return null;
        });

        svc.detect();

        assertTrue(lookedUp.isEmpty(), "Windows keeps where.exe and the suffix fallback");
        assertTrue(exes.stream().noneMatch(e -> e.contains("zsh") || e.contains("bash") || e.endsWith("sh")), exes.toString());
        assertTrue(exes.contains("where.exe"));
    }

    @Test
    void cliRunsKeepTheirPathAndGainOnlyMissingShellEntries() {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setEnvForTest(name -> null);
        svc.setUserHomeForTest(null);
        svc.setProcessRunnerForTest(cmd -> cmd.getParametersList().getList().contains(CliDetectionService.PRINT_PATH)
                ? CliDetectionService.PATH_BEGIN + "/home/me/.nvm/bin:/usr/bin" + CliDetectionService.PATH_END : null);
        svc.detect();
        svc.setIdePathForTest(name -> null, () -> "/opt/homebrew/bin:/usr/bin");

        GeneralCommandLine inherits = new GeneralCommandLine("openspec");
        svc.applyLoginShellPath(inherits);
        assertEquals("/opt/homebrew/bin:/usr/bin:/home/me/.nvm/bin", inherits.getEnvironment().get("PATH"),
                "the IDE's PATH first, then only what it lacked");

        GeneralCommandLine ownPath = new GeneralCommandLine("openspec");
        ownPath.getEnvironment().put("PATH", "/home/me/.nvm/bin:/usr/bin");
        svc.applyLoginShellPath(ownPath);
        assertEquals("/home/me/.nvm/bin:/usr/bin", ownPath.getEnvironment().get("PATH"), "nothing missing, unchanged");
    }

    // --- Capture: a background child holding the shell's output must not hang detection ---

    private static boolean posix() {
        return !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")
                && new java.io.File("/bin/sh").canExecute();
    }

    @Test
    void captureReturnsTheOutputWhileABackgroundChildStillHoldsIt() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "needs /bin/sh");
        java.nio.file.Path pidFile = java.nio.file.Files.createTempFile("detect-child", ".pid");
        try {
            // The child inherits stdout and outlives the shell, as an agent or tmux server started from ~/.zshrc does.
            // With a pipe, the read would wait for the child (30 s); the hard timeout fails such a regression fast.
            GeneralCommandLine cmd = new GeneralCommandLine("/bin/sh", "-c",
                    "printf 'ready'; sleep 30 & echo $! > '" + pidFile + "'");
            long start = System.nanoTime();

            String out = assertTimeoutPreemptively(Duration.ofSeconds(8),
                    () -> CliDetectionService.runAndCapture(cmd, 10_000));

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertEquals("ready", out);
            assertTrue(elapsedMs < 3_000, "returned once the shell exited, not when the child did: " + elapsedMs + " ms");
        } finally {
            String pid = java.nio.file.Files.readString(pidFile).strip();
            if (!pid.isEmpty()) ProcessHandle.of(Long.parseLong(pid)).ifPresent(ProcessHandle::destroyForcibly);
            java.nio.file.Files.deleteIfExists(pidFile);
        }
    }

    @Test
    void aLotOfStderrDoesNotBlockTheCapture() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "needs /bin/sh");
        // More than a pipe buffer on stderr: an unread stderr pipe would block the process until the timeout.
        GeneralCommandLine cmd = new GeneralCommandLine("/bin/sh", "-c", "printf x; head -c 300000 /dev/zero >&2");
        assertEquals("x", assertTimeoutPreemptively(Duration.ofSeconds(8),
                () -> CliDetectionService.runAndCapture(cmd, 10_000)));
    }

    @Test
    void captureReturnsNullOnFailureAndOnTimeout() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "needs /bin/sh");
        assertNull(CliDetectionService.runAndCapture(new GeneralCommandLine("/bin/sh", "-c", "printf x; exit 3"), 10_000));
        long start = System.nanoTime();
        assertNull(CliDetectionService.runAndCapture(new GeneralCommandLine("/bin/sh", "-c", "sleep 30"), 300));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000, "the timeout bounds the wait");
    }

    @Test
    void captureKeepsTheCommandsInputFileAndLeavesNoTemporaryFile() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "needs /bin/sh");
        java.nio.file.Path input = java.nio.file.Files.createTempFile("detect-in", ".txt");
        java.nio.file.Path tmp = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
        try {
            java.nio.file.Files.writeString(input, "from-input");
            long before = countCaptureFiles(tmp);
            GeneralCommandLine cmd = new GeneralCommandLine("/bin/sh", "-c", "cat").withInput(input.toFile());

            assertEquals("from-input", CliDetectionService.runAndCapture(cmd, 10_000));
            assertEquals(before, countCaptureFiles(tmp), "the capture file is deleted");
        } finally {
            java.nio.file.Files.deleteIfExists(input);
        }
    }

    private static long countCaptureFiles(java.nio.file.Path dir) throws java.io.IOException {
        try (var files = java.nio.file.Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().startsWith("openspec-detect")).count();
        }
    }

    // --- The login-shell PATH is reused ---

    private CliDetectionService countingShell(List<Integer> shellCalls, Instant[] now) {
        CliDetectionService svc = hermetic();
        svc.setIsWindowsForTest(false);
        svc.setEnvForTest(name -> name.equals("SHELL") ? "/bin/zsh" : null);
        svc.setUserHomeForTest(null);
        svc.setClockForTest(() -> now[0]);
        svc.setProcessRunnerForTest(cmd -> {
            if (cmd.getParametersList().getList().contains(CliDetectionService.PRINT_PATH)) {
                shellCalls.add(1);
                return CliDetectionService.PATH_BEGIN + "/usr/bin:/shell" + shellCalls.size() + "/bin"
                        + CliDetectionService.PATH_END;
            }
            return null;
        });
        svc.setExecutableCheckForTest(path -> false);
        return svc;
    }

    @Test
    void theShellPathIsReusedUntilItExpires() {
        List<Integer> shells = new ArrayList<>();
        Instant[] now = {Instant.parse("2026-10-27T10:00:00Z")};
        CliDetectionService svc = countingShell(shells, now);

        svc.detect();
        now[0] = now[0].plus(CliDetectionService.SHELL_PATH_TTL).minusSeconds(1);
        svc.detect();
        assertEquals(1, shells.size(), "a second detection inside the window starts no shell");
        assertEquals("/usr/bin:/shell1/bin", svc.getLoginShellPath());

        now[0] = now[0].plusSeconds(1);
        svc.detect();
        assertEquals(2, shells.size(), "an expired PATH is resolved again");
        assertEquals("/usr/bin:/shell2/bin", svc.getLoginShellPath());
    }

    @Test
    void aFreshDetectionAlwaysStartsTheShell() {
        List<Integer> shells = new ArrayList<>();
        Instant[] now = {Instant.parse("2026-10-27T10:00:00Z")};
        CliDetectionService svc = countingShell(shells, now);

        svc.detect();
        svc.detect(true);

        assertEquals(2, shells.size());
        assertEquals("/usr/bin:/shell2/bin", svc.getLoginShellPath());
    }

    @Test
    void aShellThatFailsTheFirstTimeIsNotRetriedOnEveryDetection() {
        List<Integer> shells = new ArrayList<>();
        Instant[] now = {Instant.parse("2026-10-27T10:00:00Z")};
        CliDetectionService svc = countingShell(shells, now);
        svc.setProcessRunnerForTest(cmd -> {
            if (cmd.getParametersList().getList().contains(CliDetectionService.PRINT_PATH)) {
                shells.add(1);
                return null;
            }
            return null;
        });

        svc.detect();
        svc.detect();

        assertEquals(1, shells.size(), "a broken rc file costs one shell per window, not one per detection");
        assertNull(svc.getLoginShellPath());
    }

    @Test
    void aFailedShellKeepsThePreviousPathAndIsNotRetriedAtOnce() {
        List<Integer> shells = new ArrayList<>();
        Instant[] now = {Instant.parse("2026-10-27T10:00:00Z")};
        CliDetectionService svc = countingShell(shells, now);
        svc.detect();
        svc.setProcessRunnerForTest(cmd -> {
            if (cmd.getParametersList().getList().contains(CliDetectionService.PRINT_PATH)) {
                shells.add(1);
                return "no markers";
            }
            return null;
        });

        now[0] = now[0].plus(CliDetectionService.SHELL_PATH_TTL);
        svc.detect();
        svc.detect();

        assertEquals(2, shells.size(), "the failed resolution counts as a try");
        assertEquals("/usr/bin:/shell1/bin", svc.getLoginShellPath(), "the previous PATH is kept");
    }
}
