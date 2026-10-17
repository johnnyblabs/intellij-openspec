package com.johnnyblabs.openspec.services;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link SpecMerge#summarize}: entries counted as OpenSpec's reader counts them, on the captured rename shapes. */
class DeltaSummaryTest {

    private static SpecMerge.DeltaSummary captured(String c) throws IOException {
        return SpecMerge.summarize(Files.readString(Paths.get("src/test/resources/fixtures/cli/1.14.3/validate-renamed/" + c + "/delta.md")));
    }

    @Test
    void bareRenameLinesAreNotEntries() throws IOException {
        SpecMerge.DeltaSummary s = captured("bare-only");
        assertEquals(List.of("## RENAMED Requirements"), s.sections());
        assertEquals(0, s.entries());
        assertEquals(List.of(), s.unpaired());
    }

    @Test
    void bareLinesBesideAnAddedRequirementLeaveOneEntry() throws IOException {
        SpecMerge.DeltaSummary s = captured("bare-beside-added");
        assertEquals(List.of("## ADDED Requirements", "## RENAMED Requirements"), s.sections());
        assertEquals(1, s.entries());
        assertEquals(List.of(), s.unpaired());
    }

    @Test
    void anUnpairedHeaderFormFromIsReportedWithItsLine() throws IOException {
        SpecMerge.DeltaSummary s = captured("unpaired-only");
        assertEquals(0, s.entries());
        assertEquals(List.of(new SpecMerge.Unpaired("FROM", "Works", 3)), s.unpaired());
        assertEquals(1, captured("unpaired-beside-added").unpaired().size());
    }

    @Test
    void removedBulletsAndPairedRenamesAreEntries() {
        SpecMerge.DeltaSummary s = SpecMerge.summarize("""
                ## REMOVED Requirements

                - `### Requirement: Old`

                ## RENAMED Requirements

                * FROM: `### Requirement: A`
                * TO: `### Requirement: B`
                """);
        assertEquals(2, s.entries());
        assertEquals(List.of("## REMOVED Requirements", "## RENAMED Requirements"), s.sections());
    }

    @Test
    void anEmptyRenamedSectionBesideAnEntryIsFine() {
        SpecMerge.DeltaSummary s = SpecMerge.summarize("""
                ## ADDED Requirements

                ### Requirement: New
                The system SHALL be new.

                #### Scenario: New
                - **WHEN** used
                - **THEN** new

                ## RENAMED Requirements

                <!-- FROM: `### Requirement: Old` -->
                """);
        assertEquals(1, s.entries());
        assertEquals(List.of(), s.unpaired());
    }
}
