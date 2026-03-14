package com.johnnyb.openspec.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.johnnyb.openspec.model.CoverageResult;
import com.johnnyb.openspec.model.CoverageResult.DomainCoverage;
import com.johnnyb.openspec.model.CoverageResult.RequirementCoverage;
import com.johnnyb.openspec.services.SpecCoverageService;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SpecCoveragePanel extends JPanel {

    private static final Icon REQUIREMENT_ICON = IconLoader.getIcon("/icons/requirement.svg",
            SpecCoveragePanel.class);
    private static final JBColor COLOR_COVERED = new JBColor(
            new Color(0, 128, 0), new Color(80, 200, 80));
    private static final JBColor COLOR_UNCOVERED = new JBColor(Color.GRAY, Color.GRAY);

    private final Project project;
    private final Tree tree;
    private final JBLabel summaryLabel;
    private final JBLabel scanningLabel;

    public SpecCoveragePanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setOpaque(false);
        JButton refreshButton = new JButton("Refresh", AllIcons.Actions.Refresh);
        refreshButton.addActionListener(e -> refresh());
        toolbar.add(refreshButton);

        summaryLabel = new JBLabel();
        summaryLabel.setBorder(JBUI.Borders.emptyLeft(8));
        toolbar.add(summaryLabel);

        // Tree
        tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode("Coverage")));
        tree.setCellRenderer(new CoverageCellRenderer());
        tree.setRootVisible(true);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onDoubleClick();
                }
            }
        });

        // Scanning label
        scanningLabel = new JBLabel("Scanning...");
        scanningLabel.setHorizontalAlignment(SwingConstants.CENTER);
        scanningLabel.setForeground(JBColor.GRAY);
        scanningLabel.setVisible(false);

        // Layout
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JBScrollPane(tree), BorderLayout.CENTER);
        centerPanel.add(scanningLabel, BorderLayout.NORTH);

        add(toolbar, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        // Initial scan
        refresh();
    }

    public void refresh() {
        scanningLabel.setVisible(true);
        tree.setVisible(false);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            SpecCoverageService service = project.getService(SpecCoverageService.class);
            CoverageResult result = service.computeCoverage();

            SwingUtilities.invokeLater(() -> {
                buildTree(result);
                scanningLabel.setVisible(false);
                tree.setVisible(true);
            });
        });
    }

    private void buildTree(CoverageResult result) {
        int pct = result.totalRequirements() > 0
                ? (result.coveredRequirements() * 100 / result.totalRequirements()) : 0;

        String rootLabel = String.format("Specs (%d requirements, %d%% covered)",
                result.totalRequirements(), pct);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                new CoverageNodeData(rootLabel, null, null, CoverageNodeType.ROOT));

        for (DomainCoverage domain : result.domains().values()) {
            long covered = domain.coveredCount();
            String domainLabel = String.format("%s (%d/%d covered)",
                    domain.domain(), covered, domain.requirements().size());
            DefaultMutableTreeNode domainNode = new DefaultMutableTreeNode(
                    new CoverageNodeData(domainLabel, null, null, CoverageNodeType.DOMAIN));

            for (RequirementCoverage req : domain.requirements()) {
                String reqLabel;
                if (req.covered()) {
                    String fileName = req.referencingFiles().getFirst();
                    int lastSlash = fileName.lastIndexOf('/');
                    String shortName = lastSlash >= 0 ? fileName.substring(lastSlash + 1) : fileName;
                    reqLabel = req.name() + "  \u2190 " + shortName;
                } else {
                    reqLabel = req.name();
                }
                CoverageNodeType type = req.covered()
                        ? CoverageNodeType.COVERED : CoverageNodeType.UNCOVERED;
                DefaultMutableTreeNode reqNode = new DefaultMutableTreeNode(
                        new CoverageNodeData(reqLabel, req.specFilePath(), req, type));
                domainNode.add(reqNode);
            }
            root.add(domainNode);
        }

        tree.setModel(new DefaultTreeModel(root));
        summaryLabel.setText(String.format("%d/%d covered (%d%%)",
                result.coveredRequirements(), result.totalRequirements(), pct));

        // Expand all
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void onDoubleClick() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) return;
        Object userObj = node.getUserObject();
        if (!(userObj instanceof CoverageNodeData data)) return;

        if (data.specFilePath != null) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(data.specFilePath);
            if (file != null) {
                FileEditorManager.getInstance(project).openFile(file, true);
            }
        }
    }

    enum CoverageNodeType { ROOT, DOMAIN, COVERED, UNCOVERED }

    record CoverageNodeData(
            String label,
            String specFilePath,
            RequirementCoverage requirement,
            CoverageNodeType type
    ) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static class CoverageCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode treeNode
                    && treeNode.getUserObject() instanceof CoverageNodeData data) {
                switch (data.type) {
                    case ROOT -> {
                        setIcon(AllIcons.Nodes.Folder);
                        setForeground(sel ? getTextSelectionColor() : getTextNonSelectionColor());
                    }
                    case DOMAIN -> {
                        setIcon(AllIcons.Nodes.Package);
                        setForeground(sel ? getTextSelectionColor() : getTextNonSelectionColor());
                    }
                    case COVERED -> {
                        setIcon(REQUIREMENT_ICON);
                        if (!sel) setForeground(COLOR_COVERED);
                    }
                    case UNCOVERED -> {
                        setIcon(REQUIREMENT_ICON);
                        if (!sel) setForeground(COLOR_UNCOVERED);
                    }
                }
            }
            return this;
        }
    }
}
