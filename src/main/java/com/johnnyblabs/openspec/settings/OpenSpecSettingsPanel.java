package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.services.CliDetectionService;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

public class OpenSpecSettingsPanel {

    private static final Logger LOG = Logger.getInstance(OpenSpecSettingsPanel.class);
    private static final String API_KEY_MASK = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    private final JPanel mainPanel;

    // CLI section
    private final TextFieldWithBrowseButton cliPathField;
    private final JBLabel cliStatusLabel;
    private final JComboBox<String> versionCombo;

    // General section
    private final JComboBox<String> profileCombo;
    private final JSpinner cliTimeoutSpinner;
    private final JBCheckBox autoRefreshCheckbox;
    private final JBCheckBox strictValidationCheckbox;

    // Direct API section
    private JComboBox<String> aiProviderCombo;
    private JPasswordField apiKeyField;
    private JComboBox<String> aiModelCombo;
    private JBLabel aiTestResultLabel;


    // State
    private final Project project;

    public OpenSpecSettingsPanel(Project project) {
        this.project = project;

        // --- CLI Section ---
        cliPathField = new TextFieldWithBrowseButton();
        cliPathField.addBrowseFolderListener(
                new TextBrowseFolderListener(
                        FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                        project));

        JButton detectButton = new JButton("Detect");
        detectButton.addActionListener(e -> detectCli());

        JPanel cliPathRow = new JPanel();
        cliPathRow.setLayout(new BoxLayout(cliPathRow, BoxLayout.X_AXIS));
        cliPathRow.add(cliPathField);
        cliPathRow.add(Box.createHorizontalStrut(4));
        cliPathRow.add(detectButton);

        cliStatusLabel = new JBLabel("Checking...");
        versionCombo = new JComboBox<>(new String[]{"", "1.0.0", "1.1.0", "1.2.0"});
        versionCombo.setEditable(true);

        JPanel cliSection = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("CLI path:"), cliPathRow)
                .addComponent(cliStatusLabel)
                .addLabeledComponent(new JBLabel("Version override:"), versionCombo)
                .getPanel();
        cliSection.setBorder(IdeBorderFactory.createTitledBorder("OpenSpec CLI"));

        // --- General Section ---
        profileCombo = new JComboBox<>(new String[]{"", "spec-driven", "tdd", "rapid"});
        profileCombo.setEditable(true);

        cliTimeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 3600, 1));

        autoRefreshCheckbox = new JBCheckBox("Auto-refresh tool window on file changes");
        strictValidationCheckbox = new JBCheckBox("Strict validation (warnings become errors)");

        JPanel generalSection = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Schema profile:"), profileCombo)
                .addLabeledComponent(new JBLabel("CLI Timeout (seconds):"), cliTimeoutSpinner)
                .addComponent(autoRefreshCheckbox)
                .addComponent(strictValidationCheckbox)
                .getPanel();
        generalSection.setBorder(IdeBorderFactory.createTitledBorder("General"));

        // --- Direct API Section ---
        JPanel directApiSection = buildDirectApiSection();

        // --- Setup Wizard + Manage Tools shortcuts ---
        JButton wizardButton = new JButton("Run Setup Wizard...");
        wizardButton.addActionListener(e -> {
            com.johnnyblabs.openspec.dialogs.SetupWizardDialog dialog =
                    new com.johnnyblabs.openspec.dialogs.SetupWizardDialog(project);
            dialog.show();
        });
        JButton manageToolsButton = new JButton("Manage AI Tools...");
        manageToolsButton.addActionListener(e -> {
            com.johnnyblabs.openspec.dialogs.ManageAiToolsDialog dialog =
                    new com.johnnyblabs.openspec.dialogs.ManageAiToolsDialog(project);
            dialog.show();
        });
        JPanel wizardRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        wizardRow.add(wizardButton);
        wizardRow.add(manageToolsButton);

        // --- Assemble main panel ---
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        wizardRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        cliSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        generalSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        directApiSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(wizardRow);
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(cliSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(generalSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(directApiSection);

        // Initialize state
        detectCli(); // Auto-detect CLI on panel creation
        onProviderChanged();
    }

    private JPanel buildDirectApiSection() {
        JBLabel helpLabel = new JBLabel(
                "<html><body style='width:" + JBUI.scale(400) + "px'>" +
                "Use your own API key to generate specs and artifacts directly. " +
                "This is optional \u2014 not needed if you copy prompts to an AI coding tool." +
                "</body></html>");
        helpLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);

        // Provider dropdown with display names
        aiProviderCombo = new JComboBox<>();
        for (AiProvider p : AiProvider.values()) {
            aiProviderCombo.addItem(p.getDisplayName());
        }
        aiProviderCombo.addActionListener(e -> onProviderChanged());

        // API key + test button
        apiKeyField = new JPasswordField(30);
        JButton testButton = new JButton("Test");
        testButton.addActionListener(e -> testApiConnection());

        JPanel apiKeyRow = new JPanel();
        apiKeyRow.setLayout(new BoxLayout(apiKeyRow, BoxLayout.X_AXIS));
        apiKeyRow.add(apiKeyField);
        apiKeyRow.add(Box.createHorizontalStrut(4));
        apiKeyRow.add(testButton);

        // Model dropdown
        aiModelCombo = new JComboBox<>();
        aiModelCombo.setEditable(true);

        aiTestResultLabel = new JBLabel(" ");

        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(helpLabel)
                .addVerticalGap(8)
                .addLabeledComponent(new JBLabel("Provider:"), aiProviderCombo)
                .addLabeledComponent(new JBLabel("API key:"), apiKeyRow)
                .addLabeledComponent(new JBLabel("Model:"), aiModelCombo)
                .addComponent(aiTestResultLabel)
                .getPanel();
        panel.setBorder(IdeBorderFactory.createTitledBorder("Direct API"));
        return panel;
    }

    // --- CLI detection ---

    private void detectCli() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null) return;

        cliStatusLabel.setText("Detecting...");
        cliStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
        com.intellij.openapi.application.ModalityState modality =
                com.intellij.openapi.application.ModalityState.stateForComponent(mainPanel);
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                detection.detect();
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    if (detection.isAvailable()) {
                        cliPathField.setText(detection.getDetectedPath());
                        cliPathField.repaint();
                    }
                    updateCliStatus();
                    cliStatusLabel.repaint();
                }, modality);
            } catch (Exception e) {
                LOG.warn("CLI detection failed unexpectedly", e);
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    cliStatusLabel.setText("Detection failed: " + e.getMessage());
                    cliStatusLabel.setForeground(JBColor.RED);
                    cliStatusLabel.repaint();
                }, modality);
            }
        });
    }

    private void updateCliStatus() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null && detection.isAvailable()) {
            String version = detection.getDetectedVersion();
            cliStatusLabel.setText("OpenSpec CLI " + (version != null ? "v" + version : "available"));
            cliStatusLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(100, 210, 100)));
        } else {
            cliStatusLabel.setText("OpenSpec CLI not found");
            cliStatusLabel.setForeground(new JBColor(new Color(180, 80, 0), new Color(230, 160, 80)));
        }
    }

    // --- Provider change handler ---

    private void onProviderChanged() {
        AiProvider provider = getSelectedProvider();
        aiModelCombo.removeAllItems();
        for (String model : provider.getModels()) {
            aiModelCombo.addItem(model);
        }
        boolean enabled = provider != AiProvider.NONE;
        apiKeyField.setEnabled(enabled);
        aiModelCombo.setEnabled(enabled);

        if (enabled && AiCredentialStore.hasApiKey(provider)) {
            apiKeyField.setText(API_KEY_MASK);
        } else if (!enabled) {
            apiKeyField.setText("");
        }
    }

    private AiProvider getSelectedProvider() {
        Object selected = aiProviderCombo.getSelectedItem();
        return AiProvider.fromString(selected != null ? selected.toString() : "None");
    }

    // --- Test API connection ---

    private void testApiConnection() {
        aiTestResultLabel.setText("Testing...");
        aiTestResultLabel.setForeground(JBColor.GRAY);

        String key = getApiKey();
        AiProvider provider = getSelectedProvider();
        if (key != null && !key.isBlank() && !key.equals(API_KEY_MASK)) {
            AiCredentialStore.storeApiKey(provider, key);
        }

        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService == null) {
            aiTestResultLabel.setText("Service not available");
            aiTestResultLabel.setForeground(JBColor.RED);
            return;
        }

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return apiService.testConnection();
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    aiTestResultLabel.setText(result);
                    aiTestResultLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(100, 210, 100)));
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    aiTestResultLabel.setText("Failed: " + msg);
                    aiTestResultLabel.setForeground(JBColor.RED);
                }
            }
        }.execute();
    }

    // --- Public accessors ---

    public JPanel getPanel() {
        return mainPanel;
    }

    public String getVersionOverride() {
        Object selected = versionCombo.getSelectedItem();
        return selected != null ? selected.toString() : "";
    }

    public void setVersionOverride(String version) {
        versionCombo.setSelectedItem(version != null ? version : "");
    }

    public String getCliPath() {
        return cliPathField.getText();
    }

    public void setCliPath(String path) {
        cliPathField.setText(path != null ? path : "");
    }

    public String getProfile() {
        Object selected = profileCombo.getSelectedItem();
        return selected != null ? selected.toString() : "";
    }

    public void setProfile(String profile) {
        profileCombo.setSelectedItem(profile != null ? profile : "");
    }

    public int getCliTimeout() {
        return (Integer) cliTimeoutSpinner.getValue();
    }

    public void setCliTimeout(int timeout) {
        cliTimeoutSpinner.setValue(timeout);
    }

    public boolean isAutoRefresh() {
        return autoRefreshCheckbox.isSelected();
    }

    public void setAutoRefresh(boolean autoRefresh) {
        autoRefreshCheckbox.setSelected(autoRefresh);
    }

    public boolean isStrictValidation() {
        return strictValidationCheckbox.isSelected();
    }

    public void setStrictValidation(boolean strict) {
        strictValidationCheckbox.setSelected(strict);
    }

    /**
     * Returns the AI provider as the enum name (e.g., "CLAUDE") for persistence.
     */
    public String getAiProvider() {
        return getSelectedProvider().name();
    }

    /**
     * Sets the AI provider from the enum name (e.g., "CLAUDE").
     */
    public void setAiProvider(String provider) {
        AiProvider p = AiProvider.fromString(provider);
        aiProviderCombo.setSelectedItem(p.getDisplayName());
        onProviderChanged();
    }

    public String getApiKey() {
        return new String(apiKeyField.getPassword());
    }

    public void setApiKey(String key) {
        apiKeyField.setText(key != null ? key : "");
    }

    public String getAiModel() {
        Object selected = aiModelCombo.getSelectedItem();
        return selected != null ? selected.toString() : "";
    }

    public void setAiModel(String model) {
        aiModelCombo.setSelectedItem(model != null ? model : "");
    }

}
