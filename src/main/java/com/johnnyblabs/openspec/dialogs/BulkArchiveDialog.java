package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.SpecSyncService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;

public class BulkArchiveDialog extends DialogWrapper {

    private final Project project;
    private final List<ChangeRow> rows = new ArrayList<>();
    private final ChangeTableModel tableModel = new ChangeTableModel();
    private final JBLabel conflictLabel = new JBLabel();
    private final JPanel progressPanel = new JPanel();
    private JTable table;
    private boolean archiveStarted = false;
    /**
     * The dialog's modality, captured once after {@code init()}. Background results posted without it would be held by
     * the platform until this modal dialog closes; the dialog window exists after {@code init()}, and the platform
     * reuses this same modality entity when the dialog is shown.
     */
    private ModalityState dialogModality;

    public BulkArchiveDialog(Project project, List<Change> activeChanges) {
        super(project, true);
        this.project = project;
        setTitle("Bulk Archive");
        setOKButtonText("Archive Selected");
        setCancelButtonText("Cancel");

        for (Change c : activeChanges) {
            rows.add(new ChangeRow(c));
        }

        init();
        // The dialog window exists after init(), and the platform reuses its modality entity when the dialog is shown.
        // A headless peer has no content panel; fall back rather than fail (that path is never modal anyway).
        JComponent content = getContentPanel();
        dialogModality = content != null ? ModalityState.stateForComponent(content) : ModalityState.defaultModalityState();
        loadArtifactStatus();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        mainPanel.setPreferredSize(JBUI.size(550, 350));

        if (rows.isEmpty()) {
            mainPanel.add(new JBLabel("No active changes to archive."), BorderLayout.CENTER);
            setOKButtonText("OK");
            return mainPanel;
        }

        // Table
        table = new JTable(tableModel);
        table.setRowHeight(JBUI.scale(26));
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(30));  // checkbox
        table.getColumnModel().getColumn(0).setMinWidth(JBUI.scale(30));
        table.getColumnModel().getColumn(2).setMaxWidth(JBUI.scale(100)); // status
        table.getColumnModel().getColumn(3).setMaxWidth(JBUI.scale(60));  // conflicts

        // Status column renderer
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, focus, r, c);
                if ("conflict".equals(val)) {
                    setIcon(AllIcons.General.Warning);
                    setText("");
                    setToolTipText("This change shares spec domains with other selected changes");
                } else {
                    setIcon(null);
                    setText("");
                    setToolTipText(null);
                }
                return this;
            }
        });

        // Archive status column renderer
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, focus, r, c);
                String status = String.valueOf(val);
                // The column is narrow; the full status (e.g. why a change is blocked) is in the tooltip.
                setToolTipText(status.startsWith("Blocked") || status.startsWith("Failed") ? status : null);
                if (status.startsWith("Blocked")) {
                    setForeground(JBColor.ORANGE);
                } else if (status.contains("done") || status.contains("Done")) {
                    setForeground(JBColor.GREEN.darker());
                } else if (status.contains("fail") || status.contains("Failed")) {
                    setForeground(JBColor.RED);
                } else if (status.contains("Archiving")) {
                    setForeground(JBColor.BLUE);
                } else {
                    setForeground(t.getForeground());
                }
                return this;
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(table);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Conflict warning panel
        conflictLabel.setForeground(JBColor.ORANGE);
        conflictLabel.setVisible(false);
        conflictLabel.setBorder(JBUI.Borders.emptyTop(4));
        mainPanel.add(conflictLabel, BorderLayout.SOUTH);

        return mainPanel;
    }

    private void loadArtifactStatus() {
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        loadStatuses(rows, orchestration::getArtifactStatus, dialogModality, ignored -> isDisposed(), () -> {
            tableModel.fireTableDataChanged();
            updateConflicts();
        });
    }

    /**
     * Computes each row's artifact status on a pooled thread, then posts {@code onLoaded} with {@code modality}
     * (dropped if {@code expired} holds by then, e.g. the dialog has closed).
     */
    @VisibleForTesting
    static void loadStatuses(List<ChangeRow> rows, Function<String, ChangeArtifactDag> statusOf, ModalityState modality,
                             com.intellij.openapi.util.Condition<?> expired, Runnable onLoaded) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            for (ChangeRow row : rows) {
                ChangeArtifactDag dag;
                try {
                    dag = statusOf.apply(row.change.getName());
                } catch (RuntimeException e) {
                    dag = null; // one change's status failing must not leave every row on "loading..."
                }
                if (dag != null) {
                    long done = dag.getArtifacts().stream()
                            .filter(a -> a.status() == com.johnnyblabs.openspec.model.ArtifactStatus.DONE)
                            .count();
                    row.artifactStatus = done + "/" + dag.getArtifacts().size() + " complete";
                    row.allComplete = dag.isComplete();
                } else {
                    row.artifactStatus = "unknown";
                }
            }
            ApplicationManager.getApplication().invokeLater(onLoaded, modality, expired);
        });
    }

    private void updateConflicts() {
        List<String> selectedNames = getSelectedChangeNames();
        if (selectedNames.size() < 2) {
            conflictLabel.setVisible(false);
            for (ChangeRow row : rows) row.hasConflict = false;
            tableModel.fireTableDataChanged();
            return;
        }

        SpecSyncService syncService = project.getService(SpecSyncService.class);
        Map<String, List<String>> conflicts = syncService.detectConflicts(selectedNames);

        Set<String> conflictingChanges = new HashSet<>();
        for (List<String> names : conflicts.values()) {
            conflictingChanges.addAll(names);
        }

        for (ChangeRow row : rows) {
            row.hasConflict = conflictingChanges.contains(row.change.getName());
        }

        if (conflicts.isEmpty()) {
            conflictLabel.setVisible(false);
        } else {
            StringBuilder sb = new StringBuilder("Conflicts: ");
            conflicts.forEach((cap, names) -> sb.append(cap).append(" (").append(String.join(", ", names)).append(") "));
            conflictLabel.setText(sb.toString().trim());
            conflictLabel.setVisible(true);
        }

        tableModel.fireTableDataChanged();
    }

    public List<String> getSelectedChangeNames() {
        return rows.stream()
                .filter(r -> r.selected)
                .map(r -> r.change.getName())
                .toList();
    }

    public void runBulkArchive() {
        runBulkArchive(Archiver.forProject(project));
    }

    @VisibleForTesting
    void runBulkArchive(Archiver archiver) {
        archiveStarted = true;
        setOKButtonText("Close");
        getOKAction().setEnabled(false);
        getCancelAction().setEnabled(false);

        List<ChangeRow> selected = rows.stream().filter(r -> r.selected).toList();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Bulk Archive", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                archiveAll(selected, dialogModality, archiver, tableModel::fireTableDataChanged,
                        (i, name) -> {
                            indicator.setFraction((double) i / selected.size());
                            indicator.setText("Archiving: " + name);
                        });
            }

            @Override
            public void onFinished() {
                // Runs on the EDT whether run() returned or threw, so the dialog can always be closed.
                onArchiveFinished(selected);
            }
        });
    }

    /** What the archive loop does per change; the dialog's implementation delegates to the spec-sync and change services. */
    @VisibleForTesting
    interface Archiver {
        /**
         * The problems that would make the change's spec sync skip part of it (a missing MODIFIED/REMOVED target, an
         * unpaired or unknown RENAMED entry); empty when it can sync cleanly or has no delta specs. Writes nothing.
         * Called off the EDT.
         */
        List<String> check(String changeName) throws Exception;

        /**
         * Applies the change's delta specs to the main specs (no-op when it has none). Throws {@link Blocked} if a
         * blocking problem has appeared since {@link #check}. Called off the EDT.
         */
        void sync(String changeName) throws Exception;

        /** Moves the change to the archive. Called on the EDT (it performs a VFS write action). */
        void archive(Change change) throws Exception;

        /** The production archiver: spec sync through {@link SpecSyncService}, the move through {@link ChangeService}. */
        static Archiver forProject(Project project) {
            return new Archiver() {
                @Override
                public List<String> check(String changeName) {
                    // The archive-folder collision is checked here, before any spec is synced, as OpenSpec does.
                    List<String> problems = new ArrayList<>();
                    String conflict = project.getService(ChangeService.class).archiveConflict(changeName);
                    if (conflict != null) problems.add(conflict);
                    problems.addAll(blockers(project.getService(SpecSyncService.class).computeSync(changeName)));
                    return problems;
                }

                @Override
                public void sync(String changeName) throws Exception {
                    String conflict = project.getService(ChangeService.class).archiveConflict(changeName);
                    if (conflict != null) {
                        throw new Blocked(conflict);
                    }
                    SpecSyncService syncService = project.getService(SpecSyncService.class);
                    List<SpecSyncResult> syncResults = syncService.computeSync(changeName);
                    List<String> problems = blockers(syncResults);
                    if (!problems.isEmpty()) {
                        throw new Blocked(problems.get(0));
                    }
                    if (!syncResults.isEmpty()) {
                        syncService.applySync(syncResults);
                    }
                }

                @Override
                public void archive(Change change) throws Exception {
                    project.getService(ChangeService.class).archiveChange(change);
                }
            };
        }
    }

    /** Every refusal a sync result carries: OpenSpec's archive would refuse the change. Warnings don't block. */
    @VisibleForTesting
    static List<String> blockers(List<SpecSyncResult> results) {
        List<String> problems = new ArrayList<>();
        for (SpecSyncResult result : results) {
            problems.addAll(result.refusals());
        }
        return problems;
    }

    /** A change whose spec sync reports a blocking problem; it is neither synced nor archived. */
    @VisibleForTesting
    static final class Blocked extends Exception {
        Blocked(String reason) {
            super(reason);
        }
    }

    @VisibleForTesting
    interface Progress {
        void step(int index, String changeName);
    }

    /**
     * Checks every change first (any with a blocking sync result is shown as blocked and left alone), then archives the
     * rest in order: spec sync off the EDT, then the archive itself on the EDT via
     * {@code invokeAndWait} with {@code modality} (inline when already on the EDT). Row status updates are posted with
     * {@code modality} so they appear in the open dialog. A failure marks that row and processing continues.
     */
    @VisibleForTesting
    static void archiveAll(List<ChangeRow> selected, ModalityState modality, Archiver archiver,
                           Runnable onRowChanged, Progress progress) {
        // Phase 1: check every change before writing anything (upstream archive refuses the same cases).
        List<ChangeRow> clear = new ArrayList<>();
        for (ChangeRow row : selected) {
            try {
                List<String> problems = archiver.check(row.change.getName());
                if (problems.isEmpty()) {
                    clear.add(row);
                } else {
                    row.archiveStatus = "Blocked: " + problems.get(0);
                }
            } catch (Exception ex) {
                row.archiveStatus = "Failed: " + ex.getMessage();
            }
        }
        ApplicationManager.getApplication().invokeLater(onRowChanged, modality);

        // Phase 2: sync and archive the rest, in order.
        for (int i = 0; i < clear.size(); i++) {
            ChangeRow row = clear.get(i);
            progress.step(i, row.change.getName());
            row.archiveStatus = "Archiving...";
            ApplicationManager.getApplication().invokeLater(onRowChanged, modality);
            try {
                archiver.sync(row.change.getName());
                Exception[] archiveError = {null};
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    try {
                        archiver.archive(row.change);
                    } catch (Exception ex) {
                        archiveError[0] = ex;
                    }
                }, modality);
                row.archiveStatus = archiveError[0] == null ? "Done" : "Failed: " + archiveError[0].getMessage();
            } catch (Blocked ex) {
                row.archiveStatus = "Blocked: " + ex.getMessage();
            } catch (Exception ex) {
                row.archiveStatus = "Failed: " + ex.getMessage();
            }
            ApplicationManager.getApplication().invokeLater(onRowChanged, modality);
        }
    }

    /** End of a run: re-enable OK so the dialog can be closed, and report how many changes were archived. */
    @VisibleForTesting
    void onArchiveFinished(List<ChangeRow> selected) {
        getOKAction().setEnabled(true);
        tableModel.fireTableDataChanged();
        long doneCount = selected.stream().filter(r -> "Done".equals(r.archiveStatus)).count();
        OpenSpecNotifier.info(project, "Bulk Archive", doneCount + "/" + selected.size() + " changes archived");
    }

    @Override
    protected void doOKAction() {
        if (archiveStarted) {
            super.doOKAction();
            return;
        }
        List<String> selected = getSelectedChangeNames();
        if (selected.isEmpty()) {
            return;
        }
        runBulkArchive();
    }

    // --- Table model ---

    static class ChangeRow {
        // Written by background work, read by the table on the EDT.
        final Change change;
        volatile boolean selected = true;
        volatile String artifactStatus = "loading...";
        volatile boolean allComplete = false;
        volatile boolean hasConflict = false;
        volatile String archiveStatus = "";

        ChangeRow(Change change) {
            this.change = change;
        }
    }

    class ChangeTableModel extends AbstractTableModel {
        private final String[] columns = {"", "Change", "Status", ""};

        @Override
        public int getRowCount() { return rows.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0 && !archiveStarted;
        }

        @Override
        public Object getValueAt(int row, int col) {
            ChangeRow r = rows.get(row);
            return switch (col) {
                case 0 -> r.selected;
                case 1 -> r.change.getName();
                case 2 -> r.archiveStatus.isEmpty() ? r.artifactStatus : r.archiveStatus;
                case 3 -> r.hasConflict ? "conflict" : "";
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object val, int row, int col) {
            if (col == 0) {
                rows.get(row).selected = (Boolean) val;
                fireTableCellUpdated(row, col);
                updateConflicts();
            }
        }
    }
}
