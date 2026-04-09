package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
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

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.concurrent.CountDownLatch;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class BulkArchiveDialog extends DialogWrapper {

    private final Project project;
    private final List<ChangeRow> rows = new ArrayList<>();
    private final ChangeTableModel tableModel = new ChangeTableModel();
    private final JBLabel conflictLabel = new JBLabel();
    private final JPanel progressPanel = new JPanel();
    private JTable table;
    private boolean archiveStarted = false;

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
                if (status.contains("done") || status.contains("Done")) {
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
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
            for (ChangeRow row : rows) {
                ChangeArtifactDag dag = orchestration.getArtifactStatus(row.change.getName());
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
            ApplicationManager.getApplication().invokeLater(() -> {
                tableModel.fireTableDataChanged();
                updateConflicts();
            });
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
        archiveStarted = true;
        setOKButtonText("Close");
        getOKAction().setEnabled(false);
        getCancelAction().setEnabled(false);

        List<ChangeRow> selected = rows.stream().filter(r -> r.selected).toList();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Bulk Archive", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ChangeService changeService = project.getService(ChangeService.class);
                SpecSyncService syncService = project.getService(SpecSyncService.class);
                int total = selected.size();

                for (int i = 0; i < total; i++) {
                    ChangeRow row = selected.get(i);
                    indicator.setFraction((double) i / total);
                    indicator.setText("Archiving: " + row.change.getName());

                    ApplicationManager.getApplication().invokeLater(() -> {
                        row.archiveStatus = "Archiving...";
                        tableModel.fireTableDataChanged();
                    });

                    try {
                        // Spec sync first (runs on background thread)
                        List<SpecSyncResult> syncResults = syncService.computeSync(row.change.getName());
                        if (!syncResults.isEmpty()) {
                            syncService.applySync(syncResults);
                        }

                        // Archive via invokeLater + latch to avoid invokeAndWait deadlock
                        CountDownLatch archiveLatch = new CountDownLatch(1);
                        final Exception[] archiveError = {null};
                        ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                changeService.archiveChange(row.change);
                                row.archiveStatus = "Done";
                            } catch (Exception ex) {
                                archiveError[0] = ex;
                                row.archiveStatus = "Failed: " + ex.getMessage();
                            } finally {
                                tableModel.fireTableDataChanged();
                                archiveLatch.countDown();
                            }
                        });
                        archiveLatch.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        ApplicationManager.getApplication().invokeLater(() -> {
                            row.archiveStatus = "Failed: interrupted";
                            tableModel.fireTableDataChanged();
                        });
                    } catch (Exception ex) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            row.archiveStatus = "Failed: " + ex.getMessage();
                            tableModel.fireTableDataChanged();
                        });
                    }
                }

                ApplicationManager.getApplication().invokeLater(() -> {
                    getOKAction().setEnabled(true);
                    long doneCount = selected.stream().filter(r -> "Done".equals(r.archiveStatus)).count();
                    OpenSpecNotifier.info(project, "Bulk Archive",
                            doneCount + "/" + total + " changes archived");
                });
            }
        });
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
        final Change change;
        boolean selected = true;
        String artifactStatus = "loading...";
        boolean allComplete = false;
        boolean hasConflict = false;
        String archiveStatus = "";

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
