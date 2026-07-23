package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.model.ChangeStatus;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-tests the pure label/type helpers of {@link SpecTreeModel}. These cover the glyph
 * retirement (artifact labels are now plain ids), the change-node {@code X/Y} task suffix,
 * and the apply-ready {@code CHANGE_DONE} routing — all headless, no live tree or project.
 */
class SpecTreeModelLabelTest {

    private static final String NO_GLYPHS = "checkmark/circle/minus glyphs must not appear in labels anymore";

    @Test
    void doneArtifactLabelIsPlainIdWithNoGlyph() {
        ArtifactInfo proposal = new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of());
        String label = SpecTreeModel.buildArtifactLabel(proposal);
        assertEquals("proposal", label);
        assertFalse(containsStatusGlyph(label), NO_GLYPHS);
    }

    @Test
    void readyArtifactLabelIsPlainId() {
        ArtifactInfo specs = new ArtifactInfo("specs", "specs/**/*.md", ArtifactStatus.READY, List.of());
        assertEquals("specs", SpecTreeModel.buildArtifactLabel(specs));
    }

    @Test
    void blockedArtifactKeepsNeedsSuffixButNoGlyph() {
        ArtifactInfo tasks = new ArtifactInfo("tasks", "tasks.md", ArtifactStatus.BLOCKED, List.of("design"));
        String label = SpecTreeModel.buildArtifactLabel(tasks);
        assertEquals("tasks (needs: design)", label);
        assertFalse(containsStatusGlyph(label), NO_GLYPHS);
    }

    @Test
    void blockedArtifactWithMultipleMissingDeps() {
        ArtifactInfo tasks = new ArtifactInfo("tasks", "tasks.md", ArtifactStatus.BLOCKED, List.of("design", "specs"));
        assertEquals("tasks (needs: design, specs)", SpecTreeModel.buildArtifactLabel(tasks));
    }

    @Test
    void changeLabelAppendsTaskProgress() {
        String label = SpecTreeModel.buildChangeLabel("my-change", ChangeStatus.PROPOSED, new int[]{3, 7});
        assertTrue(label.contains("3/7"), "task progress X/Y must appear: " + label);
        assertTrue(label.contains("my-change"));
        assertTrue(label.contains("[proposed]"));
    }

    @Test
    void changeLabelHasNoSlashWhenNoTasksArtifact() {
        String label = SpecTreeModel.buildChangeLabel("my-change", ChangeStatus.PROPOSED, null);
        assertFalse(label.contains("/"), "no task suffix when there is no tasks artifact: " + label);
        assertEquals("my-change [proposed]", label);
    }

    @Test
    void changeLabelHasNoSuffixForZeroTasks() {
        String label = SpecTreeModel.buildChangeLabel("my-change", ChangeStatus.APPLIED, new int[]{0, 0});
        assertFalse(label.contains("/"), "an empty tasks file yields no X/Y suffix: " + label);
    }

    @Test
    void changeLabelOmitsStatusWhenUnknown() {
        String label = SpecTreeModel.buildChangeLabel("my-change", ChangeStatus.UNKNOWN, new int[]{1, 4});
        assertEquals("my-change 1/4", label);
    }

    @Test
    void completeDagRoutesToChangeDone() {
        ChangeArtifactDag dag = new ChangeArtifactDag();
        dag.setComplete(true);
        assertEquals(TreeNodeType.CHANGE_DONE, SpecTreeModel.changeNodeType(dag));
    }

    @Test
    void incompleteDagRoutesToPlainChange() {
        ChangeArtifactDag dag = new ChangeArtifactDag();
        dag.setComplete(false);
        assertEquals(TreeNodeType.CHANGE, SpecTreeModel.changeNodeType(dag));
    }

    @Test
    void nullDagRoutesToPlainChange() {
        assertEquals(TreeNodeType.CHANGE, SpecTreeModel.changeNodeType(null));
    }

    private static boolean containsStatusGlyph(String s) {
        return s.indexOf('✓') >= 0   // ✓
                || s.indexOf('○') >= 0  // ○
                || s.indexOf('−') >= 0; // −
    }
}
