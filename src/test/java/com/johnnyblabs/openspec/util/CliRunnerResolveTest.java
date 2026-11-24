package com.johnnyblabs.openspec.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** {@link CliRunner} fits every invocation to the detected release before starting a process. */
class CliRunnerResolveTest {

    private static CliDetectionService detected(String version) {
        CliDetectionService d = new CliDetectionService((Project) null);
        CliDetectionTestSupport.fakeAvailable(d, version);
        return d;
    }

    @Test
    void aRefusedInvocationNeverBecomesACommandLine() {
        CliDetectionService d = detected("1.4.1");
        assertThrows(UnsupportedCliArgumentException.class,
                () -> CliRunner.prepare(d, "/work", CliCommands.archive("demo", true)));
    }

    @Test
    void theCommandLineCarriesTheResolvedArgsAndTheExtraEnvironment() throws Exception {
        CliDetectionService d = detected("1.17.0");
        GeneralCommandLine cmd = CliRunner.prepare(d, "/work", CliCommands.updatePlan(true));
        assertEquals(List.of("update", "--dry-run", "--force", "--json"), cmd.getParametersList().getList());
        assertEquals("0", cmd.getEnvironment().get("OPEN_SPEC_INTERACTIVE"), "the invocation's own environment");
        assertEquals("0", cmd.getEnvironment().get("OPENSPEC_TELEMETRY"), "on top of the plain-output environment");
        assertEquals("openspec", cmd.getExePath(), "the detected path");
    }

    @Test
    void aRunBeforeAnyDetectionDetectsOnceAndUsesTheVersionItFound() throws Exception {
        CliDetectionService d = new CliDetectionService((Project) null);
        AtomicInteger probes = new AtomicInteger();
        d.setIdePathForTest(name -> null, () -> "");
        CliDetectionTestSupport.setRunner(d, cmd -> {
            if (!cmd.getParametersList().getList().contains("--version")) return null;
            probes.incrementAndGet();
            return "openspec 1.4.1";
        });
        assertFalse(d.isDetectedOnce());
        // 1.4.1 lacks archive --json: refused only if the run waited for detection instead of assuming the target.
        assertThrows(UnsupportedCliArgumentException.class, () -> CliRunner.resolveFor(d, CliCommands.archive("x", true)));
        assertTrue(d.isDetectedOnce());
        int afterFirst = probes.get();
        assertTrue(afterFirst >= 1, "detection ran");
        assertEquals("1.4.1", CliRunner.resolveFor(d, CliCommands.list()).version());
        assertEquals(afterFirst, probes.get(), "a later run doesn't detect again");
    }

    @Test
    void onTheEdtARunNeverWaitsForDetection() throws Exception {
        CliDetectionService d = new CliDetectionService((Project) null);
        AtomicInteger probes = new AtomicInteger();
        d.setIdePathForTest(name -> null, () -> "");
        CliDetectionTestSupport.setRunner(d, cmd -> {
            probes.incrementAndGet();
            return "openspec 1.4.1";
        });
        assertEquals(PluginVersions.targetCliVersion(), CliRunner.resolveFor(d, CliCommands.list(), true).version());
        assertEquals(0, probes.get(), "no process started on the EDT");
        assertFalse(d.isDetectedOnce());
    }

    @Test
    void concurrentFirstRunsShareOneDetection() throws Exception {
        CliDetectionService d = new CliDetectionService((Project) null);
        AtomicInteger probes = new AtomicInteger();
        d.setIdePathForTest(name -> null, () -> "");
        CliDetectionTestSupport.setRunner(d, cmd -> {
            if (!cmd.getParametersList().getList().contains("--version")) return null;
            probes.incrementAndGet();
            Thread.sleep(200);
            return "openspec 1.4.1";
        });
        Thread other = new Thread(d::ensureDetected);
        other.start();
        Thread.sleep(50);
        assertEquals("1.4.1", CliRunner.resolveFor(d, CliCommands.list(), false).version());
        other.join();
        assertEquals(1, probes.get(), "the startup detection and the first run probe once between them");
    }

    @Test
    void anAvailableCliWithAnUnreadableVersionResolvesAsTheTargetRelease() throws Exception {
        CliDetectionService d = detected("not-a-version");
        assertEquals(PluginVersions.targetCliVersion(), CliRunner.resolveFor(d, CliCommands.list()).version());
    }

    @Test
    void noCliFoundResolvesAsTheTargetRelease() throws Exception {
        CliDetectionService d = new CliDetectionService((Project) null);
        CliDetectionTestSupport.fakeUnavailable(d);
        assertEquals(PluginVersions.targetCliVersion(), CliRunner.resolveFor(d, CliCommands.list()).version());
    }
}
