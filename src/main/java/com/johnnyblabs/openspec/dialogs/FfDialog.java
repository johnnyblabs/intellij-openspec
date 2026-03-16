package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.GenerateAllListener;
import com.johnnyblabs.openspec.services.SchemaService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FfDialog extends DialogWrapper {

    private final Project project;
    private final JBTextArea descriptionField;
    private final JBTextField nameOverrideField;
    private JComboBox<String> schemaCombo;
    private boolean schemaComboVisible;

    // Progress panel components
    private final JPanel progressPanel;
    private final Map<String, JBLabel> artifactLabels = new LinkedHashMap<>();
    private final JBLabel statusLabel;
    private boolean generating = false;
    private boolean completed = false;
    private String generatedChangeName;

    public FfDialog(Project project) {
        super(project, false);
        this.project = project;
        setTitle("Fast-Forward: Create & Generate");

        descriptionField = new JBTextArea(4, 50);
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);
        descriptionField.getEmptyText().setText("Describe what you want to build or fix...");

        nameOverrideField = new JBTextField();
        nameOverrideField.getEmptyText().setText("(optional) e.g., add-user-auth");

        // Schema selector — visible only when multiple schemas exist
        schemaComboVisible = false;
        schemaCombo = new JComboBox<>();
        SchemaService schemaService = project.getService(SchemaService.class);
        if (schemaService != null) {
            List<SchemaInfo> schemas = schemaService.listSchemas();
            if (schemas.size() > 1) {
                schemaComboVisible = true;
                for (SchemaInfo info : schemas) {
                    schemaCombo.addItem(info.name());
                }
                String defaultSchema = OpenSpecSettings.getInstance(project).getDefaultSchema();
                if (defaultSchema != null && !defaultSchema.isEmpty()) {
                    schemaCombo.setSelectedItem(defaultSchema);
                }
            }
        }

        progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressPanel.setBorder(BorderFactory.createTitledBorder("Generation Progress"));
        progressPanel.setVisible(false);

        statusLabel = new JBLabel("");

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder();

        JBLabel hint = new JBLabel("<html><body style='width:" + JBUI.scale(400) + "px'>" +
                "Describe what you want to build. A change will be created and all " +
                "artifacts (proposal, specs, design, tasks) will be generated automatically." +
                "</body></html>");
        hint.setBorder(JBUI.Borders.emptyBottom(8));
        builder.addComponent(hint);

        builder.addLabeledComponent(new JBLabel("Description:"), new JBScrollPane(descriptionField))
                .addLabeledComponent(new JBLabel("Name override:"), nameOverrideField);

        if (schemaComboVisible) {
            builder.addLabeledComponent(new JBLabel("Schema:"), schemaCombo);
        }

        builder.addComponent(progressPanel)
                .addComponent(statusLabel)
                .addComponentFillVertically(new JPanel(), 0);
        return builder.getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (!generating && descriptionField.getText().isBlank()) {
            return new ValidationInfo("Please describe what you want to build", descriptionField);
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        if (completed) {
            super.doOKAction();
            return;
        }

        if (generating) {
            return;
        }

        String description = descriptionField.getText().trim();
        String changeName = nameOverrideField.getText().isBlank()
                ? deriveKebabName(description)
                : nameOverrideField.getText().trim();

        generatedChangeName = changeName;
        startGeneration(changeName, description);
    }

    private void startGeneration(String changeName, String description) {
        generating = true;
        setOKButtonText("Generating...");
        getOKAction().setEnabled(false);
        descriptionField.setEnabled(false);
        nameOverrideField.setEnabled(false);
        progressPanel.setVisible(true);
        statusLabel.setText("Creating change '" + changeName + "'...");

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fast-Forward: " + changeName, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    // Step 1: Create the change
                    indicator.setText("Creating change...");
                    String schema = getSelectedSchema();
                    CliRunner.CliResult createResult;
                    if (schema != null && !schema.isEmpty()) {
                        createResult = CliRunner.run(project,
                                "new", "change", changeName, "--schema", schema);
                    } else {
                        createResult = CliRunner.run(project,
                                "new", "change", changeName);
                    }
                    if (!createResult.isSuccess()) {
                        showError("Failed to create change: " + createResult.stderr());
                        return;
                    }

                    // Step 2: Get artifact DAG
                    indicator.setText("Loading artifact status...");
                    ArtifactOrchestrationService orchestration =
                            project.getService(ArtifactOrchestrationService.class);
                    ChangeArtifactDag dag = orchestration.getArtifactStatus(changeName);
                    if (dag == null) {
                        showError("Failed to load artifact status");
                        return;
                    }

                    // Initialize progress labels
                    ApplicationManager.getApplication().invokeLater(() -> {
                        progressPanel.removeAll();
                        for (ArtifactInfo artifact : dag.getArtifacts()) {
                            JBLabel label = new JBLabel("  " + artifact.id());
                            label.setIcon(AllIcons.RunConfigurations.TestNotRan);
                            artifactLabels.put(artifact.id(), label);
                            progressPanel.add(label);
                        }
                        progressPanel.revalidate();
                        progressPanel.repaint();
                    });

                    // Step 3: Generate all artifacts
                    indicator.setText("Generating artifacts...");
                    DirectApiService apiService = project.getService(DirectApiService.class);
                    if (apiService == null) {
                        showError("Direct API service not available. Configure an AI provider in Settings.");
                        return;
                    }

                    orchestration.generateAllRemaining(changeName, apiService, new GenerateAllListener() {
                        @Override
                        public void onArtifactStarted(String artifactId, int index, int total) {
                            indicator.setText("Generating " + artifactId + " (" + index + "/" + total + ")");
                            indicator.setFraction((double) (index - 1) / total);
                            updateArtifactLabel(artifactId, AllIcons.Process.Step_1, "Generating...", COLOR_GENERATING);
                        }

                        @Override
                        public void onArtifactCompleted(String artifactId) {
                            updateArtifactLabel(artifactId, AllIcons.RunConfigurations.TestPassed, "Done", COLOR_DONE);
                        }

                        @Override
                        public void onAllComplete() {
                            onGenerationComplete(changeName);
                        }

                        @Override
                        public void onError(String artifactId, Exception exception) {
                            if (artifactId != null) {
                                updateArtifactLabel(artifactId, AllIcons.RunConfigurations.TestError, "Error", COLOR_ERROR);
                            }
                            showError("Generation failed" + (artifactId != null ? " at " + artifactId : "") +
                                    ": " + exception.getMessage());
                        }

                        @Override
                        public void onCancelled(String artifactId) {
                            showError("Generation cancelled");
                        }
                    });

                } catch (CliRunner.CliException e) {
                    showError("CLI error: " + e.getMessage());
                }
            }
        });
    }

    private static final JBColor COLOR_GENERATING = new JBColor(new Color(60, 130, 230), new Color(80, 150, 250));
    private static final JBColor COLOR_DONE = new JBColor(new Color(0, 128, 0), new Color(100, 210, 100));
    private static final JBColor COLOR_ERROR = JBColor.RED;

    private void updateArtifactLabel(String artifactId, Icon icon, String suffix, Color color) {
        ApplicationManager.getApplication().invokeLater(() -> {
            JBLabel label = artifactLabels.get(artifactId);
            if (label != null) {
                label.setText("  " + artifactId + " — " + suffix);
                label.setIcon(icon);
                label.setForeground(color);
            }
        });
    }

    private void onGenerationComplete(String changeName) {
        ApplicationManager.getApplication().invokeLater(() -> {
            completed = true;
            generating = false;
            statusLabel.setText("<html><b>All artifacts generated for '" + changeName + "'!</b> Ready for implementation.</html>");
            statusLabel.setForeground(COLOR_DONE);
            statusLabel.setIcon(AllIcons.General.InspectionsOK);
            setOKButtonText("Done");
            getOKAction().setEnabled(true);
        });
    }

    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            generating = false;
            statusLabel.setText(message);
            statusLabel.setForeground(COLOR_ERROR);
            statusLabel.setIcon(AllIcons.General.Error);
            setOKButtonText("Close");
            getOKAction().setEnabled(true);
        });
    }

    public String getGeneratedChangeName() {
        return generatedChangeName;
    }

    public boolean isCompleted() {
        return completed;
    }

    /**
     * Returns the selected schema name, or null if the schema selector is not visible.
     */
    public @Nullable String getSelectedSchema() {
        if (!schemaComboVisible) return null;
        Object selected = schemaCombo.getSelectedItem();
        return selected != null ? selected.toString() : null;
    }

    /**
     * Returns whether the schema combo box is visible (for testing).
     */
    boolean isSchemaComboVisible() {
        return schemaComboVisible;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return descriptionField;
    }

    static String deriveKebabName(String description) {
        if (description == null || description.isBlank()) return "unnamed-change";
        String kebab = description.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
        // Limit to first 5 words worth
        String[] parts = kebab.split("-");
        if (parts.length > 5) {
            kebab = String.join("-", java.util.Arrays.copyOf(parts, 5));
        }
        return kebab.isEmpty() ? "unnamed-change" : kebab;
    }
}
