package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolInfo;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ManageAiToolsDialog extends DialogWrapper {

    private final Project project;
    private final SearchTextField searchField;
    private final JPanel toolListPanel;
    private List<ToolInfo> allTools;
    /**
     * The dialog's modality, captured once after {@code init()}. Background results posted without it are held by
     * the platform until this modal dialog closes (see {@code BulkArchiveDialog}).
     */
    private ModalityState dialogModality;

    public ManageAiToolsDialog(Project project) {
        super(project, false);
        this.project = project;
        setTitle("Manage AI Tools");
        setOKButtonText("Close");

        searchField = new SearchTextField(false);
        searchField.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull javax.swing.event.DocumentEvent e) {
                filterTools(searchField.getText().trim());
            }
        });

        toolListPanel = new JPanel();
        toolListPanel.setLayout(new BoxLayout(toolListPanel, BoxLayout.Y_AXIS));

        init();
        // A headless peer has no content panel; fall back rather than fail (that path is never modal anyway).
        JComponent content = getContentPanel();
        dialogModality = content != null ? ModalityState.stateForComponent(content) : ModalityState.defaultModalityState();
        loadTools();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(480)));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        headerPanel.setBorder(JBUI.Borders.emptyBottom(8));

        JBLabel title = new JBLabel("Configure AI tools for your project");
        title.setForeground(JBColor.GRAY);
        headerPanel.add(title, BorderLayout.NORTH);

        searchField.getTextEditor().getEmptyText().setText("Filter tools...");
        headerPanel.add(searchField, BorderLayout.CENTER);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadTools());
        headerPanel.add(refreshButton, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tool list
        JBScrollPane scrollPane = new JBScrollPane(toolListPanel);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Footer
        CliDetectionService cliService = project.getService(CliDetectionService.class);
        if (cliService == null || !cliService.isAvailable()) {
            JBLabel cliHint = new JBLabel(
                    "<html>" + cliHintBody(com.intellij.openapi.util.SystemInfo.isWindows) + "</html>");
            cliHint.setForeground(JBColor.GRAY);
            cliHint.setFont(cliHint.getFont().deriveFont(Font.PLAIN, 11f));
            JPanel footer = new JPanel(new BorderLayout());
            footer.setBorder(JBUI.Borders.emptyTop(8));
            footer.add(cliHint, BorderLayout.CENTER);
            footer.add(SetupWizardDialog.installInstructionsLink(), BorderLayout.SOUTH);
            mainPanel.add(footer, BorderLayout.SOUTH);
        }

        return mainPanel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    private void loadTools() {
        AiToolDetectionService toolService = project.getService(AiToolDetectionService.class);
        if (toolService == null) {
            allTools = List.of();
            filterTools("");
            return;
        }
        toolService.detect();
        allTools = toolService.getAllToolsWithStatus();
        filterTools(searchField.getText().trim());
    }

    private void filterTools(String query) {
        toolListPanel.removeAll();

        String lowerQuery = query.toLowerCase();
        List<ToolInfo> filtered = allTools.stream()
                .filter(t -> query.isEmpty() || t.name().toLowerCase().contains(lowerQuery))
                .toList();

        // Group by status
        List<ToolInfo> configured = filtered.stream().filter(t -> t.status() == ToolStatus.CONFIGURED).toList();
        List<ToolInfo> detected = filtered.stream().filter(t -> t.status() == ToolStatus.DETECTED).toList();
        List<ToolInfo> available = filtered.stream().filter(t -> t.status() == ToolStatus.AVAILABLE).toList();

        if (!configured.isEmpty()) {
            addSectionHeader("CONFIGURED");
            configured.forEach(this::addToolRow);
        }
        if (!detected.isEmpty()) {
            addSectionHeader("DETECTED — needs OpenSpec skills");
            detected.forEach(this::addToolRow);
        }
        if (!available.isEmpty()) {
            addSectionHeader("AVAILABLE");
            available.forEach(this::addToolRow);
        }

        if (filtered.isEmpty()) {
            JBLabel empty = new JBLabel("No tools match \"" + query + "\"");
            empty.setForeground(JBColor.GRAY);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setBorder(JBUI.Borders.empty(20));
            toolListPanel.add(empty);
        }

        toolListPanel.revalidate();
        toolListPanel.repaint();
    }

    private void addSectionHeader(String text) {
        JBLabel header = new JBLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setForeground(JBColor.GRAY);
        header.setBorder(JBUI.Borders.empty(12, 4, 4, 4));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(28)));
        toolListPanel.add(header);
    }

    private void addToolRow(ToolInfo tool) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        row.setBorder(JBUI.Borders.empty(4, 8, 4, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(36)));

        // Left: status icon + name
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        namePanel.setOpaque(false);

        Icon statusIcon = switch (tool.status()) {
            case CONFIGURED -> AllIcons.General.InspectionsOK;
            case DETECTED -> AllIcons.General.Warning;
            case AVAILABLE -> AllIcons.General.Information;
        };
        namePanel.add(new JBLabel(statusIcon));

        JBLabel nameLabel = new JBLabel(tool.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 13f));
        namePanel.add(nameLabel);

        // Type badge
        String typeText = tool.type() == AiToolDetectionService.ToolType.CLI ? "CLI" : "IDE";
        JBLabel typeBadge = new JBLabel(typeText);
        typeBadge.setFont(typeBadge.getFont().deriveFont(Font.PLAIN, 10f));
        typeBadge.setForeground(JBColor.GRAY);
        typeBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(1, 4)));
        namePanel.add(typeBadge);

        row.add(namePanel, BorderLayout.CENTER);

        // Right: action button
        JButton actionButton = createActionButton(tool);
        if (actionButton != null) {
            row.add(actionButton, BorderLayout.EAST);
        }

        // Hover highlight
        row.setOpaque(true);
        row.setBackground(UIManager.getColor("List.background"));
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                row.setBackground(UIManager.getColor("List.selectionInactiveBackground"));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                row.setBackground(UIManager.getColor("List.background"));
            }
        });

        toolListPanel.add(row);
    }

    private @Nullable JButton createActionButton(ToolInfo tool) {
        CliDetectionService cliService = project.getService(CliDetectionService.class);
        boolean cliAvailable = cliService != null && cliService.isAvailable();

        return switch (tool.status()) {
            case CONFIGURED -> {
                if (!cliAvailable) yield null;
                JButton btn = new JButton("Update");
                btn.setToolTipText("Refresh OpenSpec skill and command files");
                btn.addActionListener(e -> updateTool(tool, btn));
                yield btn;
            }
            case DETECTED -> {
                JButton btn = new JButton("Configure");
                if (cliAvailable && tool.cliId() != null) {
                    btn.setToolTipText("Generate OpenSpec skills and commands for " + tool.name());
                    btn.addActionListener(e -> runCliAction(
                            "init --tools " + tool.cliId(),
                            "Configuring " + tool.name(), btn));
                } else {
                    btn.setToolTipText("Set as preferred tool (install CLI for full skill generation)");
                    btn.addActionListener(e -> {
                        setPreferredTool(tool.name());
                        btn.setText("Selected");
                        btn.setEnabled(false);
                    });
                }
                yield btn;
            }
            case AVAILABLE -> {
                JButton btn = new JButton("Add");
                if (cliAvailable && tool.cliId() != null) {
                    btn.setToolTipText("Add " + tool.name() + " to your project with OpenSpec skills");
                    btn.addActionListener(e -> runCliAction(
                            "init --tools " + tool.cliId(),
                            "Adding " + tool.name(), btn));
                } else {
                    btn.setToolTipText("Create tool directory and set as preferred (install CLI for skill generation)");
                    btn.addActionListener(e -> {
                        addToolBuiltIn(tool);
                        btn.setText("Added");
                        btn.setEnabled(false);
                    });
                }
                yield btn;
            }
        };
    }

    /**
     * The Update button. {@code openspec update} always covers every configured tool, so on CLI 1.15+ the preview shows
     * the whole plan, with this tool's group first; older CLIs run the update directly.
     */
    private void updateTool(ToolInfo tool, JButton sourceButton) {
        com.johnnyblabs.openspec.services.UpdatePreviewService preview =
                com.johnnyblabs.openspec.services.UpdatePreviewService.getInstance(project);
        if (preview == null || !preview.supportsPreview()) {
            runCliAction("update", "Updating " + tool.name(), sourceButton);
            return;
        }
        String title = "Updating " + tool.name();
        preview.previewAndApply(com.johnnyblabs.openspec.services.UpdatePreviewService.Origin.MANAGE_TOOLS, tool.cliId(),
                result -> {
                    if (isDisposed() || project.isDisposed()) return;
                    if (result.isSuccess()) {
                        String basePath = project.getBasePath();
                        VirtualFile projectDir = basePath != null
                                ? LocalFileSystem.getInstance().findFileByPath(basePath) : null;
                        refreshThenPost(projectDir, dialogModality, ignored -> isDisposed() || project.isDisposed(), () -> {
                            loadTools();
                            OpenSpecNotifier.info(project, "AI Tools", title + " — done");
                        });
                    } else {
                        OpenSpecNotifier.warn(project, "AI Tools", title + " failed: " + result.stderr().trim());
                    }
                });
    }

    private void runCliAction(String command, String title, JButton sourceButton) {
        sourceButton.setEnabled(false);
        sourceButton.setText("...");

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Condition<?> expired = ignored -> isDisposed() || project.isDisposed();
                try {
                    String[] args = command.split("\\s+");
                    CliRunner.CliResult result = CliRunner.run(project, args);
                    if (result.isSuccess()) {
                        // The CLI wrote files outside the VFS. A synchronous refresh is illegal here (off the EDT),
                        // so refresh the project directory asynchronously and reload the list once it finishes.
                        // loadTools() rebuilds every row, which also replaces the "..." button.
                        String basePath = project.getBasePath();
                        VirtualFile projectDir = basePath != null
                                ? LocalFileSystem.getInstance().findFileByPath(basePath) : null;
                        refreshThenPost(projectDir, dialogModality, expired, () -> {
                            loadTools();
                            OpenSpecNotifier.info(project, "AI Tools", title + " — done");
                        });
                    } else {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            OpenSpecNotifier.warn(project, "AI Tools",
                                    title + " failed: " + result.stderr().trim());
                            sourceButton.setEnabled(true);
                            sourceButton.setText("Retry");
                        }, dialogModality, expired);
                    }
                } catch (ProcessCanceledException ex) {
                    // Cancellation must propagate, but the button still comes back.
                    ApplicationManager.getApplication().invokeLater(() -> {
                        sourceButton.setEnabled(true);
                        sourceButton.setText("Retry");
                    }, dialogModality, expired);
                    throw ex;
                } catch (Exception ex) {
                    // Any failure, not only a CLI error, must restore the button and tell the user.
                    String reason = ex instanceof CliRunner.CliException ? ex.getMessage() : ex.toString();
                    ApplicationManager.getApplication().invokeLater(() -> {
                        OpenSpecNotifier.error(project, "AI Tools", title + " failed: " + reason);
                        sourceButton.setEnabled(true);
                        sourceButton.setText("Retry");
                    }, dialogModality, expired);
                }
            }
        });
    }

    /**
     * Refreshes {@code dir} asynchronously, then runs {@code post} on the EDT under {@code modality}. Safe to call from
     * a background thread (never while holding a read action). The modality must be passed to the refresh itself:
     * {@code LocalFileSystem.refreshFiles} uses the calling thread's default modality, which on a
     * {@code Task.Backgroundable} is non-modal, so the refresh, and anything chained after it, would be held until the
     * dialog closes. With no directory, {@code post} is scheduled directly.
     */
    @VisibleForTesting
    static void refreshThenPost(@Nullable VirtualFile dir, ModalityState modality, Condition<?> expired, Runnable post) {
        Runnable onEdt = () -> ApplicationManager.getApplication().invokeLater(post, modality, expired);
        if (dir == null) {
            onEdt.run();
            return;
        }
        RefreshQueue.getInstance().refresh(true, true, onEdt, modality, List.of(dir));
    }

    private void addToolBuiltIn(ToolInfo tool) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        com.johnnyblabs.openspec.services.AiToolRegistry.AiTool entry =
                com.johnnyblabs.openspec.services.AiToolRegistry.byName(tool.name());
        String dirName = entry != null ? com.johnnyblabs.openspec.services.AiToolRegistry.builtInDir(entry) : null;
        if (dirName == null) return;
        String marker = com.johnnyblabs.openspec.services.AiToolRegistry.builtInMarker(entry);

        try {
            com.intellij.openapi.vfs.VirtualFile baseDir =
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath);
            if (baseDir == null) {
                OpenSpecNotifier.error(project, "AI Tools", "Project directory not found");
                return;
            }
            com.intellij.openapi.application.WriteAction.run(() -> {
                com.intellij.openapi.vfs.VirtualFile dir = com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing(baseDir, dirName);
                if (marker != null && dir != null && dir.findChild(".openspec-target") == null) {
                    com.intellij.openapi.vfs.VfsUtil.saveText(dir.createChildData(this, ".openspec-target"), entry.id() + "\n");
                }
            });
            // syncRefresh() is legal only because this runs on the EDT (a button handler); never call it from runCliAction's
            // background task.
            VirtualFileManager.getInstance().syncRefresh();
            setPreferredTool(tool.name());
            loadTools();
            OpenSpecNotifier.info(project, "AI Tools",
                    tool.name() + " directory created. Install the OpenSpec CLI to generate skills.");
        } catch (Exception ex) {
            OpenSpecNotifier.error(project, "AI Tools",
                    "Failed to create " + dirName + ": " + ex.getMessage());
        }
    }

    private void setPreferredTool(String toolName) {
        com.johnnyblabs.openspec.settings.OpenSpecSettings settings =
                com.johnnyblabs.openspec.settings.OpenSpecSettings.getInstance(project);
        settings.setPreferredTool(toolName);
    }

    /** The footer shown when the CLI is missing, without its HTML wrapper. */
    static String cliHintBody(boolean isWindows) {
        return "Install the OpenSpec CLI to add and configure tools. "
                + com.johnnyblabs.openspec.util.CliInstallHints.html(isWindows);
    }
}
