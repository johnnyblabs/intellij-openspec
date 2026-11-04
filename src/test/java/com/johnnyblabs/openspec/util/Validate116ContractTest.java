package com.johnnyblabs.openspec.util;

import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two validate behaviours CLI 1.16 added, read through the plugin's validate-output parser from real 1.16.0
 * captures ({@code fixtures/cli/1.16.0/validate-change/} and {@code 1.16.0/errors/}). Only the parse is pinned here:
 * how the Validate action routes a configuration error is separate work.
 */
class Validate116ContractTest {

    /** The captured {@code validate <change> --type change --json} run for {@code name}. */
    private static CliRunner.CliResult validateChange(String name) {
        String base = "1.16.0/validate-change/" + name;
        return new CliRunner.CliResult(Integer.parseInt(CliFixtures.text(base + ".exit").strip()),
                CliFixtures.text(base + ".json"), "");
    }

    /** The scenario-step warning's message, read from the capture rather than retyped. */
    private static String capturedStepWarning(CliRunner.CliResult run) {
        return JsonParser.parseString(run.stdout()).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject()
                .getAsJsonArray("issues").get(0).getAsJsonObject().get("message").getAsString();
    }

    @Test
    void aChangeWhoseOnlyIssueIsAMissingStepPassesInDefaultMode() {
        CliRunner.CliResult run = validateChange("step-warning-change");
        assertEquals(0, run.exitCode(), "capture precondition: the CLI exits 0");
        assertTrue(capturedStepWarning(run).contains("missing a THEN step"), "capture precondition: the step warning");

        ValidationResult result = CliOutputParser.parseJsonOutput(run);

        assertTrue(result.passed(), "a WARNING on a valid change is not a failure");
    }

    @Test
    void underStrictTheSameChangeFailsWithTheStepWarning() {
        CliRunner.CliResult run = validateChange("step-warning-change-strict");
        assertEquals(1, run.exitCode(), "capture precondition: the CLI exits 1");

        ValidationResult result = CliOutputParser.parseJsonOutput(run);

        assertFalse(result.passed(), "the verdict comes from the item's valid field, not from the absence of ERRORs");
        ValidationIssue issue = assertInstanceOf(ValidationIssue.class,
                result.issues().stream().filter(i -> i.message().equals(capturedStepWarning(run))).findFirst().orElse(null),
                "the strict failure carries the CLI's step warning: " + result.issues());
        assertEquals(ValidationIssue.Severity.WARNING, issue.severity());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "validate-unparseable-config, unparseable_config",
            "validate-unreadable-config, unreadable_config",
    })
    void aBrokenConfigIsAnErrorResponseNeverAPass(String fixture, String code) {
        CliRunner.CliResult result = CliFixtures.error("1.16.0", fixture);
        assertEquals(1, result.exitCode(), "capture precondition: the CLI exits 1");

        CliJson.Failed failed = assertInstanceOf(CliJson.Failed.class, CliJson.readReport(result));
        assertEquals(CliJson.Kind.ENVELOPE, failed.kind());
        assertEquals(code, failed.code());
        assertNotNull(failed.fix(), "the CLI says how to fix it");
        assertTrue(result.stdout().contains(failed.fix()), "fix comes from the capture");

        assertThrows(CliOutputParser.CliErrorResponseException.class, () -> CliOutputParser.parseJsonOutput(result));
    }
}
