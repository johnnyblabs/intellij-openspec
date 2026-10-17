package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The MODIFIED-target guard and the all-or-nothing write, against the real merge and {@link SpecSyncService#applySync}.
 */
class SpecSyncPostMergeTest {

    private static final String MAIN = """
            # workflow Specification

            ## Requirements

            ### Requirement: Existing
            The system SHALL do X.

            #### Scenario: X works
            - **WHEN** triggered
            - **THEN** X happens
            """;

    private static final String ADD = """
            ## ADDED Requirements

            ### Requirement: New feature
            The system SHALL add Y.

            #### Scenario: Y works
            - **WHEN** triggered
            - **THEN** Y happens
            """;

    private static final String MODIFY_MISSING = """
            ## MODIFIED Requirements

            ### Requirement: Nonexistent
            Updated.

            #### Scenario: S
            - **WHEN** x
            - **THEN** y
            """;

    @Test
    void cleanMerge_keepsExistingAndAppends() {
        SpecMerge.Result r = SpecMerge.merge("workflow", "c1", ADD, MAIN);

        assertNull(r.refusal());
        assertTrue(r.warnings().isEmpty());
        assertTrue(r.projected().indexOf("### Requirement: Existing") < r.projected().indexOf("### Requirement: New feature"));
    }

    @Test
    void modifiedUnmatched_isAnAlwaysOnRefusal() {
        SpecMerge.Result r = SpecMerge.merge("workflow", "c1", MODIFY_MISSING, MAIN);

        assertEquals("workflow MODIFIED failed for header \"### Requirement: Nonexistent\" - not found", r.refusal());
        assertNull(r.projected(), "a refused capability has no projected content");
    }

    @Test
    void applySync_refusesTheWholeChangeBeforeWritingAnything(@TempDir Path dir) throws IOException {
        Path good = dir.resolve("good/spec.md");
        Path bad = dir.resolve("bad/spec.md");
        Files.createDirectories(good.getParent());
        Files.createDirectories(bad.getParent());
        Files.writeString(good, MAIN);
        Files.writeString(bad, MAIN);

        SpecMerge.Result ok = SpecMerge.merge("good", "c1", ADD, MAIN);
        SpecMerge.Result refused = SpecMerge.merge("bad", "c1", MODIFY_MISSING, MAIN);
        // The applying capability comes first, so a per-capability write would already have changed it.
        List<SpecSyncResult> results = List.of(
                new SpecSyncResult("good", good.toString(), MAIN, ok.projected(), ok.operations(), ok.warnings(), List.of()),
                new SpecSyncResult("bad", bad.toString(), MAIN, MAIN, refused.operations(), refused.warnings(), List.of(refused.refusal())));

        SpecSyncService service = new SpecSyncService(Mockito.mock(Project.class));
        IOException e = assertThrows(IOException.class, () -> service.applySync(results));

        assertTrue(e.getMessage().contains(refused.refusal()), e.getMessage());
        assertEquals(MAIN, Files.readString(good), "no capability is written when any is refused");
        assertEquals(MAIN, Files.readString(bad));
    }
}
