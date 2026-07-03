package com.johnnyblabs.openspec.coordination;

import com.johnnyblabs.openspec.coordination.CoordinationService.WriteResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests that parse <b>real</b> OpenSpec 1.5.0 write/doctor JSON captured as fixtures under
 * {@code src/test/resources/fixtures/cli/1.5.0/}. They pin the write-envelope parser
 * ({@link CoordinationService#parseWriteEnvelope}) and the uniform {@code status[]} handling against
 * the actual CLI output shape (field names, nesting) — never a hand-authored approximation.
 *
 * <p>Capture recipe (see the change's task 1): under an isolated {@code XDG_DATA_HOME}/{@code HOME}
 * with {@code OPENSPEC_TELEMETRY=0}, in a scratch temp dir, run {@code store setup <id> --path <p>
 * --json} (success and, with {@code --path} omitted, the {@code store_setup_path_required} error),
 * {@code store register}/{@code unregister}/{@code remove --yes} (and the confirmation-required
 * error without {@code --yes}), {@code workset create}/{@code remove --yes}, and
 * {@code doctor --store <id> --json} (healthy and broken). Absolute paths are sanitized to
 * {@code /fixture/...}. Re-capture and fix failures if the CLI output format changes.
 */
class StoreWorksetWriteContractTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.5.0/" + name;
        try (InputStream is = StoreWorksetWriteContractTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    // ---- store setup ---------------------------------------------------------

    @Test
    void parsesRealStoreSetupSuccessFromExactNestedKeys() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                true, fixture("store-setup.json"), "ok", false);
        assertTrue(r.success());
        // store.root — the exact nested key the CLI emits.
        assertEquals("/fixture/store-a", r.createdPath());
        // created_files[] retained verbatim.
        assertTrue(r.createdFiles().contains("openspec/config.yaml"),
                "the created_files array must be read from its exact key");
        assertTrue(r.createdFiles().contains(".openspec-store/store.yaml"));
        assertTrue(r.diagnostics().isEmpty(), "a clean setup has an empty status[] envelope");
        assertNull(r.fix());
    }

    @Test
    void parsesRealStoreSetupPathRequiredErrorAsFieldMessageAndFix() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                false, fixture("store-setup-path-required.json"), "ok", false);
        assertFalse(r.success());
        // Message and fix come from the parsed status[] — never stderr.
        assertEquals("Pass --path with the folder where this store should live.", r.message());
        assertNotNull(r.fix());
        assertTrue(r.fix().contains("--path"), "the ready-made fix must be surfaced verbatim");
        assertNull(r.createdPath());
        // The recognizable machine code proves the status[] path is exercised.
        Diagnostic d = r.diagnostics().get(0);
        assertEquals("store_setup_path_required", d.code());
        assertEquals("store.root", d.target());
    }

    // ---- store register ------------------------------------------------------

    @Test
    void parsesRealStoreRegisterSuccess() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                true, fixture("store-register.json"), "ok", false);
        assertTrue(r.success());
        assertEquals("/fixture/store-d", r.createdPath());
    }

    @Test
    void parsesRealStoreRegisterUnhealthyError() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                false, fixture("store-register-unhealthy.json"), "ok", false);
        assertFalse(r.success());
        assertEquals("store_register_root_unhealthy", r.diagnostics().get(0).code());
        assertNotNull(r.fix());
    }

    // ---- store unregister / remove ------------------------------------------

    @Test
    void parsesRealStoreUnregisterSuccessNotDestructive() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                true, fixture("store-unregister.json"), "ok", false);
        assertTrue(r.success());
        assertFalse(r.destructive(), "unregister keeps files — not destructive");
    }

    @Test
    void parsesRealStoreRemoveSuccessFlaggedDestructive() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                true, fixture("store-remove.json"), "Removed store.", true);
        assertTrue(r.success());
        assertTrue(r.destructive(), "store remove deletes local files — must be flagged destructive");
    }

    @Test
    void parsesRealStoreRemoveConfirmationRequiredError() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                false, fixture("store-remove-confirmation-required.json"), "ok", true);
        assertFalse(r.success());
        assertEquals("store_remove_confirmation_required", r.diagnostics().get(0).code());
        // The fix instructs passing --yes; the plugin always passes it after its own confirmation.
        assertTrue(r.fix().contains("--yes"));
    }

    // ---- workset create / remove --------------------------------------------

    @Test
    void parsesRealWorksetCreateSuccess() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                true, fixture("workset-create.json"), "Created workset.", false);
        assertTrue(r.success());
        assertTrue(r.diagnostics().isEmpty());
        // workset create has no "store" root — createdPath stays null.
        assertNull(r.createdPath());
    }

    @Test
    void parsesRealWorksetRemoveSuccessAndConfirmationRequired() {
        WriteResult ok = CoordinationService.parseWriteEnvelope(
                true, fixture("workset-remove.json"), "Removed workset.", false);
        assertTrue(ok.success());

        WriteResult err = CoordinationService.parseWriteEnvelope(
                false, fixture("workset-remove-confirmation-required.json"), "ok", false);
        assertFalse(err.success());
        assertEquals("workset_remove_confirmation_required", err.diagnostics().get(0).code());
        assertTrue(err.fix().contains("--yes"));
    }

    // ---- doctor --store ------------------------------------------------------

    @Test
    void parsesRealDoctorStoreHealthyAsNoActionableDiagnostic() {
        // The healthy doctor output has an empty top-level status[] — nothing for the health strip.
        List<Diagnostic> diagnostics = CoordinationService.parseDiagnostics(
                com.google.gson.JsonParser.parseString(fixture("doctor-store.json")).getAsJsonObject());
        assertTrue(diagnostics.isEmpty());
        assertNull(CoordinationService.highestSeverity(diagnostics));
    }

    @Test
    void parsesRealDoctorStoreIssueHighestSeverityWithFix() {
        List<Diagnostic> diagnostics = CoordinationService.parseDiagnostics(
                com.google.gson.JsonParser.parseString(fixture("doctor-store-issue.json")).getAsJsonObject());
        assertEquals(1, diagnostics.size());
        Diagnostic top = CoordinationService.highestSeverity(diagnostics);
        assertNotNull(top);
        assertEquals("error", top.severity());
        assertEquals("unhealthy_store_root", top.code());
        assertNotNull(top.fix(), "the doctor fix must be retained verbatim");
    }
}
