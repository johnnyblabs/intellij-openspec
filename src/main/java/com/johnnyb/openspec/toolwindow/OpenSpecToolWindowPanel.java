package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.treeStructure.Tree;
import com.johnnyb.openspec.util.OpenSpecFileUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class OpenSpecToolWindowPanel extends JPanel {

    private final Project project;
    private final Tree tree;

    public OpenSpecToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        SpecTreeModel treeModel = new SpecTreeModel(project);
        tree = new Tree(treeModel.buildModel());
        tree.setCellRenderer(new SpecTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                }
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
        add(createActionToolbar(), BorderLayout.NORTH);

        registerFileListener();
    }

    private JComponent createActionToolbar() {
        DefaultActionGroup group = (DefaultActionGroup) ActionManager.getInstance()
                .getAction("OpenSpec.ToolWindowToolbar");

        if (group == null) {
            group = new DefaultActionGroup();
        }

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("OpenSpecToolWindow", group, true);
        toolbar.setTargetComponent(this);
        return toolbar.getComponent();
    }

    private void handleDoubleClick() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof SpecTreeModel.TreeNodeData data && data.filePath() != null) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(data.filePath());
            if (file != null && !file.isDirectory()) {
                FileEditorManager.getInstance(project).openFile(file, true);
            }
        }
    }

    public void refresh() {
        SpecTreeModel treeModel = new SpecTreeModel(project);
        tree.setModel(treeModel.buildModel());
    }

    private void registerFileListener() {
        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file != null && OpenSpecFileUtil.isUnderOpenSpec(file, project)) {
                        SwingUtilities.invokeLater(() -> refresh());
                        break;
                    }
                }
            }
        });
    }
}
