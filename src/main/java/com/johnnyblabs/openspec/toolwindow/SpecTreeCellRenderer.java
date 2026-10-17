package com.johnnyblabs.openspec.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Multi-fragment tree renderer. Built on {@link ColoredTreeCellRenderer} (not the single-color
 * {@code DefaultTreeCellRenderer}) so a change node reads name-first: the change <b>name</b> in the
 * default/primary color and the {@code X/Y} task count dimmed as a trailing secondary counter.
 * Selection legibility is handled by the base
 * (it forces the selection foreground on a focused-selected row), so — unlike the old renderer — no
 * manual selection-color branching is needed (mirrors {@code CoordinationPanel.CoordinationCellRenderer}).
 */
public class SpecTreeCellRenderer extends ColoredTreeCellRenderer {

    private static final Icon OPENSPEC_ICON = IconLoader.getIcon("/icons/openspec.svg", SpecTreeCellRenderer.class);
    // Package-private base icons: the renderer test asserts the layered icons compose these bases.
    static final Icon CHANGE_ICON = IconLoader.getIcon("/icons/change.svg", SpecTreeCellRenderer.class);
    static final Icon ARTIFACT_ICON = IconLoader.getIcon("/icons/artifact.svg", SpecTreeCellRenderer.class);
    private static final Icon DELTA_SPEC_ICON = IconLoader.getIcon("/icons/delta-spec.svg", SpecTreeCellRenderer.class);
    static final Icon MISSING_ARTIFACT_ICON = IconLoader.getIcon("/icons/missing-artifact.svg", SpecTreeCellRenderer.class);

    // Status badge overlays, composed once as static constants (zero per-paint allocation).
    // Base icon on layer 0, a small distinct-shape platform badge on layer 1 in the SE corner.
    // Done/ready read as a status light (green/yellow dot); blocked/missing use mark shapes so
    // the states remain distinguishable without relying on color alone (reinforced by the
    // foreground styling and the "(needs: …)" label suffix on blocked artifacts).
    static final Icon ARTIFACT_DONE_ICON = badged(ARTIFACT_ICON, AllIcons.RunConfigurations.TestState.Green2);
    static final Icon ARTIFACT_READY_ICON = badged(ARTIFACT_ICON, AllIcons.RunConfigurations.TestState.Yellow2);
    static final Icon ARTIFACT_BLOCKED_ICON = badged(ARTIFACT_ICON, AllIcons.Nodes.ErrorMark);
    static final Icon MISSING_ARTIFACT_BADGED_ICON = badged(MISSING_ARTIFACT_ICON, AllIcons.Nodes.WarningMark);
    static final Icon CHANGE_DONE_ICON = badged(CHANGE_ICON, AllIcons.RunConfigurations.TestState.Green2);
    // Archive-ready (artifacts complete AND all tasks done) replaces the apply-ready dot with a check — a
    // different shape, so the stages stay distinguishable without color; it echoes the Archive check icon.
    static final Icon CHANGE_ARCHIVE_READY_ICON = badged(CHANGE_ICON, AllIcons.General.GreenCheckmark);

    private static LayeredIcon badged(Icon base, Icon badge) {
        LayeredIcon layered = new LayeredIcon(2);
        layered.setIcon(base, 0);
        layered.setIcon(badge, 1, SwingConstants.SOUTH_EAST);
        return layered;
    }

    private static final JBColor MISSING_COLOR = new JBColor(Color.GRAY, new Color(150, 150, 150));
    private static final JBColor DONE_COLOR = new JBColor(new Color(0, 128, 0), new Color(120, 220, 120));
    private static final JBColor READY_COLOR = new JBColor(new Color(0, 0, 200), new Color(120, 160, 255));
    private static final JBColor BLOCKED_COLOR = new JBColor(Color.GRAY, new Color(150, 150, 150));

    // Per-fragment attributes, cached once (never allocate inside customizeCellRenderer). Bold/italic
    // come from the STYLE_* bits, not Font.deriveFont — SimpleColoredComponent ignores per-fragment font.
    private static final SimpleTextAttributes DONE_ATTR = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DONE_COLOR);
    private static final SimpleTextAttributes READY_ATTR = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, READY_COLOR);
    private static final SimpleTextAttributes BLOCKED_ATTR = new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, BLOCKED_COLOR);
    private static final SimpleTextAttributes MISSING_ATTR = new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, MISSING_COLOR);
    /** The archive-ready text cue. The words carry the meaning; the theme's success color only reinforces it. */
    static final SimpleTextAttributes ALL_TASKS_DONE_ATTR = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
            JBColor.namedColor("Label.successForeground", DONE_COLOR));
    /** Says "all tasks done", never "ready to archive": Verify owns that phrase, and it also requires validation. */
    static final String ALL_TASKS_DONE_TEXT = " · all tasks done";

    @Override
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof DefaultMutableTreeNode node)) return;
        Object userObject = node.getUserObject();

        if (userObject instanceof SpecTreeModel.TreeNodeData data) {
            setIcon(iconForType(data.type()));
            setToolTipText(data.tooltip());

            switch (data.type()) {
                case CHANGE, CHANGE_DONE, CHANGE_ARCHIVE_READY -> appendChangeFragments(data);
                case MISSING_ARTIFACT -> append(data.label(), MISSING_ATTR);
                case ARTIFACT_DONE -> append(data.label(), DONE_ATTR);
                case ARTIFACT_READY -> append(data.label(), READY_ATTR);
                case ARTIFACT_BLOCKED -> append(data.label(), BLOCKED_ATTR);
                case HINT -> {
                    setIcon(null);
                    append(data.label(), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                }
                default -> append(data.label(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        } else if (node.isRoot()) {
            setIcon(OPENSPEC_ICON);
            append(String.valueOf(node.getUserObject()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        } else if (userObject != null) {
            append(String.valueOf(userObject), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }

    /**
     * Fragments a change node so the row reads name-first. The <b>name</b> is the default color
     * (primary — the identifier the eye scans for) and the {@code X/Y} count is dimmed gray. The count
     * is deliberately NOT greened at N/N — "done" is owned by the {@code CHANGE_DONE} icon badge, which
     * is driven by the artifact DAG (a distinct upstream signal from tasks.md checkboxes and free to
     * diverge). Falls back to the flat label if structured parts are absent.
     */
    private void appendChangeFragments(SpecTreeModel.TreeNodeData data) {
        SpecTreeModel.TreeNodeData.ChangeLabelParts parts = data.changeParts();
        if (parts == null) {
            append(data.label(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            return;
        }
        for (LabelFragment fragment : changeFragments(parts, data.type())) {
            append(fragment.text(), fragment.attributes());
        }
    }

    /** One appended run of text with its attributes — the unit the pure {@link #changeFragments} emits. */
    record LabelFragment(String text, SimpleTextAttributes attributes) {}

    /**
     * The pure fragmentation decision for a change node, extracted so the visual-hierarchy contract is
     * unit-testable without a running IDE: the name is always {@code REGULAR_ATTRIBUTES} (primary), and
     * the {@code X/Y} count is always {@code GRAYED_ATTRIBUTES} — including at {@code N/N}, so a complete
     * task count is never greened (that "done" story belongs to the icon badge, on the artifact-DAG signal).
     * An archive-ready node adds a separate {@link #ALL_TASKS_DONE_TEXT} fragment after the still-dimmed count.
     */
    static java.util.List<LabelFragment> changeFragments(SpecTreeModel.TreeNodeData.ChangeLabelParts parts,
                                                         SpecTreeModel.TreeNodeType type) {
        java.util.List<LabelFragment> fragments = new java.util.ArrayList<>();
        fragments.add(new LabelFragment(parts.name(), SimpleTextAttributes.REGULAR_ATTRIBUTES));

        int[] counts = parts.taskCounts();
        if (counts != null && counts.length == 2 && counts[1] > 0) {
            fragments.add(new LabelFragment(" " + counts[0] + "/" + counts[1], SimpleTextAttributes.GRAYED_ATTRIBUTES));
        }
        if (type == SpecTreeModel.TreeNodeType.CHANGE_ARCHIVE_READY) {
            fragments.add(new LabelFragment(ALL_TASKS_DONE_TEXT, ALL_TASKS_DONE_ATTR));
        }
        return fragments;
    }

    /**
     * Maps a tree node type to its icon. Status-bearing node types
     * (change-artifact, missing-artifact, apply-ready change) get a cached
     * {@link LayeredIcon} with a corner status badge; every other type — including
     * spec, requirement, delta-spec, and config nodes — gets a plain icon. That
     * boundary is the on-model line: only client-owned status carries a badge, so a
     * spec or requirement node is never badged (which would repeat the removed
     * {@code @spec} coverage scorecard). A renderer unit test asserts both directions.
     * Package-private and static so it is unit-testable without a live tree.
     */
    static Icon iconForType(SpecTreeModel.TreeNodeType type) {
        return switch (type) {
            case CHANGES, CHANGE -> CHANGE_ICON;
            case CHANGE_DONE -> CHANGE_DONE_ICON;
            case CHANGE_ARCHIVE_READY -> CHANGE_ARCHIVE_READY_ICON;
            case ARTIFACT -> ARTIFACT_ICON;
            case ARTIFACT_DONE -> ARTIFACT_DONE_ICON;
            case ARTIFACT_READY -> ARTIFACT_READY_ICON;
            case ARTIFACT_BLOCKED -> ARTIFACT_BLOCKED_ICON;
            case MISSING_ARTIFACT -> MISSING_ARTIFACT_BADGED_ICON;
            case DELTA_SPEC -> DELTA_SPEC_ICON;
            case HINT -> null;
        };
    }
}
