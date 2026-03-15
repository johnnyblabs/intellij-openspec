package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.GenerateAllListener;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.ArtifactFileWatcher;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;

import com.johnnyblabs.openspec.util.ApplyPromptBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Workflow action panel displayed below the tree in the tool window.
 * Shows a change selector, interactive artifact pipeline, tool selector, and Generate button.
 */
public class WorkflowActionPanel extends JPanel {

    // --- Color constants (light, dark) ---
    // Chip foreground
    private static final JBColor COLOR_DONE = new JBColor(new Color(0, 128, 0), new Color(100, 210, 100));
    private static final JBColor COLOR_READY = JBColor.BLUE;
    private static final JBColor COLOR_GENERATING = new JBColor(new Color(60, 130, 230), new Color(80, 150, 250));
    private static final JBColor COLOR_ERROR = JBColor.RED;
    private static final JBColor COLOR_BLOCKED = new JBColor(new Color(128, 128, 128), new Color(140, 140, 140));
    // Chip backgrounds
    private static final JBColor COLOR_CHIP_BG_TRANSPARENT = new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0));
    private static final JBColor COLOR_READY_BG = new JBColor(new Color(220, 235, 255), new Color(35, 50, 75));
    private static final JBColor COLOR_ERROR_BG = new JBColor(new Color(255, 230, 230), new Color(90, 25, 25));
    // Chip borders (pulsing pair for GENERATING)
    private static final JBColor COLOR_GENERATING_BORDER_BRIGHT = new JBColor(new Color(60, 130, 230), new Color(80, 150, 250));
    private static final JBColor COLOR_GENERATING_BORDER_DIM = new JBColor(new Color(140, 180, 240), new Color(50, 90, 160));
    // Guidance / feedback text
    private static final JBColor COLOR_SUCCESS = new JBColor(new Color(0, 128, 0), new Color(100, 210, 100));
    private static final JBColor COLOR_FLASH_GREEN = new JBColor(new Color(0, 180, 0), new Color(80, 220, 80));

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

    // Apply controls
    private final JButton applyButton;
    private final JBLabel taskProgressLabel;
    private final JBLabel taskHintLabel;

    // Inline guidance (replaces old card-based guidance)
    private final JPanel guidancePanel;
    private final JTextArea guidanceMessageLabel;
    private final JTextArea guidanceWatchingLabel;
    private final JTextArea guidanceNextLabel;
    private final JButton copyAgainButton;
    private final JButton checkUpdatesButton;

    // Progress bar and elapsed time for Generate All
    private final JProgressBar generateAllProgressBar;
    private final JBLabel elapsedTimeLabel;
    private javax.swing.Timer elapsedTimer;
    private long generateAllStartNanos;

    // Animation state
    private javax.swing.Timer pulseTimer;
    private javax.swing.Timer spinnerTimer;
    private String generatingArtifactId;
    private String errorArtifactId;
    private int spinnerStep;

    // Archive and post-archive controls
    private final JButton archiveButton;
    private final JButton startNewChangeButton;

    // Retry state
    private final JButton retryButton;

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
                JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(8))
        ));
        setOpaque(false);

        // --- Change selector ---
        changeSelectorPanel = new JPanel(new CardLayout());
        changeSelectorPanel.setOpaque(false);

        singleChangeLabel = new JBLabel("No active change");
        singleChangeLabel.setFont(singleChangeLabel.getFont().deriveFont(Font.BOLD, 13f));

        changeCombo = new JComboBox<>();
        changeCombo.setFont(changeCombo.getFont().deriveFont(Font.BOLD, 13f));
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
        pipelinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        pipelinePanel.setOpaque(false);

        // Inline guidance (below pipeline, visible during waiting)
        guidancePanel = new JPanel();
        guidancePanel.setLayout(new BoxLayout(guidancePanel, BoxLayout.Y_AXIS));
        guidancePanel.setOpaque(false);
        guidancePanel.setVisible(false);
        guidancePanel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(JBUI.scale(4), 0, 0, 0)));

        guidanceMessageLabel = createWrappingLabel(Font.BOLD, 13f);

        guidanceWatchingLabel = createWrappingLabel(Font.ITALIC, 11f);
        guidanceWatchingLabel.setForeground(JBColor.GRAY);

        guidanceNextLabel = createWrappingLabel(Font.ITALIC, 11f);
        guidanceNextLabel.setForeground(JBColor.BLUE);

        copyAgainButton = new JButton("Copy again");
        copyAgainButton.addActionListener(e -> {
            if (lastPrompt != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(lastPrompt), null);
                OpenSpecNotifier.info(project, "Generate", "Prompt re-copied to clipboard");
            }
        });

        checkUpdatesButton = new JButton("Check for updates");
        checkUpdatesButton.addActionListener(e -> onCheckForUpdates());

        JPanel guidanceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        guidanceButtons.setOpaque(false);
        guidanceButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        guidanceButtons.add(copyAgainButton);
        guidanceButtons.add(checkUpdatesButton);

        guidancePanel.add(guidanceMessageLabel);
        guidancePanel.add(Box.createVerticalStrut(JBUI.scale(2)));
        guidancePanel.add(guidanceWatchingLabel);
        guidancePanel.add(guidanceNextLabel);
        guidancePanel.add(Box.createVerticalStrut(JBUI.scale(4)));
        guidancePanel.add(guidanceButtons);

        // Progress bar and elapsed time for Generate All
        JPanel progressRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        progressRow.setOpaque(false);
        progressRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        generateAllProgressBar = new JProgressBar(0, 1);
        generateAllProgressBar.setStringPainted(true);
        generateAllProgressBar.setVisible(false);
        generateAllProgressBar.setPreferredSize(new Dimension(JBUI.scale(200), JBUI.scale(18)));

        elapsedTimeLabel = new JBLabel();
        elapsedTimeLabel.setFont(elapsedTimeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        elapsedTimeLabel.setForeground(JBColor.GRAY);
        elapsedTimeLabel.setVisible(false);

        progressRow.add(generateAllProgressBar);
        progressRow.add(elapsedTimeLabel);

        // Retry button for error recovery
        retryButton = new JButton("Retry");
        retryButton.setIcon(AllIcons.Actions.Restart);
        retryButton.setVisible(false);
        retryButton.addActionListener(e -> onGenerateAll());

        // Task progress and hint labels
        taskProgressLabel = new JBLabel();
        taskProgressLabel.setFont(taskProgressLabel.getFont().deriveFont(Font.PLAIN, 12f));
        taskProgressLabel.setForeground(JBColor.GRAY);
        taskProgressLabel.setVisible(false);
        taskProgressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        taskHintLabel = new JBLabel();
        taskHintLabel.setFont(taskHintLabel.getFont().deriveFont(Font.ITALIC, 11f));
        taskHintLabel.setForeground(JBColor.ORANGE);
        taskHintLabel.setVisible(false);
        taskHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Tool selector
        toolSelector = new JComboBox<>();
        toolSelector.addActionListener(e -> onToolSelectionChanged());

        noToolsLabel = new JBLabel(
                "<html><small>Configure an AI tool or API key to get started.</small></html>");
        noToolsLabel.setForeground(JBColor.GRAY);
        noToolsLabel.setVisible(false);

        // Buttons
        generateButton = new JButton("Generate");
        generateButton.setEnabled(false);
        generateButton.addActionListener(e -> onGenerate());

        generateAllButton = new JButton("Generate All");
        generateAllButton.setIcon(AllIcons.Actions.Execute);
        generateAllButton.setFont(generateAllButton.getFont().deriveFont(Font.BOLD));
        generateAllButton.putClientProperty("JButton.buttonType", "gradient");
        generateAllButton.setVisible(false);
        generateAllButton.addActionListener(e -> onGenerateAll());

        cancelButton = new JButton("Cancel");
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> onCancelGenerateAll());

        applyButton = new JButton("Apply Tasks");
        applyButton.setVisible(false);
        applyButton.addActionListener(e -> onApplyTasks());

        archiveButton = new JButton("Archive");
        archiveButton.setIcon(AllIcons.Actions.Checked);
        archiveButton.setVisible(false);
        archiveButton.addActionListener(e -> onArchive());

        startNewChangeButton = new JButton("Start New Change");
        startNewChangeButton.setIcon(AllIcons.General.Add);
        startNewChangeButton.setVisible(false);
        startNewChangeButton.addActionListener(e -> onStartNewChange());

        // --- Layout: vertical stack with full-width guidance ---

        // Header row: change selector (left) + tool dropdown (right)
        JPanel headerRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        changeSelectorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.add(changeSelectorPanel, BorderLayout.CENTER);

        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0));
        toolRow.setOpaque(false);
        toolRow.add(noToolsLabel);
        toolRow.add(toolSelector);
        headerRow.add(toolRow, BorderLayout.EAST);

        // Pipeline row
        pipelinePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pipelinePanel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(JBUI.scale(4), 0, JBUI.scale(2), 0)));

        // Action buttons row (below pipeline, not competing with text)
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(JBUI.scale(4), 0, JBUI.scale(2), 0)));
        actionRow.add(generateButton);
        actionRow.add(generateAllButton);
        actionRow.add(applyButton);
        actionRow.add(archiveButton);
        actionRow.add(startNewChangeButton);
        actionRow.add(retryButton);
        actionRow.add(cancelButton);

        // Assemble vertical stack — guidance gets full panel width
        guidancePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.add(headerRow);
        contentPanel.add(pipelinePanel);
        contentPanel.add(actionRow);
        contentPanel.add(progressRow);
        contentPanel.add(taskProgressLabel);
        contentPanel.add(taskHintLabel);
        contentPanel.add(guidancePanel);

        add(contentPanel, BorderLayout.CENTER);

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

        // Update button labels
        if (nextArtifactId != null) {
            generateButton.setText(buildGenerateLabel(nextArtifactId));
        }
        if (applyButton.isVisible() && applyButton.isEnabled()) {
            DeliveryMode m = ts.mode;
            String suffix = switch (m) {
                case CLIPBOARD -> "clipboard";
                case EDITOR_TAB -> "editor tab";
                case DIRECT_API -> "API";
            };
            applyButton.setText("Apply tasks \u2192 " + suffix);
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
                generateButton.setVisible(true);
                generateButton.setEnabled(false);
                generateButton.setText("Generate");
                generateAllButton.setVisible(false);
                applyButton.setVisible(false);
                taskProgressLabel.setVisible(false);
                taskHintLabel.setVisible(false);
                activeChangeName = null;
                JBLabel emptyLabel = new JBLabel("No changes yet.");
                emptyLabel.setForeground(JBColor.GRAY);
                pipelinePanel.add(emptyLabel);
                com.intellij.ui.HyperlinkLabel proposeLink = new com.intellij.ui.HyperlinkLabel("Propose a change");
                proposeLink.addHyperlinkListener(ev -> {
                    AnAction action = ActionManager.getInstance().getAction("OpenSpec.Propose");
                    if (action != null) {
                        DataContext ctx = dataId -> com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.is(dataId) ? project : null;
                        com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(action, ctx, "WorkflowPanel", null, null);
                    }
                });
                pipelinePanel.add(proposeLink);
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
                generateButton.setVisible(false);
                generateAllButton.setVisible(false);
                showApplyState(dag);
            } else if (nextArtifactId != null) {
                generateButton.setVisible(true);
                generateButton.setText(buildGenerateLabel(nextArtifactId));
                generateButton.setEnabled(true);
                applyButton.setVisible(false);
                taskProgressLabel.setVisible(false);
                taskHintLabel.setVisible(false);
                updateGenerateAllVisibility(dag);
            } else {
                generateButton.setVisible(true);
                generateButton.setText("Generate");
                generateButton.setEnabled(false);
                generateAllButton.setVisible(false);
                applyButton.setVisible(false);
                taskProgressLabel.setVisible(false);
                taskHintLabel.setVisible(false);
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

        Color color;
        switch (artifact.status()) {
            case DONE -> {
                color = COLOR_DONE;
                chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                chip.setBackground(COLOR_CHIP_BG_TRANSPARENT);
                chip.setBorder(JBUI.Borders.empty(1));
            }
            case READY -> {
                color = COLOR_READY;
                chip.setBackground(COLOR_READY_BG);
                chip.setBorder(BorderFactory.createLineBorder(COLOR_READY, 1, true));
            }
            case GENERATING -> {
                color = COLOR_GENERATING;
                chip.setBackground(COLOR_READY_BG);
                // Pulsing border — alternates via repaint driven by pulseTimer
                boolean bright = (pulseTimer != null && System.currentTimeMillis() % 1200 < 600);
                Color borderColor = bright ? COLOR_GENERATING_BORDER_BRIGHT : COLOR_GENERATING_BORDER_DIM;
                chip.setBorder(BorderFactory.createLineBorder(borderColor, 2, true));
            }
            case ERROR -> {
                color = COLOR_ERROR;
                chip.setBackground(COLOR_ERROR_BG);
                chip.setBorder(BorderFactory.createLineBorder(COLOR_ERROR, 2, true));
            }
            default -> {
                color = COLOR_BLOCKED;
                chip.setBackground(COLOR_CHIP_BG_TRANSPARENT);
                chip.setBorder(JBUI.Borders.empty(1));
            }
        }

        // Icon: use AllIcons for generating/error/done states, unicode for others
        JBLabel iconLabel;
        if (artifact.status() == ArtifactStatus.GENERATING) {
            iconLabel = new JBLabel(getSpinnerIcon());
        } else if (artifact.status() == ArtifactStatus.ERROR) {
            iconLabel = new JBLabel(AllIcons.General.Error);
        } else if (artifact.status() == ArtifactStatus.DONE) {
            iconLabel = new JBLabel(AllIcons.Actions.Checked);
        } else {
            String iconText = artifact.status() == ArtifactStatus.READY ? "\u25CF" : "\u25CB";
            iconLabel = new JBLabel(iconText);
            iconLabel.setForeground(color);
            iconLabel.setFont(iconLabel.getFont().deriveFont(12f));
        }

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

    // --- Apply ---

    /**
     * Selects the given change and triggers apply.
     */
    public void selectChangeAndApply(String changeName) {
        activeChangeName = changeName;
        refresh();
        ApplicationManager.getApplication().invokeLater(this::onApplyTasks);
    }

    private void showApplyState(ChangeArtifactDag dag) {
        String changeName = activeChangeName;
        String changeDir = project.getBasePath() + "/openspec/changes/" + changeName;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String tasksContent = null;
            try {
                Path tasksPath = Path.of(changeDir, "tasks.md");
                if (Files.exists(tasksPath)) {
                    tasksContent = Files.readString(tasksPath);
                }
            } catch (IOException ignored) {
            }

            String finalTasksContent = tasksContent;
            ApplicationManager.getApplication().invokeLater(() -> applyStateToUi(finalTasksContent));
        });
    }

    private void applyStateToUi(String tasksContent) {
        if (tasksContent == null) {
            generateButton.setVisible(false);
            applyButton.setVisible(false);
            archiveButton.setVisible(true);
            archiveButton.setEnabled(true);
            archiveButton.setText("Archive");
            startNewChangeButton.setVisible(false);
            taskProgressLabel.setVisible(false);
            taskHintLabel.setVisible(false);
            runChangeValidation();
            return;
        }

        int[] counts = ApplyPromptBuilder.countTasks(tasksContent);
        int complete = counts[0];
        int total = counts[1];
        int remaining = total - complete;

        if (total == 0 || remaining == 0) {
            generateButton.setVisible(false);
            applyButton.setVisible(false);
            archiveButton.setVisible(true);
            archiveButton.setEnabled(true);
            archiveButton.setText("Archive");
            startNewChangeButton.setVisible(false);
            taskProgressLabel.setText(total > 0 ? complete + "/" + total + " tasks complete" : "");
            taskProgressLabel.setVisible(total > 0);
            taskHintLabel.setVisible(false);
            runChangeValidation();
            return;
        }

        // Show apply button with delivery label
        DeliveryMode mode = getSelectedDeliveryMode();
        String suffix = switch (mode) {
            case CLIPBOARD -> "clipboard";
            case EDITOR_TAB -> "editor tab";
            case DIRECT_API -> "API";
        };
        applyButton.setText("Apply tasks \u2192 " + suffix);
        applyButton.setEnabled(true);
        applyButton.setVisible(true);

        taskProgressLabel.setText(complete + "/" + total + " tasks complete");
        taskProgressLabel.setVisible(true);

        // Hint for large task lists
        taskHintLabel.setVisible(remaining >= 10);
        if (remaining >= 10) {
            taskHintLabel.setText("Large task list \u2014 consider reviewing tasks.md first");
        }
    }

    private void onApplyTasks() {
        if (activeChangeName == null) return;
        String changeName = activeChangeName;
        String changeDir = project.getBasePath() + "/openspec/changes/" + changeName;

        applyButton.setEnabled(false);
        applyButton.setText("Preparing...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String prompt = ApplyPromptBuilder.build(changeName, changeDir);
            DeliveryMode mode = getSelectedDeliveryMode();

            lastPrompt = prompt;

            switch (mode) {
                case CLIPBOARD -> {
                    String toolName = getSelectedToolName();
                    String clipboardPrompt = prompt;
                    if (!toolName.isBlank() && AiToolDetectionService.isCliTool(toolName)) {
                        clipboardPrompt = ApplyPromptBuilder.appendSavePathHint(prompt, changeDir);
                    }
                    lastPrompt = clipboardPrompt;
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(clipboardPrompt), null);
                    ApplicationManager.getApplication().invokeLater(() ->
                            showApplyGuidance(changeName, changeDir,
                                    toolName.isBlank() ? null : toolName));
                }
                case EDITOR_TAB -> ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        VirtualFile scratch = com.intellij.openapi.application.WriteAction.compute(() -> {
                            VirtualFile tmp = LocalFileSystem.getInstance()
                                    .findFileByPath(System.getProperty("java.io.tmpdir"));
                            if (tmp == null) return null;
                            String name = "openspec-apply-" + changeName + "-prompt.md";
                            VirtualFile file = tmp.findChild(name);
                            if (file != null) file.delete(this);
                            file = tmp.createChildData(this, name);
                            file.setBinaryContent(prompt.getBytes(StandardCharsets.UTF_8));
                            return file;
                        });
                        if (scratch != null) {
                            FileEditorManager.getInstance(project).openFile(scratch, true);
                        }
                        showApplyGuidance(changeName, changeDir, null);
                    } catch (IOException ex) {
                        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "Apply",
                                "Failed to open prompt: " + ex.getMessage(), com.intellij.notification.NotificationType.ERROR);
                    }
                });
                case DIRECT_API -> {
                    // Direct API: send prompt, but there's no single output file for apply
                    OpenSpecNotifier.warn(project, "Apply",
                            "Apply delivers a prompt for your AI tool to implement. Use Clipboard or Editor Tab to copy the prompt, then paste it into your AI tool.");
                    ApplicationManager.getApplication().invokeLater(() -> {
                        applyButton.setEnabled(true);
                        applyButton.setText("Apply Tasks");
                    });
                }
            }
        });
    }

    private void showApplyGuidance(String changeName, String changeDir, String toolLabel) {
        String toolName = toolLabel != null ? toolLabel : "your AI tool";
        AiToolDetectionService.ToolGuidance guidance = AiToolDetectionService.getToolGuidance(toolName);

        guidanceMessageLabel.setText("\u2713 Implementation prompt copied");
        guidanceMessageLabel.setForeground(COLOR_SUCCESS);

        if (guidance.canAutoSave()) {
            guidanceWatchingLabel.setText(guidance.pasteAction() + " \u2014 watching tasks.md for progress...");
        } else {
            guidanceWatchingLabel.setText(guidance.pasteAction() + ". Save tasks.md when the tool finishes working through the tasks.");
        }

        guidanceNextLabel.setVisible(false);
        copyAgainButton.setVisible(true);
        guidancePanel.setVisible(true);
        guidancePanel.revalidate();

        applyButton.setText("Waiting...");
        applyButton.setEnabled(false);

        // Watch tasks.md for changes
        startTaskWatcher(changeName, changeDir);
    }

    private void startTaskWatcher(String changeName, String changeDir) {
        disposeWatcher();
        activeWatcher = new ArtifactFileWatcher(
                changeDir, "tasks.md",
                () -> {
                    // tasks.md changed — re-parse and update progress
                    onTaskFileChanged(changeName, changeDir);
                },
                () -> {
                    guidanceWatchingLabel.setText("No progress detected yet. Click 'Check progress' to refresh.");
                }
        );
        activeWatcher.start();
    }

    private void onTaskFileChanged(String changeName, String changeDir) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String content = Files.readString(Path.of(changeDir, "tasks.md"));
                int[] counts = ApplyPromptBuilder.countTasks(content);
                int complete = counts[0];
                int total = counts[1];

                ApplicationManager.getApplication().invokeLater(() -> {
                    taskProgressLabel.setText(complete + "/" + total + " tasks complete");

                    if (total > 0 && complete == total) {
                        // All done — show archive button
                        guidancePanel.setVisible(false);
                        applyButton.setVisible(false);
                        generateButton.setVisible(false);
                        archiveButton.setVisible(true);
                        archiveButton.setEnabled(true);
                        archiveButton.setText("Archive");
                        startNewChangeButton.setVisible(false);
                        taskHintLabel.setVisible(false);
                        runChangeValidation();
                        if (onRefreshRequested != null) onRefreshRequested.run();
                    } else {
                        guidanceWatchingLabel.setText("Progress: " + complete + "/" + total + " \u2014 still watching...");
                    }
                });
            } catch (IOException ignored) {
            }
        });
    }

    // --- Archive ---

    private void onArchive() {
        if (activeChangeName == null) return;
        String changeName = activeChangeName;

        archiveButton.setEnabled(false);
        archiveButton.setText("Archiving...");

        ChangeService changeService = project.getService(ChangeService.class);
        Change target = null;
        for (Change c : changeService.getActiveChanges()) {
            if (changeName.equals(c.getName())) {
                target = c;
                break;
            }
        }
        if (target == null) {
            archiveButton.setEnabled(true);
            archiveButton.setText("Archive");
            OpenSpecNotifier.error(project, "Archive", "Change not found: " + changeName);
            return;
        }

        Change finalTarget = target;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Archiving " + changeName, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    ApplicationManager.getApplication().invokeAndWait(() -> {
                        try {
                            changeService.archiveChange(finalTarget);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });

                    SwingUtilities.invokeLater(() -> showPostArchiveState(changeName));
                } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                    throw e;
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        archiveButton.setEnabled(true);
                        archiveButton.setText("Archive");
                        OpenSpecNotifier.error(project, "Archive", "Archive failed: " + ex.getMessage());
                    });
                }
            }
        });
    }

    private void showPostArchiveState(String changeName) {
        archiveButton.setVisible(false);
        generateButton.setVisible(false);
        applyButton.setVisible(false);
        generateAllButton.setVisible(false);
        cancelButton.setVisible(false);
        retryButton.setVisible(false);
        taskHintLabel.setVisible(false);
        taskProgressLabel.setVisible(false);

        startNewChangeButton.setVisible(true);

        guidanceMessageLabel.setText("\u2713 " + changeName + " archived");
        guidanceMessageLabel.setForeground(COLOR_SUCCESS);
        guidanceWatchingLabel.setVisible(false);
        guidanceNextLabel.setVisible(false);
        copyAgainButton.setVisible(false);
        checkUpdatesButton.setVisible(false);
        guidancePanel.setVisible(true);

        if (onRefreshRequested != null) onRefreshRequested.run();
    }

    private void onStartNewChange() {
        AnAction proposeAction = ActionManager.getInstance().getAction("OpenSpec.Propose");
        if (proposeAction != null) {
            com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(proposeAction, DataContext.EMPTY_CONTEXT, "OpenSpecWorkflowPanel", null, null);
        }
    }

    // --- Change-level validation ---

    private void runChangeValidation() {
        if (activeChangeName == null) return;
        String changeName = activeChangeName;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Validating " + changeName, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                BuiltInValidator validator = project.getService(BuiltInValidator.class);
                ValidationResult result = validator.validateChange(changeName);

                SwingUtilities.invokeLater(() -> {
                    long errorCount = result.issues().stream()
                            .filter(i -> i.severity() == ValidationIssue.Severity.ERROR).count();
                    long warnCount = result.issues().stream()
                            .filter(i -> i.severity() == ValidationIssue.Severity.WARNING).count();

                    if (errorCount > 0) {
                        String firstError = result.issues().stream()
                                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                                .findFirst().map(ValidationIssue::message).orElse("");
                        guidanceMessageLabel.setText("\u2717 " + errorCount + " error" +
                                (errorCount > 1 ? "s" : "") + ": " + firstError);
                        guidanceMessageLabel.setForeground(COLOR_ERROR);
                    } else if (warnCount > 0) {
                        String firstWarn = result.issues().stream()
                                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING)
                                .findFirst().map(ValidationIssue::message).orElse("");
                        guidanceMessageLabel.setText("\u26A0 " + warnCount + " warning" +
                                (warnCount > 1 ? "s" : "") + ": " + firstWarn);
                        guidanceMessageLabel.setForeground(JBColor.ORANGE);
                    } else {
                        guidanceMessageLabel.setText("\u2713 Change valid");
                        guidanceMessageLabel.setForeground(COLOR_SUCCESS);
                    }
                    guidanceWatchingLabel.setVisible(false);
                    guidanceNextLabel.setVisible(false);
                    copyAgainButton.setVisible(false);
                    checkUpdatesButton.setVisible(false);
                    guidancePanel.setVisible(true);
                });
            }
        });
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

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating " + artifactId, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
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
                                OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                        "Failed to open prompt: " + ex.getMessage(), com.intellij.notification.NotificationType.ERROR);
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
                                    VirtualFile generatedFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
                                    if (generatedFile != null) {
                                        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                                "Generated " + artifactId, com.intellij.notification.NotificationType.INFORMATION,
                                                OpenSpecNotifier.openFileAction(generatedFile));
                                    } else {
                                        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                                "Generated " + artifactId, com.intellij.notification.NotificationType.INFORMATION);
                                    }
                                    orchestration.invalidateCache(changeName);
                                    refresh();
                                    if (onRefreshRequested != null) onRefreshRequested.run();
                                } catch (IOException ex) {
                                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                            "Failed to write artifact: " + ex.getMessage(), com.intellij.notification.NotificationType.ERROR);
                                }
                            });
                        }
                    }
                } catch (AiApiException ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        String content = "AI generation failed: " + ex.getMessage();
                        if (ex.getSuggestion() != null) {
                            content += "\n" + ex.getSuggestion();
                        }
                        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                content, com.intellij.notification.NotificationType.ERROR,
                                OpenSpecNotifier.openSettingsAction());
                    });
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                    "Generation failed: " + ex.getMessage(), com.intellij.notification.NotificationType.ERROR));
                } finally {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        generateButton.setEnabled(true);
                        generateButton.setText(buildGenerateLabel(artifactId));
                    });
                }
            }
        });
    }

    // --- Inline guidance ---

    private void showInlineGuidance(String deliveryType, String changeDir,
                                     String outputPath, String toolLabel, String nextArtifact) {
        String toolName = toolLabel != null ? toolLabel : "your AI tool";
        AiToolDetectionService.ToolGuidance guidance = AiToolDetectionService.getToolGuidance(toolName);

        if ("clipboard".equals(deliveryType)) {
            guidanceMessageLabel.setText("\u2713 Copied to clipboard");
            guidanceMessageLabel.setForeground(COLOR_SUCCESS);

            if (guidance.canAutoSave()) {
                guidanceWatchingLabel.setText(guidance.pasteAction() + " \u2014 it will save automatically.");
            } else {
                String savePath = (changeDir != null && outputPath != null)
                        ? changeDir + "/" + outputPath : (outputPath != null ? outputPath : "change directory");
                guidanceWatchingLabel.setText(guidance.pasteAction() + ". Save the response to: " + savePath);
            }
            copyAgainButton.setVisible(true);
        } else {
            guidanceMessageLabel.setText("\u2713 Opened in editor tab");
            guidanceMessageLabel.setForeground(COLOR_SUCCESS);
            String savePath = (changeDir != null && outputPath != null)
                    ? changeDir + "/" + outputPath : (outputPath != null ? outputPath : "change directory");
            guidanceWatchingLabel.setText("Copy the prompt to " + guidance.chatPanelName() + ", then save output to: " + savePath);
            copyAgainButton.setVisible(false);
        }

        if (nextArtifact != null) {
            String nextText = "Next: Generate " + nextArtifact;
            if (guidance.promptPrefix() != null) {
                nextText += "  |  Tip: You can also use " + guidance.promptPrefix() + "propose directly in " + guidance.chatPanelName();
            }
            guidanceNextLabel.setText(nextText);
            guidanceNextLabel.setVisible(true);
        } else if (guidance.promptPrefix() != null) {
            guidanceNextLabel.setText("Tip: You can also use " + guidance.promptPrefix() + "propose directly in " + guidance.chatPanelName());
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
            // If in apply state, also check task progress
            String changeDir = project.getBasePath() + "/openspec/changes/" + activeChangeName;
            onTaskFileChanged(activeChangeName, changeDir);
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
        boolean visible = hasApi && remaining >= 2;
        generateAllButton.setVisible(visible);
        if (visible) {
            generateAllButton.setText("Generate All (" + remaining + ")");
        }
    }

    private void onGenerateAll() {
        if (activeChangeName == null) return;

        String changeName = activeChangeName;

        // Reset UI state
        disposeAnimations();
        generatingArtifactId = null;
        generateButton.setEnabled(false);
        generateButton.setText("Generating...");
        generateAllButton.setVisible(false);
        retryButton.setVisible(false);
        cancelButton.setVisible(true);
        guidancePanel.setVisible(false);

        // Count remaining artifacts for progress bar
        ArtifactOrchestrationService orch = project.getService(ArtifactOrchestrationService.class);
        ChangeArtifactDag currentDag = orch.getCachedArtifactStatus(changeName);
        int totalRemaining = currentDag != null
                ? (int) currentDag.getArtifacts().stream().filter(a -> a.status() != ArtifactStatus.DONE).count()
                : 4;

        // Show progress bar
        generateAllProgressBar.setMaximum(totalRemaining);
        generateAllProgressBar.setValue(0);
        generateAllProgressBar.setString("0 of " + totalRemaining + " artifacts");
        generateAllProgressBar.setForeground(null); // reset to default color
        generateAllProgressBar.setVisible(true);

        // Start elapsed timer
        generateAllStartNanos = System.nanoTime();
        elapsedTimeLabel.setText("0s elapsed");
        elapsedTimeLabel.setVisible(true);
        elapsedTimer = new javax.swing.Timer(1000, e -> updateElapsedTime());
        elapsedTimer.start();

        final int totalCount = totalRemaining;

        GenerateAllListener listener = new GenerateAllListener() {
            private int completedCount = 0;

            @Override
            public void onArtifactStarted(String artifactId, int index, int total) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    generatingArtifactId = artifactId;
                    generateButton.setText("Generating " + artifactId + "... " + index + "/" + total);
                    // Refresh chips to show GENERATING state
                    ArtifactOrchestrationService o = project.getService(ArtifactOrchestrationService.class);
                    ChangeArtifactDag dag = o.getCachedArtifactStatus(changeName);
                    if (dag != null) {
                        refreshPipelineChips(dag);
                    }
                    startPulseAnimation();
                });
            }

            @Override
            public void onArtifactCompleted(String artifactId) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    completedCount++;
                    generatingArtifactId = null;
                    stopPulseAnimation();

                    // Update progress bar
                    generateAllProgressBar.setValue(completedCount);
                    generateAllProgressBar.setString(completedCount + " of " + totalCount + " artifacts");

                    // Refresh chips
                    ArtifactOrchestrationService o = project.getService(ArtifactOrchestrationService.class);
                    ChangeArtifactDag dag = o.getCachedArtifactStatus(changeName);
                    if (dag != null) {
                        refreshPipelineChips(dag);
                    }
                });
            }

            @Override
            public void onAllComplete() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    disposeAnimations();
                    generatingArtifactId = null;
                    cancelButton.setVisible(false);

                    // Completion celebration
                    generateAllProgressBar.setValue(generateAllProgressBar.getMaximum());
                    generateAllProgressBar.setString("All complete");
                    generateAllProgressBar.setForeground(COLOR_SUCCESS);

                    // Flash all chips green
                    flashPipelineChipsGreen(changeName);

                    // Show success message
                    guidanceMessageLabel.setText("\u2713 All artifacts generated");
                    guidanceMessageLabel.setForeground(COLOR_SUCCESS);
                    guidanceWatchingLabel.setText("Ready to review or apply tasks");
                    guidanceNextLabel.setVisible(false);
                    copyAgainButton.setVisible(false);
                    checkUpdatesButton.setVisible(false);
                    guidancePanel.setVisible(true);
                    guidancePanel.revalidate();

                    // Auto-hide progress bar and elapsed time after 3 seconds
                    javax.swing.Timer hideTimer = new javax.swing.Timer(3000, ev -> {
                        generateAllProgressBar.setVisible(false);
                        elapsedTimeLabel.setVisible(false);
                        refresh();
                        if (onRefreshRequested != null) onRefreshRequested.run();
                    });
                    hideTimer.setRepeats(false);
                    hideTimer.start();

                    long elapsedSeconds = (System.nanoTime() - generateAllStartNanos) / 1_000_000_000L;
                    OpenSpecNotifier.generateAllSummary(project, generateAllProgressBar.getMaximum(), elapsedSeconds);
                });
            }

            @Override
            public void onError(String artifactId, Exception exception) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    disposeAnimations();
                    generatingArtifactId = null;
                    cancelButton.setVisible(false);

                    // Error state: red progress bar
                    generateAllProgressBar.setForeground(JBColor.RED);

                    // Show error chip state
                    errorArtifactId = artifactId;
                    ArtifactOrchestrationService o = project.getService(ArtifactOrchestrationService.class);
                    ChangeArtifactDag dag = o.getCachedArtifactStatus(changeName);
                    if (dag != null) {
                        refreshPipelineChips(dag);
                    }

                    // Show inline error message
                    String msg = artifactId != null
                            ? artifactId + " failed: " + exception.getMessage()
                            : "Generation failed: " + exception.getMessage();
                    if (exception instanceof AiApiException apiEx && apiEx.getSuggestion() != null) {
                        msg += " — " + apiEx.getSuggestion();
                    }
                    guidanceMessageLabel.setText("\u2717 " + msg);
                    guidanceMessageLabel.setForeground(JBColor.RED);
                    guidanceWatchingLabel.setText("");
                    guidanceNextLabel.setVisible(false);
                    copyAgainButton.setVisible(false);
                    checkUpdatesButton.setVisible(false);
                    guidancePanel.setVisible(true);
                    guidancePanel.revalidate();

                    // Show retry button
                    retryButton.setVisible(true);
                });
            }

            @Override
            public void onCancelled(String artifactId) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    disposeAnimations();
                    generatingArtifactId = null;
                    cancelButton.setVisible(false);
                    generateAllProgressBar.setVisible(false);
                    elapsedTimeLabel.setVisible(false);
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate All",
                            "Generation cancelled", com.intellij.notification.NotificationType.INFORMATION);
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
            // Override status for generating/error artifacts
            ArtifactInfo displayArtifact = a;
            if (a.id().equals(generatingArtifactId) && a.status() != ArtifactStatus.DONE) {
                displayArtifact = new ArtifactInfo(a.id(), a.outputPath(), ArtifactStatus.GENERATING, a.missingDeps());
            } else if (a.id().equals(errorArtifactId) && a.status() != ArtifactStatus.DONE) {
                displayArtifact = new ArtifactInfo(a.id(), a.outputPath(), ArtifactStatus.ERROR, a.missingDeps());
            }
            pipelinePanel.add(createPipelineChip(displayArtifact));
            if (i < artifacts.size() - 1) {
                JBLabel arrow = new JBLabel("\u2192");
                arrow.setForeground(JBColor.GRAY);
                pipelinePanel.add(arrow);
            }
        }
        pipelinePanel.revalidate();
        pipelinePanel.repaint();
    }

    // --- Animation helpers ---

    private void updateElapsedTime() {
        long elapsed = (System.nanoTime() - generateAllStartNanos) / 1_000_000_000L;
        String text;
        if (elapsed < 60) {
            text = elapsed + "s elapsed";
        } else {
            text = (elapsed / 60) + "m " + (elapsed % 60) + "s elapsed";
        }
        elapsedTimeLabel.setText(text);
    }

    private void startPulseAnimation() {
        stopPulseAnimation();
        // Start spinner icon rotation
        spinnerStep = 0;
        spinnerTimer = new javax.swing.Timer(100, e -> {
            spinnerStep = (spinnerStep + 1) % 12;
            // Trigger chip repaint by refreshing the pipeline
            pipelinePanel.repaint();
        });
        spinnerTimer.start();

        // Pulse border animation
        pulseTimer = new javax.swing.Timer(600, e -> pipelinePanel.repaint());
        pulseTimer.start();
    }

    private void stopPulseAnimation() {
        if (pulseTimer != null) {
            pulseTimer.stop();
            pulseTimer = null;
        }
        if (spinnerTimer != null) {
            spinnerTimer.stop();
            spinnerTimer = null;
        }
    }

    private void flashPipelineChipsGreen(String changeName) {
        // Set all chip borders to bright green
        Color flashColor = COLOR_FLASH_GREEN;
        for (Component comp : pipelinePanel.getComponents()) {
            if (comp instanceof JPanel chip) {
                chip.setBorder(BorderFactory.createLineBorder(flashColor, 2, true));
            }
        }
        pipelinePanel.repaint();

        // Restore normal state after 300ms
        javax.swing.Timer flashTimer = new javax.swing.Timer(300, e -> {
            ArtifactOrchestrationService o = project.getService(ArtifactOrchestrationService.class);
            ChangeArtifactDag dag = o.getCachedArtifactStatus(changeName);
            if (dag != null) {
                refreshPipelineChips(dag);
            }
        });
        flashTimer.setRepeats(false);
        flashTimer.start();
    }

    private void disposeAnimations() {
        stopPulseAnimation();
        if (elapsedTimer != null) {
            elapsedTimer.stop();
            elapsedTimer = null;
        }
        errorArtifactId = null;
    }

    @Override
    public void removeNotify() {
        disposeAnimations();
        disposeWatcher();
        super.removeNotify();
    }

    private static JTextArea createWrappingLabel(int fontStyle, float fontSize) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UIManager.getFont("Label.font").deriveFont(fontStyle, fontSize));
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    Icon getSpinnerIcon() {
        return switch (spinnerStep) {
            case 0 -> AllIcons.Process.Step_1;
            case 1 -> AllIcons.Process.Step_2;
            case 2 -> AllIcons.Process.Step_3;
            case 3 -> AllIcons.Process.Step_4;
            case 4 -> AllIcons.Process.Step_5;
            case 5 -> AllIcons.Process.Step_6;
            case 6 -> AllIcons.Process.Step_7;
            case 7 -> AllIcons.Process.Step_8;
            default -> AllIcons.Process.Step_1;
        };
    }
}
