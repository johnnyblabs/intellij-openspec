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
import com.johnnyb.openspec.services.DeliveryMethodResolver;
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
 * Shows a change selector, interactive artifact pipeline, and a delivery-aware Generate button.
 */
public class WorkflowActionPanel extends JPanel {

    private static final Map<String, String> ARTIFACT_DESCRIPTIONS = Map.of(
            "proposal", "Why this change is needed",
            "design", "How to implement it",
            "specs", "What to build (requirements)",
            "tasks", "Implementation checklist"
    );

    private final Project project;

    // Change selector
    private final JPanel changeSelectorPanel;
    private final JBLabel singleChangeLabel;
    private final JComboBox<String> changeCombo;

    // Pipeline visualization
    private final JPanel pipelinePanel;

    // Generate controls
    private final JButton generateButton;
    private final JButton dropdownButton;
    private final JButton generateAllButton;
    private final JButton cancelButton;

    // Inline guidance (replaces old card-based guidance)
    private final JPanel guidancePanel;
    private final JBLabel guidanceMessageLabel;
    private final JBLabel guidanceWatchingLabel;
    private final JBLabel guidanceNextLabel;
    private final JButton copyAgainButton;
    private final JButton checkUpdatesButton;

    // Setup card (first-run)
    private final JPanel setupCard;
    private final CardLayout cardLayout;

    private String activeChangeName;
    private String nextArtifactId;
    private String lastPrompt;
    private String lastOutputPath;
    private Runnable onRefreshRequested;
    private boolean updatingCombo;
    private ArtifactFileWatcher activeWatcher;

    public WorkflowActionPanel(Project project) {
        this.project = project;
        this.cardLayout = new CardLayout();
        setLayout(cardLayout);
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(6, 8)
        ));

        // --- Normal panel ---
        JPanel normalPanel = new JPanel(new BorderLayout(8, 4));
        normalPanel.setOpaque(false);

        // Change selector
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

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttonPanel.setOpaque(false);
        generateButton = new JButton("Generate");
        generateButton.setEnabled(false);
        generateButton.addActionListener(e -> onGenerate());

        dropdownButton = new JButton("\u25BE");
        dropdownButton.setMargin(JBUI.insets(2));
        dropdownButton.addActionListener(e -> showDeliveryMenu());

        generateAllButton = new JButton("Generate All");
        generateAllButton.setVisible(false);
        generateAllButton.addActionListener(e -> onGenerateAll());

        cancelButton = new JButton("Cancel");
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> onCancelGenerateAll());

        buttonPanel.add(generateButton);
        buttonPanel.add(dropdownButton);
        buttonPanel.add(generateAllButton);
        buttonPanel.add(cancelButton);

        normalPanel.add(infoPanel, BorderLayout.CENTER);
        normalPanel.add(buttonPanel, BorderLayout.EAST);

        // Setup card (first-run)
        setupCard = createSetupCard();

        add(normalPanel, "normal");
        add(setupCard, "setup");
        cardLayout.show(this, "normal");
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

    public void selectChangeAndGenerate(String changeName) {
        activeChangeName = changeName;
        refresh();
        ApplicationManager.getApplication().invokeLater(this::onGenerate);
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
                dropdownButton.setEnabled(false);
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
                dropdownButton.setEnabled(false);
                generateAllButton.setVisible(false);
            } else if (nextArtifactId != null) {
                generateButton.setText(buildGenerateLabel(nextArtifactId));
                generateButton.setEnabled(true);
                dropdownButton.setEnabled(true);
                updateGenerateAllVisibility(dag);
            } else {
                generateButton.setText("Generate");
                generateButton.setEnabled(false);
                dropdownButton.setEnabled(false);
                generateAllButton.setVisible(false);
            }
        });
    }

    private String buildGenerateLabel(String artifactId) {
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMethodResolver.ResolvedMethod method = resolver.resolve();
        String suffix = switch (method.mode()) {
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

        // Click handlers for completed chips
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
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        if (!resolver.hasPreference()) {
            cardLayout.show(this, "setup");
            return;
        }
        DeliveryMethodResolver.ResolvedMethod method = resolver.resolve();
        executeGeneration(method.mode());
    }

    // --- Generation ---

    private void onGenerate() {
        if (activeChangeName == null || nextArtifactId == null) return;

        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);

        if (!resolver.hasPreference()) {
            cardLayout.show(this, "setup");
            return;
        }

        DeliveryMethodResolver.ResolvedMethod method = resolver.resolve();
        executeGeneration(method.mode());
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
                        AiToolDetectionService det = project.getService(AiToolDetectionService.class);
                        String toolLabel = (det != null && det.hasDetectedTools())
                                ? det.getPreferredToolLabel() : null;
                        String clipboardPrompt = prompt;
                        if (toolLabel != null && AiToolDetectionService.isCliTool(toolLabel)
                                && instruction.changeDir() != null && lastOutputPath != null) {
                            clipboardPrompt = prompt + "\n\nSave your response to: "
                                    + instruction.changeDir() + "/" + lastOutputPath;
                        }
                        lastPrompt = clipboardPrompt;
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(clipboardPrompt), null);
                        ApplicationManager.getApplication().invokeLater(() ->
                                showInlineGuidance("clipboard", instruction.changeDir(),
                                        lastOutputPath, toolLabel, nextAfterThis));
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
                            AiToolDetectionService det = project.getService(AiToolDetectionService.class);
                            String toolLabel = (det != null && det.hasDetectedTools())
                                    ? det.getPreferredToolLabel() : null;
                            showInlineGuidance("editor", instruction.changeDir(),
                                    lastOutputPath, toolLabel, nextAfterThis);
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

        // Show normal panel with inline guidance visible
        cardLayout.show(this, "normal");

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

    // --- Delivery menu ---

    private void showDeliveryMenu() {
        JPopupMenu menu = new JPopupMenu();

        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);

        for (DeliveryMode mode : DeliveryMode.values()) {
            String label = mode.getDisplayName();
            if (mode == DeliveryMode.CLIPBOARD) {
                DeliveryMethodResolver.ResolvedMethod resolved = resolver.resolve();
                if (resolved.mode() == DeliveryMode.CLIPBOARD && !resolved.label().equals("Copy to Clipboard")) {
                    label = resolved.label();
                }
            }

            String finalLabel = label;
            JMenuItem item = new JMenuItem(finalLabel);
            item.addActionListener(e -> {
                resolver.savePreference(mode);
                // Update button label immediately
                if (nextArtifactId != null) {
                    generateButton.setText(buildGenerateLabel(nextArtifactId));
                }
                executeGeneration(mode);
            });
            menu.add(item);
        }

        menu.show(dropdownButton, 0, dropdownButton.getHeight());
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
        dropdownButton.setEnabled(false);
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

    // --- Setup card (first-run) ---

    private JPanel createSetupCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBorder(JBUI.Borders.empty(8));
        card.setOpaque(false);

        JBLabel title = new JBLabel("Choose how to generate artifacts:");
        title.setFont(title.getFont().deriveFont(Font.BOLD));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
        if (detection != null) {
            detection.detect();
        }

        JComboBox<String> toolSelector = null;
        if (detection != null && detection.getDetectedTools().size() > 1) {
            JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            toolPanel.setOpaque(false);
            toolPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            toolPanel.add(new JBLabel("AI tool:"));
            toolSelector = new JComboBox<>(detection.getDetectedTools().toArray(new String[0]));
            toolPanel.add(toolSelector);
            content.add(toolPanel);
            content.add(Box.createVerticalStrut(8));
        } else if (detection != null && detection.getDetectedTools().size() == 1) {
            OpenSpecSettings.getInstance(project).setPreferredTool(detection.getDetectedTools().getFirst());
        }

        JPanel options = new JPanel(new GridLayout(0, 1, 0, 4));
        options.setOpaque(false);
        options.setAlignmentX(Component.LEFT_ALIGNMENT);

        DirectApiService apiService = project.getService(DirectApiService.class);
        boolean hasApi = apiService != null && apiService.isConfigured();

        if (hasApi) {
            addSetupOption(options, "Generate via API (configured)", DeliveryMode.DIRECT_API, toolSelector);
        }

        if (detection != null && detection.hasDetectedTools()) {
            String toolName = detection.getPreferredToolLabel();
            addSetupOption(options, "Copy for " + toolName, DeliveryMode.CLIPBOARD, toolSelector);
        }

        addSetupOption(options, "Copy to Clipboard", DeliveryMode.CLIPBOARD, toolSelector);
        addSetupOption(options, "Open in Editor Tab", DeliveryMode.EDITOR_TAB, toolSelector);

        if (!hasApi) {
            JBLabel apiHint = new JBLabel("Configure an API key in Settings > Tools > OpenSpec for direct generation");
            apiHint.setForeground(JBColor.GRAY);
            options.add(apiHint);
        }

        content.add(options);
        card.add(title, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);

        return card;
    }

    private void addSetupOption(JPanel container, String label, DeliveryMode mode,
                                JComboBox<String> toolSelector) {
        JButton btn = new JButton(label);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.addActionListener(e -> {
            if (toolSelector != null) {
                String selectedTool = (String) toolSelector.getSelectedItem();
                if (selectedTool != null) {
                    OpenSpecSettings.getInstance(project).setPreferredTool(selectedTool);
                }
            }
            DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
            resolver.savePreference(mode);
            cardLayout.show(this, "normal");
            refresh();
            executeGeneration(mode);
        });
        container.add(btn);
    }
}
