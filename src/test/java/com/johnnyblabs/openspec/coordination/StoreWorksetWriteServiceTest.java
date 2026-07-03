package com.johnnyblabs.openspec.coordination;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.coordination.CoordinationService.WriteResult;
import com.johnnyblabs.openspec.services.CliDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * Service-level gating and result-contract tests for the 1.5 store/workset write surface. Below the
 * bar (CLI &lt; 1.5.0 or unavailable) every write method must short-circuit with guidance and never
 * shell out; the parsed {@code status[]} {@code fix} must be surfaced verbatim and raw stderr never
 * returned; and {@code store remove} must be flagged destructive.
 */
@ExtendWith(MockitoExtension.class)
class StoreWorksetWriteServiceTest {

    @Mock Project project;
    @Mock CliDetectionService detection;

    private CoordinationService serviceWith(String version, boolean available) {
        CoordinationService service = new CoordinationService(project);
        lenient().when(project.getService(CliDetectionService.class)).thenReturn(detection);
        lenient().when(detection.isAvailable()).thenReturn(available);
        lenient().when(detection.getDetectedVersion()).thenReturn(version);
        return service;
    }

    // ---- T.2: gated below the 1.5.0 floor, no shell-out ---------------------

    @Test
    void writeMethodsGatedBelowFloorReturnGuidanceWithoutShellingOut() {
        // At 1.4.9 the store CLI gate is false. Each write must return the guidance failure. If any
        // method reached CliRunner it would call project.getService for detection again AND attempt a
        // process; the distinct guidance message proves the short-circuit ran first.
        CoordinationService service = serviceWith("1.4.9", true);

        for (WriteResult r : List.of(
                service.setupStore("s", "/tmp/x"),
                service.registerStore("/tmp/x"),
                service.unregisterStore("s"),
                service.removeStore("s"),
                service.createWorkset("w", List.of(new WorksetEntry.Member("m", "/tmp/x"))),
                service.removeWorkset("w"))) {
            assertFalse(r.success(), "must fail below the 1.5.0 floor");
            assertEquals(CoordinationService.STORE_WRITE_GUIDANCE, r.message(),
                    "below the floor the write short-circuits with guidance, never a CLI error");
            assertNull(r.fix());
        }
    }

    @Test
    void writeMethodsGatedWhenCliUnavailable() {
        CoordinationService service = serviceWith("1.5.0", false);
        assertFalse(service.setupStore("s", "/tmp/x").success());
        assertFalse(service.createWorkset("w", List.of()).success());
    }

    @Test
    void storeDoctorGatedBelowFloorDoesNotShellOut() {
        CoordinationService service = serviceWith("1.4.9", true);
        assertNull(service.storeDoctor("s"), "doctor must not run below the store floor");
        // Never asks the CLI for a path (which would be the first step of a shell-out).
        verify(detection, never()).getDetectedPath();
    }

    // ---- T.2: status[].fix surfaced verbatim; stderr never surfaced ---------

    @Test
    void failureMessageAndFixComeFromStatusNeverStderr() {
        // A realistic failure envelope with a fix; parseWriteEnvelope must use the status message and
        // fix, not the (here deliberately alarming) text that a stderr dump might contain.
        String json = "{\"store\":null,\"status\":[{\"severity\":\"error\","
                + "\"code\":\"store_setup_path_required\","
                + "\"message\":\"Pass --path with the folder where this store should live.\","
                + "\"target\":\"store.root\",\"fix\":\"openspec store setup s --path ~/openspec/s\"}]}";
        WriteResult r = CoordinationService.parseWriteEnvelope(false, json, "ok", false);
        assertFalse(r.success());
        assertEquals("Pass --path with the folder where this store should live.", r.message());
        assertEquals("openspec store setup s --path ~/openspec/s", r.fix());
    }

    @Test
    void failureWithEmptyStatusUsesGenericMessageNotStderr() {
        WriteResult r = CoordinationService.parseWriteEnvelope(false, "{\"status\":[]}", "ok", false);
        assertFalse(r.success());
        assertEquals("The OpenSpec CLI command did not complete.", r.message());
        assertNull(r.fix());
    }

    @Test
    void highestSeverityPicksErrorOverWarningOverInfo() {
        List<Diagnostic> diagnostics = List.of(
                new Diagnostic("info", "i", "info msg", null, null),
                new Diagnostic("warning", "w", "warn msg", null, "warn fix"),
                new Diagnostic("error", "e", "err msg", null, "err fix"));
        Diagnostic top = CoordinationService.highestSeverity(diagnostics);
        assertEquals("error", top.severity());
    }

    // ---- T.2: removeStore is destructive; unregister is not -----------------

    @Test
    void removeStoreResultIsDestructiveUnregisterIsNot() {
        WriteResult remove = CoordinationService.parseWriteEnvelope(
                true, "{\"store\":{\"id\":\"s\",\"root\":\"/fixture/s\"},\"status\":[]}",
                "Removed store.", true);
        assertTrue(remove.destructive(), "store remove deletes files — must be destructive");

        WriteResult unregister = CoordinationService.parseWriteEnvelope(
                true, "{\"store\":{\"id\":\"s\",\"root\":\"/fixture/s\"},\"status\":[]}",
                "Unregistered store.", false);
        assertFalse(unregister.destructive());
    }
}
