package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFileManager;
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

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ManageAiToolsDialog extends DialogWrapper {

    private final Project project;
    private final SearchTextField searchField;
    private final JPanel toolListPanel;
    private List<ToolInfo> allTools;

    public ManageAiToolsDialog(Project project) {
        super(project, false);
        this.project = project;
        setTitle("Manage AI Tools");
        setOKButtonText("Close");
        setCancelButtonText(null);

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
                    "<html>Install the OpenSpec CLI to add and configure tools: " +
                    "<code>npm i -g @fission-ai/openspec</code></html>");
            cliHint.setForeground(JBColor.GRAY);
            cliHint.setFont(cliHint.getFont().deriveFont(Font.PLAIN, 11f));
            cliHint.setBorder(JBUI.Borders.emptyTop(8));
            mainPanel.add(cliHint, BorderLayout.SOUTH);
        }

        return mainPanel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    private void loadTools() {
        AiToolDetectionService toolService = project.getService(AiToolDetectionService.class);
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
                btn.addActionListener(e -> runCliAction("update", "Updating " + tool.name(), btn));
                yield btn;
            }
            case DETECTED -> {
                JButton btn = new JButton("Configure");
                if (cliAvailable) {
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
                if (cliAvailable) {
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

    private void runCliAction(String command, String title, JButton sourceButton) {
        sourceButton.setEnabled(false);
        sourceButton.setText("...");

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    String[] args = command.split("\\s+");
                    CliRunner.CliResult result = CliRunner.run(project, args);
                    if (result.isSuccess()) {
                        // Synchronous VFS refresh
                        VirtualFileManager.getInstance().syncRefresh();
                        SwingUtilities.invokeLater(() -> {
                            loadTools();
                            OpenSpecNotifier.info(project, "AI Tools", title + " — done");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            OpenSpecNotifier.warn(project, "AI Tools",
                                    title + " failed: " + result.stderr().trim());
                            sourceButton.setEnabled(true);
                            sourceButton.setText("Retry");
                        });
                    }
                } catch (CliRunner.CliException ex) {
                    SwingUtilities.invokeLater(() -> {
                        OpenSpecNotifier.error(project, "AI Tools",
                                title + " failed: " + ex.getMessage());
                        sourceButton.setEnabled(true);
                        sourceButton.setText("Retry");
                    });
                }
            }
        });
    }

    private void addToolBuiltIn(ToolInfo tool) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        String dirName = AiToolDetectionService.getToolDirName(tool.name());
        if (dirName == null) return;

        try {
            com.intellij.openapi.vfs.VirtualFile baseDir =
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath);
            if (baseDir != null) {
                com.intellij.openapi.application.WriteAction.run(() ->
                        baseDir.createChildDirectory(this, dirName));
                VirtualFileManager.getInstance().syncRefresh();
                setPreferredTool(tool.name());
                loadTools();
                OpenSpecNotifier.info(project, "AI Tools",
                        tool.name() + " directory created. Install the OpenSpec CLI to generate skills.");
            }
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
}
