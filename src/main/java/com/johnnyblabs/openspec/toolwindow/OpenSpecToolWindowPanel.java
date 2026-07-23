package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.johnnyblabs.openspec.util.MarkdownHtmlRenderer;
import com.johnnyblabs.openspec.actions.CreateDeltaSpecAction;
import com.johnnyblabs.openspec.actions.OpenSpecDataKeys;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
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
    private static final Logger LOG = Logger.getInstance(OpenSpecToolWindowPanel.class);

    private final Project project;
    private final Tree tree;
    private final SearchTextField searchField;
    private final JLabel statusLabel;
    private final JLabel aiStatusLabel;
    private final WorkflowActionPanel workflowPanel;
    private final JEditorPane previewPane;
    private final Alarm refreshAlarm;
    private final Alarm filterAlarm;
    private final Alarm previewAlarm;
    private Set<String> preFilterExpansionState;

    /** What the preview render path needs, snapshotted on the EDT at selection time. */
    private record PreviewSelection(String filePath, SpecPreviewRenderer.PreviewKind kind, String requirementName) {}

    public OpenSpecToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
        this.filterAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
        this.previewAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);

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

        // Status bar — compact single row
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        aiStatusLabel = new JLabel();
        aiStatusLabel.setFont(aiStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        updateCliStatus();
        updateAiStatus();
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        statusBar.setBorder(JBUI.Borders.empty(2, 4, 2, 4));
        statusBar.add(statusLabel);
        JLabel sep = new JLabel("|");
        sep.setForeground(JBColor.GRAY);
        sep.setFont(sep.getFont().deriveFont(Font.PLAIN, 11f));
        statusBar.add(sep);
        statusBar.add(aiStatusLabel);

        // Workflow action panel
        workflowPanel = new WorkflowActionPanel(project);
        workflowPanel.setOnRefreshRequested(this::refreshAsync);

        // Sync tree selection → workflow panel (after workflowPanel is constructed)
        tree.addTreeSelectionListener(e -> {
            String changeName = SpecTreeModel.resolveChangeName(e.getPath());
            if (changeName != null) {
                workflowPanel.setActiveChange(changeName);
            }
        });

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
        bottomPanel.add(workflowPanel, BorderLayout.CENTER);
        bottomPanel.add(statusBar, BorderLayout.SOUTH);

        // Top panel: toolbar + search
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(createActionToolbar(), BorderLayout.NORTH);
        topPanel.add(searchField, BorderLayout.SOUTH);

        // Use a split pane so the tree gets priority and the user can resize
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(JBUI.Borders.empty());
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, bottomPanel);
        splitPane.setResizeWeight(1.0); // Tree gets all extra space
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(0);
        splitPane.setBorder(null);
        // Minimum must fit: header row + pipeline chips + icon bar + status strip
        bottomPanel.setMinimumSize(new Dimension(0, JBUI.scale(140)));

        // Read-only rendered-markdown preview pane (right side of a horizontal splitter). Uses the
        // modern HTMLEditorKitBuilder (LAF/HiDPI-correct) rather than the raw HTMLEditorKit.
        previewPane = new JEditorPane();
        previewPane.setEditorKit(new HTMLEditorKitBuilder().build());
        previewPane.setEditable(false);
        previewPane.setContentType("text/html");
        previewPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        JBScrollPane previewScroll = new JBScrollPane(previewPane);
        previewScroll.setBorder(JBUI.Borders.empty());
        showPreviewEmptyState();

        // Debounced single-click preview: selection snapshot on EDT → pooled read+render → EDT setText.
        tree.addTreeSelectionListener(e -> schedulePreview());

        // Master (tree + workflow) on the left, preview on the right. The preview gets an even
        // share by default so it's readable on first open (this is a viewing feature); the
        // proportion is then persisted per key, and the right pane can be dragged shut to reclaim
        // width in a side-anchored tool window.
        OnePixelSplitter browseSplitter =
                new OnePixelSplitter(false, "OpenSpec.BrowsePreview.proportion", 0.5f);
        browseSplitter.setFirstComponent(splitPane);
        browseSplitter.setSecondComponent(previewScroll);
        browseSplitter.setHonorComponentsMinimumSize(false);

        add(topPanel, BorderLayout.NORTH);
        add(browseSplitter, BorderLayout.CENTER);

        registerFileListener();
        refreshAsync();
    }

    // Accessible name of the preview pane, flipped between these on empty/render so assistive
    // tech (and UI-driver tests) can observe that a selection actually produced a rendered preview.
    static final String PREVIEW_RENDERED_NAME = "OpenSpec preview rendered";
    static final String PREVIEW_EMPTY_NAME = "OpenSpec preview empty";

    // Bumped on the EDT for every selection; a completed off-EDT render only applies if its
    // token is still current, so a slow render of an earlier selection can't overwrite a newer one.
    private int previewGeneration = 0;

    private void schedulePreview() {
        // Snapshot the selection on the EDT; the file read + render then runs off-EDT.
        PreviewSelection selection = snapshotSelection(tree.getSelectionPath());
        final int generation = ++previewGeneration;
        previewAlarm.cancelAllRequests();
        previewAlarm.addRequest(() -> renderPreview(selection, generation), 120);
    }

    /** Applies a preview mutation on the EDT only if no newer selection has superseded it. */
    private void applyIfCurrent(int generation, Runnable action) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (generation == previewGeneration) action.run();
        });
    }

    private PreviewSelection snapshotSelection(TreePath path) {
        if (path == null) return null;
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode node)) return null;
        if (!(node.getUserObject() instanceof SpecTreeModel.TreeNodeData data)) return null;

        SpecPreviewRenderer.PreviewKind kind = SpecPreviewRenderer.classify(data.type(), data.filePath());
        // For a Requirement node, the node's tooltip carries the plain requirement name (its file
        // path is the parent spec) — anchor the scroll to that requirement's section.
        String requirementName =
                data.type() == SpecTreeModel.TreeNodeType.REQUIREMENT ? data.tooltip() : null;
        return new PreviewSelection(data.filePath(), kind, requirementName);
    }

    private void renderPreview(PreviewSelection selection, int generation) {
        if (selection == null || selection.kind() == SpecPreviewRenderer.PreviewKind.NONE) {
            applyIfCurrent(generation, this::showPreviewEmptyState);
            return;
        }
        String fragment;
        try {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(selection.filePath());
            if (file == null || !file.isValid() || file.isDirectory()) {
                applyIfCurrent(generation, this::showPreviewEmptyState);
                return;
            }
            String markdown = VfsUtilCore.loadText(file);
            fragment = SpecPreviewRenderer.renderMarkdown(selection.kind(), markdown);
        } catch (Exception ex) {
            LOG.debug("Failed to render preview for " + selection.filePath(), ex);
            applyIfCurrent(generation, this::showPreviewEmptyState);
            return;
        }
        final String renderedFragment = fragment;
        applyIfCurrent(generation, () -> {
            String css = MarkdownHtmlRenderer.buildThemeStylesheet() + DeltaBadgeDecorator.badgeCss();
            previewPane.setText(MarkdownHtmlRenderer.wrapInHtml(css, renderedFragment));
            previewPane.setCaretPosition(0);
            if (selection.requirementName() != null) {
                previewPane.scrollToReference(RequirementAnchors.anchorId(selection.requirementName()));
            }
            previewPane.getAccessibleContext().setAccessibleName(PREVIEW_RENDERED_NAME);
        });
    }

    private void showPreviewEmptyState() {
        String css = MarkdownHtmlRenderer.buildThemeStylesheet() + DeltaBadgeDecorator.badgeCss();
        previewPane.setText(MarkdownHtmlRenderer.wrapInHtml(css, SpecPreviewRenderer.emptyState()));
        previewPane.setCaretPosition(0);
        previewPane.getAccessibleContext().setAccessibleName(PREVIEW_EMPTY_NAME);
    }

    private void refreshAsync() {
        // Ensure expansion state is captured on EDT before background work
        Runnable doRefresh = () -> {
            String query = searchField.getText();
            boolean hasFilter = query != null && !query.isBlank();
            Set<String> expandedLabels = hasFilter ? null : saveExpansionState();
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
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
                } catch (Exception e) {
                    LOG.warn("Failed to build tree model", e);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        DefaultMutableTreeNode errorRoot = new DefaultMutableTreeNode("OpenSpec");
                        errorRoot.add(new DefaultMutableTreeNode("Error loading — click Refresh to retry"));
                        tree.setModel(new DefaultTreeModel(errorRoot));
                        updateCliStatus();
                        updateAiStatus();
                    });
                }
            });
            workflowPanel.refresh();
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            doRefresh.run();
        } else {
            ApplicationManager.getApplication().invokeLater(doRefresh);
        }
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
                    com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(action, context, "SpecTree", null, null);
                }
            }
        } else if ("OpenSpec".equals(parent.getUserObject())) {
            // Root-level hint — trigger Init
            try {
                com.johnnyblabs.openspec.scaffolding.ScaffoldingService scaffolding =
                        project.getService(com.johnnyblabs.openspec.scaffolding.ScaffoldingService.class);
                scaffolding.initOpenSpec();
                refreshAsync();
            } catch (java.io.IOException ex) {
                com.johnnyblabs.openspec.util.OpenSpecNotifier.notify(project,
                        com.johnnyblabs.openspec.util.OpenSpecNotifier.GROUP_SYSTEM, "Initialize",
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
            case DELTA_SPEC -> {
                if (data.filePath() != null) {
                    contextMenu.add(new com.johnnyblabs.openspec.actions.DeltaSpecDiffAction(data.filePath()));
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

    void updateCliStatus() {
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
