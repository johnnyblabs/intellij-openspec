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
    void windowsCommonPaths_bothEnvVarsPresent_returnsAllThreeCandidates() {
        Map<String, String> env = Map.of(
                "APPDATA", "C:\\Users\\me\\AppData\\Roaming",
                "LOCALAPPDATA", "C:\\Users\\me\\AppData\\Local"
        );
        List<String> paths = CliDetectionService.windowsCommonPaths(env::get);

        assertEquals(List.of(
                "C:\\Users\\me\\AppData\\Roaming\\npm\\openspec.cmd",
                "C:\\Users\\me\\AppData\\Local\\npm\\openspec.cmd",
                "C:\\Users\\me\\AppData\\Local\\Microsoft\\WinGet\\Links\\openspec.cmd"
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
                "C:\\Local\\Microsoft\\WinGet\\Links\\openspec.cmd"
        ), paths);
    }

    @Test
    void windowsCommonPaths_firstFails_fallsThroughToNext() {
        // Integration-style test: feed the list through tryPath and ensure
        // a failing first candidate doesn't short-circuit the rest.
        CliDetectionService svc = new CliDetectionService((Project) null);
        svc.setIsWindowsForTest(true);

        List<String> attempted = new ArrayList<>();
        svc.setProcessRunnerForTest(cmd -> {
            String exe = cmd.getExePath();
            attempted.add(exe);
            return exe.contains("Local") ? "openspec 2.0.0" : null;
        });

        // Simulate detect() iterating windowsCommonPaths
        boolean found = false;
        for (String candidate : List.of(
                "C:\\Roaming\\npm\\openspec.cmd",
                "C:\\Local\\npm\\openspec.cmd",
                "C:\\Local\\Microsoft\\WinGet\\Links\\openspec.cmd")) {
            if (svc.tryPath(candidate)) { found = true; break; }
        }

        assertTrue(found);
        assertEquals(2, attempted.size(), "Should fall through first failure to second success");
        assertEquals("C:\\Local\\npm\\openspec.cmd", svc.getDetectedPath());
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
}
