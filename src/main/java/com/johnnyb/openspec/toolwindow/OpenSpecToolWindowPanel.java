package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.johnnyb.openspec.actions.CreateDeltaSpecAction;
import com.johnnyb.openspec.actions.OpenSpecDataKeys;
import com.johnnyb.openspec.services.AiToolDetectionService;
import com.johnnyb.openspec.services.CliDetectionService;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.util.OpenSpecFileUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class OpenSpecToolWindowPanel extends JPanel implements DataProvider {

    private final Project project;
    private final Tree tree;
    private final JLabel statusLabel;
    private final JLabel aiStatusLabel;
    private final WorkflowActionPanel workflowPanel;
    private final Alarm refreshAlarm;

    public OpenSpecToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);

        // Build tree with placeholder; real model loads async
        DefaultMutableTreeNode placeholder = new DefaultMutableTreeNode("Loading...");
        tree = new Tree(new DefaultTreeModel(placeholder));
        tree.setCellRenderer(new SpecTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e);
                }
            }
        });

        // Status bar
        statusLabel = new JLabel();
        aiStatusLabel = new JLabel();
        updateCliStatus();
        updateAiStatus();
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        statusBar.add(statusLabel);
        statusBar.add(new JLabel(" | "));
        statusBar.add(aiStatusLabel);

        // Workflow action panel
        workflowPanel = new WorkflowActionPanel(project);
        workflowPanel.setOnRefreshRequested(this::refreshAsync);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(workflowPanel, BorderLayout.CENTER);
        bottomPanel.add(statusBar, BorderLayout.SOUTH);

        add(createActionToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        registerFileListener();
        refreshAsync();
    }

    private void refreshAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            SpecTreeModel treeModel = new SpecTreeModel(project);
            DefaultTreeModel model = treeModel.buildModel();
            ApplicationManager.getApplication().invokeLater(() -> {
                tree.setModel(model);
                updateCliStatus();
                updateAiStatus();
            });
        });
        workflowPanel.refresh();
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
                // Opening markdown files triggers slow file index operations in the
                // Markdown preview plugin. Allow slow operations to avoid EDT assertion.
                com.intellij.util.SlowOperations.allowSlowOperations(
                        () -> FileEditorManager.getInstance(project).openFile(file, true));
            }
        }
    }

    private void showContextMenu(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        tree.setSelectionPath(path);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (!(userObject instanceof SpecTreeModel.TreeNodeData data)) return;

        DefaultActionGroup contextMenu = new DefaultActionGroup();

        switch (data.type()) {
            case CHANGE -> {
                contextMenu.add(ActionManager.getInstance().getAction("OpenSpec.Apply"));
                contextMenu.add(ActionManager.getInstance().getAction("OpenSpec.Archive"));
                if (data.filePath() != null) {
                    contextMenu.addSeparator();
                    contextMenu.add(new CreateDeltaSpecAction(data.filePath()));
                    contextMenu.add(createRenameAction(data.filePath()));
                }
                if (data.changeName() != null) {
                    contextMenu.addSeparator();
                    contextMenu.add(createGenerateViaPanel(data.changeName()));
                }
            }
            case CHANGES -> {
                contextMenu.add(ActionManager.getInstance().getAction("OpenSpec.Propose"));
            }
            case SPEC_DOMAIN -> {
                if (data.filePath() != null) {
                    contextMenu.add(new AnAction("Open File") {
                        @Override
                        public void actionPerformed(@org.jetbrains.annotations.NotNull AnActionEvent ae) {
                            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(data.filePath());
                            if (file != null) {
                                FileEditorManager.getInstance(project).openFile(file, true);
                            }
                        }
                    });
                }
            }
            case ARTIFACT_READY -> {
                // Generation is handled via the Workflow Action Panel
            }
            case ARTIFACT_DONE -> {
                if (data.filePath() != null) {
                    contextMenu.add(new AnAction("Open File") {
                        @Override
                        public void actionPerformed(@org.jetbrains.annotations.NotNull AnActionEvent ae) {
                            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(data.filePath());
                            if (file != null) {
                                FileEditorManager.getInstance(project).openFile(file, true);
                            }
                        }
                    });
                }
            }
            case ARTIFACT_BLOCKED -> {
                // Info only, no actions
                return;
            }
            default -> {
                return;
            }
        }

        if (contextMenu.getChildrenCount() > 0) {
            ActionPopupMenu popupMenu = ActionManager.getInstance()
                    .createActionPopupMenu("OpenSpecContextMenu", contextMenu);
            popupMenu.getComponent().show(tree, e.getX(), e.getY());
        }
    }

    private AnAction createRenameAction(String changePath) {
        return new AnAction("Rename Change...") {
            @Override
            public void actionPerformed(@org.jetbrains.annotations.NotNull AnActionEvent ae) {
                VirtualFile changeDir = LocalFileSystem.getInstance().findFileByPath(changePath);
                if (changeDir == null || !changeDir.isDirectory()) return;

                String currentName = changeDir.getName();
                String newName = Messages.showInputDialog(project,
                        "Enter new name for the change:",
                        "Rename Change", Messages.getQuestionIcon(), currentName, null);
                if (newName == null || newName.isBlank() || newName.equals(currentName)) return;

                try {
                    WriteAction.run(() -> changeDir.rename(this, newName));
                    refreshAsync();
                } catch (Exception ex) {
                    Messages.showErrorDialog(project,
                            "Failed to rename change: " + ex.getMessage(), "Rename Error");
                }
            }
        };
    }

    private AnAction createGenerateViaPanel(String changeName) {
        return new AnAction("Generate...") {
            @Override
            public void actionPerformed(@org.jetbrains.annotations.NotNull AnActionEvent ae) {
                workflowPanel.selectChangeAndGenerate(changeName);
            }
        };
    }

    private void updateCliStatus() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null && detection.isAvailable()) {
            String version = detection.getDetectedVersion();
            statusLabel.setText("CLI: available" + (version != null ? " v" + version : ""));
            statusLabel.setForeground(new Color(0, 128, 0));
        } else {
            statusLabel.setText("CLI: not found (built-in mode)");
            statusLabel.setForeground(Color.RED);
        }
    }

    public void refresh() {
        refreshAsync();
    }

    public void selectChange(String changeName) {
        workflowPanel.selectChange(changeName);
    }

    private void updateAiStatus() {
        AiToolDetectionService aiDetection = project.getService(AiToolDetectionService.class);
        if (aiDetection != null && aiDetection.hasDetectedTools()) {
            aiStatusLabel.setText(aiDetection.getSummary());
            aiStatusLabel.setForeground(new Color(0, 128, 0));
        } else {
            aiStatusLabel.setText("AI: none");
            aiStatusLabel.setForeground(Color.GRAY);
        }
    }

    @Override
    public Object getData(@org.jetbrains.annotations.NotNull String dataId) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (!(userObject instanceof SpecTreeModel.TreeNodeData data)) return null;

        if (OpenSpecDataKeys.CHANGE_NAME.is(dataId)) {
            return data.changeName();
        }
        if (OpenSpecDataKeys.ARTIFACT_ID.is(dataId)) {
            return data.artifactId();
        }
        return null;
    }

    private void registerFileListener() {
        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file != null && OpenSpecFileUtil.isUnderOpenSpec(file, project)) {
                        debouncedRefresh();
                        break;
                    }
                }
            }
        });
    }

    private void debouncedRefresh() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (!settings.isAutoRefresh()) return;

        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(this::refreshAsync, 300);
    }
}
