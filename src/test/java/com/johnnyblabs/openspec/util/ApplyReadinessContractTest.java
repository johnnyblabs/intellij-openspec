package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Apply is ready when every artifact in the CLI's {@code applyRequires} is done, not when the whole change is complete.
 * Checked against captured {@code openspec status --json} output: {@code status.json} has {@code applyRequires: [tasks]}
 * with tasks done and the design and specs still ready, so the CLI treats apply as ready while the change is not complete.
 */
class ApplyReadinessContractTest {

    @Test
    void applyIsReadyOnceTheRequiredArtifactIsDoneEvenIfTheChangeIsNotComplete() {
        ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(CliFixtures.text("status.json"));

        assertEquals(java.util.List.of("tasks"), dag.getApplyRequires(), "the captured apply gate");
        assertFalse(dag.isComplete(), "the captured change is not complete");
        assertTrue(dag.isApplyReady(), "tasks is done, so apply is ready");
    }

    @Test
    void applyIsNotReadyWhileARequiredArtifactIsStillBlocked() {
        ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(CliFixtures.text("status-with-context.json"));

        assertEquals(java.util.List.of("tasks"), dag.getApplyRequires());
        assertFalse(dag.isApplyReady(), "tasks is blocked, so apply is not ready");
    }

    @Test
    void withNoApplyRequiresApplyWaitsForTheWholeChange() {
        ChangeArtifactDag dag = new ChangeArtifactDag();
        dag.setComplete(false);

        assertFalse(dag.isApplyReady(), "with no declared gate, apply needs the whole change");
    }
}
