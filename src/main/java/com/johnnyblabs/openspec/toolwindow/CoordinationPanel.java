package com.johnnyblabs.openspec.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.coordination.ContextStoreEntry;
import com.johnnyblabs.openspec.coordination.CoordinationActionGating;
import com.johnnyblabs.openspec.coordination.CoordinationActionGating.SelectionKind;
import com.johnnyblabs.openspec.coordination.CoordinationData;
import com.johnnyblabs.openspec.coordination.CoordinationService;
import com.johnnyblabs.openspec.coordination.Diagnostic;
import com.johnnyblabs.openspec.coordination.InitiativeArtifact;
import com.johnnyblabs.openspec.coordination.InitiativeEntry;
import com.johnnyblabs.openspec.coordination.InitiativeStatus;
import com.johnnyblabs.openspec.coordination.StoreEntry;
import com.johnnyblabs.openspec.coordination.WorkspaceEntry;
import com.johnnyblabs.openspec.coordination.WorksetEntry;
import com.johnnyblabs.openspec.coordination.WorksetOpenPlan;
import com.johnnyblabs.openspec.dialogs.NewStoreDialog;
import com.johnnyblabs.openspec.dialogs.NewWorksetDialog;
import com.johnnyblabs.openspec.services.WorkflowSchemaContextService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Tool-window tab presenting the OpenSpec coordination collections. On the OpenSpec 1.5 store model
 * (CLI &gt;= 1.5.0) it renders Stores and Worksets and, at the Full tier, exposes CLI-delegated write
 * actions through an {@link ActionToolbar} and a tree right-click menu: New/Register store, per-store
 * Doctor / Open Root / Unregister / Remove (guarded, destructive), and New / Open / Remove workset. A
 * {@code doctor}-driven health strip surfaces the highest-severity {@code status[]} entry with its
 * {@code fix}. Legacy 1.4 workspaces / context stores / initiatives are shown read-only (demoted when
 * the store model leads).
 *
 * <p>All CLI/IO runs on a pooled thread; the tree, health strip, and action enablement are updated on
 * the EDT via {@code invokeLater}. Every action's {@code update()} reads cached tier/selection only.
 */
public final class CoordinationPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(CoordinationPanel.class);

    private final Project project;
    private final Tree tree;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Coordination");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JBLabel statusLabel = new JBLabel();
    private final HealthStrip healthStrip = new HealthStrip();
    private ActionToolbar toolbar;

    /** Whether store/workset writes are permitted right now — Full tier AND CLI &gt;= 1.5.0. */
    private volatile boolean writeEnabled = false;
    /** Whether the store model leads (CLI &gt;= 1.5.0); gates whether the actions are shown at all. */
    private volatile boolean storeModel = false;

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
            public void mousePressed(MouseEvent e) {
                // Right-click selects the node under the cursor so context actions target it.
                if (e.isPopupTrigger()) {
                    selectRowAt(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    selectRowAt(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                    handleOpen();
                }
            }
        });

        DefaultActionGroup actions = buildActionGroup();
        add(buildHeader(actions), BorderLayout.NORTH);
        add(new JBScrollPane(tree), BorderLayout.CENTER);

        toolbar.setTargetComponent(this);
        PopupHandler.installPopupMenu(tree, actions, "OpenSpecCoordinationPopup");

        if (initialData != null) {
            render(initialData);
        } else {
            statusLabel.setText("Loading coordination state…");
            reload();
        }
    }

    private void selectRowAt(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
            tree.setSelectionRow(row);
        }
    }

    private JComponent buildHeader(DefaultActionGroup actions) {
        toolbar = ActionManager.getInstance().createActionToolbar("OpenSpecCoordinationToolbar", actions, true);

        JPanel header = new JPanel(new BorderLayout());
        header.add(healthStrip, BorderLayout.NORTH);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(toolbar.getComponent(), BorderLayout.NORTH);
        statusLabel.setBorder(JBUI.Borders.empty(2, 6, 4, 6));
        statusLabel.setComponentStyle(com.intellij.util.ui.UIUtil.ComponentStyle.SMALL);
        bar.add(statusLabel, BorderLayout.SOUTH);
        header.add(bar, BorderLayout.CENTER);
        return header;
    }

    private DefaultActionGroup buildActionGroup() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new RefreshAction());
        group.addSeparator();
        group.add(new NewStoreAction());
        group.add(new RegisterStoreAction());
        group.add(new NewWorksetAction());
        group.add(new Separator());
        group.add(new StoreDoctorAction());
        group.add(new OpenStoreRootAction());
        group.add(new UnregisterStoreAction());
        group.add(new RemoveStoreAction());
        group.add(new Separator());
        group.add(new OpenWorksetAction());
        group.add(new RemoveWorksetAction());
        return group;
    }

    /** Reloads coordination state off the EDT and rebuilds the tree on the EDT. */
    public void reload() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CoordinationService service = project.getService(CoordinationService.class);
            boolean coordinationMode = isCoordinationModeActive();
            CoordinationData data = service != null
                    ? service.getCoordinationData(coordinationMode)
                    : CoordinationData.EMPTY_HIDDEN;
            ApplicationManager.getApplication().invokeLater(() -> render(data));
        });
    }

    private boolean isCoordinationModeActive() {
        WorkflowSchemaContextService ctx = project.getService(WorkflowSchemaContextService.class);
        return ctx != null && ctx.hasNonDefaultModeCached();
    }

    private void render(CoordinationData data) {
        root.removeAllChildren();

        if (data.storesAreLeadModel()) {
            renderStoreGroups(root, data);
            if (data.legacyStateExists()) {
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

    /** The legacy 1.4 Workspaces / Context Stores / Initiatives groups (read-only). */
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
        storeModel = data.storesAreLeadModel();
        writeEnabled = CoordinationActionGating.writeEnabled(data.tier().allowsWriteActions(), storeModel);

        // Health strip reflects the highest-severity actionable doctor diagnostic across stores.
        healthStrip.update(CoordinationService.highestActionableDiagnostic(data.stores()));

        if (storeModel) {
            String source = data.storesSourcedFromCli() ? "OpenSpec CLI" : "on-disk state";
            String legacy = data.legacyStateExists() ? " Legacy pre-1.5 state is shown read-only." : "";
            if (writeEnabled) {
                statusLabel.setText("Stores & worksets — Full (write actions enabled), sourced from "
                        + source + "." + legacy);
            } else {
                statusLabel.setText("Stores & worksets (read-only) — sourced from " + source
                        + ". OpenSpec CLI 1.5.0+ is required for write actions." + legacy);
            }
        } else {
            String source = data.sourcedFromCli() ? "OpenSpec CLI" : "on-disk state";
            statusLabel.setText("Legacy coordination (read-only) — sourced from " + source
                    + ". Write actions require the OpenSpec 1.5 store/workset model.");
        }

        if (toolbar != null) {
            toolbar.updateActionsImmediately();
        }
    }

    // ---- selection helpers ---------------------------------------------------

    @Nullable
    private Object selectedPayload() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        Object node = path.getLastPathComponent();
        return node instanceof DefaultMutableTreeNode dmtn ? dmtn.getUserObject() : null;
    }

    private SelectionKind selectionKind() {
        Object payload = selectedPayload();
        if (payload == null) return SelectionKind.NONE;
        if (payload instanceof StoreEntry) return SelectionKind.STORE;
        if (payload instanceof WorksetEntry) return SelectionKind.WORKSET;
        if (payload instanceof WorksetEntry.Member) return SelectionKind.MEMBER;
        return SelectionKind.OTHER;
    }

    @Nullable
    private StoreEntry selectedStore() {
        return selectedPayload() instanceof StoreEntry s ? s : null;
    }

    @Nullable
    private WorksetEntry selectedWorkset() {
        return selectedPayload() instanceof WorksetEntry w ? w : null;
    }

    // ---- open / navigation ---------------------------------------------------

    private void handleOpen() {
        if (selectedPayload() instanceof ArtifactNode artifactNode) {
            openArtifact(artifactNode);
        }
    }

    private void openArtifact(ArtifactNode artifactNode) {
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

    // ---- store write actions -------------------------------------------------

    private void onNewStore() {
        NewStoreDialog dlg = new NewStoreDialog(project);
        if (!dlg.showAndGet()) return;
        String id = dlg.getStoreId();
        String path = dlg.getStorePath();
        runStoreWrite(service -> service.setupStore(id, path));
    }

    private void onRegisterStore() {
        VirtualFile chosen = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle("Register Existing Store")
                        .withDescription("Choose an existing store's root folder"),
                project, null);
        if (chosen == null) return;
        String path = chosen.getPath();
        runStoreWrite(service -> service.registerStore(path));
    }

    private void onStoreDoctor(StoreEntry store) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CoordinationService service = project.getService(CoordinationService.class);
            Diagnostic top = service != null ? service.storeDoctor(store.id()) : null;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (top == null) {
                    Messages.showInfoMessage(project, "Store '" + store.id() + "' is healthy.", "Store Doctor");
                } else {
                    Messages.showWarningDialog(project, formatDiagnostic(top), "Store Doctor");
                }
                reload();
            });
        });
    }

    private void onOpenStoreRoot(StoreEntry store) {
        if (store.root() == null) {
            Messages.showInfoMessage(project, "Store '" + store.id() + "' has no known root folder.", "Open Root");
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            File dir = new File(store.root());
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (dir.isDirectory()) {
                    RevealFileAction.openDirectory(dir);
                } else {
                    Messages.showWarningDialog(project, "The store root no longer exists on disk.", "Open Root");
                }
            });
        });
    }

    private void onUnregisterStore(StoreEntry store) {
        runStoreWrite(service -> service.unregisterStore(store.id()));
    }

    private void onRemoveStore(StoreEntry store) {
        String where = store.root() != null ? "\n\nFolder: " + store.root() : "";
        int answer = Messages.showYesNoDialog(project,
                "Remove store '" + store.id() + "'?\n\nThis DELETES the store's local files on disk and "
                        + "cannot be undone." + where,
                "Remove Store", "Delete Files", "Cancel", Messages.getWarningIcon());
        if (answer != Messages.YES) return;
        runStoreWrite(service -> service.removeStore(store.id()));
    }

    // ---- workset write actions ----------------------------------------------

    private void onNewWorkset() {
        NewWorksetDialog dlg = new NewWorksetDialog(project);
        if (!dlg.showAndGet()) return;
        String name = dlg.getWorksetName();
        List<WorksetEntry.Member> members = dlg.getMembers();
        runStoreWrite(service -> service.createWorkset(name, members));
    }

    private void onRemoveWorkset(WorksetEntry workset) {
        int answer = Messages.showYesNoDialog(project,
                "Remove workset '" + workset.name() + "'?\n\nMember folders are NOT touched — only the "
                        + "saved workset is deleted.",
                "Remove Workset", Messages.getQuestionIcon());
        if (answer != Messages.YES) return;
        runStoreWrite(service -> service.removeWorkset(workset.name()));
    }

    /**
     * Opens a workset by mapping its members onto the IDE's multi-folder / attached-project model —
     * never the CLI's {@code --code-workspace} flag. Confirms the folder count first, resolves and
     * refreshes VFS off the EDT, then attaches each member folder to the current window on the EDT.
     */
    private void onOpenWorkset(WorksetEntry workset) {
        List<String> paths = WorksetOpenPlan.orderedPaths(workset);
        if (paths.isEmpty()) {
            Messages.showInfoMessage(project,
                    "Workset '" + workset.name() + "' has no member folders to open.", "Open Workset");
            return;
        }
        int answer = Messages.showYesNoDialog(project,
                "Open workset '" + workset.name() + "'?\n\nThis reveals " + paths.size() + " member folder"
                        + (paths.size() == 1 ? "" : "s") + " in your file manager.",
                "Open Workset", Messages.getQuestionIcon());
        if (answer != Messages.YES) return;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<File> dirs = new ArrayList<>();
            for (String p : paths) {
                File dir = new File(p);
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
                dirs.add(dir);
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                int opened = 0;
                for (File dir : dirs) {
                    try {
                        if (dir.isDirectory()) {
                            RevealFileAction.openDirectory(dir);
                            opened++;
                        }
                    } catch (Throwable t) {
                        LOG.warn("Failed to reveal workset member folder: " + dir, t);
                    }
                }
                if (opened == 0) {
                    Messages.showWarningDialog(project,
                            "Could not reveal the workset's member folders.", "Open Workset");
                }
            });
        });
    }

    // ---- write dispatch ------------------------------------------------------

    private void runStoreWrite(Function<CoordinationService, CoordinationService.WriteResult> action) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CoordinationService service = project.getService(CoordinationService.class);
            CoordinationService.WriteResult result = service != null
                    ? action.apply(service)
                    : CoordinationService.WriteResult.fail("Coordination service unavailable.");
            // Refresh VFS for a newly created store root off the EDT so it appears immediately.
            if (result.success() && result.createdPath() != null) {
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(result.createdPath()));
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                if (result.success()) {
                    reload();
                } else {
                    showWriteFailure(result);
                }
            });
        });
    }

    /** Surfaces a failed write using only the parsed status message and {@code fix} — never stderr. */
    private void showWriteFailure(CoordinationService.WriteResult result) {
        String message = result.message();
        if (result.fix() != null && !result.fix().isBlank()) {
            message += "\n\nSuggested fix: " + result.fix();
        }
        Messages.showErrorDialog(project, message, "Coordination Action Failed");
    }

    private static String formatDiagnostic(Diagnostic d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.message() != null ? d.message() : "The store reported a problem.");
        if (d.fix() != null && !d.fix().isBlank()) {
            sb.append("\n\nSuggested fix: ").append(d.fix());
        }
        return sb.toString();
    }

    // ---- actions -------------------------------------------------------------

    /** Base action: EDT update thread; enablement decided from cached state only. */
    private abstract class CoordinationAction extends AnAction {
        CoordinationAction(String text, @Nullable String description, @Nullable Icon icon) {
            super(text, description, icon);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    private final class RefreshAction extends CoordinationAction {
        RefreshAction() {
            super("Refresh", "Reload coordination state", AllIcons.Actions.Refresh);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            reload();
        }
    }

    private final class NewStoreAction extends CoordinationAction {
        NewStoreAction() {
            super("New Store", "Create and register a new store", AllIcons.General.Add);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(CoordinationActionGating.creationEnabled(writeEnabled));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            onNewStore();
        }
    }

    private final class RegisterStoreAction extends CoordinationAction {
        RegisterStoreAction() {
            super("Register Existing Store", "Register an existing store root", AllIcons.Nodes.Folder);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(CoordinationActionGating.creationEnabled(writeEnabled));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            onRegisterStore();
        }
    }

    private final class NewWorksetAction extends CoordinationAction {
        NewWorksetAction() {
            super("New Workset", "Compose a new workset", AllIcons.Actions.AddMulticaret);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(CoordinationActionGating.creationEnabled(writeEnabled));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            onNewWorkset();
        }
    }

    private final class StoreDoctorAction extends CoordinationAction {
        StoreDoctorAction() {
            super("Doctor", "Check this store's health", AllIcons.Actions.IntentionBulb);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(
                    CoordinationActionGating.storeScopedEnabled(writeEnabled, selectionKind()));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            StoreEntry s = selectedStore();
            if (s != null) onStoreDoctor(s);
        }
    }

    private final class OpenStoreRootAction extends CoordinationAction {
        OpenStoreRootAction() {
            super("Open Root", "Reveal the store's root folder", AllIcons.Actions.MenuOpen);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(
                    CoordinationActionGating.storeScopedEnabled(writeEnabled, selectionKind()));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            StoreEntry s = selectedStore();
            if (s != null) onOpenStoreRoot(s);
        }
    }

    private final class UnregisterStoreAction extends CoordinationAction {
        UnregisterStoreAction() {
            super("Unregister", "Forget this store (keeps files)", AllIcons.Actions.Uninstall);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(
                    CoordinationActionGating.storeScopedEnabled(writeEnabled, selectionKind()));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            StoreEntry s = selectedStore();
            if (s != null) onUnregisterStore(s);
        }
    }

    private final class RemoveStoreAction extends CoordinationAction {
        RemoveStoreAction() {
            super("Remove", "Forget this store and delete its files", AllIcons.Actions.GC);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(
                    CoordinationActionGating.storeScopedEnabled(writeEnabled, selectionKind()));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            StoreEntry s = selectedStore();
            if (s != null) onRemoveStore(s);
        }
    }

    private final class OpenWorksetAction extends CoordinationAction {
        OpenWorksetAction() {
            super("Open Workset", "Open the workset's member folders", AllIcons.Actions.MenuOpen);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(
                    CoordinationActionGating.worksetScopedEnabled(writeEnabled, selectionKind()));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            WorksetEntry w = selectedWorkset();
            if (w != null) onOpenWorkset(w);
        }
    }

    private final class RemoveWorksetAction extends CoordinationAction {
        RemoveWorksetAction() {
            super("Remove Workset", "Delete this workset (keeps member folders)", AllIcons.Actions.GC);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(storeModel);
            e.getPresentation().setEnabled(
                    CoordinationActionGating.worksetScopedEnabled(writeEnabled, selectionKind()));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            WorksetEntry w = selectedWorkset();
            if (w != null) onRemoveWorkset(w);
        }
    }

    // ---- health strip --------------------------------------------------------

    /**
     * A slim row at the top of the panel rendering the highest-severity actionable {@code doctor}
     * diagnostic. Shows the message with a severity icon and exposes the {@code fix} string verbatim
     * as an inline {@link HyperlinkLabel} (clicking copies the fix command to the clipboard). Hidden
     * when there is no actionable diagnostic.
     */
    private final class HealthStrip extends JPanel {
        private final JBLabel messageLabel = new JBLabel();
        private final HyperlinkLabel fixLink = new HyperlinkLabel();

        HealthStrip() {
            super(new BorderLayout(JBUI.scale(8), 0));
            setBorder(JBUI.Borders.empty(3, 8));
            add(messageLabel, BorderLayout.CENTER);
            add(fixLink, BorderLayout.EAST);
            fixLink.addHyperlinkListener(e -> copyFixToClipboard());
            setVisible(false);
        }

        private @Nullable String currentFix;

        void update(@Nullable Diagnostic diagnostic) {
            if (diagnostic == null) {
                currentFix = null;
                setVisible(false);
                return;
            }
            boolean error = CoordinationService.severityRank(diagnostic.severity()) >= 3;
            messageLabel.setIcon(error ? AllIcons.General.Error : AllIcons.General.Warning);
            messageLabel.setText(diagnostic.message() != null ? diagnostic.message() : "Store health issue detected.");
            currentFix = diagnostic.fix();
            if (currentFix != null && !currentFix.isBlank()) {
                fixLink.setHyperlinkText(currentFix);
                fixLink.setVisible(true);
            } else {
                fixLink.setVisible(false);
            }
            setVisible(true);
            revalidate();
            repaint();
        }

        private void copyFixToClipboard() {
            if (currentFix == null || currentFix.isBlank()) return;
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(currentFix), null);
            Messages.showInfoMessage(project,
                    "Copied the suggested fix command to the clipboard:\n\n" + currentFix,
                    "Suggested Fix");
        }
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
