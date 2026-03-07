package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class SpecTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final Icon OPENSPEC_ICON = IconLoader.getIcon("/icons/openspec.svg", SpecTreeCellRenderer.class);
    private static final Icon SPEC_ICON = IconLoader.getIcon("/icons/spec.svg", SpecTreeCellRenderer.class);
    private static final Icon CHANGE_ICON = IconLoader.getIcon("/icons/change.svg", SpecTreeCellRenderer.class);
    private static final Icon REQUIREMENT_ICON = IconLoader.getIcon("/icons/requirement.svg", SpecTreeCellRenderer.class);
    private static final Icon ARCHIVE_ICON = IconLoader.getIcon("/icons/archive.svg", SpecTreeCellRenderer.class);

    private static final Color PROPOSED_COLOR = new Color(0, 128, 0);
    private static final Color APPLIED_COLOR = new Color(0, 0, 200);
    private static final Color MISSING_COLOR = Color.GRAY;
    private static final Color DONE_COLOR = new Color(0, 128, 0);
    private static final Color READY_COLOR = new Color(0, 0, 200);
    private static final Color BLOCKED_COLOR = Color.GRAY;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                   boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode node) {
            Object userObject = node.getUserObject();

            if (userObject instanceof SpecTreeModel.TreeNodeData data) {
                setIcon(getIconForType(data.type()));

                // Apply status styling
                switch (data.type()) {
                    case CHANGE -> {
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
                        setForeground(Color.GRAY);
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

    private Icon getIconForType(SpecTreeModel.TreeNodeType type) {
        return switch (type) {
            case SPECS, SPEC_DOMAIN -> SPEC_ICON;
            case REQUIREMENT -> REQUIREMENT_ICON;
            case CHANGES, CHANGE -> CHANGE_ICON;
            case ARTIFACT, ARTIFACT_DONE, ARTIFACT_READY, ARTIFACT_BLOCKED -> SPEC_ICON;
            case MISSING_ARTIFACT -> SPEC_ICON;
            case DELTA_SPEC -> SPEC_ICON;
            case ARCHIVE -> ARCHIVE_ICON;
            case HINT -> null;
        };
    }
}
