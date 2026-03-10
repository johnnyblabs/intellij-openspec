package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.johnnyb.openspec.ai.AiApiException;
import com.johnnyb.openspec.ai.DeliveryMode;
import com.johnnyb.openspec.ai.DirectApiService;
import com.johnnyb.openspec.model.ArtifactInfo;
import com.johnnyb.openspec.model.ArtifactInstruction;
import com.johnnyb.openspec.model.ArtifactStatus;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeArtifactDag;
import com.johnnyb.openspec.services.AiToolDetectionService;
import com.johnnyb.openspec.services.ArtifactOrchestrationService;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.services.GenerateAllListener;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.util.ArtifactFileWatcher;
import com.johnnyb.openspec.util.OpenSpecNotifier;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Workflow action panel displayed below the tree in the tool window.
 * Shows a change selector, interactive artifact pipeline, tool selector, and Generate button.
 */
public class WorkflowActionPanel extends JPanel {

    private static final Map<String, String> ARTIFACT_DESCRIPTIONS = Map.of(
            "proposal", "Why this change is needed",
            "design", "How to implement it",
            "specs", "What to build (requirements)",
            "tasks", "Implementation checklist"
    );

    private static final String SEPARATOR_ITEM = "───────────────";

    private final Project project;

    // Change selector
    private final JPanel changeSelectorPanel;
    private final JBLabel singleChangeLabel;
    private final JComboBox<String> changeCombo;

    // Pipeline visualization
    private final JPanel pipelinePanel;

    // Tool selector
    private final JComboBox<String> toolSelector;
    private final JBLabel noToolsLabel;

    // Generate controls
    private final JButton generateButton;
    private final JButton generateAllButton;
    private final JButton cancelButton;

    // Inline guidance (replaces old card-based guidance)
    private final JPanel guidancePanel;
    private final JBLabel guidanceMessageLabel;
    private final JBLabel guidanceWatchingLabel;
    private final JBLabel guidanceNextLabel;
    private final JButton copyAgainButton;
    private final JButton checkUpdatesButton;

    private String activeChangeName;
    private String nextArtifactId;
    private String lastPrompt;
    private String lastOutputPath;
    private Runnable onRefreshRequested;
    private boolean updatingCombo;
    private boolean updatingToolSelector;
    private ArtifactFileWatcher activeWatcher;

    public WorkflowActionPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout(8, 4));
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(6, 8)
        ));
        setOpaque(false);

        // --- Change selector ---
        changeSelectorPanel = new JPanel(new CardLayout());
        changeSelectorPanel.setOpaque(false);

        singleChangeLabel = new JBLabel("No active change");
        singleChangeLabel.setFont(singleChangeLabel.getFont().deriveFont(Font.BOLD));

        changeCombo = new JComboBox<>();
        changeCombo.setFont(changeCombo.getFont().deriveFont(Font.BOLD));
        changeCombo.addActionListener(e -> {
            if (updatingCombo) return;
            String selected = (String) changeCombo.getSelectedItem();
            if (selected != null && !selected.equals(activeChangeName)) {
                activeChangeName = selected;
                disposeWatcher();
                refreshForChange(selected);
            }
        });

        changeSelectorPanel.add(singleChangeLabel, "label");
        changeSelectorPanel.add(changeCombo, "combo");

        // Pipeline chips
        pipelinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pipelinePanel.setOpaque(false);

        // Inline guidance (below pipeline, visible during waiting)
        guidancePanel = new JPanel();
        guidancePanel.setLayout(new BoxLayout(guidancePanel, BoxLayout.Y_AXIS));
        guidancePanel.setOpaque(false);
        guidancePanel.setVisible(false);
        guidancePanel.setBorder(JBUI.Borders.emptyTop(4));

        guidanceMessageLabel = new JBLabel();
        guidanceMessageLabel.setFont(guidanceMessageLabel.getFont().deriveFont(Font.BOLD, 11f));
        guidanceMessageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        guidanceWatchingLabel = new JBLabel();
        guidanceWatchingLabel.setForeground(JBColor.GRAY);
        guidanceWatchingLabel.setFont(guidanceWatchingLabel.getFont().deriveFont(Font.ITALIC, 11f));
        guidanceWatchingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        guidanceNextLabel = new JBLabel();
        guidanceNextLabel.setForeground(JBColor.BLUE);
        guidanceNextLabel.setFont(guidanceNextLabel.getFont().deriveFont(Font.ITALIC, 11f));
        guidanceNextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        copyAgainButton = new JButton("Copy again");
        copyAgainButton.addActionListener(e -> {
            if (lastPrompt != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(lastPrompt), null);
                OpenSpecNotifier.info(project, "Prompt re-copied to clipboard");
            }
        });

        checkUpdatesButton = new JButton("Check for updates");
        checkUpdatesButton.addActionListener(e -> onCheckForUpdates());

        JPanel guidanceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        guidanceButtons.setOpaque(false);
        guidanceButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        guidanceButtons.add(copyAgainButton);
        guidanceButtons.add(checkUpdatesButton);

        guidancePanel.add(guidanceMessageLabel);
        guidancePanel.add(Box.createVerticalStrut(2));
        guidancePanel.add(guidanceWatchingLabel);
        guidancePanel.add(guidanceNextLabel);
        guidancePanel.add(Box.createVerticalStrut(4));
        guidancePanel.add(guidanceButtons);

        // Info column: selector + pipeline + inline guidance
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        changeSelectorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pipelinePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        guidancePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(changeSelectorPanel);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(pipelinePanel);
        infoPanel.add(guidancePanel);

        // Tool selector
        toolSelector = new JComboBox<>();
        toolSelector.addActionListener(e -> onToolSelectionChanged());

        noToolsLabel = new JBLabel(
                "<html><small>Configure an AI tool or API key to get started.</small></html>");
        noToolsLabel.setForeground(JBColor.GRAY);
        noToolsLabel.setVisible(false);

        // Buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setOpaque(false);

        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolRow.setOpaque(false);
        toolRow.add(noToolsLabel);
        toolRow.add(toolSelector);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actionRow.setOpaque(false);

        generateButton = new JButton("Generate");
        generateButton.setEnabled(false);
        generateButton.addActionListener(e -> onGenerate());

        generateAllButton = new JButton("Generate All");
        generateAllButton.setVisible(false);
        generateAllButton.addActionListener(e -> onGenerateAll());

        cancelButton = new JButton("Cancel");
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> onCancelGenerateAll());

        actionRow.add(generateButton);
        actionRow.add(generateAllButton);
        actionRow.add(cancelButton);

        buttonPanel.add(toolRow);
        buttonPanel.add(Box.createVerticalStrut(4));
        buttonPanel.add(actionRow);

        add(infoPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.EAST);

        // Populate tool selector
        populateToolSelector();
    }

    public void setOnRefreshRequested(Runnable callback) {
        this.onRefreshRequested = callback;
    }

    public void refresh() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ChangeService changeService = project.getService(ChangeService.class);
            List<Change> active = changeService.getActiveChanges();
            String[] names = active.stream().map(Change::getName).toArray(String[]::new);

            if (active.isEmpty()) {
                updateChangeSelector(names);
                updatePipelineAndButton(null);
                return;
            }

            String currentSelection = activeChangeName;
            String selected;
            if (currentSelection != null && active.stream().anyMatch(c -> c.getName().equals(currentSelection))) {
                selected = currentSelection;
            } else {
                selected = names[0];
            }

            updateChangeSelector(names);
            refreshForChangeOnPool(selected);
        });
    }

    /**
     * Selects the given change and triggers generation.
     */
    public void selectChangeAndGenerate(String changeName) {
        activeChangeName = changeName;
        refresh();
        ApplicationManager.getApplication().invokeLater(this::onGenerate);
    }

    /**
     * Selects the given change without triggering generation.
     * Used by ProposeAction to auto-focus a newly created change.
     */
    public void selectChange(String changeName) {
        activeChangeName = changeName;
        refresh();
    }

    // --- Tool selector ---

    private void populateToolSelector() {
        updatingToolSelector = true;
        toolSelector.removeAllItems();

        AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
        if (detection != null) {
            detection.detect();
            List<String> tools = detection.getDetectedTools();
            for (String tool : tools) {
                AiToolDetectionService.ToolType type = AiToolDetectionService.getToolType(tool);
                String label = tool + "  [" + (type == AiToolDetectionService.ToolType.CLI ? "CLI" : "IDE") + "]";
                toolSelector.addItem(label);
            }
        }

        DirectApiService apiService = project.getService(DirectApiService.class);
        boolean hasApi = apiService != null && apiService.isConfigured();

        if (hasApi) {
            if (toolSelector.getItemCount() > 0) {
                toolSelector.addItem(SEPARATOR_ITEM);
            }
            toolSelector.addItem("Direct API  [API]");
        }

        if (toolSelector.getItemCount() > 0) {
            toolSelector.addItem(SEPARATOR_ITEM);
        }
        toolSelector.addItem("Editor Tab");
        toolSelector.addItem("Clipboard");

        // Show/hide based on whether any real tools are available
        boolean hasTools = (detection != null && detection.hasDetectedTools()) || hasApi;
        noToolsLabel.setVisible(!hasTools && (detection == null || !detection.hasDetectedTools()));

        // Restore saved selection
        restoreToolSelection();
        updatingToolSelector = false;
    }

    private void restoreToolSelection() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String savedTool = settings.getPreferredTool();
        String savedMethod = settings.getPreferredDeliveryMethod();

        // Try to match saved tool name in the selector items
        if (savedTool != null && !savedTool.isBlank()) {
            for (int i = 0; i < toolSelector.getItemCount(); i++) {
                String item = toolSelector.getItemAt(i);
                if (item.startsWith(savedTool)) {
                    toolSelector.setSelectedIndex(i);
                    return;
                }
            }
        }

        // Try to match by delivery method
        if (savedMethod != null && !savedMethod.isBlank()) {
            String match = switch (savedMethod) {
                case "DIRECT_API" -> "Direct API";
                case "EDITOR_TAB" -> "Editor Tab";
                case "CLIPBOARD" -> "Clipboard";
                default -> null;
            };
            if (match != null) {
                for (int i = 0; i < toolSelector.getItemCount(); i++) {
                    String item = toolSelector.getItemAt(i);
                    if (item.startsWith(match)) {
                        toolSelector.setSelectedIndex(i);
                        return;
                    }
                }
            }
        }

        // Default: first non-separator item
        if (toolSelector.getItemCount() > 0) {
            toolSelector.setSelectedIndex(0);
        }
    }

    private void onToolSelectionChanged() {
        if (updatingToolSelector) return;
        String selected = (String) toolSelector.getSelectedItem();
        if (selected == null || selected.equals(SEPARATOR_ITEM)) return;

        // Save preference
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        ToolSelection ts = parseToolSelection(selected);
        settings.setPreferredTool(ts.toolName);
        settings.setPreferredDeliveryMethod(ts.mode.name());

        // Update generate button label
        if (nextArtifactId != null) {
            generateButton.setText(buildGenerateLabel(nextArtifactId));
        }
    }

    private record ToolSelection(String toolName, DeliveryMode mode) {}

    private ToolSelection parseToolSelection(String selectorItem) {
        if (selectorItem == null || selectorItem.equals(SEPARATOR_ITEM)) {
            return new ToolSelection("", DeliveryMode.CLIPBOARD);
        }
        if (selectorItem.startsWith("Direct API")) {
            return new ToolSelection("", DeliveryMode.DIRECT_API);
        }
        if (selectorItem.equals("Editor Tab")) {
            return new ToolSelection("", DeliveryMode.EDITOR_TAB);
        }
        if (selectorItem.equals("Clipboard")) {
            return new ToolSelection("", DeliveryMode.CLIPBOARD);
        }
        // Tool with type label, e.g. "Claude Code  [CLI]"
        String toolName = selectorItem.replaceAll("\\s+\\[(?:CLI|IDE)]$", "").trim();
        return new ToolSelection(toolName, DeliveryMode.CLIPBOARD);
    }

    private DeliveryMode getSelectedDeliveryMode() {
        String selected = (String) toolSelector.getSelectedItem();
        return parseToolSelection(selected).mode;
    }

    private String getSelectedToolName() {
        String selected = (String) toolSelector.getSelectedItem();
        return parseToolSelection(selected).toolName;
    }

    // --- Change selector ---

    private void updateChangeSelector(String[] names) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (names.length == 0) {
                singleChangeLabel.setText("No active change");
                ((CardLayout) changeSelectorPanel.getLayout()).show(changeSelectorPanel, "label");
                return;
            }

            if (names.length == 1) {
                singleChangeLabel.setText(names[0]);
                singleChangeLabel.setFont(singleChangeLabel.getFont().deriveFont(Font.BOLD));
                ((CardLayout) changeSelectorPanel.getLayout()).show(changeSelectorPanel, "label");
                activeChangeName = names[0];
            } else {
                updatingCombo = true;
                changeCombo.removeAllItems();
                for (String name : names) {
                    changeCombo.addItem(name);
                }
                if (activeChangeName != null) {
                    changeCombo.setSelectedItem(activeChangeName);
                }
                updatingCombo = false;
                ((CardLayout) changeSelectorPanel.getLayout()).show(changeSelectorPanel, "combo");
            }
        });
    }

    // --- Pipeline ---

    private void refreshForChange(String changeName) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> refreshForChangeOnPool(changeName));
    }

    private void refreshForChangeOnPool(String changeName) {
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        ChangeArtifactDag dag = orchestration.getArtifactStatus(changeName);
        updatePipelineAndButton(dag);
    }

    private void updatePipelineAndButton(ChangeArtifactDag dag) {
        ApplicationManager.getApplication().invokeLater(() -> {
            pipelinePanel.removeAll();
            guidancePanel.setVisible(false);

            if (dag == null) {
                generateButton.setEnabled(false);
                generateButton.setText("Generate");
                generateAllButton.setVisible(false);
                activeChangeName = null;
                pipelinePanel.add(new JBLabel("Use Propose to create a change"));
                pipelinePanel.revalidate();
                pipelinePanel.repaint();
                return;
            }

            activeChangeName = dag.getChangeName();

            // Build interactive pipeline chips
            List<ArtifactInfo> artifacts = dag.getArtifacts();
            for (int i = 0; i < artifacts.size(); i++) {
                ArtifactInfo a = artifacts.get(i);
                pipelinePanel.add(createPipelineChip(a));
                if (i < artifacts.size() - 1) {
                    JBLabel arrow = new JBLabel("\u2192");
                    arrow.setForeground(JBColor.GRAY);
                    pipelinePanel.add(arrow);
                }
            }
            pipelinePanel.revalidate();
            pipelinePanel.repaint();

            // Update generate button with delivery method
            List<ArtifactInfo> ready = dag.getReadyArtifacts();
            nextArtifactId = ready.isEmpty() ? null : ready.getFirst().id();

            if (dag.isComplete()) {
                generateButton.setEnabled(false);
                generateButton.setText("All complete");
                generateAllButton.setVisible(false);
            } else if (nextArtifactId != null) {
                generateButton.setText(buildGenerateLabel(nextArtifactId));
                generateButton.setEnabled(true);
                updateGenerateAllVisibility(dag);
            } else {
                generateButton.setText("Generate");
                generateButton.setEnabled(false);
                generateAllButton.setVisible(false);
            }
        });
    }

    private String buildGenerateLabel(String artifactId) {
        DeliveryMode mode = getSelectedDeliveryMode();
        String suffix = switch (mode) {
            case CLIPBOARD -> "clipboard";
            case EDITOR_TAB -> "editor tab";
            case DIRECT_API -> "API";
        };
        return "Generate " + artifactId + " \u2192 " + suffix;
    }

    private JPanel createPipelineChip(ArtifactInfo artifact) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        chip.setOpaque(true);

        String icon;
        Color color;
        switch (artifact.status()) {
            case DONE -> {
                icon = "\u2713";
                color = new JBColor(new Color(0, 128, 0), new Color(80, 200, 80));
                chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            case READY -> {
                icon = "\u25CF";
                color = JBColor.BLUE;
                chip.setBackground(new JBColor(new Color(220, 235, 255), new Color(40, 55, 80)));
                chip.setBorder(BorderFactory.createLineBorder(JBColor.BLUE, 1, true));
            }
            default -> {
                icon = "\u25CB";
                color = JBColor.GRAY;
            }
        }

        if (artifact.status() != ArtifactStatus.READY) {
            chip.setBackground(new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0)));
            chip.setBorder(JBUI.Borders.empty(1));
        }

        JBLabel iconLabel = new JBLabel(icon);
        iconLabel.setForeground(color);
        iconLabel.setFont(iconLabel.getFont().deriveFont(12f));
        JBLabel nameLabel = new JBLabel(artifact.id());
        nameLabel.setForeground(color);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));

        chip.add(iconLabel);
        chip.add(nameLabel);

        // Tooltip with artifact description
        String desc = ARTIFACT_DESCRIPTIONS.getOrDefault(artifact.id(), artifact.id());
        chip.setToolTipText(artifact.id() + ": " + desc);

        // Click handlers
        if (artifact.status() == ArtifactStatus.DONE) {
            chip.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        openArtifactFile(artifact);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) showChipContextMenu(e, artifact);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) showChipContextMenu(e, artifact);
                }
            });
        } else if (artifact.status() == ArtifactStatus.READY) {
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chip.setToolTipText("Click to generate " + artifact.id());
            chip.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        nextArtifactId = artifact.id();
                        onGenerate();
                    }
                }
            });
        }

        return chip;
    }

    private void openArtifactFile(ArtifactInfo artifact) {
        if (activeChangeName == null || artifact.outputPath() == null) return;
        String basePath = project.getBasePath();
        if (basePath == null) return;

        String outputPath = artifact.outputPath();
        // For glob paths (specs), open the change directory instead
        if (outputPath.contains("*")) {
            outputPath = "";
        }
        String filePath = basePath + "/openspec/changes/" + activeChangeName + "/" + outputPath;
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
        if (file != null) {
            FileEditorManager.getInstance(project).openFile(file, true);
        }
    }

    private void showChipContextMenu(MouseEvent e, ArtifactInfo artifact) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(ev -> openArtifactFile(artifact));
        menu.add(openItem);

        JMenuItem regenItem = new JMenuItem("Regenerate");
        regenItem.addActionListener(ev -> onRegenerateArtifact(artifact));
        menu.add(regenItem);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void onRegenerateArtifact(ArtifactInfo artifact) {
        if (activeChangeName == null) return;

        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        List<String> downstream = orchestration.getCompletedDownstream(activeChangeName, artifact.id());

        if (!downstream.isEmpty()) {
            int result = Messages.showYesNoDialog(
                    project,
                    "Regenerating \"" + artifact.id() + "\" may make these downstream artifacts inconsistent:\n"
                            + String.join(", ", downstream) + "\n\nContinue?",
                    "Regenerate Artifact",
                    Messages.getWarningIcon());
            if (result != Messages.YES) return;
        }

        // Treat as if this artifact is the next one to generate
        nextArtifactId = artifact.id();
        executeGeneration(getSelectedDeliveryMode());
    }

    // --- Generation ---

    private void onGenerate() {
        if (activeChangeName == null || nextArtifactId == null) return;
        executeGeneration(getSelectedDeliveryMode());
    }

    private void executeGeneration(DeliveryMode mode) {
        if (activeChangeName == null || nextArtifactId == null) return;

        generateButton.setEnabled(false);
        generateButton.setText("Generating...");

        String changeName = activeChangeName;
        String artifactId = nextArtifactId;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
                ArtifactInstruction instruction = orchestration.getInstruction(changeName, artifactId);
                String prompt = instruction.buildPrompt();

                lastPrompt = prompt;
                lastOutputPath = instruction.outputPath();

                List<String> unlocks = instruction.unlocks();
                String nextAfterThis = unlocks.isEmpty() ? null : unlocks.getFirst();

                switch (mode) {
                    case CLIPBOARD -> {
                        String toolName = getSelectedToolName();
                        String clipboardPrompt = prompt;
                        if (!toolName.isBlank() && AiToolDetectionService.isCliTool(toolName)
                                && instruction.changeDir() != null && lastOutputPath != null) {
                            clipboardPrompt = prompt + "\n\nSave your response to: "
                                    + instruction.changeDir() + "/" + lastOutputPath;
                        }
                        lastPrompt = clipboardPrompt;
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(clipboardPrompt), null);
                        ApplicationManager.getApplication().invokeLater(() ->
                                showInlineGuidance("clipboard", instruction.changeDir(),
                                        lastOutputPath, toolName.isBlank() ? null : toolName, nextAfterThis));
                    }
                    case EDITOR_TAB -> ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            VirtualFile scratch = com.intellij.openapi.application.WriteAction.compute(() -> {
                                VirtualFile tmp = LocalFileSystem.getInstance()
                                        .findFileByPath(System.getProperty("java.io.tmpdir"));
                                if (tmp == null) return null;
                                String name = "openspec-" + artifactId + "-prompt.md";
                                VirtualFile file = tmp.findChild(name);
                                if (file != null) file.delete(this);
                                file = tmp.createChildData(this, name);
                                file.setBinaryContent(prompt.getBytes(StandardCharsets.UTF_8));
                                return file;
                            });
                            if (scratch != null) {
                                FileEditorManager.getInstance(project).openFile(scratch, true);
                            }
                            showInlineGuidance("editor", instruction.changeDir(),
                                    lastOutputPath, null, nextAfterThis);
                        } catch (IOException ex) {
                            OpenSpecNotifier.error(project, "Failed to open prompt: " + ex.getMessage());
                        }
                    });
                    case DIRECT_API -> {
                        DirectApiService apiService = project.getService(DirectApiService.class);
                        String result = apiService.generate(instruction);
                        String outputPath = instruction.changeDir() + "/" + instruction.outputPath();
                        ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                com.intellij.openapi.application.WriteAction.run(() -> {
                                    VirtualFile outFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
                                    if (outFile == null) {
                                        VirtualFile parent = LocalFileSystem.getInstance()
                                                .findFileByPath(instruction.changeDir());
                                        if (parent != null) {
                                            outFile = parent.createChildData(this, instruction.outputPath());
                                        }
                                    }
                                    if (outFile != null) {
                                        outFile.setBinaryContent(result.getBytes(StandardCharsets.UTF_8));
                                    }
                                });
                                OpenSpecNotifier.info(project, "Generated " + artifactId);
                                orchestration.invalidateCache(changeName);
                                refresh();
                                if (onRefreshRequested != null) onRefreshRequested.run();
                            } catch (IOException ex) {
                                OpenSpecNotifier.error(project, "Failed to write artifact: " + ex.getMessage());
                            }
                        });
                    }
                }
            } catch (AiApiException ex) {
                ApplicationManager.getApplication().invokeLater(() ->
                        OpenSpecNotifier.error(project, "AI generation failed: " + ex.getMessage()));
            } catch (Exception ex) {
                ApplicationManager.getApplication().invokeLater(() ->
                        OpenSpecNotifier.error(project, "Generation failed: " + ex.getMessage()));
            }
        });
    }

    // --- Inline guidance ---

    private void showInlineGuidance(String deliveryType, String changeDir,
                                     String outputPath, String toolLabel, String nextArtifact) {
        String toolName = toolLabel != null ? toolLabel : "your AI tool";

        if ("clipboard".equals(deliveryType)) {
            guidanceMessageLabel.setText("\u2713 Copied to clipboard");
            guidanceMessageLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(80, 200, 80)));

            if (AiToolDetectionService.isCliTool(toolName)) {
                guidanceWatchingLabel.setText("Paste into " + toolName + " \u2014 it will save automatically.");
            } else {
                guidanceWatchingLabel.setText("Paste into " + toolName + ", then save to: " + (outputPath != null ? outputPath : "change directory"));
            }
            copyAgainButton.setVisible(true);
        } else {
            guidanceMessageLabel.setText("\u2713 Opened in editor tab");
            guidanceMessageLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(80, 200, 80)));
            guidanceWatchingLabel.setText("Copy the prompt to " + toolName + ", then save output to: " + (outputPath != null ? outputPath : "change directory"));
            copyAgainButton.setVisible(false);
        }

        if (nextArtifact != null) {
            guidanceNextLabel.setText("Next: Generate " + nextArtifact);
            guidanceNextLabel.setVisible(true);
        } else {
            guidanceNextLabel.setVisible(false);
        }

        guidancePanel.setVisible(true);
        guidancePanel.revalidate();

        // Set generate button to waiting state
        if (nextArtifactId != null) {
            generateButton.setText("Waiting for " + nextArtifactId + "...");
            generateButton.setEnabled(false);
        }

        // Start file watcher
        if (changeDir != null && outputPath != null) {
            startFileWatcher(changeDir, outputPath);
        }
    }

    private void startFileWatcher(String changeDir, String outputPath) {
        disposeWatcher();
        activeWatcher = new ArtifactFileWatcher(
                changeDir, outputPath,
                () -> {
                    // File detected — refresh
                    guidancePanel.setVisible(false);
                    ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
                    if (activeChangeName != null) {
                        orchestration.invalidateCache(activeChangeName);
                    }
                    refresh();
                    if (onRefreshRequested != null) onRefreshRequested.run();
                },
                () -> {
                    // Timeout — show manual hint
                    guidanceWatchingLabel.setText("Artifact not detected yet. Click 'Check for updates' to refresh manually.");
                }
        );
        activeWatcher.start();
    }

    private void disposeWatcher() {
        if (activeWatcher != null) {
            activeWatcher.dispose();
            activeWatcher = null;
        }
    }

    private void onCheckForUpdates() {
        disposeWatcher();
        guidancePanel.setVisible(false);
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        if (activeChangeName != null) {
            orchestration.invalidateCache(activeChangeName);
        }
        refresh();
        if (onRefreshRequested != null) onRefreshRequested.run();
    }

    // --- Generate All ---

    private void updateGenerateAllVisibility(ChangeArtifactDag dag) {
        DirectApiService apiService = project.getService(DirectApiService.class);
        boolean hasApi = apiService != null && apiService.isConfigured();
        long remaining = dag.getArtifacts().stream()
                .filter(a -> a.status() != ArtifactStatus.DONE)
                .count();
        generateAllButton.setVisible(hasApi && remaining >= 2);
    }

    private void onGenerateAll() {
        if (activeChangeName == null) return;

        String changeName = activeChangeName;

        generateButton.setEnabled(false);
        generateButton.setText("Generating...");
        generateAllButton.setVisible(false);
        cancelButton.setVisible(true);

        GenerateAllListener listener = new GenerateAllListener() {
            @Override
            public void onArtifactStarted(String artifactId, int index, int total) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    generateButton.setText("Generating " + artifactId + "... " + index + "/" + total);
                });
            }

            @Override
            public void onArtifactCompleted(String artifactId) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    ArtifactOrchestrationService orch = project.getService(ArtifactOrchestrationService.class);
                    ChangeArtifactDag dag = orch.getArtifactStatus(changeName);
                    if (dag != null) {
                        refreshPipelineChips(dag);
                    }
                });
            }

            @Override
            public void onAllComplete() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    cancelButton.setVisible(false);
                    OpenSpecNotifier.info(project, "All artifacts generated for " + changeName);
                    refresh();
                    if (onRefreshRequested != null) onRefreshRequested.run();
                });
            }

            @Override
            public void onError(String artifactId, Exception exception) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    cancelButton.setVisible(false);
                    String msg = artifactId != null
                            ? "Generation failed on " + artifactId + ": " + exception.getMessage()
                            : "Generation failed: " + exception.getMessage();
                    OpenSpecNotifier.error(project, msg);
                    refresh();
                    if (onRefreshRequested != null) onRefreshRequested.run();
                });
            }

            @Override
            public void onCancelled(String artifactId) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    cancelButton.setVisible(false);
                    OpenSpecNotifier.info(project, "Generation cancelled");
                    refresh();
                    if (onRefreshRequested != null) onRefreshRequested.run();
                });
            }
        };

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
            DirectApiService apiService = project.getService(DirectApiService.class);
            orchestration.generateAllRemaining(changeName, apiService, listener);
        });
    }

    private void onCancelGenerateAll() {
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        orchestration.cancelGenerateAll();
        cancelButton.setEnabled(false);
        cancelButton.setText("Cancelling...");
    }

    private void refreshPipelineChips(ChangeArtifactDag dag) {
        pipelinePanel.removeAll();
        List<ArtifactInfo> artifacts = dag.getArtifacts();
        for (int i = 0; i < artifacts.size(); i++) {
            ArtifactInfo a = artifacts.get(i);
            pipelinePanel.add(createPipelineChip(a));
            if (i < artifacts.size() - 1) {
                JBLabel arrow = new JBLabel("\u2192");
                arrow.setForeground(JBColor.GRAY);
                pipelinePanel.add(arrow);
            }
        }
        pipelinePanel.revalidate();
        pipelinePanel.repaint();
    }
}
