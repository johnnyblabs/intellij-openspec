package com.johnnyblabs.openspec.coordination;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.coordination.CoordinationService.WriteResult;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliFixtures;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
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

    // ---- Store Doctor: a failed check is never "healthy" -----------------------

    private Diagnostic doctorAnswering(CliRunner.CliResult answer) {
        CoordinationService service = serviceWith("1.14.3", true);
        try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
            cli.when(() -> CliRunner.run(eq(project), eq("doctor"), eq("--store"), eq("storea"), eq("--json")))
                    .thenReturn(answer);
            return service.storeDoctor("storea");
        }
    }

    @Test
    void storeDoctorReportsTheCapturedRootEnvelopeWithItsFix() {
        // Regression lock: real 1.14.3 doctor --store for a store whose root was removed (exit 1, top-level error).
        Diagnostic top = doctorAnswering(CliFixtures.error("doctor-store-missing-root"));
        assertNotNull(top);
        assertEquals("unhealthy_store_root", top.code());
        assertNotNull(top.fix());
        assertFalse(top.isUnknown());
    }

    @Test
    void storeDoctorTimeoutIsUnknownNotHealthy() {
        CoordinationService service = serviceWith("1.14.3", true);
        try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
            cli.when(() -> CliRunner.run(eq(project), eq("doctor"), eq("--store"), eq("storea"), eq("--json")))
                    .thenThrow(new CliRunner.CliException("OpenSpec CLI command timed out after 30s"));
            Diagnostic top = service.storeDoctor("storea");
            assertNotNull(top, "a timed-out check used to return null, which the action shows as healthy");
            assertTrue(top.isUnknown());
        }
    }

    @Test
    void storeDoctorFailingWithoutAnEnvelopeIsUnknownNotHealthy() {
        // Derived from the capture (its status emptied): a non-zero exit whose JSON gives no reason.
        com.google.gson.JsonObject raw = com.google.gson.JsonParser.parseString(
                CliFixtures.error("doctor-store-missing-root").stdout()).getAsJsonObject();
        raw.add("status", new com.google.gson.JsonArray());
        Diagnostic top = doctorAnswering(new CliRunner.CliResult(1, raw.toString(), ""));
        assertNotNull(top);
        assertTrue(top.isUnknown());
    }

    @Test
    void storeDoctorUnreadableOutputIsUnknownNotHealthy() {
        String captured = CliFixtures.error("doctor-store-missing-root").stdout();
        Diagnostic top = doctorAnswering(new CliRunner.CliResult(0, CliFixtures.truncated(captured), ""));
        assertNotNull(top, "unreadable output used to return null, which the action shows as healthy");
        assertTrue(top.isUnknown());
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

    // ---- register probe/retry argv guards (CLI 1.10, confirm-then-`--yes`) ---
    //
    // These are the regression backstops for the dead-end this change fixes: the probe MUST NOT carry
    // `--yes` (else a healthy not-yet-a-store root is silently registered without confirmation) and the
    // retry MUST carry it (else the confirmed register never creates identity metadata). Real 1.10.0
    // captures back the parse assertions so the guards are not vacuous.

    private static String fixture110(String name) {
        String path = "/fixtures/cli/1.10.0/" + name;
        try (InputStream is = StoreWorksetWriteServiceTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    @Test
    void registerStoreProbeInvokesNoYesArgvAndReportsConfirmationGate() {
        CoordinationService service = serviceWith("1.10.0", true);
        String path = "/fixture/healthy-root";
        // A refusal exit (non-zero) carrying the confirmation-required envelope on stdout.
        CliRunner.CliResult probeResult = new CliRunner.CliResult(
                1, fixture110("store-register-confirmation-required.json"), "");

        // Benign default for ANY unmatched CliRunner.run(...) so a wrong argv (e.g. a probe that
        // gains `--yes`, or a retry that loses it) surfaces as a clean verify/assertion failure
        // rather than an NPE on a null result dereferenced before the verify runs.
        try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class,
                invocation -> new CliRunner.CliResult(1, "{\"status\":[]}", ""))) {
            cli.when(() -> CliRunner.run(eq(project), eq("store"), eq("register"), eq(path), eq("--json")))
                    .thenReturn(probeResult);

            WriteResult r = service.registerStore(path);

            // The probe reaches the CLI with the exact no-`--yes` argv...
            cli.verify(() -> CliRunner.run(eq(project), eq("store"), eq("register"), eq(path), eq("--json")));
            // ...and NEVER the `--yes` variant.
            cli.verify(() -> CliRunner.run(eq(project), eq("store"), eq("register"), eq(path),
                    eq("--yes"), eq("--json")), never());
            // The parsed confirmation gate is surfaced (drives the panel's confirm dialog).
            assertFalse(r.success());
            assertTrue(r.identityConfirmationRequired(),
                    "the no-`--yes` probe on a healthy not-yet-a-store root reports the identity gate");
        }
    }

    @Test
    void registerStoreConfirmRetryInvokesYesArgvAndCreatesIdentity() {
        CoordinationService service = serviceWith("1.10.0", true);
        String path = "/fixture/healthy-root";
        CliRunner.CliResult yesResult = new CliRunner.CliResult(
                0, fixture110("store-register-yes-success.json"), "");

        // Benign default for ANY unmatched CliRunner.run(...) so a wrong argv (e.g. a probe that
        // gains `--yes`, or a retry that loses it) surfaces as a clean verify/assertion failure
        // rather than an NPE on a null result dereferenced before the verify runs.
        try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class,
                invocation -> new CliRunner.CliResult(1, "{\"status\":[]}", ""))) {
            cli.when(() -> CliRunner.run(eq(project), eq("store"), eq("register"), eq(path),
                    eq("--yes"), eq("--json"))).thenReturn(yesResult);

            WriteResult r = service.registerStore(path, true);

            cli.verify(() -> CliRunner.run(eq(project), eq("store"), eq("register"), eq(path),
                    eq("--yes"), eq("--json")));
            assertTrue(r.success(), "the `--yes` retry registers the root");
            assertTrue(r.createdFiles().contains(".openspec-store/store.yaml"),
                    "the `--yes` retry creates the store-identity metadata");
        }
    }

    @Test
    void registerStoreFalseOverloadProducesIdenticalNoYesArgvAsProbe() {
        // Delegation guard: registerStore(path) and registerStore(path, false) must issue the SAME
        // no-`--yes` argv, and neither the `--yes` variant.
        CoordinationService service = serviceWith("1.10.0", true);
        String path = "/fixture/healthy-root";
        CliRunner.CliResult probeResult = new CliRunner.CliResult(
                1, fixture110("store-register-confirmation-required.json"), "");

        // Benign default for ANY unmatched CliRunner.run(...) so a wrong argv (e.g. a probe that
        // gains `--yes`, or a retry that loses it) surfaces as a clean verify/assertion failure
        // rather than an NPE on a null result dereferenced before the verify runs.
        try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class,
                invocation -> new CliRunner.CliResult(1, "{\"status\":[]}", ""))) {
            cli.when(() -> CliRunner.run(eq(project), eq("store"), eq("register"), eq(path), eq("--json")))
                    .thenReturn(probeResult);

            service.registerStore(path);
            service.registerStore(path, false);

            cli.verify(() -> CliRunner.run(eq(project), eq("store"), eq("register"), eq(path), eq("--json")),
                    times(2));
            cli.verify(() -> CliRunner.run(eq(project), eq("store"), eq("register"), eq(path),
                    eq("--yes"), eq("--json")), never());
        }
    }
}
