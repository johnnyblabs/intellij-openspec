package com.johnnyblabs.openspec.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CliJson#read} against real CLI 1.14.3 failure output ({@code fixtures/cli/1.14.3/errors/}).
 * Expected codes are literals; messages and fixes are checked to come from the capture, never recomputed.
 */
class CliJsonReadContractTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "validate-noroot, no_openspec_root",
            "list-noroot, no_openspec_root",
            "validate-noroot-withstores, no_root_with_registered_stores",
            "list-noroot-withstores, no_root_with_registered_stores",
            "validate-unknown, unknown_item",
            "show-error, show_error",
            "status-unknown, change_error",
            "status-badyaml, change_error",
            "instructions-unknown-artifact, change_error",
            "archive-unknown, archive_change_not_found",
            "doctor-store-missing-root, unhealthy_store_root",
    })
    void everyCapturedEnvelopeIsAFailureWithTheCliReason(String fixture, String code) {
        CliRunner.CliResult result = CliFixtures.error(fixture);
        assertEquals(1, result.exitCode(), "capture precondition: the CLI exits 1");

        CliJson.Failed failed = assertInstanceOf(CliJson.Failed.class, CliJson.read(result));
        assertEquals(CliJson.Kind.ENVELOPE, failed.kind());
        assertEquals(code, failed.code());
        assertFalse(failed.message().isBlank());
        assertTrue(result.stdout().contains(jsonEscaped(failed.message())), "message comes from the capture");

        // The same answer through the report reader: an envelope is a failure whatever the command.
        assertInstanceOf(CliJson.Failed.class, CliJson.readReport(result));
    }

    @Test
    void fixIsCarriedWhenTheCliGivesOne() {
        CliRunner.CliResult result = CliFixtures.error("validate-noroot-withstores");
        assertTrue(result.stdout().contains("\"fix\""), "capture precondition: the envelope has a fix");
        CliJson.Failed failed = (CliJson.Failed) CliJson.read(result);
        assertNotNull(failed.fix());
        assertTrue(result.stdout().contains(failed.fix()));
        assertTrue(failed.describe().endsWith(failed.fix()));
    }

    @Test
    void textBeforeTheJsonReadsTheSame() {
        CliRunner.CliResult plain = CliFixtures.error("status-unknown");
        CliRunner.CliResult prefixed = new CliRunner.CliResult(plain.exitCode(),
                "[WARN] something the shell printed\n" + plain.stdout(), plain.stderr());
        assertEquals(CliJson.read(plain), CliJson.read(prefixed));
    }

    @Test
    void truncatedJsonIsUnreadable() {
        CliRunner.CliResult whole = CliFixtures.error("validate-noroot");
        CliJson.Failed failed = assertInstanceOf(CliJson.Failed.class, CliJson.read(
                new CliRunner.CliResult(0, CliFixtures.truncated(whole.stdout()), "")));
        assertEquals(CliJson.Kind.UNREADABLE, failed.kind());
    }

    @Test
    void textOnlyFailureUsesStderrAsTheReason() {
        CliRunner.CliResult result = CliFixtures.error("update-fail");
        assertTrue(result.stdout().isEmpty(), "capture precondition: no stdout");
        CliJson.Failed failed = assertInstanceOf(CliJson.Failed.class, CliJson.read(result));
        assertEquals(CliJson.Kind.UNREADABLE, failed.kind());
        assertEquals(result.stderr().trim(), failed.message());
    }

    @Test
    void validateReportWithExitOneIsAnAnswer() {
        // Real validate --all --json over the parity corpus: some items invalid, so the CLI exits 1 with a report.
        String report = CliFixtures.text("1.14.3/validate-parity-corpus.json");
        assertTrue(report.contains("\"valid\": false"), "capture precondition: the report has a failing item");
        CliRunner.CliResult result = new CliRunner.CliResult(1, report, "");

        CliJson.Ok ok = assertInstanceOf(CliJson.Ok.class, CliJson.readReport(result));
        assertTrue(ok.object().has("items"));
        // A command that may not exit non-zero with a normal answer reads the same output as a failure.
        assertInstanceOf(CliJson.Failed.class, CliJson.read(result));
    }

    @Test
    void onlyErrorSeverityMakesAnEnvelope() {
        // Derived from the capture (severity mutated): a warning-only status is an answer, not a failure.
        CliRunner.CliResult captured = CliFixtures.error("validate-noroot");
        com.google.gson.JsonObject raw = com.google.gson.JsonParser.parseString(captured.stdout()).getAsJsonObject();
        raw.getAsJsonArray("status").get(0).getAsJsonObject().addProperty("severity", "warning");
        assertInstanceOf(CliJson.Ok.class, CliJson.read(new CliRunner.CliResult(0, raw.toString(), "")));
    }

    @Test
    void nestedErrorsUnderAnEmptyTopLevelStatusAreNotAnEnvelope() {
        // Real 1.14.3 store-doctor output: exit 0, top-level status empty, errors only nested per store.
        CliRunner.CliResult result = CliFixtures.error("store-doctor-missing-root");
        assertEquals(0, result.exitCode());
        assertInstanceOf(CliJson.Ok.class, CliJson.read(result));
    }

    @Test
    void successfulPayloadReadsOk() {
        CliRunner.CliResult result = CliFixtures.error("config-list-default");
        CliJson.Ok ok = assertInstanceOf(CliJson.Ok.class, CliJson.read(result));
        assertEquals("core", ok.object().get("profile").getAsString());
    }

    private static String jsonEscaped(String message) {
        return message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
