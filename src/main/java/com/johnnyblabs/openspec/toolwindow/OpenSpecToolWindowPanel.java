package com.johnnyblabs.openspec.toolwindow;

import com.intellij.ide.util.treeView.PathElementIdProvider;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.openapi.Disposable;
import com.intellij.util.Alarm;
import com.johnnyblabs.openspec.ai.AiConfigListener;
import com.johnnyblabs.openspec.model.ChangeDeltaModel;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.MarkdownHtmlRenderer;
import com.johnnyblabs.openspec.actions.CreateDeltaSpecAction;
import com.johnnyblabs.openspec.actions.DeltaSpecDiffAction;
import com.johnnyblabs.openspec.actions.OpenSpecDataKeys;
import com.johnnyblabs.openspec.actions.OpenSpecValidateAction;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.validation.BuiltInValidator;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class OpenSpecToolWindowPanel extends JPanel implements DataProvider, Disposable {
    private static final Logger LOG = Logger.getInstance(OpenSpecToolWindowPanel.class);

    private final Project project;
    private final Tree tree;
    private final JLabel statusLabel;
    private final JLabel aiStatusLabel;
    private final WorkflowActionPanel workflowPanel;
    private final JEditorPane previewPane;
    private final Alarm refreshAlarm;
    private final Alarm previewAlarm;

    /** What the preview render path needs, snapshotted on the EDT at selection time. */
    record PreviewSelection(String filePath, SpecPreviewRenderer.PreviewKind kind,
                            String requirementName, String changeName, String openFilePath) {}

    // Per-change rendered-deltas cache, keyed on change name. Avoids re-spawning the CLI on
    // re-selection of the same change; cleared by the VFS listener when OpenSpec files change.
    private final java.util.concurrent.ConcurrentHashMap<String, String> changeDeltasCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Bumped whenever the deltas cache is invalidated (VFS change). An off-EDT CLI render captures
    // this before its slow call and only writes the cache if it's unchanged afterward — so a render
    // that completes after an invalidation cannot repopulate the just-cleared cache with stale deltas.
    private volatile int cacheEpoch = 0;

    // The change DIRECTORY currently rendered in the deltas view, read by the diff cross-link
    // HyperlinkListener to build the delta-spec path for a capability. Volatile: written on the EDT
    // when a deltas render applies, read on the EDT when a link fires.
    private volatile String deltasChangeDir;

    // Builds the tree model off the EDT. A field so tests can supply models (including failing ones).
    private final java.util.function.Supplier<DefaultTreeModel> modelBuilder;
    // Bumped on the EDT when a refresh starts; a built model applies only if no newer refresh started since.
    private int treeGeneration = 0;
    // True only while a rebuilt model is swapped in and its state restored, so the selection listeners
    // ignore the transient clear-and-reselect and the panel syncs once afterwards.
    private boolean restoringTree = false;
    // Whether the shown model has any change under Changes; picks the empty state's Propose link.
    private boolean modelHasChanges = true;
    // Whether a built model has been shown; until then a failed build shows the error node.
    private boolean treeLoaded = false;
    private int refreshRetryDelayMs = 1000;

    public OpenSpecToolWindowPanel(Project project) {
        this(project, () -> new SpecTreeModel(project).buildModel());
    }

    /** {@code modelBuilder} builds the tree off the EDT; tests pass their own, so even the first load is theirs. */
    OpenSpecToolWindowPanel(Project project, java.util.function.Supplier<DefaultTreeModel> modelBuilder) {
        super(new BorderLayout());
        this.project = project;
        this.modelBuilder = modelBuilder;
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        this.previewAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

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
            if (restoringTree) return;
            // The current selection, not e.getPath(): on a clear that is the path being removed.
            String changeName = SpecTreeModel.resolveChangeName(tree.getSelectionPath());
            if (changeName != null) {
                workflowPanel.setActiveChange(changeName);
            }
        });

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
        bottomPanel.add(workflowPanel, BorderLayout.CENTER);
        bottomPanel.add(statusBar, BorderLayout.SOUTH);

        // Top panel: toolbar (no tree filter — spec content-search lives in Search Everywhere)
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(createActionToolbar(), BorderLayout.NORTH);

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

        // Diff cross-link: a capability section in the consolidated deltas view emits an
        // openspec-diff:<capability> anchor; resolve an activated one to DeltaSpecDiffAction for that
        // capability. Non-diff links (e.g. a real http link) resolve to null and are left untouched.
        previewPane.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String href = e.getDescription();
            if (SpecPreviewRenderer.PROPOSE_HREF.equals(href) || SpecPreviewRenderer.EXPLORE_HREF.equals(href)) {
                String actionId = SpecPreviewRenderer.PROPOSE_HREF.equals(href) ? "OpenSpec.Propose" : "OpenSpec.Explore";
                AnAction action = ActionManager.getInstance().getAction(actionId);
                if (action != null) {
                    com.johnnyblabs.openspec.actions.ActionLauncher.launch(action, previewPane, "OpenSpecPreview");
                }
                return;
            }
            String validateChange = SpecPreviewRenderer.validateChangeFromHref(href);
            if (validateChange != null) {
                OpenSpecValidateAction.validateChange(project, validateChange);
                return;
            }
            String openFile = SpecPreviewRenderer.openFileFromHref(href);
            if (openFile != null) {
                String dir = deltasChangeDir;
                VirtualFile file = dir == null ? null : LocalFileSystem.getInstance().findFileByPath(dir + "/" + openFile);
                if (file != null && file.isValid() && !file.isDirectory()) {
                    new OpenFileDescriptor(project, file).navigate(true);
                }
                return;
            }
            String capability = DeltaDiffAnchor.capabilityFromHref(href);
            if (capability == null) return;
            String changeDir = deltasChangeDir;
            if (changeDir == null) return;
            String deltaSpecPath = changeDir + "/specs/" + capability + "/spec.md";
            com.johnnyblabs.openspec.actions.ActionLauncher.launch(
                    new DeltaSpecDiffAction(deltaSpecPath), previewPane, "OpenSpecPreview");
        });

        showPreviewEmptyState();

        // Debounced single-click preview: selection snapshot on EDT → pooled read+render → EDT setText.
        tree.addTreeSelectionListener(e -> {
            if (!restoringTree) schedulePreview();
        });

        // Master (tree + workflow) on the left, preview on the right. The Changes tree is the
        // load-bearing surface, so it gets the larger default share; the preview earns width only
        // when a change is selected. The proportion is then persisted per key, and the right pane
        // can be dragged shut to reclaim width in a side-anchored tool window.
        OnePixelSplitter browseSplitter =
                new OnePixelSplitter(false, "OpenSpec.BrowsePreview.proportion", 0.68f);
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
    // A successful consolidated change-deltas render flips to one of these. The "badged" variant is
    // set only when the rendered fragment actually carries an operation badge span — the uiSmoke
    // journey waits on it to prove, in one observable the driver can read, both that the deltas
    // render fired AND that a badge is present (the JEditorPane's HTML body is not readable via hasText).
    static final String PREVIEW_CHANGE_DELTAS_NAME = "OpenSpec preview change deltas";
    static final String PREVIEW_CHANGE_DELTAS_BADGED_NAME = "OpenSpec preview change deltas badged";
    // The CLI-unavailable placeholder, distinct from the empty state so the two can be told apart.
    static final String PREVIEW_CLI_UNAVAILABLE_NAME = "OpenSpec preview needs CLI";
    static final String PREVIEW_CLI_REJECTED_NAME = "OpenSpec preview: CLI could not read the change";

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

    static PreviewSelection snapshotSelection(TreePath path) {
        if (path == null) return null;
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode node)) return null;
        if (!(node.getUserObject() instanceof SpecTreeModel.TreeNodeData data)) return null;

        // An artifact sits directly under its change node, whose path is the change directory.
        String changeDir = enclosingChangeDir(path);
        SpecPreviewRenderer.PreviewKind kind =
                SpecPreviewRenderer.classify(data.type(), data.filePath(), changeDir);
        // The tree no longer holds requirement nodes (plain specs open in the editor), so there is no
        // requirement anchor to resolve here.
        String requirementName = null;
        if (kind != SpecPreviewRenderer.PreviewKind.CHANGE_DELTAS) {
            return new PreviewSelection(data.filePath(), kind, requirementName, null, null);
        }
        if (data.type().isChange()) {
            // The CLI is keyed by change name; the node's path is the change dir, whose basename is the name.
            String name = data.changeName() != null ? data.changeName() : new java.io.File(data.filePath()).getName();
            return new PreviewSelection(data.filePath(), kind, requirementName, name, null);
        }
        // An artifact previews its change: the name and directory come from the change node (a no-CLI
        // fallback artifact carries no change name), and the diff links resolve against that directory.
        String name = SpecTreeModel.resolveChangeName(path);
        if (name == null) name = new java.io.File(changeDir).getName();
        String own = data.filePath();
        String openFilePath = own != null && !SpecPreviewRenderer.isGlob(own) ? own : null;
        return new PreviewSelection(changeDir, kind, requirementName, name, openFilePath);
    }

    private static String enclosingChangeDir(TreePath path) {
        TreePath parent = path.getParentPath();
        if (parent != null && parent.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof SpecTreeModel.TreeNodeData data
                && data.type().isChange()) {
            return data.filePath();
        }
        return null;
    }

    private void renderPreview(PreviewSelection selection, int generation) {
        if (selection == null || selection.kind() == SpecPreviewRenderer.PreviewKind.NONE) {
            applyIfCurrent(generation, this::showPreviewEmptyState);
            return;
        }
        if (selection.kind() == SpecPreviewRenderer.PreviewKind.CHANGE_DELTAS) {
            renderChangeDeltasPreview(selection, generation);
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
            String css = MarkdownHtmlRenderer.buildThemeStylesheet() + DeltaBadgeDecorator.badgeCss() + SpecPreviewRenderer.paneCss();
            previewPane.setText(MarkdownHtmlRenderer.wrapInHtml(css, renderedFragment));
            previewPane.setCaretPosition(0);
            if (selection.requirementName() != null) {
                previewPane.scrollToReference(RequirementAnchors.anchorId(selection.requirementName()));
            }
            previewPane.getAccessibleContext().setAccessibleName(PREVIEW_RENDERED_NAME);
        });
    }

    private void showPreviewEmptyState() {
        String css = MarkdownHtmlRenderer.buildThemeStylesheet() + DeltaBadgeDecorator.badgeCss() + SpecPreviewRenderer.paneCss();
        previewPane.setText(MarkdownHtmlRenderer.wrapInHtml(css, SpecPreviewRenderer.emptyState(modelHasChanges)));
        previewPane.setCaretPosition(0);
        previewPane.getAccessibleContext().setAccessibleName(PREVIEW_EMPTY_NAME);
    }

    /**
     * Renders the consolidated deltas for a change node. Runs off the EDT (this method is invoked on
     * the {@code previewAlarm} pooled thread), generation-guarded like every preview path. Serves a
     * per-change cache first; otherwise spawns {@code openspec show <name> --type change --json},
     * parses stdout only (stderr carries a spurious flag warning on success), and renders. A CLI that
     * isn't there or can't start yields the cli-unavailable placeholder; a CLI that ran but couldn't
     * read the change yields the cli-rejected placeholder with its reason, never cached. Neither is
     * the no-deltas empty state.
     */
    private void renderChangeDeltasPreview(PreviewSelection selection, int generation) {
        String name = selection.changeName();
        String changeDir = selection.filePath();
        if (name == null || name.isBlank()) {
            applyIfCurrent(generation, this::showPreviewEmptyState);
            return;
        }
        String openLink = openLinkPath(selection.openFilePath(), changeDir);

        int epochAtStart = cacheEpoch;
        String cached = changeDeltasCache.get(name);
        if (cached != null) {
            applyChangeDeltas(generation, SpecPreviewRenderer.withOpenLink(cached, openLink), changeDir);
            return;
        }

        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null || !detection.isAvailable()) {
            applyCliUnavailable(generation, changeDir, name, openLink);
            return;
        }

        SpecPreviewRenderer.ChangeShowOutcome outcome;
        try {
            outcome = SpecPreviewRenderer.changeShowOutcome(name,
                    CliRunner.run(project, ChangeDeltaModel.changeShowArgs(name)));
        } catch (CliRunner.CliException ex) {
            LOG.debug("openspec show could not run for change " + name, ex);
            outcome = SpecPreviewRenderer.changeShowOutcome(name, ex);
        } catch (com.intellij.openapi.progress.ProcessCanceledException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Building the command line can fail before anything runs; never leave the pane on the old selection.
            LOG.debug("openspec show could not start for change " + name, ex);
            applyCliUnavailable(generation, changeDir, name, openLink);
            return;
        }
        switch (outcome.kind()) {
            case UNAVAILABLE -> {
                applyCliUnavailable(generation, changeDir, name, openLink);
                return;
            }
            case REJECTED -> {
                // Not cached: the user may fix the change (write its proposal, restore its directory) and reselect.
                applyCliRejected(generation, changeDir, SpecPreviewRenderer.withOpenLink(outcome.fragment(), openLink));
                return;
            }
            case DELTAS -> {
            }
        }
        String fragment = outcome.fragment();
        String validationLine = cliValidationLine(name);
        if (validationLine != null) {
            fragment = SpecPreviewRenderer.withValidationLine(fragment, validationLine);
        }

        // Only cache if no invalidation raced in during the CLI calls — otherwise this now-stale
        // fragment would repopulate a just-cleared cache and be served on the next re-selection.
        // Without a verdict it isn't cached either, so reselecting the change asks again.
        if (outcome.cacheable() && validationLine != null && cacheEpoch == epochAtStart) {
            changeDeltasCache.put(name, fragment);
        }
        applyChangeDeltas(generation, SpecPreviewRenderer.withOpenLink(fragment, openLink), changeDir);
    }

    /**
     * The "Validate" line for a change: the same CLI run and reading as the Validate action for one change, or null
     * when the CLI gave no verdict. Runs off the EDT.
     */
    private String cliValidationLine(String name) {
        try {
            return SpecPreviewRenderer.cliValidationLine(name,
                    CliRunner.run(project, OpenSpecValidateAction.changeCliArgs(name)));
        } catch (com.intellij.openapi.progress.ProcessCanceledException ex) {
            throw ex;
        } catch (CliRunner.CliException | RuntimeException ex) {
            LOG.debug("openspec validate gave no verdict for change " + name, ex);
            return null;
        }
    }

    private void applyChangeDeltas(int generation, String fragment, String changeDir) {
        applyIfCurrent(generation, () -> {
            String css = MarkdownHtmlRenderer.buildThemeStylesheet() + DeltaBadgeDecorator.badgeCss() + SpecPreviewRenderer.paneCss();
            previewPane.setText(MarkdownHtmlRenderer.wrapInHtml(css, fragment));
            previewPane.setCaretPosition(0);
            deltasChangeDir = changeDir;
            boolean badged = fragment.contains(DeltaBadgeDecorator.BADGE_CLASS);
            previewPane.getAccessibleContext().setAccessibleName(
                    badged ? PREVIEW_CHANGE_DELTAS_BADGED_NAME : PREVIEW_CHANGE_DELTAS_NAME);
        });
    }

    /**
     * The artifact's path relative to the change directory when it is an existing file inside it, for the
     * open-in-editor link; otherwise null. Runs off the EDT.
     */
    private static String openLinkPath(String openFilePath, String changeDir) {
        if (openFilePath == null || changeDir == null || !openFilePath.startsWith(changeDir + "/")) return null;
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(openFilePath);
        if (file == null || !file.isValid() || file.isDirectory()) return null;
        return openFilePath.substring(changeDir.length() + 1);
    }

    private void applyCliRejected(int generation, String changeDir, String fragment) {
        applyIfCurrent(generation, () -> {
            String css = MarkdownHtmlRenderer.buildThemeStylesheet() + DeltaBadgeDecorator.badgeCss() + SpecPreviewRenderer.paneCss();
            previewPane.setText(MarkdownHtmlRenderer.wrapInHtml(css, fragment));
            previewPane.setCaretPosition(0);
            deltasChangeDir = changeDir;
            previewPane.getAccessibleContext().setAccessibleName(PREVIEW_CLI_REJECTED_NAME);
        });
    }

    private void applyCliUnavailable(int generation, String changeDir, String changeName, String openLink) {
        String placeholder = SpecPreviewRenderer.withOpenLink(SpecPreviewRenderer.cliUnavailablePlaceholder(changeName), openLink);
        // Without a CLI the plugin's own check is all there is; it is labelled as such and has no link, since
        // Validate without a CLI runs this same check.
        BuiltInValidator validator = project.getService(BuiltInValidator.class);
        String fragment = validator == null ? placeholder
                : SpecPreviewRenderer.withValidationLine(placeholder, SpecPreviewRenderer.validationLine(
                        SpecPreviewRenderer.BUILT_IN_VALIDATION_LABEL, validator.validateChange(changeName), null));
        applyIfCurrent(generation, () -> {
            String css = MarkdownHtmlRenderer.buildThemeStylesheet() + DeltaBadgeDecorator.badgeCss() + SpecPreviewRenderer.paneCss();
            previewPane.setText(MarkdownHtmlRenderer.wrapInHtml(css, fragment));
            previewPane.setCaretPosition(0);
            deltasChangeDir = changeDir;
            previewPane.getAccessibleContext().setAccessibleName(PREVIEW_CLI_UNAVAILABLE_NAME);
        });
    }

    private void refreshAsync() {
        refreshAsync(false);
    }

    /**
     * Rebuilds the tree off the EDT and swaps it in on the EDT, keeping the user's selection and expansion. A
     * {@code retry} is the single quiet retry after a failed build; it never schedules another.
     */
    private void refreshAsync(boolean retry) {
        Runnable doRefresh = () -> {
            final int generation = ++treeGeneration;
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    DefaultTreeModel model = modelBuilder.get();
                    ApplicationManager.getApplication().invokeLater(() -> applyModel(model, generation));
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> onBuildFailed(e, generation, retry));
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

    /** The Changes node of a model, or null. */
    private static DefaultMutableTreeNode changesNode(DefaultTreeModel model) {
        if (!(model.getRoot() instanceof DefaultMutableTreeNode root)) return null;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof DefaultMutableTreeNode child
                    && child.getUserObject() instanceof SpecTreeModel.TreeNodeData data
                    && data.type() == SpecTreeModel.TreeNodeType.CHANGES) {
                return child;
            }
        }
        return null;
    }

    private static TreePath changesPath(DefaultTreeModel model) {
        DefaultMutableTreeNode changes = changesNode(model);
        return changes == null ? null : new TreePath(changes.getPath());
    }

    /** Whether the model lists any change; with none, Changes holds only the propose hint. */
    static boolean hasChanges(DefaultTreeModel model) {
        DefaultMutableTreeNode changes = changesNode(model);
        if (changes == null) return false;
        for (int i = 0; i < changes.getChildCount(); i++) {
            if (changes.getChildAt(i) instanceof DefaultMutableTreeNode child
                    && child.getUserObject() instanceof SpecTreeModel.TreeNodeData data
                    && data.type().isChange()) {
                return true;
            }
        }
        return false;
    }

    /** Swaps in a rebuilt model, restoring selection and expansion by node identity ({@link TreeState}). */
    private void applyModel(DefaultTreeModel model, int generation) {
        if (generation != treeGeneration) return; // a newer refresh started since this one
        // Captured now, not when the refresh started, so anything the user did during the build is kept.
        String selectedId = nodeId(tree.getSelectionPath());
        TreeState state = TreeState.createOn(tree, new TreePath(tree.getModel().getRoot()));
        state.setScrollToSelection(false);
        restoringTree = true;
        modelHasChanges = hasChanges(model);
        try {
            tree.setModel(model);
            state.applyTo(tree);
            // The first load has no expansion to restore; open Changes so the user sees their changes. Later
            // loads skip this, so TreeState keeps whatever the user expanded or collapsed since.
            if (!treeLoaded && modelHasChanges) {
                tree.expandPath(changesPath(model));
            }
            // TreeState falls back to a sibling or the parent when the selected node is gone; the user didn't
            // choose that node, so clear instead.
            TreePath restored = tree.getSelectionPath();
            if (restored != null && selectedId != null && !selectedId.equals(nodeId(restored))) {
                tree.clearSelection();
            }
        } finally {
            restoringTree = false;
        }
        treeLoaded = true;

        TreePath selection = tree.getSelectionPath();
        if (selection != null) {
            Rectangle bounds = tree.getPathBounds(selection);
            if (bounds != null && !tree.getVisibleRect().contains(bounds)) {
                tree.scrollPathToVisible(selection);
            }
            String changeName = SpecTreeModel.resolveChangeName(selection);
            if (changeName != null) workflowPanel.setActiveChange(changeName);
        }
        // Once, for the restored (or cleared) selection: re-renders the same node with its new content.
        schedulePreview();
        updateCliStatus();
        updateAiStatus();
    }

    /**
     * A build that throws is a transient race (a change directory removed mid-scan, a file caught half-written);
     * the burst of file changes behind it normally triggers a refresh that succeeds. So a loaded tree is kept as it
     * is, nothing is shown, and the build is retried once in case no further change arrives.
     */
    private void onBuildFailed(Exception e, int generation, boolean retry) {
        if (generation != treeGeneration) return;
        LOG.warn("Failed to build tree model", e);
        if (treeLoaded) {
            if (!retry) refreshAlarm.addRequest(() -> refreshAsync(true), refreshRetryDelayMs);
            return;
        }
        DefaultMutableTreeNode errorRoot = new DefaultMutableTreeNode("OpenSpec");
        errorRoot.add(new DefaultMutableTreeNode("Error loading — click Refresh to retry"));
        tree.setModel(new DefaultTreeModel(errorRoot));
        updateCliStatus();
        updateAiStatus();
    }

    private static String nodeId(TreePath path) {
        if (path == null) return null;
        if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof PathElementIdProvider provider) {
            return provider.getPathElementId();
        }
        return String.valueOf(path.getLastPathComponent());
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
                            () -> new OpenFileDescriptor(project, file).navigate(true));
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
                    com.johnnyblabs.openspec.actions.ActionLauncher.launch(action, tree, "SpecTree");
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

        DefaultActionGroup contextMenu = contextMenuFor(data);
        if (contextMenu.getChildrenCount() > 0) {
            ActionPopupMenu popupMenu = ActionManager.getInstance()
                    .createActionPopupMenu("OpenSpecContextMenu", contextMenu);
            popupMenu.getComponent().show(tree, e.getX(), e.getY());
        }
    }

    /** The right-click menu for a tree node; package-private so tests can check its contents without a popup. */
    DefaultActionGroup contextMenuFor(SpecTreeModel.TreeNodeData data) {
        DefaultActionGroup contextMenu = new DefaultActionGroup();

        switch (data.type()) {
            case CHANGE, CHANGE_DONE, CHANGE_ARCHIVE_READY -> {
                contextMenu.add(ActionManager.getInstance().getAction("OpenSpec.ExploreChange"));
                contextMenu.add(ActionManager.getInstance().getAction("OpenSpec.ReviseChange"));
                contextMenu.addSeparator();
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
                contextMenu.add(ActionManager.getInstance().getAction("OpenSpec.Explore"));
                contextMenu.add(ActionManager.getInstance().getAction("OpenSpec.Propose"));
            }
            case DELTA_SPEC -> {
                if (data.filePath() != null) {
                    contextMenu.add(new com.johnnyblabs.openspec.actions.DeltaSpecDiffAction(data.filePath()));
                    contextMenu.add(new AnAction("Open File") {
                        @Override
                        public void actionPerformed(@org.jetbrains.annotations.NotNull AnActionEvent ae) {
                            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(data.filePath());
                            if (file != null) {
                                new OpenFileDescriptor(project, file).navigate(true);
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
                                new OpenFileDescriptor(project, file).navigate(true);
                            }
                        }
                    });
                }
            }
            case ARTIFACT_BLOCKED -> {
                // Info only, no actions
            }
            default -> {
            }
        }
        return contextMenu;
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

    @org.jetbrains.annotations.TestOnly
    void setRefreshRetryDelayForTest(int retryDelayMs) {
        this.refreshRetryDelayMs = retryDelayMs;
    }

    @org.jetbrains.annotations.TestOnly
    WorkflowActionPanel workflowPanelForTest() {
        return workflowPanel;
    }

    @org.jetbrains.annotations.TestOnly
    Tree treeForTest() {
        return tree;
    }

    @org.jetbrains.annotations.TestOnly
    javax.accessibility.AccessibleContext previewAccessibleContextForTest() {
        return previewPane.getAccessibleContext();
    }

    @org.jetbrains.annotations.TestOnly
    String previewHtmlForTest() {
        return previewPane.getText();
    }

    @org.jetbrains.annotations.TestOnly
    JEditorPane previewPaneForTest() {
        return previewPane;
    }

    @org.jetbrains.annotations.TestOnly
    void seedDeltasCacheForTest(String changeName, String fragment) {
        changeDeltasCache.put(changeName, fragment);
    }

    @org.jetbrains.annotations.TestOnly
    String previewAccessibleNameForTest() {
        return previewPane.getAccessibleContext().getAccessibleName();
    }

    @org.jetbrains.annotations.TestOnly
    javax.swing.tree.TreeModel treeModelForTest() {
        return tree.getModel();
    }

    @org.jetbrains.annotations.TestOnly
    String cliStatusTextForTest() {
        return statusLabel.getText();
    }

    void updateCliStatus() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null && !detection.isDetectedOnce()) {
            // Startup detection hasn't finished yet: the CLI's availability is unknown, not missing.
            statusLabel.setText("CLI: detecting…");
            statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        } else if (detection != null && detection.isAvailable()) {
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

    @Override
    public void dispose() {
        // The Alarms (parented to this) and the VFS message-bus connection (connect(this)) are
        // released when this panel is disposed via its Content disposer (see OpenSpecToolWindowFactory).
        // Wiring the disposer is what makes createNormalContent rebuilds not accumulate subscriptions.
    }

    private void registerFileListener() {
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file != null && OpenSpecFileUtil.isUnderOpenSpec(file, project)) {
                        // Invalidate cached delta renders — a change's specs/** may have changed.
                        // Bump the epoch first so an in-flight render can't repopulate after this clear.
                        cacheEpoch++;
                        changeDeltasCache.clear();
                        debouncedRefresh();
                        break;
                    }
                }
            }
        });

        // AI config changed at runtime (Settings applied / Setup Wizard finished): re-offer or
        // withdraw the Direct API delivery option immediately, without waiting for an unrelated
        // refresh. Connected via connect(this) so the subscription is released on panel dispose.
        project.getMessageBus().connect(this).subscribe(AiConfigListener.TOPIC,
                () -> ApplicationManager.getApplication().invokeLater(
                        workflowPanel::refreshDeliveryOptions));

        // Any completed CLI detection (project open, tool-window/focus re-check, Settings, manual refresh) redraws
        // the CLI status. The label is built once; without this it kept whatever it read at construction, which
        // could be mid-startup-detection, until a manual refresh.
        project.getMessageBus().connect(this).subscribe(CliDetectionService.TOPIC,
                (CliDetectionService.Listener) (available, version) -> ApplicationManager.getApplication()
                        .invokeLater(this::updateCliStatus, project.getDisposed()));
    }

    private void debouncedRefresh() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (!settings.isAutoRefresh()) return;

        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(this::refreshAsync, 300);
    }
}
