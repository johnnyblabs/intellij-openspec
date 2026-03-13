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
import com.intellij.ui.SearchTextField;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.johnnyb.openspec.actions.CreateDeltaSpecAction;
import com.johnnyb.openspec.actions.OpenSpecDataKeys;
import com.johnnyb.openspec.services.AiToolDetectionService;
import com.johnnyb.openspec.services.CliDetectionService;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.util.OpenSpecFileUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class OpenSpecToolWindowPanel extends JPanel implements DataProvider {

    private final Project project;
    private final Tree tree;
    private final SearchTextField searchField;
    private final JLabel statusLabel;
    private final JLabel aiStatusLabel;
    private final WorkflowActionPanel workflowPanel;
    private final Alarm refreshAlarm;
    private final Alarm filterAlarm;
    private Set<String> preFilterExpansionState;

    public OpenSpecToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
        this.filterAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);

        // Build tree with placeholder; real model loads async
        DefaultMutableTreeNode placeholder = new DefaultMutableTreeNode("Loading...");
        tree = new Tree(new DefaultTreeModel(placeholder));
        tree.setCellRenderer(new SpecTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);

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

        // Search field
        searchField = new SearchTextField(false);

        // Ctrl+F / Cmd+F to focus search
        int modifier = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        tree.registerKeyboardAction(
                e -> searchField.requestFocusInWindow(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F, modifier),
                JComponent.WHEN_FOCUSED);
        searchField.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { debouncedFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { debouncedFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { debouncedFilter(); }
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

        // Top panel: toolbar + search
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(createActionToolbar(), BorderLayout.NORTH);
        topPanel.add(searchField, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        registerFileListener();
        refreshAsync();
    }

    private void refreshAsync() {
        String query = searchField.getText();
        boolean hasFilter = query != null && !query.isBlank();
        Set<String> expandedLabels = hasFilter ? null : saveExpansionState();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            SpecTreeModel treeModel = new SpecTreeModel(project);
            DefaultTreeModel model = treeModel.buildModel(hasFilter ? query : null);
            ApplicationManager.getApplication().invokeLater(() -> {
                tree.setModel(model);
                if (hasFilter) {
                    expandAllNodes();
                } else if (expandedLabels != null) {
                    restoreExpansionState(expandedLabels);
                }
                updateCliStatus();
                updateAiStatus();
            });
        });
        workflowPanel.refresh();
    }

    private void debouncedFilter() {
        filterAlarm.cancelAllRequests();
        filterAlarm.addRequest(() -> ApplicationManager.getApplication().invokeLater(this::applyFilter), 150);
    }

    private void applyFilter() {
        String query = searchField.getText();
        boolean hasFilter = query != null && !query.isBlank();

        // Save expansion state before first filter keystroke
        if (hasFilter && preFilterExpansionState == null) {
            preFilterExpansionState = saveExpansionState();
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            SpecTreeModel treeModel = new SpecTreeModel(project);
            DefaultTreeModel model = treeModel.buildModel(hasFilter ? query : null);
            ApplicationManager.getApplication().invokeLater(() -> {
                tree.setModel(model);
                if (hasFilter) {
                    expandAllNodes();
                } else {
                    // Filter cleared — restore pre-filter expansion state
                    if (preFilterExpansionState != null) {
                        restoreExpansionState(preFilterExpansionState);
                        preFilterExpansionState = null;
                    }
                }
            });
        });
    }

    private void expandAllNodes() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private Set<String> saveExpansionState() {
        Set<String> expanded = new HashSet<>();
        int rowCount = tree.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            TreePath path = tree.getPathForRow(i);
            if (path != null && tree.isExpanded(path)) {
                expanded.add(pathToKey(path));
            }
        }
        return expanded;
    }

    private void restoreExpansionState(Set<String> expandedKeys) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        restoreNodeExpansion(root, new TreePath(root), expandedKeys);
    }

    private void restoreNodeExpansion(DefaultMutableTreeNode node, TreePath path, Set<String> expandedKeys) {
        if (expandedKeys.contains(pathToKey(path))) {
            tree.expandPath(path);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            restoreNodeExpansion(child, path.pathByAddingChild(child), expandedKeys);
        }
    }

    private String pathToKey(TreePath path) {
        StringBuilder sb = new StringBuilder();
        for (Object component : path.getPath()) {
            if (!sb.isEmpty()) sb.append("/");
            sb.append(component.toString());
        }
        return sb.toString();
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

        if (userObject instanceof SpecTreeModel.TreeNodeData data) {
            if (data.type() == SpecTreeModel.TreeNodeType.HINT) {
                handleHintAction(node);
                return;
            }
            if (data.filePath() != null) {
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(data.filePath());
                if (file != null && !file.isDirectory()) {
                    ApplicationManager.getApplication().invokeLater(
                            () -> FileEditorManager.getInstance(project).openFile(file, true));
                }
            }
        }
    }

    private void handleHintAction(DefaultMutableTreeNode hintNode) {
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) hintNode.getParent();
        if (parent == null) return;
        Object parentObj = parent.getUserObject();

        if (parentObj instanceof SpecTreeModel.TreeNodeData parentData) {
            if (parentData.type() == SpecTreeModel.TreeNodeType.CHANGES) {
                // "No active changes" — trigger Propose action
                AnAction action = ActionManager.getInstance().getAction("OpenSpec.Propose");
                if (action != null) {
                    DataContext context = dataId -> CommonDataKeys.PROJECT.is(dataId) ? project : null;
                    Presentation presentation = action.getTemplatePresentation().clone();
                    AnActionEvent event = new AnActionEvent(null, context, "SpecTree", presentation, ActionManager.getInstance(), 0);
                    com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks(action, event);
                }
            }
        } else if ("OpenSpec".equals(parent.getUserObject())) {
            // Root-level hint — trigger Init
            try {
                com.johnnyb.openspec.scaffolding.ScaffoldingService scaffolding =
                        project.getService(com.johnnyb.openspec.scaffolding.ScaffoldingService.class);
                scaffolding.initOpenSpec();
                refreshAsync();
            } catch (java.io.IOException ex) {
                com.johnnyb.openspec.util.OpenSpecNotifier.notify(project,
                        com.johnnyb.openspec.util.OpenSpecNotifier.GROUP_SYSTEM, "Initialize",
                        "Failed to initialize: " + ex.getMessage(),
                        com.intellij.notification.NotificationType.ERROR);
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
            statusLabel.setForeground(JBColor.namedColor("Label.successForeground", new JBColor(new Color(0, 128, 0), new Color(100, 210, 100))));
        } else {
            statusLabel.setText("CLI: not found (built-in mode)");
            statusLabel.setForeground(JBColor.RED);
        }
    }

    public void refresh() {
        refreshAsync();
    }

    public void selectChange(String changeName) {
        workflowPanel.selectChange(changeName);
    }

    public void selectChangeAndApply(String changeName) {
        workflowPanel.selectChangeAndApply(changeName);
    }

    private void updateAiStatus() {
        AiToolDetectionService aiDetection = project.getService(AiToolDetectionService.class);
        if (aiDetection != null && aiDetection.hasDetectedTools()) {
            aiStatusLabel.setText(aiDetection.getSummary());
            aiStatusLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(100, 210, 100)));
        } else {
            aiStatusLabel.setText("AI: none");
            aiStatusLabel.setForeground(JBColor.GRAY);
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
