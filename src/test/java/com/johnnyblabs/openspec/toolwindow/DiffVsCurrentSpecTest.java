package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The "Diff vs current spec" entries: one per capability for the done specs artifact, none otherwise, and wording that
 * never reads as history. The specs check runs on output paths parsed from captured CLI output, not hand-typed ones.
 */
class DiffVsCurrentSpecTest {

    private static final String CHANGE_DIR = "/p/openspec/changes/add-auth";

    private static ArtifactInfo artifact(String id, String outputPath, ArtifactStatus status) {
        return new ArtifactInfo(id, outputPath, status, List.of());
    }

    @Test
    void theDoneSpecsArtifactGivesOneEntryPerCapabilityWithItsDeltaSpecPath() {
        List<DiffVsCurrentSpec.Entry> entries = DiffVsCurrentSpec.entries(
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE), CHANGE_DIR, List.of("auth", "billing"));

        assertEquals(List.of(
                new DiffVsCurrentSpec.Entry("auth", CHANGE_DIR + "/specs/auth/spec.md"),
                new DiffVsCurrentSpec.Entry("billing", CHANGE_DIR + "/specs/billing/spec.md")), entries);
    }

    @Test
    void anotherArtifactGivesNone() {
        assertTrue(DiffVsCurrentSpec.entries(
                artifact("design", "design.md", ArtifactStatus.DONE), CHANGE_DIR, List.of("auth")).isEmpty());
    }

    @Test
    void aSpecsArtifactThatIsNotDoneGivesNone() {
        assertTrue(DiffVsCurrentSpec.entries(
                artifact("specs", "specs/**/*.md", ArtifactStatus.READY), CHANGE_DIR, List.of("auth")).isEmpty());
    }

    @Test
    void aChangeWithoutDeltaSpecsGivesNone() {
        assertTrue(DiffVsCurrentSpec.entries(
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE), CHANGE_DIR, List.of()).isEmpty());
    }

    @Test
    void noChangeDirGivesNone() {
        assertTrue(DiffVsCurrentSpec.entries(
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE), null, List.of("auth")).isEmpty());
    }

    @Test
    void theSpecsCheckMatchesTheCliStatusOutput() {
        ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture("1.7.0/status-complete.json"));

        for (ArtifactInfo a : dag.getArtifacts()) {
            List<DiffVsCurrentSpec.Entry> entries = DiffVsCurrentSpec.entries(a, CHANGE_DIR, List.of("auth"));
            assertEquals(a.id().equals("specs"), !entries.isEmpty(), a.id() + " (" + a.outputPath() + ")");
        }
    }

    @Test
    void theSpecsCheckMatchesTheCliInstructionsOutput() {
        ArtifactInstruction specs = CliOutputParser.parseArtifactInstruction(fixture("1.7.0/instructions-specs.json"));
        ArtifactInstruction proposal = CliOutputParser.parseArtifactInstruction(fixture("instructions-proposal.json"));

        assertTrue(DiffVsCurrentSpec.isSpecsOutput(specs.outputPath()), specs.outputPath());
        assertFalse(DiffVsCurrentSpec.isSpecsOutput(proposal.outputPath()), proposal.outputPath());
    }

    @Test
    void theWordingNeverReadsAsHistory() {
        for (String text : List.of(DiffVsCurrentSpec.LABEL, DiffVsCurrentSpec.tooltip("auth"))) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String word : List.of("changed", "last", "before", "previous")) {
                assertFalse(lower.contains(word), "\"" + text + "\" says " + word);
            }
        }
        assertTrue(DiffVsCurrentSpec.tooltip("auth").contains("specs/auth/spec.md"));
        assertTrue(DiffVsCurrentSpec.tooltip("auth").contains("compared with the current spec"));
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = DiffVsCurrentSpecTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
