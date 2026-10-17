package com.johnnyblabs.openspec.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.johnnyblabs.openspec.toolwindow.SpecTreeCellRenderer.LabelFragment;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData.ChangeLabelParts;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-tests {@link SpecTreeCellRenderer#iconForType} — the pure TreeNodeType → Icon map —
 * without a live tree. It proves the on-model boundary in both directions: only status-bearing
 * node types (change-artifact, missing-artifact, apply-ready change) get a {@link LayeredIcon}
 * badge, and spec/requirement/config/plain nodes never do. It also proves the badge composition
 * (base icon on layer 0, the chosen platform badge on layer 1) and that the four artifact states
 * use distinct badge constants.
 */
class SpecTreeCellRendererTest {

    @BeforeAll
    static void precomputeUiScale() {
        // LayeredIcon dimension math goes through JBUIScale; pre-seed the scale so the lazy
        // "Must be precomputed" init doesn't log an error the test logger turns into a failure
        // (same anti-flake pattern as EmptyStateFactoryTest).
        JBUIScale.setSystemScaleFactor(1f);
        JBUIScale.setUserScaleFactor(1f);
    }

    @Test
    void statusBearingNodesGetLayeredIcon() {
        assertInstanceOf(LayeredIcon.class, SpecTreeCellRenderer.iconForType(TreeNodeType.ARTIFACT_DONE));
        assertInstanceOf(LayeredIcon.class, SpecTreeCellRenderer.iconForType(TreeNodeType.ARTIFACT_READY));
        assertInstanceOf(LayeredIcon.class, SpecTreeCellRenderer.iconForType(TreeNodeType.ARTIFACT_BLOCKED));
        assertInstanceOf(LayeredIcon.class, SpecTreeCellRenderer.iconForType(TreeNodeType.MISSING_ARTIFACT));
        assertInstanceOf(LayeredIcon.class, SpecTreeCellRenderer.iconForType(TreeNodeType.CHANGE_DONE));
        assertInstanceOf(LayeredIcon.class, SpecTreeCellRenderer.iconForType(TreeNodeType.CHANGE_ARCHIVE_READY));
    }

    @Test
    void nonStatusNodesAreNeverBadged() {
        // The on-model guard: a badge on a spec/requirement node would repeat the removed
        // @spec coverage scorecard. A future edit that badges one of these must fail here.
        assertFalse(SpecTreeCellRenderer.iconForType(TreeNodeType.DELTA_SPEC) instanceof LayeredIcon,
                "delta-spec node must never be badged");
        assertFalse(SpecTreeCellRenderer.iconForType(TreeNodeType.CHANGES) instanceof LayeredIcon,
                "the Changes group node must not be badged");
        assertFalse(SpecTreeCellRenderer.iconForType(TreeNodeType.CHANGE) instanceof LayeredIcon,
                "a non-apply-ready change node must not be badged");
        assertFalse(SpecTreeCellRenderer.iconForType(TreeNodeType.ARTIFACT) instanceof LayeredIcon,
                "a plain (no-CLI-status) artifact node must not be badged");
    }

    @Test
    void artifactDoneComposesBaseIconPlusChosenBadge() {
        LayeredIcon done = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.ARTIFACT_DONE);
        assertEquals(2, done.getIconCount());
        assertSame(SpecTreeCellRenderer.ARTIFACT_ICON, done.getIcon(0), "layer 0 is the artifact base icon");
        assertSame(AllIcons.RunConfigurations.TestState.Green2, done.getIcon(1), "layer 1 is the done badge");
    }

    @Test
    void missingArtifactComposesMissingBase() {
        LayeredIcon missing = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.MISSING_ARTIFACT);
        assertSame(SpecTreeCellRenderer.MISSING_ARTIFACT_ICON, missing.getIcon(0));
        assertSame(AllIcons.Nodes.WarningMark, missing.getIcon(1));
    }

    @Test
    void changeDoneComposesChangeBase() {
        LayeredIcon changeDone = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.CHANGE_DONE);
        assertSame(SpecTreeCellRenderer.CHANGE_ICON, changeDone.getIcon(0));
        assertSame(AllIcons.RunConfigurations.TestState.Green2, changeDone.getIcon(1));
    }

    @Test
    void archiveReadyComposesChangeBaseWithADistinctCheckBadge() {
        LayeredIcon archiveReady = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.CHANGE_ARCHIVE_READY);
        LayeredIcon changeDone = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.CHANGE_DONE);
        assertEquals(2, archiveReady.getIconCount());
        assertSame(SpecTreeCellRenderer.CHANGE_ICON, archiveReady.getIcon(0));
        assertSame(AllIcons.General.GreenCheckmark, archiveReady.getIcon(1));
        assertNotSame(changeDone.getIcon(1), archiveReady.getIcon(1),
                "archive-ready must differ from apply-ready by badge shape, not color alone");
    }

    @Test
    void archiveReady_addsAllTasksDoneFragmentAfterTheDimmedCount() {
        List<LabelFragment> f = SpecTreeCellRenderer.changeFragments(
                new ChangeLabelParts("ship-it", new int[]{3, 3}), TreeNodeType.CHANGE_ARCHIVE_READY);
        assertEquals(3, f.size(), "name, count, cue: " + f);
        assertEquals("ship-it", f.get(0).text());
        assertSame(SimpleTextAttributes.REGULAR_ATTRIBUTES, f.get(0).attributes());
        assertEquals(" 3/3", f.get(1).text());
        assertSame(SimpleTextAttributes.GRAYED_ATTRIBUTES, f.get(1).attributes(), "the count is never greened");
        String cue = f.get(2).text();
        assertTrue(cue.startsWith(" · ") && cue.contains("all tasks done"), cue);
        assertFalse(cue.toLowerCase(java.util.Locale.ROOT).contains("ready to archive"),
                "Verify owns 'ready to archive' (it also requires validation): " + cue);
        assertSame(SpecTreeCellRenderer.ALL_TASKS_DONE_ATTR, f.get(2).attributes());
        assertNotSame(SimpleTextAttributes.GRAYED_ATTRIBUTES, f.get(2).attributes());
        assertNotSame(SimpleTextAttributes.REGULAR_ATTRIBUTES, f.get(2).attributes());
    }

    @Test
    void completeCountWithoutArchiveReady_hasNoCue() {
        // The cue depends on the node's state, not on N/N alone (an incomplete DAG can still have 8/8 tasks).
        for (TreeNodeType type : List.of(TreeNodeType.CHANGE, TreeNodeType.CHANGE_DONE)) {
            List<LabelFragment> f = SpecTreeCellRenderer.changeFragments(new ChangeLabelParts("c", new int[]{8, 8}), type);
            assertEquals(2, f.size(), type + ": " + f);
            assertSame(SimpleTextAttributes.GRAYED_ATTRIBUTES, f.get(1).attributes());
        }
    }

    // --- Change-node fragmentation: name un-tinted (primary), count dimmed ---

    @Test
    void changeName_isPrimaryColor() {
        List<LabelFragment> f = SpecTreeCellRenderer.changeFragments(
                new ChangeLabelParts("add-user-auth", new int[]{3, 11}), TreeNodeType.CHANGE);
        // The NAME is the first fragment and must use REGULAR (default) — the whole point of the
        // refactor is that the name reads primary and stops competing with the count.
        assertEquals("add-user-auth", f.get(0).text());
        assertSame(SimpleTextAttributes.REGULAR_ATTRIBUTES, f.get(0).attributes(),
                "the change name must be the default color");
    }

    @Test
    void nameAndCount_areTheOnlyFragments_countGrayed() {
        // The invented [status] tag is retired — a change node is just name + (optional) dimmed count.
        List<LabelFragment> f = SpecTreeCellRenderer.changeFragments(
                new ChangeLabelParts("add-user-auth", new int[]{3, 11}), TreeNodeType.CHANGE);
        assertEquals(2, f.size(), "name + count, no status tag");
        assertEquals("add-user-auth", f.get(0).text());
        assertEquals(" 3/11", f.get(1).text());
        assertSame(SimpleTextAttributes.GRAYED_ATTRIBUTES, f.get(1).attributes(),
                "the trailing task count must be dimmed");
    }

    @Test
    void completeCount_staysGrayed_isNotGreened() {
        // 8/8 is "all tasks checked" but that is NOT the apply-ready/done signal (the icon badge owns
        // that, from the artifact DAG). The count must stay uniformly gray so it never contradicts a
        // non-done icon.
        List<LabelFragment> f = SpecTreeCellRenderer.changeFragments(
                new ChangeLabelParts("fix-login", new int[]{8, 8}), TreeNodeType.CHANGE);
        LabelFragment count = f.get(f.size() - 1);
        assertEquals(" 8/8", count.text());
        assertSame(SimpleTextAttributes.GRAYED_ATTRIBUTES, count.attributes(),
                "a complete count must not be greened — it stays the neutral gray counter");
    }

    @Test
    void noTasksArtifact_omitsCount() {
        List<LabelFragment> f = SpecTreeCellRenderer.changeFragments(
                new ChangeLabelParts("plan-only", null), TreeNodeType.CHANGE);
        assertEquals(1, f.size(), "just the name when there are no tasks");
        assertEquals("plan-only", f.get(0).text());
    }

    @Test
    void zeroTotalTasks_omitsCount() {
        List<LabelFragment> f = SpecTreeCellRenderer.changeFragments(
                new ChangeLabelParts("empty-tasks", new int[]{0, 0}), TreeNodeType.CHANGE);
        assertEquals(1, f.size(), "an empty tasks file yields no count fragment");
        assertEquals("empty-tasks", f.get(0).text());
    }

    @Test
    void distinctBadgesPerStatus() {
        LayeredIcon done = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.ARTIFACT_DONE);
        LayeredIcon ready = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.ARTIFACT_READY);
        LayeredIcon blocked = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.ARTIFACT_BLOCKED);
        LayeredIcon missing = (LayeredIcon) SpecTreeCellRenderer.iconForType(TreeNodeType.MISSING_ARTIFACT);
        assertNotSame(done.getIcon(1), ready.getIcon(1), "done and ready badges must differ");
        assertNotSame(done.getIcon(1), blocked.getIcon(1), "done and blocked badges must differ");
        assertNotSame(ready.getIcon(1), blocked.getIcon(1), "ready and blocked badges must differ");
        assertNotSame(blocked.getIcon(1), missing.getIcon(1), "blocked and missing badges must differ");
    }
}
