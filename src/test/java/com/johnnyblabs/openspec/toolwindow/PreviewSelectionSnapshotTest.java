package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.toolwindow.SpecPreviewRenderer.PreviewKind;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the preview is asked to render for a selection. The specs artifact's path is a glob, so its
 * selection must carry the deltas kind, the change name the CLI is keyed on, and the change
 * directory the deltas view builds its diff links from.
 */
class PreviewSelectionSnapshotTest {

    private static final String CHANGE_DIR = "/p/openspec/changes/demo-change";

    private static TreePath artifactPath(String artifactId, String filePath) {
        DefaultMutableTreeNode change = new DefaultMutableTreeNode(
                new TreeNodeData("demo-change", TreeNodeType.CHANGE, CHANGE_DIR, "demo-change", null));
        DefaultMutableTreeNode artifact = new DefaultMutableTreeNode(
                new TreeNodeData(artifactId, TreeNodeType.ARTIFACT_DONE, filePath, "demo-change", artifactId));
        change.add(artifact);
        return new TreePath(new Object[]{change, artifact});
    }

    @Test
    void specsArtifactSnapshotsToTheChangesDeltas() {
        String glob = CHANGE_DIR + "/" + SpecPreviewRenderTest.capturedSpecsOutputPath();
        OpenSpecToolWindowPanel.PreviewSelection selection =
                OpenSpecToolWindowPanel.snapshotSelection(artifactPath("specs", glob));

        assertEquals(PreviewKind.CHANGE_DELTAS, selection.kind());
        assertEquals(CHANGE_DIR, selection.filePath(), "diff links resolve against the change directory");
        assertEquals("demo-change", selection.changeName());
    }

    @Test
    void proposalArtifactStillSnapshotsToItsFile() {
        String proposal = CHANGE_DIR + "/proposal.md";
        OpenSpecToolWindowPanel.PreviewSelection selection =
                OpenSpecToolWindowPanel.snapshotSelection(artifactPath("proposal", proposal));

        assertEquals(PreviewKind.CHANGE_ARTIFACT, selection.kind());
        assertEquals(proposal, selection.filePath());
    }
}
