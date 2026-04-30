package com.johnnyblabs.openspec.services;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CliDetectionServiceTest {

    private static final Duration STALENESS = Duration.ofSeconds(30);

    // ---------- Staleness check (pre-existing) ----------

    @Test
    void stalenessCheck_nullTimestamp_isStale() {
        boolean stale = isStale(null);
        assertTrue(stale, "Null timestamp (never detected) should be considered stale");
    }

    @Test
    void stalenessCheck_recentTimestamp_isNotStale() {
        Instant last = Instant.now().minus(Duration.ofSeconds(10));
        assertFalse(isStale(last), "Detection 10s ago should not be stale (threshold is 30s)");
    }

    @Test
    void stalenessCheck_oldTimestamp_isStale() {
        Instant last = Instant.now().minus(Duration.ofSeconds(45));
        assertTrue(isStale(last), "Detection 45s ago should be stale (threshold is 30s)");
    }

    @Test
    void stalenessCheck_exactlyAtThreshold_isNotStale() {
        Instant last = Instant.now().minus(Duration.ofSeconds(30));
        assertTrue(isStale(last), "Detection exactly at threshold should be considered stale");
    }

    @Test
    void stalenessCheck_justUnderThreshold_isNotStale() {
        Instant last = Instant.now().minus(Duration.ofSeconds(29));
        assertFalse(isStale(last), "Detection 29s ago should not be stale");
    }

    private boolean isStale(Instant last) {
        if (last == null) return true;
        return Duration.between(last, Instant.now()).compareTo(STALENESS) >= 0;
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
}
