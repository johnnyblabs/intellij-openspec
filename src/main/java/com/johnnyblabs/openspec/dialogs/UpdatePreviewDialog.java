package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.services.UpdatePreviewModel;
import com.johnnyblabs.openspec.util.UpdatePlanParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * Shows what {@code openspec update} will do before it does it (CLI 1.15.0+): upstream's notes, any per-tool failures,
 * and the planned changes grouped by tool, with changes outside the project in their own group. Apply runs the update;
 * Cancel changes nothing. Everything shown comes from {@link UpdatePreviewModel}.
 */
public class UpdatePreviewDialog extends DialogWrapper {

    public static final String TITLE = "OpenSpec Update Preview";
    public static final String APPLY = "Apply";

    private final UpdatePreviewModel model;
    private Tree tree;

    public UpdatePreviewDialog(Project project, UpdatePreviewModel model) {
        super(project, true);
        this.model = model;
        setTitle(TITLE);
        setOKButtonText(APPLY);
        setOKActionEnabled(model.applyEnabled());
        if (model.cancelIsDefault()) {
            getOKAction().putValue(DEFAULT_ACTION, null);
            getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
        }
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(left(new JBLabel("<html><b>" + StringUtil.escapeXmlEntities(model.summary()) + "</b></html>")));
        for (UpdatePreviewModel.NoteRow note : model.notes()) {
            JBLabel label = new JBLabel("<html>" + (note.warning() ? "<b>" : "")
                    + StringUtil.escapeXmlEntities(note.message()) + (note.warning() ? "</b>" : "") + "</html>",
                    note.warning() ? AllIcons.General.Warning : AllIcons.General.Information, SwingConstants.LEFT);
            label.getAccessibleContext().setAccessibleName("Update note " + note.code());
            top.add(left(label));
        }
        if (!model.failures().isEmpty()) {
            for (UpdatePlanParser.Failure f : model.failures()) {
                JBLabel label = new JBLabel("<html>" + StringUtil.escapeXmlEntities(f.tool() + ": " + f.error()) + "</html>",
                        AllIcons.General.Error, SwingConstants.LEFT);
                label.setForeground(JBColor.RED);
                top.add(left(label));
            }
            top.add(left(new JBLabel(UpdatePreviewModel.FAILURES_EXPLANATION)));
        }

        tree = new Tree(new DefaultTreeModel(treeRoot(model)));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.getAccessibleContext().setAccessibleName("Planned update changes");
        expandGroups();

        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        panel.add(top, BorderLayout.NORTH);
        if (!model.groups().isEmpty()) {
            JBScrollPane scroll = new JBScrollPane(tree);
            scroll.setPreferredSize(JBUI.size(560, 320));
            panel.add(scroll, BorderLayout.CENTER);
        }
        panel.add(new JBLabel("<html><small>Apply runs <code>openspec update</code> once. Cancel changes nothing.</small></html>"),
                BorderLayout.SOUTH);
        return panel;
    }

    /** A root holding one node per group, each holding its changes. Package-private for tests. */
    static DefaultMutableTreeNode treeRoot(UpdatePreviewModel model) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (UpdatePreviewModel.Group group : model.groups()) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(group);
            group.changes().forEach(c -> node.add(new DefaultMutableTreeNode(c)));
            root.add(node);
        }
        return root;
    }

    /** Expands the focus tool's group, or every group when there's no focus or only a few changes. */
    private void expandGroups() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        int total = model.groups().stream().mapToInt(g -> g.changes().size()).sum();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            UpdatePreviewModel.Group group = (UpdatePreviewModel.Group) node.getUserObject();
            boolean focus = group.tool().equals(model.focusTool());
            if (focus || model.focusTool() == null && total <= 30 || group.outsideProject()) {
                tree.expandPath(new TreePath(node.getPath()));
            }
        }
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setBorder(JBUI.Borders.emptyBottom(4));
        return c;
    }

    private static final class Renderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            if (o instanceof UpdatePreviewModel.Group g) {
                setIcon(g.outsideProject() ? AllIcons.Nodes.HomeFolder : AllIcons.Nodes.Folder);
                append(g.label(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            } else if (o instanceof UpdatePlanParser.Change c) {
                switch (c.action()) {
                    case "delete" -> {
                        setIcon(AllIcons.General.Remove);
                        append(c.path(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED));
                    }
                    case "add" -> {
                        setIcon(AllIcons.General.Add);
                        append(c.path());
                    }
                    case "update" -> {
                        setIcon(AllIcons.Actions.Edit);
                        append(c.path());
                    }
                    default -> {
                        setIcon(AllIcons.Nodes.Unknown);
                        append(c.path());
                    }
                }
                append("  " + c.action(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                if (c.outsideProject()) {
                    append("  (" + c.tool() + ", outside project)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                }
            }
        }
    }

    @TestOnly
    public boolean isApplyEnabledForTest() {
        return isOKActionEnabled();
    }

    @TestOnly
    public boolean isCancelDefaultForTest() {
        return Boolean.TRUE.equals(getCancelAction().getValue(DEFAULT_ACTION))
                && getOKAction().getValue(DEFAULT_ACTION) == null;
    }

    @TestOnly
    public Tree treeForTest() {
        return tree;
    }
}
