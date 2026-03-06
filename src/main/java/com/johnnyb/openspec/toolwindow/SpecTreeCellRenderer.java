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

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                   boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode node) {
            Object userObject = node.getUserObject();

            if (userObject instanceof SpecTreeModel.TreeNodeData data) {
                setIcon(getIconForType(data.type()));
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
            case ARTIFACT -> SPEC_ICON;
            case ARCHIVE -> ARCHIVE_ICON;
        };
    }
}
