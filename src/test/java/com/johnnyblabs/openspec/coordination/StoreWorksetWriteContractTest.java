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
 * Contract tests that parse <b>real</b> OpenSpec write/doctor JSON captured as fixtures under
 * {@code src/test/resources/fixtures/cli/<cli-version>/} ({@code 1.5.0/} and {@code 1.6.0/}). They
 * pin the write-envelope parser ({@link CoordinationService#parseWriteEnvelope}) and the uniform
 * {@code status[]} handling against the actual CLI output shape (field names, nesting) — never a
 * hand-authored approximation. Register outcome semantics differ per CLI generation (1.5 refuses a
 * root without planning directories; 1.6 registers it), so register coverage is organized per
 * generation and both fixture sets are kept while both generations are supported.
 *
 * <p>Capture recipe, 1.5.0 set: under an isolated {@code XDG_DATA_HOME}/{@code HOME} with
 * {@code OPENSPEC_TELEMETRY=0}, in a scratch temp dir, run {@code store setup <id> --path <p>
 * --json} (success and, with {@code --path} omitted, the {@code store_setup_path_required} error),
 * {@code store register}/{@code unregister}/{@code remove --yes} (and the confirmation-required
 * error without {@code --yes}), {@code workset create}/{@code remove --yes}, and
 * {@code doctor --store <id> --json} (healthy and broken). Absolute paths are sanitized to
 * {@code /fixture/...}. Re-capture and fix failures if the CLI output format changes.
 *
 * <p>Capture recipe, 1.6.0 register set: same isolation; {@code store register <root> --yes --json}
 * on a config-only root ({@code openspec/config.yaml} only, no specs/changes/archive dirs) for the
 * fresh-root success; the same command without {@code --yes} for
 * {@code store_register_identity_confirmation_required}; a root whose config declares
 * {@code store: <id>} for {@code store_root_pointer_declared}; and a root whose {@code store:}
 * value is not a single id string for {@code invalid_store_pointer}.
 */
class StoreWorksetWriteContractTest {

    private static String fixture(String name) {
        return fixtureAt("1.5.0", name);
    }

    private static String fixture16(String name) {
        return fixtureAt("1.6.0", name);
    }

    private static String fixtureAt(String cliVersion, String name) {
        String path = "/fixtures/cli/" + cliVersion + "/" + name;
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

    // 1.5-generation behavior ONLY: a root without planning directories is refused at register.
    // CLI 1.6+ registers the same root successfully (see the 1.6 register tests below) and no
    // longer emits this code, so this pins the 1.5 generation, not general plugin behavior.
    @Test
    void parsesRealStoreRegisterUnhealthyErrorOn15Generation() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                false, fixture("store-register-unhealthy.json"), "ok", false);
        assertFalse(r.success());
        assertEquals("store_register_root_unhealthy", r.diagnostics().get(0).code());
        assertNotNull(r.fix());
    }

    // ---- store register, 1.6 generation --------------------------------------

    @Test
    void parsesReal16StoreRegisterFreshRootSuccess() {
        // A config-only root (no specs/changes/archive dirs) registers successfully on 1.6+.
        WriteResult r = CoordinationService.parseWriteEnvelope(
                true, fixture16("store-register-fresh-root.json"), "ok", false);
        assertTrue(r.success());
        assertEquals("/fixture/fresh-root", r.createdPath());
        // 1.6 register creates store metadata on a never-registered root.
        assertTrue(r.createdFiles().contains(".openspec-store/store.yaml"));
        assertTrue(r.diagnostics().isEmpty(), "a fresh-root register is clean — no diagnostics");
        assertNull(r.fix());
    }

    @Test
    void parsesReal16StoreRegisterPointerDeclaredRefusal() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                false, fixture16("store-register-pointer-declared.json"), "ok", false);
        assertFalse(r.success());
        Diagnostic d = r.diagnostics().get(0);
        assertEquals("store_root_pointer_declared", d.code());
        assertEquals("store.pointer", d.target());
        // The CLI's ready-made remediation is surfaced verbatim — never stderr.
        assertNotNull(r.fix());
        assertTrue(r.fix().contains("store:"), "the fix must be the CLI's own remediation text");
        assertTrue(r.message().contains("externalized"), "the message must be the parsed status[] message");
    }

    @Test
    void parsesReal16StoreRegisterInvalidPointerRefusal() {
        WriteResult r = CoordinationService.parseWriteEnvelope(
                false, fixture16("store-register-invalid-pointer.json"), "ok", false);
        assertFalse(r.success());
        Diagnostic d = r.diagnostics().get(0);
        assertEquals("invalid_store_pointer", d.code());
        assertEquals("store.pointer", d.target());
        assertNotNull(r.fix());
    }

    @Test
    void parsesReal16StoreRegisterIdentityConfirmationRequired() {
        // Registering a never-a-store root without --yes asks for identity confirmation on 1.6 —
        // same confirmation-envelope pattern as store remove, surfaced with its fix verbatim.
        WriteResult r = CoordinationService.parseWriteEnvelope(
                false, fixture16("store-register-confirmation-required.json"), "ok", false);
        assertFalse(r.success());
        Diagnostic d = r.diagnostics().get(0);
        assertEquals("store_register_identity_confirmation_required", d.code());
        assertNotNull(r.fix());
        assertTrue(r.fix().contains("--yes"));
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
