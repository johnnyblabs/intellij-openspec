package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A tree node's identity survives the label and status changes a rebuild brings, so selection and expansion do too. */
class TreeNodeIdentityTest {

    private static TreeNodeData change(String label, TreeNodeType type) {
        return new TreeNodeData(label, type, "/p/openspec/changes/add-auth", "add-auth", null, "tip");
    }

    private static TreeNodeData artifact(String label, TreeNodeType type) {
        return new TreeNodeData(label, type, "/p/openspec/changes/add-auth/tasks.md", "add-auth", "tasks", "tip");
    }

    @Test
    void changeIdIgnoresTaskCountAndRollupState() {
        assertEquals(change("add-auth 1/3", TreeNodeType.CHANGE).getPathElementId(),
                change("add-auth 3/3", TreeNodeType.CHANGE_ARCHIVE_READY).getPathElementId());
        assertEquals(change("add-auth", TreeNodeType.CHANGE).getPathElementId(),
                change("add-auth 2/3", TreeNodeType.CHANGE_DONE).getPathElementId());
    }

    @Test
    void artifactIdIgnoresStatusAndNeedsSuffix() {
        String missing = new TreeNodeData("tasks", TreeNodeType.MISSING_ARTIFACT, null, "add-auth", "tasks", "Not yet created")
                .getPathElementId();
        assertEquals(missing, artifact("tasks (needs: specs, design)", TreeNodeType.ARTIFACT_BLOCKED).getPathElementId());
        assertEquals(missing, artifact("tasks", TreeNodeType.ARTIFACT_READY).getPathElementId());
        assertEquals(missing, artifact("tasks", TreeNodeType.ARTIFACT_DONE).getPathElementId());
    }

    @Test
    void differentChangesHaveDifferentIds() {
        assertNotEquals(change("add-auth", TreeNodeType.CHANGE).getPathElementId(),
                new TreeNodeData("add-auth", TreeNodeType.CHANGE, "/p/x", "other", null).getPathElementId());
    }

    @Test
    void specsArtifactAndSpecsDeltaCapabilityDoNotCollide() {
        TreeNodeData specsArtifact = new TreeNodeData("specs", TreeNodeType.ARTIFACT_DONE,
                "/p/openspec/changes/add-auth/specs/**/*.md", "add-auth", "specs");
        TreeNodeData specsDelta = new TreeNodeData("specs", TreeNodeType.DELTA_SPEC,
                "/p/openspec/changes/add-auth/specs/specs/spec.md", "add-auth", null);
        assertNotEquals(specsArtifact.getPathElementId(), specsDelta.getPathElementId());
    }

    @Test
    void labelStaysTheDisplayString() {
        assertEquals("add-auth 1/3", change("add-auth 1/3", TreeNodeType.CHANGE).toString());
    }

    // Real labels: the plugin's own label builder over captured CLI `status --json`, where `tasks` goes from blocked
    // ("tasks (needs: specs, design)") to done ("tasks") between the two captures.

    private static com.johnnyblabs.openspec.model.ChangeArtifactDag capturedDag(String fixture) throws java.io.IOException {
        try (java.io.InputStream in = TreeNodeIdentityTest.class.getResourceAsStream("/fixtures/cli/" + fixture)) {
            assertNotNull(in, "missing fixture " + fixture);
            return com.johnnyblabs.openspec.util.CliOutputParser.parseChangeStatus(
                    new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static TreeNodeData capturedArtifactNode(String fixture, String id) throws java.io.IOException {
        for (com.johnnyblabs.openspec.model.ArtifactInfo a : capturedDag(fixture).getArtifacts()) {
            if (a.id().equals(id)) {
                return new TreeNodeData(SpecTreeModel.buildArtifactLabel(a), TreeNodeType.ARTIFACT, null, "demo", a.id());
            }
        }
        throw new AssertionError("no artifact " + id + " in " + fixture);
    }

    @Test
    void capturedBlockedToDoneTransitionKeepsTheId() throws Exception {
        TreeNodeData blocked = capturedArtifactNode("1.7.0/status-with-context.json", "tasks");
        TreeNodeData done = capturedArtifactNode("1.7.0/status.json", "tasks");
        assertNotEquals(blocked.label(), done.label(), "the captures must really differ in label");
        assertEquals(blocked.getPathElementId(), done.getPathElementId());
    }
}
