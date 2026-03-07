package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
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
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.util.OpenSpecNotifier;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Workflow action panel displayed below the tree in the tool window.
 * Shows a change selector, artifact pipeline status, and a one-click Generate button.
 */
public class WorkflowActionPanel extends JPanel {

    private final Project project;

    // Change selector — swaps between label (single change) and combo (multiple)
    private final JPanel changeSelectorPanel;
    private final JBLabel singleChangeLabel;
    private final JComboBox<String> changeCombo;

    // Pipeline visualization
    private final JPanel pipelinePanel;

    // Generate controls
    private final JButton generateButton;
    private final JButton dropdownButton;

    // Cards
    private final JPanel setupCard;
    private final JPanel guidanceCard;
    private final JBLabel guidanceConfirmLabel;
    private final JBLabel guidanceToolLabel;
    private final JBLabel guidancePathLabel;
    private final JBLabel guidanceNextLabel;
    private final JButton copyAgainButton;
    private final JPanel normalPanel;
    private final CardLayout cardLayout;

    private String activeChangeName;
    private String nextArtifactId;
    private String lastPrompt;
    private String lastOutputPath;
    private Runnable onRefreshRequested;
    private boolean updatingCombo; // guard against combo selection feedback loop

    public WorkflowActionPanel(Project project) {
        this.project = project;
        this.cardLayout = new CardLayout();
        setLayout(cardLayout);
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(6, 8)
        ));

        // Normal panel
        normalPanel = new JPanel(new BorderLayout(8, 4));
        normalPanel.setOpaque(false);

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
                refreshForChange(selected);
            }
        });

        changeSelectorPanel.add(singleChangeLabel, "label");
        changeSelectorPanel.add(changeCombo, "combo");

        // --- Pipeline chips ---
        pipelinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pipelinePanel.setOpaque(false);

        // Info column: selector + pipeline
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        changeSelectorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pipelinePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(changeSelectorPanel);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(pipelinePanel);

        // --- Generate button + dropdown ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttonPanel.setOpaque(false);
        generateButton = new JButton("Generate");
        generateButton.setEnabled(false);
        generateButton.addActionListener(e -> onGenerate());

        dropdownButton = new JButton("\u25BE");
        dropdownButton.setMargin(JBUI.insets(2));
        dropdownButton.addActionListener(e -> showDeliveryMenu());

        buttonPanel.add(generateButton);
        buttonPanel.add(dropdownButton);

        normalPanel.add(infoPanel, BorderLayout.CENTER);
        normalPanel.add(buttonPanel, BorderLayout.EAST);

        // Setup card (shown on first use)
        setupCard = createSetupCard();

        // Guidance card (shown after clipboard/editor delivery)
        guidanceConfirmLabel = new JBLabel();
        guidanceConfirmLabel.setFont(guidanceConfirmLabel.getFont().deriveFont(Font.BOLD));
        guidanceToolLabel = new JBLabel();
        guidancePathLabel = new JBLabel();
        guidancePathLabel.setForeground(JBColor.GRAY);
        guidancePathLabel.setFont(guidancePathLabel.getFont().deriveFont(Font.PLAIN, 11f));
        guidanceNextLabel = new JBLabel();
        guidanceNextLabel.setForeground(JBColor.BLUE);
        guidanceNextLabel.setFont(guidanceNextLabel.getFont().deriveFont(Font.ITALIC, 11f));
        copyAgainButton = new JButton("Copy again");
        guidanceCard = createGuidanceCard();

        add(normalPanel, "normal");
        add(setupCard, "setup");
        add(guidanceCard, "guidance");
        cardLayout.show(this, "normal");
    }

    public void setOnRefreshRequested(Runnable callback) {
        this.onRefreshRequested = callback;
    }

    /**
     * Refreshes the panel state from the current project data.
     */
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

            // Determine which change to show
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
     * Sets the active change externally (e.g., from context menu) and triggers generation.
     */
    public void selectChangeAndGenerate(String changeName) {
        activeChangeName = changeName;
        refresh();
        ApplicationManager.getApplication().invokeLater(this::onGenerate);
    }

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

            if (dag == null) {
                generateButton.setEnabled(false);
                generateButton.setText("Generate");
                dropdownButton.setEnabled(false);
                activeChangeName = null;
                pipelinePanel.add(new JBLabel("Use Propose to create a change"));
                pipelinePanel.revalidate();
                pipelinePanel.repaint();
                return;
            }

            activeChangeName = dag.getChangeName();

            // Build pipeline chips
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

            // Update generate button
            List<ArtifactInfo> ready = dag.getReadyArtifacts();
            nextArtifactId = ready.isEmpty() ? null : ready.getFirst().id();

            if (dag.isComplete()) {
                generateButton.setEnabled(false);
                generateButton.setText("All complete");
                dropdownButton.setEnabled(false);
            } else if (nextArtifactId != null) {
                generateButton.setText("Generate " + nextArtifactId);
                generateButton.setEnabled(true);
                dropdownButton.setEnabled(true);
            } else {
                generateButton.setText("Generate");
                generateButton.setEnabled(false);
                dropdownButton.setEnabled(false);
            }
        });
    }

    private JPanel createPipelineChip(ArtifactInfo artifact) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        chip.setOpaque(false);

        String icon;
        Color color;
        switch (artifact.status()) {
            case DONE -> { icon = "\u2713"; color = new JBColor(new Color(0, 128, 0), new Color(80, 200, 80)); }
            case READY -> { icon = "\u25CF"; color = JBColor.BLUE; }
            default -> { icon = "\u25CB"; color = JBColor.GRAY; }
        }

        JBLabel iconLabel = new JBLabel(icon);
        iconLabel.setForeground(color);
        JBLabel nameLabel = new JBLabel(artifact.id());
        nameLabel.setForeground(color);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));

        chip.add(iconLabel);
        chip.add(nameLabel);
        return chip;
    }

    private void onGenerate() {
        if (activeChangeName == null || nextArtifactId == null) return;

        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);

        // First-run: show setup card if no preference set
        if (!resolver.hasPreference()) {
            cardLayout.show(this, "setup");
            return;
        }

        DeliveryMethodResolver.ResolvedMethod method = resolver.resolve();
        executeGeneration(method.mode());
    }

    private void showGuidanceCard(String confirmMessage, String outputPath,
                                   boolean showCopyAgain, String nextArtifact) {
        AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
        String toolName = (detection != null && detection.hasDetectedTools())
                ? detection.getPreferredToolLabel()
                : "your AI tool";

        guidanceConfirmLabel.setText("\u2713 " + confirmMessage);
        guidanceConfirmLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(80, 200, 80)));

        if (AiToolDetectionService.isCliTool(toolName)) {
            guidanceToolLabel.setText("Paste into " + toolName + " \u2014 it will save the output automatically");
        } else {
            guidanceToolLabel.setText("Paste into " + toolName + " Chat, copy the response, and save to:");
        }
        guidancePathLabel.setText(outputPath != null ? outputPath : "the change directory");
        copyAgainButton.setVisible(showCopyAgain);

        if (nextArtifact != null) {
            guidanceNextLabel.setText("Next up: Generate " + nextArtifact);
            guidanceNextLabel.setVisible(true);
        } else {
            guidanceNextLabel.setVisible(false);
        }

        cardLayout.show(this, "guidance");
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

                // Determine next artifact from unlocks
                List<String> unlocks = instruction.unlocks();
                String nextAfterThis = unlocks.isEmpty() ? null : unlocks.getFirst();

                switch (mode) {
                    case CLIPBOARD -> {
                        // Append save-path hint for CLI tools
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
                                showGuidanceCard("Instructions copied to clipboard",
                                        lastOutputPath, true, nextAfterThis));
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
                                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                                        .openFile(scratch, true);
                            }
                            showGuidanceCard("Instructions opened in editor tab",
                                    lastOutputPath, false, nextAfterThis);
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
            } finally {
                if (mode != DeliveryMode.DIRECT_API) {
                    // Don't refresh back to normal — guidance card is now showing
                }
            }
        });
    }

    private void onCheckForUpdates() {
        cardLayout.show(this, "normal");
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        if (activeChangeName != null) {
            orchestration.invalidateCache(activeChangeName);
        }
        refresh();
        if (onRefreshRequested != null) onRefreshRequested.run();
    }

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
                executeGeneration(mode);
            });
            menu.add(item);
        }

        menu.show(dropdownButton, 0, dropdownButton.getHeight());
    }

    private JPanel createGuidanceCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(JBUI.Borders.empty(8));
        card.setOpaque(false);

        guidanceConfirmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        guidanceToolLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        guidancePathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        guidanceNextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(guidanceConfirmLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(guidanceToolLabel);
        card.add(Box.createVerticalStrut(2));
        card.add(guidancePathLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(guidanceNextLabel);
        card.add(Box.createVerticalStrut(8));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        copyAgainButton.addActionListener(e -> {
            if (lastPrompt != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(lastPrompt), null);
                OpenSpecNotifier.info(project, "Prompt re-copied to clipboard");
            }
        });

        JButton checkButton = new JButton("Check for updates");
        checkButton.addActionListener(e -> onCheckForUpdates());

        buttons.add(copyAgainButton);
        buttons.add(checkButton);
        card.add(buttons);

        return card;
    }

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

        // Tool selector — shown when multiple tools detected
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
            // Single tool — auto-set preference
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
            // Persist selected tool if selector present
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
