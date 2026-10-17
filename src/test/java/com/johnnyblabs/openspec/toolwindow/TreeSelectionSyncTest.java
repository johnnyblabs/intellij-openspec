package com.johnnyblabs.openspec.toolwindow;

import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import static org.junit.jupiter.api.Assertions.*;

class TreeSelectionSyncTest {

    @Test
    void resolveChangeName_fromChangeNode() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changesNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("Changes", SpecTreeModel.TreeNodeType.CHANGES, null, null, null, null));
        DefaultMutableTreeNode changeNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("my-change", SpecTreeModel.TreeNodeType.CHANGE, "/path", "my-change", null, null));
        root.add(changesNode);
        changesNode.add(changeNode);

        TreePath path = new TreePath(new Object[]{root, changesNode, changeNode});
        assertEquals("my-change", SpecTreeModel.resolveChangeName(path));
    }

    @Test
    void resolveChangeName_fromChildOfChangeNode() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changesNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("Changes", SpecTreeModel.TreeNodeType.CHANGES, null, null, null, null));
        DefaultMutableTreeNode changeNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("my-change", SpecTreeModel.TreeNodeType.CHANGE, "/path", "my-change", null, null));
        DefaultMutableTreeNode artifactNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("proposal", SpecTreeModel.TreeNodeType.ARTIFACT_DONE, "/path/proposal.md", "my-change", "proposal", null));
        root.add(changesNode);
        changesNode.add(changeNode);
        changeNode.add(artifactNode);

        TreePath path = new TreePath(new Object[]{root, changesNode, changeNode, artifactNode});
        assertEquals("my-change", SpecTreeModel.resolveChangeName(path));
    }

    @Test
    void resolveChangeName_fromArchiveReadyChangeAndItsChild() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changesNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("Changes", SpecTreeModel.TreeNodeType.CHANGES, null, null, null, null));
        DefaultMutableTreeNode changeNode = new DefaultMutableTreeNode(new SpecTreeModel.TreeNodeData(
                "ship-it", SpecTreeModel.TreeNodeType.CHANGE_ARCHIVE_READY, "/path", "ship-it", null, null));
        DefaultMutableTreeNode child = new DefaultMutableTreeNode(new SpecTreeModel.TreeNodeData(
                "tasks", SpecTreeModel.TreeNodeType.ARTIFACT_DONE, "/path/tasks.md", null, "tasks", null));
        root.add(changesNode);
        changesNode.add(changeNode);
        changeNode.add(child);

        assertEquals("ship-it", SpecTreeModel.resolveChangeName(new TreePath(new Object[]{root, changesNode, changeNode})));
        assertEquals("ship-it", SpecTreeModel.resolveChangeName(new TreePath(new Object[]{root, changesNode, changeNode, child})));
    }

    @Test
    void resolveChangeName_fromChangesGroupNode_returnsNull() {
        // Selecting the "Changes" group header (not a specific change) resolves to null.
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changesNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("Changes", SpecTreeModel.TreeNodeType.CHANGES, null, null, null, null));
        root.add(changesNode);

        TreePath path = new TreePath(new Object[]{root, changesNode});
        assertNull(SpecTreeModel.resolveChangeName(path));
    }

    @Test
    void resolveChangeName_fromHintNode_returnsNull() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode hintNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("No changes", SpecTreeModel.TreeNodeType.HINT, null, null, null, null));
        root.add(hintNode);

        TreePath path = new TreePath(new Object[]{root, hintNode});
        assertNull(SpecTreeModel.resolveChangeName(path));
    }

    @Test
    void resolveChangeName_nullPath_returnsNull() {
        assertNull(SpecTreeModel.resolveChangeName(null));
    }
}
