package com.johnnyblabs.openspec.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.SpecMerge;
import com.johnnyblabs.openspec.util.CliFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A requirement under a section OpenSpec does not read as a delta section. The captured CLI 1.16.1 run
 * ({@code fixtures/cli/1.16.1/behaviours/validate-all.json}) holds the verdict for the {@code depr} change, and
 * {@code deprecated-section/spec.md} is the delta it was run on. The plugin must report the same error at the same line,
 * with the same wording, and must refuse the archive as the CLI does.
 */
class UnrecognizedDeltaSectionContractTest {

    private static final String CAPTURE = "1.16.1/behaviours/validate-all.json";
    private static final String DELTA = "1.16.1/behaviours/deprecated-section/spec.md";

    /** The captured CLI issues for the {@code depr} change. */
    private static JsonArray capturedIssues() {
        JsonArray items = JsonParser.parseString(CliFixtures.text(CAPTURE)).getAsJsonObject().getAsJsonArray("items");
        for (var element : items) {
            JsonObject item = element.getAsJsonObject();
            if (item.get("id").getAsString().equals("depr")) return item.getAsJsonArray("issues");
        }
        throw new AssertionError("capture has no item depr");
    }

    /** The captured ERROR issue, read from the capture rather than retyped. */
    private static JsonObject capturedError() {
        for (var element : capturedIssues()) {
            JsonObject issue = element.getAsJsonObject();
            if (issue.get("level").getAsString().equals("ERROR")) return issue;
        }
        throw new AssertionError("capture has no ERROR for depr");
    }

    @Test
    void theCapturedDeltaHoldsOneOrphanAtTheCapturedLine() {
        List<SpecMerge.Orphan> orphans = SpecMerge.orphans(CliFixtures.text(DELTA));

        assertEquals(1, orphans.size(), orphans.toString());
        SpecMerge.Orphan orphan = orphans.get(0);
        assertEquals("Deprecated Thing", orphan.name());
        assertEquals("DEPRECATED Requirements", orphan.section());
        assertEquals(capturedError().get("line").getAsInt(), orphan.line(), "the captured error line");
        assertTrue(orphan.fails(), "a DEPRECATED Requirements section fails, as the CLI does");
    }

    @Test
    void theValidatorReportsTheCapturedErrorWordForWord() {
        List<ValidationIssue> issues = new java.util.ArrayList<>();
        BuiltInValidator.validateDeltaContent(CliFixtures.text(DELTA), "feat/spec.md", issues);

        ValidationIssue error = issues.stream()
                .filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR)
                .filter(issue -> issue.rule().equals("delta-requirement-unrecognized-section"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no unrecognized-section error: " + issues));
        JsonObject captured = capturedError();
        assertEquals(captured.get("line").getAsInt(), error.line(), "the captured error line");
        assertEquals(captured.get("message").getAsString(), error.message(), "the captured error message");
    }

    @Test
    void theOrphanRequirementIsTheOnlyErrorInTheCapturedDelta() {
        // The CLI also warns that archive does not carry the section. The plugin reports the requirement itself, which
        // gives the same verdict; the section-level note is not mirrored.
        List<ValidationIssue> issues = new java.util.ArrayList<>();
        BuiltInValidator.validateDeltaContent(CliFixtures.text(DELTA), "feat/spec.md", issues);
        assertTrue(issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR
                && !issue.rule().equals("delta-requirement-unrecognized-section")), issues.toString());
    }

    @Test
    void theArchiveRefusesTheCapturedDeltaWithTheCapturedMessage() {
        SpecMerge.Result result = SpecMerge.merge("feat", "depr", CliFixtures.text(DELTA), null);

        assertTrue(result.refused(), "archive refuses a requirement under DEPRECATED Requirements");
        assertTrue(result.refusal().endsWith(capturedError().get("message").getAsString()),
                "the refusal carries the CLI's wording: " + result.refusal());
        assertNull(result.projected(), "no spec is written for a refused change");
    }

    @Test
    void aRequirementUnderProseWarnsButDoesNotRefuse() {
        String delta = """
                ## ADDED Requirements

                ### Requirement: Feature

                The system SHALL do the feature.

                #### Scenario: Feature works
                - **WHEN** a user acts
                - **THEN** it works

                ## Notes

                ### Requirement: Noted thing

                The system SHALL do the noted thing.

                #### Scenario: Noted
                - **WHEN** a user acts
                - **THEN** it works
                """;

        List<SpecMerge.Orphan> orphans = SpecMerge.orphans(delta);
        assertEquals(1, orphans.size());
        assertFalse(orphans.get(0).fails(), "a requirement under prose is a warning");

        List<ValidationIssue> issues = new java.util.ArrayList<>();
        BuiltInValidator.validateDeltaContent(delta, "feat/spec.md", issues);
        assertTrue(issues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.WARNING
                && issue.rule().equals("delta-requirement-orphan")), issues.toString());
        assertFalse(issues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR), issues.toString());

        assertFalse(SpecMerge.merge("feat", "change", delta, null).refused(), "archive does not refuse on prose");
    }

    @Test
    void aBareRequirementsHeadingFailsLikeTheDeprecatedSection() {
        String delta = """
                ## ADDED Requirements

                ### Requirement: Feature

                The system SHALL do the feature.

                #### Scenario: Feature works
                - **WHEN** a user acts
                - **THEN** it works

                ## Requirements

                ### Requirement: Plain heading

                The system SHALL do the other thing.

                #### Scenario: Other
                - **WHEN** a user acts
                - **THEN** it works
                """;

        List<SpecMerge.Orphan> orphans = SpecMerge.orphans(delta);
        assertEquals(1, orphans.size());
        assertTrue(orphans.get(0).fails(), "the CLI reports a bare Requirements heading as an error");
        assertTrue(SpecMerge.merge("feat", "change", delta, null).refused());
    }

    @Test
    void aCleanDeltaHasNoOrphans() {
        String delta = """
                ## ADDED Requirements

                ### Requirement: Feature

                The system SHALL do the feature.

                #### Scenario: Feature works
                - **WHEN** a user acts
                - **THEN** it works
                """;

        assertTrue(SpecMerge.orphans(delta).isEmpty());
        assertFalse(SpecMerge.merge("feat", "change", delta, null).refused());
    }
}
