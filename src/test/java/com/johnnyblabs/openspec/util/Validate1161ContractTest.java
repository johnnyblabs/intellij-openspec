package com.johnnyblabs.openspec.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two validate behaviours CLI 1.16.1 changed, read through the plugin's validate-output parser from a real 1.16.1
 * capture ({@code fixtures/cli/1.16.1/behaviours/validate-all.json}). The project holds a closing-run spec with its plain
 * twin, and a clean delta with a delta that puts a requirement under {@code ## DEPRECATED Requirements}. Only the parse
 * is pinned here; how the plugin reads each behaviour is a separate change.
 */
class Validate1161ContractTest {

    private static final String CAPTURE = "1.16.1/behaviours/validate-all.json";

    /** The whole captured {@code validate --all --json} run. The CLI exits 1 because one item fails. */
    private static CliRunner.CliResult captured() {
        return new CliRunner.CliResult(1, CliFixtures.text(CAPTURE), "");
    }

    /** The captured item with this id, read from the capture rather than retyped. */
    private static JsonObject item(String id) {
        JsonArray items = JsonParser.parseString(CliFixtures.text(CAPTURE)).getAsJsonObject().getAsJsonArray("items");
        for (var element : items) {
            JsonObject item = element.getAsJsonObject();
            if (item.get("id").getAsString().equals(id)) return item;
        }
        throw new AssertionError("capture has no item " + id);
    }

    @Test
    void aClosingRunHeadingSpecValidatesLikeItsPlainTwin() {
        JsonObject closed = item("closed");
        JsonObject plain = item("plain");

        assertTrue(closed.get("valid").getAsBoolean(), "a spec with closing-run headings is valid");
        assertTrue(plain.get("valid").getAsBoolean(), "its plain twin is valid");
        assertEquals(plain.getAsJsonArray("issues").size(), closed.getAsJsonArray("issues").size(),
                "closing-run headings add no issues over the plain form");
    }

    @Test
    void aRequirementUnderAnUnrecognizedDeltaSectionFailsValidate() {
        JsonObject depr = item("depr");
        assertFalse(depr.get("valid").getAsBoolean(), "a requirement under DEPRECATED Requirements fails validate");

        ValidationResult result = CliOutputParser.parseJsonOutput(captured());
        assertFalse(result.passed(), "the run fails when that change is in it");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR
                && issue.message().contains("DEPRECATED Requirements")), result.issues().toString());
    }

    @Test
    void theCleanTwinOfTheUnrecognizedSectionChangePasses() {
        assertTrue(item("clean").get("valid").getAsBoolean(),
                "the same delta without the DEPRECATED section is valid");
    }
}
