package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.coordination.ContextStoreEntry;
import com.johnnyblabs.openspec.coordination.CoordinationData;
import com.johnnyblabs.openspec.coordination.CoordinationService;
import com.johnnyblabs.openspec.coordination.CoordinationTier;
import com.johnnyblabs.openspec.coordination.Diagnostic;
import com.johnnyblabs.openspec.coordination.InitiativeArtifact;
import com.johnnyblabs.openspec.coordination.InitiativeEntry;
import com.johnnyblabs.openspec.coordination.InitiativeStatus;
import com.johnnyblabs.openspec.coordination.StoreEntry;
import com.johnnyblabs.openspec.coordination.WorkspaceEntry;
import com.johnnyblabs.openspec.coordination.WorksetEntry;
import com.johnnyblabs.openspec.services.WorkflowSchemaContextService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

/**
 * Tool-window tab presenting the OpenSpec 1.4 coordination collections — workspaces, context
 * stores, and initiatives — at the tier resolved by {@link CoordinationService}. Read-only in
 * the Awareness tier; adds initiative-artifact navigation and CLI-delegated write actions in
 * the Full tier.
 *
 * <p>All resolution runs on a pooled thread; the tree is rebuilt on the EDT via
 * {@code invokeLater}.
 */
public final class CoordinationPanel extends JPanel {

    private final Project project;
    private final Tree tree;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Coordination");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JBLabel statusLabel = new JBLabel();

    private final JButton refreshButton = new JButton("Refresh");
    private final JButton createInitiativeButton = new JButton("New Initiative");
    private final JButton setupStoreButton = new JButton("Set Up Context Store");
    private final JButton setupWorkspaceButton = new JButton("Set Up Workspace");

    public CoordinationPanel(Project project) {
        this(project, null);
    }

    public CoordinationPanel(Project project, @Nullable CoordinationData initialData) {
        super(new BorderLayout());
        this.project = project;

        tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new CoordinationCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleOpen();
                }
            }
        });

        add(buildToolbar(), BorderLayout.NORTH);
        add(new JBScrollPane(tree), BorderLayout.CENTER);

        if (initialData != null) {
            render(initialData);
        } else {
            statusLabel.setText("Loading coordination state…");
            reload();
        }
    }

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(4)));
        refreshButton.addActionListener(e -> reload());
        createInitiativeButton.addActionListener(e -> onCreateInitiative());
        setupStoreButton.addActionListener(e -> onSetupContextStore());
        setupWorkspaceButton.addActionListener(e -> onSetupWorkspace());
        bar.add(refreshButton);
        bar.add(createInitiativeButton);
        bar.add(setupStoreButton);
        bar.add(setupWorkspaceButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(bar, BorderLayout.NORTH);
        statusLabel.setBorder(JBUI.Borders.empty(2, 6, 4, 6));
        statusLabel.setComponentStyle(com.intellij.util.ui.UIUtil.ComponentStyle.SMALL);
        north.add(statusLabel, BorderLayout.SOUTH);
        return north;
    }

    /** Reloads coordination state off the EDT and rebuilds the tree on the EDT. */
    public void reload() {
        refreshButton.setEnabled(false);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CoordinationService service = project.getService(CoordinationService.class);
            boolean coordinationMode = isCoordinationModeActive();
            CoordinationData data = service != null
                    ? service.getCoordinationData(coordinationMode)
                    : CoordinationData.EMPTY_HIDDEN;
            ApplicationManager.getApplication().invokeLater(() -> {
                render(data);
                refreshButton.setEnabled(true);
            });
        });
    }

    private boolean isCoordinationModeActive() {
        WorkflowSchemaContextService ctx = project.getService(WorkflowSchemaContextService.class);
        return ctx != null && ctx.hasNonDefaultModeCached();
    }

    private void render(CoordinationData data) {
        root.removeAllChildren();

        if (data.storesAreLeadModel()) {
            // OpenSpec 1.5: stores/worksets are the canonical lead model, read-only.
            renderStoreGroups(root, data);
            if (data.legacyStateExists()) {
                // Demote any surviving 1.4 state to a muted, read-only group. No migration.
                int legacyCount = data.workspaces().size() + data.contextStores().size()
                        + data.initiatives().size();
                DefaultMutableTreeNode legacy =
                        new DefaultMutableTreeNode(new GroupNode("Legacy (pre-1.5)", legacyCount, true));
                renderLegacyGroups(legacy, data);
                root.add(legacy);
            }
        } else {
            renderLegacyGroups(root, data);
        }

        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }

        applyTier(data);
    }

    /** Read-only 1.5 Stores and Worksets groups. */
    private void renderStoreGroups(DefaultMutableTreeNode parent, CoordinationData data) {
        DefaultMutableTreeNode stores = new DefaultMutableTreeNode(new GroupNode("Stores", data.stores().size(), false));
        if (data.stores().isEmpty()) {
            stores.add(new DefaultMutableTreeNode(new InfoNode("No stores")));
        } else {
            for (StoreEntry s : data.stores()) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(s);
                for (Diagnostic d : s.diagnostics()) {
                    if (d.message() != null || d.fix() != null) {
                        node.add(new DefaultMutableTreeNode(new DiagnosticNode(d)));
                    }
                }
                stores.add(node);
            }
        }
        parent.add(stores);

        DefaultMutableTreeNode worksets = new DefaultMutableTreeNode(new GroupNode("Worksets", data.worksets().size(), false));
        if (data.worksets().isEmpty()) {
            worksets.add(new DefaultMutableTreeNode(new InfoNode("No worksets")));
        } else {
            for (WorksetEntry w : data.worksets()) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(w);
                for (WorksetEntry.Member m : w.members()) {
                    node.add(new DefaultMutableTreeNode(m));
                }
                worksets.add(node);
            }
        }
        parent.add(worksets);
    }

    /** The legacy 1.4 Workspaces / Context Stores / Initiatives groups. */
    private void renderLegacyGroups(DefaultMutableTreeNode parent, CoordinationData data) {
        boolean muted = parent != root;

        DefaultMutableTreeNode workspaces = new DefaultMutableTreeNode(new GroupNode("Workspaces", data.workspaces().size(), muted));
        if (data.workspaces().isEmpty()) {
            workspaces.add(new DefaultMutableTreeNode(new InfoNode("No workspaces")));
        } else {
            for (WorkspaceEntry w : data.workspaces()) {
                workspaces.add(new DefaultMutableTreeNode(w));
            }
        }
        parent.add(workspaces);

        DefaultMutableTreeNode stores = new DefaultMutableTreeNode(new GroupNode("Context Stores", data.contextStores().size(), muted));
        if (data.contextStores().isEmpty()) {
            stores.add(new DefaultMutableTreeNode(new InfoNode("No context stores")));
        } else {
            for (ContextStoreEntry s : data.contextStores()) {
                stores.add(new DefaultMutableTreeNode(s));
            }
        }
        parent.add(stores);

        DefaultMutableTreeNode initiatives = new DefaultMutableTreeNode(new GroupNode("Initiatives", data.initiatives().size(), muted));
        if (data.initiatives().isEmpty()) {
            initiatives.add(new DefaultMutableTreeNode(new InfoNode(
                    data.contextStores().isEmpty()
                            ? "No initiatives — set up a context store first"
                            : "No initiatives")));
        } else {
            for (InitiativeEntry i : data.initiatives()) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(i);
                for (InitiativeArtifact artifact : InitiativeArtifact.values()) {
                    node.add(new DefaultMutableTreeNode(new ArtifactNode(i, artifact)));
                }
                initiatives.add(node);
            }
        }
        parent.add(initiatives);
    }

    private void applyTier(CoordinationData data) {
        // The 1.5 store/workset surface is read-only in this change: no write actions for stores or
        // worksets. The legacy write buttons are only meaningful (and only enabled) in the legacy
        // Full tier, which is unreachable once stores are the lead model; hide them then.
        boolean full = data.tier().allowsWriteActions();
        boolean showLegacyButtons = !data.storesAreLeadModel();
        createInitiativeButton.setVisible(showLegacyButtons);
        setupStoreButton.setVisible(showLegacyButtons);
        setupWorkspaceButton.setVisible(showLegacyButtons);
        createInitiativeButton.setEnabled(full);
        setupStoreButton.setEnabled(full);
        setupWorkspaceButton.setEnabled(full);

        if (data.storesAreLeadModel()) {
            String source = data.storesSourcedFromCli() ? "OpenSpec CLI" : "on-disk state";
            String legacy = data.legacyStateExists() ? " Legacy pre-1.5 state is shown read-only." : "";
            statusLabel.setText("Stores & worksets (read-only) — sourced from " + source + "." + legacy);
        } else {
            String source = data.sourcedFromCli() ? "OpenSpec CLI" : "on-disk state";
            if (full) {
                statusLabel.setText("Full — sourced from " + source);
            } else {
                statusLabel.setText("Awareness (read-only) — sourced from " + source
                        + ". OpenSpec CLI 1.4+ is required for coordination actions.");
            }
        }
    }

    // ---- open / navigation ---------------------------------------------------

    private void handleOpen() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;
        Object node = path.getLastPathComponent();
        if (!(node instanceof DefaultMutableTreeNode dmtn)) return;
        Object payload = dmtn.getUserObject();
        if (payload instanceof ArtifactNode artifactNode) {
            openArtifact(artifactNode);
        }
    }

    private void openArtifact(ArtifactNode artifactNode) {
        // Resolve existence and refresh VFS off the EDT — both touch disk.
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path existing = artifactNode.artifact().resolveExistingPath(artifactNode.initiative());
            VirtualFile vf = existing != null
                    ? LocalFileSystem.getInstance().refreshAndFindFileByNioFile(existing)
                    : null;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (existing == null) {
                    Messages.showInfoMessage(project,
                            artifactNode.artifact().displayLabel() + " has not been created for initiative '"
                                    + artifactNode.initiative().id() + "'.",
                            "Artifact Not Created");
                } else if (vf != null) {
                    FileEditorManager.getInstance(project).openFile(vf, true);
                } else {
                    Messages.showErrorDialog(project, "Could not open " + existing, "Open Failed");
                }
            });
        });
    }

    // ---- write actions -------------------------------------------------------

    private void onCreateInitiative() {
        String id = Messages.showInputDialog(project, "Initiative id:", "New Initiative", null);
        if (id == null || id.isBlank()) return;
        String title = Messages.showInputDialog(project, "Initiative title:", "New Initiative", null);
        if (title == null || title.isBlank()) return;
        runWrite(service -> service.createInitiative(id.trim(), title.trim()));
    }

    private void onSetupContextStore() {
        String id = Messages.showInputDialog(project,
                "Context store id (leave blank for managed default):", "Set Up Context Store", null);
        if (id == null) return;
        runWrite(service -> service.setupContextStore(id.isBlank() ? null : id.trim()));
    }

    private void onSetupWorkspace() {
        String name = Messages.showInputDialog(project, "Workspace name:", "Set Up Workspace", null);
        if (name == null || name.isBlank()) return;
        runWrite(service -> service.setupWorkspace(name.trim()));
    }

    private void runWrite(java.util.function.Function<CoordinationService, CoordinationService.WriteResult> action) {
        setWriteButtonsEnabled(false);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CoordinationService service = project.getService(CoordinationService.class);
            CoordinationService.WriteResult result = service != null
                    ? action.apply(service)
                    : CoordinationService.WriteResult.fail("Coordination service unavailable.");
            // Refresh VFS for the newly created file off the EDT; open it on the EDT.
            VirtualFile created = (result.success() && result.createdPath() != null)
                    ? LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(result.createdPath()))
                    : null;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (result.success()) {
                    if (created != null) {
                        FileEditorManager.getInstance(project).openFile(created, true);
                    }
                    reload(); // re-enables buttons via render→applyTier
                } else {
                    Messages.showErrorDialog(project, result.message(), "Coordination Action Failed");
                    setWriteButtonsEnabled(true);
                }
            });
        });
    }

    private void setWriteButtonsEnabled(boolean enabled) {
        createInitiativeButton.setEnabled(enabled);
        setupStoreButton.setEnabled(enabled);
        setupWorkspaceButton.setEnabled(enabled);
    }

    // ---- tree node payloads --------------------------------------------------

    private record GroupNode(String label, int count, boolean muted) {
    }

    private record InfoNode(String text) {
    }

    private record ArtifactNode(InitiativeEntry initiative, InitiativeArtifact artifact) {
    }

    private record DiagnosticNode(Diagnostic diagnostic) {
    }

    private static final class CoordinationCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)) return;
            Object payload = node.getUserObject();
            boolean muted = isUnderMutedGroup(node);
            if (payload instanceof GroupNode group) {
                append(group.label(), group.muted()
                        ? SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
                        : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                append("  (" + group.count() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else if (payload instanceof InfoNode info) {
                append(info.text(), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
            } else if (payload instanceof StoreEntry s) {
                append(s.id());
                if (s.root() != null) {
                    append("  " + s.root(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                append1_5StoreHealth(s);
            } else if (payload instanceof WorksetEntry w) {
                append(w.name());
                append("  (" + w.members().size() + " member" + (w.members().size() == 1 ? "" : "s") + ")",
                        SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else if (payload instanceof WorksetEntry.Member m) {
                append(m.name());
                if (m.path() != null) {
                    append("  " + m.path(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            } else if (payload instanceof DiagnosticNode dn) {
                appendDiagnostic(dn.diagnostic());
            } else if (payload instanceof WorkspaceEntry w) {
                append(w.name(), muted ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                append(w.resolvesLocally() ? "  resolved" : "  unresolved",
                        w.resolvesLocally() ? SimpleTextAttributes.GRAYED_ATTRIBUTES
                                : SimpleTextAttributes.ERROR_ATTRIBUTES);
            } else if (payload instanceof ContextStoreEntry s) {
                append(s.id(), muted ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                if (s.root() != null) {
                    append("  " + s.root(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                appendStoreHealth(s);
            } else if (payload instanceof InitiativeEntry i) {
                append(i.title().isEmpty() ? i.id() : i.title(),
                        muted ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                append("  [" + i.status().displayLabel() + "]", statusAttributes(i.status()));
            } else if (payload instanceof ArtifactNode a) {
                append(a.artifact().displayLabel(),
                        muted ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else if (payload != null) {
                append(String.valueOf(payload));
            }
        }

        /** True when the node is nested under a group flagged muted (the demoted Legacy group). */
        private boolean isUnderMutedGroup(DefaultMutableTreeNode node) {
            for (javax.swing.tree.TreeNode p = node.getParent(); p instanceof DefaultMutableTreeNode dmtn;
                 p = dmtn.getParent()) {
                if (dmtn.getUserObject() instanceof GroupNode g && g.muted()) {
                    return true;
                }
            }
            return false;
        }

        private void append1_5StoreHealth(StoreEntry s) {
            if (Boolean.FALSE.equals(s.metadataValid()) || Boolean.FALSE.equals(s.metadataPresent())) {
                append("  metadata issue", SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
            if (Boolean.FALSE.equals(s.openspecRootHealthy())) {
                append("  unhealthy openspec-root", SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
            if (Boolean.TRUE.equals(s.gitRepository())) {
                append("  git", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else if (Boolean.FALSE.equals(s.gitRepository())) {
                append("  not a git repo", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }

        private void appendDiagnostic(Diagnostic d) {
            boolean error = d.severity() != null && d.severity().toLowerCase(java.util.Locale.ROOT).contains("error");
            if (d.message() != null) {
                append(d.message(), error ? SimpleTextAttributes.ERROR_ATTRIBUTES
                        : SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
            }
            if (d.fix() != null) {
                // Read-only guidance — the fix is displayed, never executed.
                append("  fix: " + d.fix(), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
            }
        }

        private void appendStoreHealth(ContextStoreEntry s) {
            if (Boolean.FALSE.equals(s.metadataValid()) || Boolean.FALSE.equals(s.metadataPresent())) {
                append("  metadata issue", SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
            if (Boolean.TRUE.equals(s.gitRepository())) {
                append("  git", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }

        private SimpleTextAttributes statusAttributes(InitiativeStatus status) {
            return switch (status) {
                case ACTIVE -> SimpleTextAttributes.SYNTHETIC_ATTRIBUTES;
                case COMPLETE -> SimpleTextAttributes.GRAYED_ATTRIBUTES;
                case ARCHIVED -> SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES;
                default -> SimpleTextAttributes.REGULAR_ATTRIBUTES;
            };
        }
    }
}
