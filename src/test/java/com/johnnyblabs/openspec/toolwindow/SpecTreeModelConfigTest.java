package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.OpenSpecConfig;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpecTreeModelConfigTest {

    private static final String CONFIG_PATH = "/project/openspec/config.yaml";

    @Test
    void nullConfigShowsHint() {
        DefaultMutableTreeNode node = SpecTreeModel.buildConfigNode(null, CONFIG_PATH);

        assertEquals(1, node.getChildCount());
        SpecTreeModel.TreeNodeData child = (SpecTreeModel.TreeNodeData) ((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject();
        assertEquals(SpecTreeModel.TreeNodeType.HINT, child.type());
        assertEquals("No config.yaml found", child.label());
    }

    @Test
    void configNodeHasCorrectType() {
        DefaultMutableTreeNode node = SpecTreeModel.buildConfigNode(new OpenSpecConfig(), CONFIG_PATH);

        SpecTreeModel.TreeNodeData data = (SpecTreeModel.TreeNodeData) node.getUserObject();
        assertEquals("Config", data.label());
        assertEquals(SpecTreeModel.TreeNodeType.CONFIG, data.type());
        assertEquals(CONFIG_PATH, data.filePath());
    }

    @Test
    void fullConfigCreatesAllEntries() {
        OpenSpecConfig config = new OpenSpecConfig();
        config.setSchema("spec-driven");
        config.setVersion("1.2.0");
        config.setProfile(Map.of("name", "MyPlugin", "language", "Java 21"));
        config.setContext("IntelliJ Platform plugin with tree-based UI");
        config.setRules(Map.of("services", "All services SHALL be registered", "rfc", "Use RFC 2119"));

        DefaultMutableTreeNode node = SpecTreeModel.buildConfigNode(config, CONFIG_PATH);

        assertEquals(5, node.getChildCount());
        assertEntryLabel(node, 0, "schema: spec-driven");
        assertEntryLabel(node, 1, "version: 1.2.0");
        assertEntryLabel(node, 2, "profile: MyPlugin");
        assertEntryLabel(node, 3, "context: IntelliJ Platform plugin with tree-based UI");
        assertEntryLabel(node, 4, "rules: 2 defined");
    }

    @Test
    void longContextIsTruncated() {
        OpenSpecConfig config = new OpenSpecConfig();
        config.setContext("A".repeat(100));

        DefaultMutableTreeNode node = SpecTreeModel.buildConfigNode(config, CONFIG_PATH);

        SpecTreeModel.TreeNodeData entry = getChildData(node, 0);
        assertEquals("context: " + "A".repeat(60) + "...", entry.label());
        // Tooltip has the full text
        assertEquals("A".repeat(100), entry.tooltip());
    }

    @Test
    void emptyFieldsAreSkipped() {
        OpenSpecConfig config = new OpenSpecConfig();
        // All fields null/empty by default

        DefaultMutableTreeNode node = SpecTreeModel.buildConfigNode(config, CONFIG_PATH);

        assertEquals(0, node.getChildCount());
    }

    @Test
    void allEntriesAreConfigEntryType() {
        OpenSpecConfig config = new OpenSpecConfig();
        config.setSchema("spec-driven");
        config.setVersion("1.0.0");

        DefaultMutableTreeNode node = SpecTreeModel.buildConfigNode(config, CONFIG_PATH);

        for (int i = 0; i < node.getChildCount(); i++) {
            SpecTreeModel.TreeNodeData data = getChildData(node, i);
            assertEquals(SpecTreeModel.TreeNodeType.CONFIG_ENTRY, data.type());
            assertEquals(CONFIG_PATH, data.filePath());
        }
    }

    @Test
    void profileWithoutNameShowsUnnamed() {
        OpenSpecConfig config = new OpenSpecConfig();
        config.setProfile(Map.of("language", "Java 21"));

        DefaultMutableTreeNode node = SpecTreeModel.buildConfigNode(config, CONFIG_PATH);

        SpecTreeModel.TreeNodeData entry = getChildData(node, 0);
        assertEquals("profile: unnamed", entry.label());
    }

    @Test
    void rulesCountReflectsMapSize() {
        OpenSpecConfig config = new OpenSpecConfig();
        config.setRules(Map.of("a", "1", "b", "2", "c", "3"));

        DefaultMutableTreeNode node = SpecTreeModel.buildConfigNode(config, CONFIG_PATH);

        SpecTreeModel.TreeNodeData entry = getChildData(node, 0);
        assertEquals("rules: 3 defined", entry.label());
    }

    private void assertEntryLabel(DefaultMutableTreeNode parent, int childIndex, String expectedLabel) {
        SpecTreeModel.TreeNodeData data = getChildData(parent, childIndex);
        assertEquals(expectedLabel, data.label());
    }

    private SpecTreeModel.TreeNodeData getChildData(DefaultMutableTreeNode parent, int childIndex) {
        return (SpecTreeModel.TreeNodeData) ((DefaultMutableTreeNode) parent.getChildAt(childIndex)).getUserObject();
    }
}
