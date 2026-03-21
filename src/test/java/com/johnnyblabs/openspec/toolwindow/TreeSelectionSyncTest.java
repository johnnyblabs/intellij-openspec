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
    void resolveChangeName_fromNonChangeNode_returnsNull() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode specsNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("Specs", SpecTreeModel.TreeNodeType.SPECS, null, null, null, null));
        DefaultMutableTreeNode domainNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("plugin-core", SpecTreeModel.TreeNodeType.SPEC_DOMAIN, "/path/spec.md", null, null, null));
        root.add(specsNode);
        specsNode.add(domainNode);

        TreePath path = new TreePath(new Object[]{root, specsNode, domainNode});
        assertNull(SpecTreeModel.resolveChangeName(path));
    }

    @Test
    void resolveChangeName_fromConfigNode_returnsNull() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode configNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("Config", SpecTreeModel.TreeNodeType.CONFIG, "/path/config.yaml", null, null, null));
        root.add(configNode);

        TreePath path = new TreePath(new Object[]{root, configNode});
        assertNull(SpecTreeModel.resolveChangeName(path));
    }

    @Test
    void resolveChangeName_fromArchiveNode_returnsNull() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode archiveNode = new DefaultMutableTreeNode(
                new SpecTreeModel.TreeNodeData("Archive", SpecTreeModel.TreeNodeType.ARCHIVE, null, null, null, null));
        root.add(archiveNode);

        TreePath path = new TreePath(new Object[]{root, archiveNode});
        assertNull(SpecTreeModel.resolveChangeName(path));
    }

    @Test
    void resolveChangeName_nullPath_returnsNull() {
        assertNull(SpecTreeModel.resolveChangeName(null));
    }
}
