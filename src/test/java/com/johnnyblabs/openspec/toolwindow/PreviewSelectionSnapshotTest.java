package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.toolwindow.SpecPreviewRenderer.PreviewKind;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the preview is asked to render for a selection. Any node under a change previews that change,
 * so its selection must carry the deltas kind, the change name the CLI is keyed on, the change
 * directory the diff links resolve against, and the artifact's own file for the open-in-editor link.
 */
class PreviewSelectionSnapshotTest {

    private static final String CHANGE_DIR = "/p/openspec/changes/demo-change";

    private static TreePath artifactPath(TreeNodeType type, String filePath, String artifactChangeName) {
        DefaultMutableTreeNode change = new DefaultMutableTreeNode(
                new TreeNodeData("demo-change 1/3", TreeNodeType.CHANGE, CHANGE_DIR, "demo-change", null));
        DefaultMutableTreeNode artifact = new DefaultMutableTreeNode(
                new TreeNodeData("artifact", type, filePath, artifactChangeName, "artifact"));
        change.add(artifact);
        return new TreePath(new Object[]{change, artifact});
    }

    private static OpenSpecToolWindowPanel.PreviewSelection snapshot(TreePath path) {
        return OpenSpecToolWindowPanel.snapshotSelection(path);
    }

    @Test
    void proposalArtifactPreviewsItsChangeAndOffersItsFile() {
        String proposal = CHANGE_DIR + "/proposal.md";
        var selection = snapshot(artifactPath(TreeNodeType.ARTIFACT_DONE, proposal, "demo-change"));

        assertEquals(PreviewKind.CHANGE_DELTAS, selection.kind());
        assertEquals(CHANGE_DIR, selection.filePath(), "diff links resolve against the change directory");
        assertEquals("demo-change", selection.changeName());
        assertEquals(proposal, selection.openFilePath());
    }

    @Test
    void blockedArtifactWithNoFilePreviewsItsChange() {
        var selection = snapshot(artifactPath(TreeNodeType.ARTIFACT_BLOCKED, CHANGE_DIR + "/tasks.md", "demo-change"));
        assertEquals(PreviewKind.CHANGE_DELTAS, selection.kind());
        assertEquals("demo-change", selection.changeName());
    }

    @Test
    void fallbackArtifactTakesTheChangeNameFromItsParent() {
        // The no-CLI fallback tree builds artifact nodes with no change name.
        var selection = snapshot(artifactPath(TreeNodeType.ARTIFACT, CHANGE_DIR + "/design.md", null));
        assertEquals(PreviewKind.CHANGE_DELTAS, selection.kind());
        assertEquals("demo-change", selection.changeName(), "the CLI key comes from the change node, not the file");
    }

    @Test
    void specsGlobPreviewsItsChangeWithNoFileToOpen() {
        String glob = CHANGE_DIR + "/" + SpecPreviewRenderTest.capturedSpecsOutputPath();
        var selection = snapshot(artifactPath(TreeNodeType.ARTIFACT_DONE, glob, "demo-change"));

        assertEquals(PreviewKind.CHANGE_DELTAS, selection.kind());
        assertEquals(CHANGE_DIR, selection.filePath());
        assertNull(selection.openFilePath(), "a glob is never a file to open");
    }

    @Test
    void changeNodePreviewsItselfWithNoFileToOpen() {
        DefaultMutableTreeNode change = new DefaultMutableTreeNode(
                new TreeNodeData("demo-change 1/3", TreeNodeType.CHANGE, CHANGE_DIR, "demo-change", null));
        var selection = snapshot(new TreePath(change));
        assertEquals(PreviewKind.CHANGE_DELTAS, selection.kind());
        assertEquals(CHANGE_DIR, selection.filePath());
        assertEquals("demo-change", selection.changeName());
        assertNull(selection.openFilePath());
    }
}
