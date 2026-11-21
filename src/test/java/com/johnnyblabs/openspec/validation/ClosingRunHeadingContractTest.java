package com.johnnyblabs.openspec.validation;

import com.johnnyblabs.openspec.services.SpecMerge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Section headings written with a CommonMark closing {@code #} run ({@code ## Purpose ##}). Captured against the 1.16.1
 * CLI: a spec and a delta written with closing-run headings validate valid with no issues, the same as their plain
 * forms. The plugin must not be stricter than that.
 */
class ClosingRunHeadingContractTest {

    private static final String CLOSED_SPEC = """
            # Closed Specification

            ## Purpose ##

            A plain purpose that is long enough to be a real purpose statement here.

            ## Requirements ##

            ### Requirement: Thing

            The system SHALL do the thing.

            #### Scenario: Thing works
            - **WHEN** a user acts
            - **THEN** it works
            """;

    private static final String CLOSED_ADDED_DELTA = """
            ## ADDED Requirements ##

            ### Requirement: Feature

            The system SHALL do the feature.

            #### Scenario: Feature works
            - **WHEN** a user acts
            - **THEN** it works
            """;

    private static final String CLOSED_MODIFIED_DELTA = """
            ## MODIFIED Requirements ##

            ### Requirement: Thing

            The system SHALL do the thing, now with more care.

            #### Scenario: Thing works
            - **WHEN** a user acts
            - **THEN** it works
            """;

    @Test
    void aMainSpecWithClosingRunHeadingsHasNoErrors() {
        List<ValidationIssue> issues = new ArrayList<>();
        BuiltInValidator.validateSpecContent(CLOSED_SPEC, "closed/spec.md", issues);

        assertTrue(issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR),
                "the CLI validates this spec clean: " + issues);
    }

    @Test
    void aDeltaWithClosingRunSectionHeadingIsRecognized() {
        SpecMerge.DeltaSummary summary = SpecMerge.summarize(CLOSED_ADDED_DELTA);

        assertEquals(List.of("## ADDED Requirements"), summary.sections(), "the closed heading is an ADDED section");
        assertEquals(1, summary.entries(), "its requirement is an entry");
    }

    @Test
    void aDeltaWithClosingRunSectionHeadingHasNoErrors() {
        List<ValidationIssue> issues = new ArrayList<>();
        BuiltInValidator.validateDeltaContent(CLOSED_ADDED_DELTA, "feat/spec.md", issues);

        assertTrue(issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR),
                "the CLI validates this delta clean: " + issues);
    }

    @Test
    void aModifiedDeltaMergesIntoAClosingRunMainSpec() {
        SpecMerge.Result result = SpecMerge.merge("closed", "change", CLOSED_MODIFIED_DELTA, CLOSED_SPEC);

        assertFalse(result.refused(), "archive applies the change: " + result.refusal());
        assertNotNull(result.projected());
        assertTrue(result.projected().contains("now with more care"), result.projected());
    }
}
