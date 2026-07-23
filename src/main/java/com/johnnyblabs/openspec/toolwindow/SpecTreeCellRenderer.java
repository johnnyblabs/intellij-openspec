package com.johnnyblabs.openspec.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class SpecTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final Icon OPENSPEC_ICON = IconLoader.getIcon("/icons/openspec.svg", SpecTreeCellRenderer.class);
    private static final Icon SPEC_ICON = IconLoader.getIcon("/icons/spec.svg", SpecTreeCellRenderer.class);
    // Package-private base icons: the renderer test asserts the layered icons compose these bases.
    static final Icon CHANGE_ICON = IconLoader.getIcon("/icons/change.svg", SpecTreeCellRenderer.class);
    private static final Icon REQUIREMENT_ICON = IconLoader.getIcon("/icons/requirement.svg", SpecTreeCellRenderer.class);
    private static final Icon ARCHIVE_ICON = IconLoader.getIcon("/icons/archive.svg", SpecTreeCellRenderer.class);
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

    private static LayeredIcon badged(Icon base, Icon badge) {
        LayeredIcon layered = new LayeredIcon(2);
        layered.setIcon(base, 0);
        layered.setIcon(badge, 1, SwingConstants.SOUTH_EAST);
        return layered;
    }

    private static final JBColor PROPOSED_COLOR = new JBColor(new Color(0, 128, 0), new Color(120, 220, 120));
    private static final JBColor APPLIED_COLOR = new JBColor(new Color(0, 0, 200), new Color(120, 160, 255));
    private static final JBColor MISSING_COLOR = new JBColor(Color.GRAY, new Color(150, 150, 150));
    private static final JBColor DONE_COLOR = new JBColor(new Color(0, 128, 0), new Color(120, 220, 120));
    private static final JBColor READY_COLOR = new JBColor(new Color(0, 0, 200), new Color(120, 160, 255));
    private static final JBColor BLOCKED_COLOR = new JBColor(Color.GRAY, new Color(150, 150, 150));
    private static final JBColor HINT_COLOR = new JBColor(Color.GRAY, new Color(150, 150, 150));

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                   boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode node) {
            Object userObject = node.getUserObject();

            if (userObject instanceof SpecTreeModel.TreeNodeData data) {
                setIcon(iconForType(data.type()));
                setToolTipText(data.tooltip());

                // Apply status styling
                switch (data.type()) {
                    case CHANGE, CHANGE_DONE -> {
                        String label = data.label();
                        if (label.contains("[proposed]")) {
                            setForeground(sel ? getTextSelectionColor() : PROPOSED_COLOR);
                        } else if (label.contains("[applied]")) {
                            setForeground(sel ? getTextSelectionColor() : APPLIED_COLOR);
                        }
                    }
                    case MISSING_ARTIFACT -> {
                        setForeground(MISSING_COLOR);
                        Font font = getFont();
                        if (font != null) {
                            setFont(font.deriveFont(Font.ITALIC));
                        }
                    }
                    case ARTIFACT_DONE -> {
                        setForeground(sel ? getTextSelectionColor() : DONE_COLOR);
                    }
                    case ARTIFACT_READY -> {
                        setForeground(sel ? getTextSelectionColor() : READY_COLOR);
                        Font font = getFont();
                        if (font != null) {
                            setFont(font.deriveFont(Font.BOLD));
                        }
                    }
                    case ARTIFACT_BLOCKED -> {
                        setForeground(BLOCKED_COLOR);
                        Font font = getFont();
                        if (font != null) {
                            setFont(font.deriveFont(Font.ITALIC));
                        }
                    }
                    case HINT -> {
                        setForeground(HINT_COLOR);
                        Font font = getFont();
                        if (font != null) {
                            setFont(font.deriveFont(Font.ITALIC));
                        }
                        setIcon(null);
                    }
                    case DELTA_SPEC -> {
                        // delta specs use spec icon with a distinctive look
                    }
                    default -> {
                    }
                }
            } else if (node.isRoot()) {
                setIcon(OPENSPEC_ICON);
            }
        }

        return this;
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
            case SPECS, SPEC_DOMAIN -> SPEC_ICON;
            case REQUIREMENT -> REQUIREMENT_ICON;
            case CHANGES, CHANGE -> CHANGE_ICON;
            case CHANGE_DONE -> CHANGE_DONE_ICON;
            case ARTIFACT -> ARTIFACT_ICON;
            case ARTIFACT_DONE -> ARTIFACT_DONE_ICON;
            case ARTIFACT_READY -> ARTIFACT_READY_ICON;
            case ARTIFACT_BLOCKED -> ARTIFACT_BLOCKED_ICON;
            case MISSING_ARTIFACT -> MISSING_ARTIFACT_BADGED_ICON;
            case DELTA_SPEC -> DELTA_SPEC_ICON;
            case ARCHIVE -> ARCHIVE_ICON;
            case CONFIG -> AllIcons.General.Settings;
            case CONFIG_ENTRY -> null;
            case HINT -> null;
        };
    }
}
