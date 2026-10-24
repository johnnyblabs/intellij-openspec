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

    // ---------- Staleness check: the real detectIfStale(), not a test-local copy ----------
    // (These previously exercised a private isStale() mirror in this file, so deleting the service's throttle could
    // not fail them.) Each counts CLI process runs to prove whether detection actually ran.

    private static CliDetectionService serviceCountingRuns(java.util.concurrent.atomic.AtomicInteger runs) {
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
    void tryLoginShellWhich_unix_doesNotInvokeWhereExe() {
        CliDetectionService svc = new CliDetectionService((Project) null);
        svc.setIsWindowsForTest(false);

        List<String> exes = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            exes.add(cmd.getExePath());
            return null;
        });

        svc.tryLoginShellWhich();

        assertFalse(exes.isEmpty(), "Should still attempt the Unix shell lookup");
        assertFalse(exes.get(0).endsWith("where.exe"),
                "On Unix the executable must not be where.exe; got: " + exes.get(0));
    }

    // ---------- Version detection (contract against captured `openspec --version`) ----------

    @Test
    void parsesRealVersionOutput_stripsToNumericAndClearsFloor() {
        // The captured `openspec --version` on 1.7 is bare "1.7.0". Feed the raw fixture through the
        // real detection: the recovered numeric version feeds every floor gate and must clear the
        // 1.3.0 floor. The gate is a floor (>= 1.3.0), never an allowlist/equality, so any future
        // version flows through — the broad no-upper-cap coverage lives in CliVersionAtLeastTest;
        // here it is tied to the actually-detected version.
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
    void detect_theFileCheckDoesNotGateTheBareNameOrTheShellPath() {
        List<String> probed = new ArrayList<>();
        CliDetectionService svc = new CliDetectionService((Project) null);
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> {
            List<String> args = cmd.getParametersList().getList();
            if (args.contains("which openspec")) return "/custom/bin/openspec";
            if (!args.contains("--version")) return null;
            probed.add(cmd.getExePath());
            return cmd.getExePath().equals("/custom/bin/openspec") ? "openspec 1.15.0" : null;
        });
        svc.setExecutableCheckForTest(path -> false);

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
        CliDetectionService svc = new CliDetectionService((Project) null);
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
        CliDetectionService svc = new CliDetectionService((Project) null);

        svc.setIsWindowsForTest(false);
        assertFalse(svc.executableCheck().test(file.toString()), "Unix needs the execute bit");
        svc.setIsWindowsForTest(true);
        assertTrue(svc.executableCheck().test(file.toString()), "Windows needs only a regular file");
    }
}
