package com.johnnyblabs.openspec.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.scale.JBUIScale;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    }

    @Test
    void nonStatusNodesAreNeverBadged() {
        // The on-model guard: a badge on a spec/requirement node would repeat the removed
        // @spec coverage scorecard. A future edit that badges one of these must fail here.
        assertFalse(SpecTreeCellRenderer.iconForType(TreeNodeType.REQUIREMENT) instanceof LayeredIcon,
                "requirement node must never be badged");
        assertFalse(SpecTreeCellRenderer.iconForType(TreeNodeType.SPEC_DOMAIN) instanceof LayeredIcon,
                "spec node must never be badged");
        assertFalse(SpecTreeCellRenderer.iconForType(TreeNodeType.DELTA_SPEC) instanceof LayeredIcon,
                "delta-spec node must never be badged");
        assertFalse(SpecTreeCellRenderer.iconForType(TreeNodeType.CONFIG) instanceof LayeredIcon,
                "config node must never be badged");
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
