package com.johnnyb.openspec.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.johnnyb.openspec.ai.AiCredentialStore;
import com.johnnyb.openspec.ai.AiProvider;
import com.johnnyb.openspec.ai.DirectApiService;
import com.johnnyb.openspec.services.AiToolDetectionService;
import com.johnnyb.openspec.services.CliDetectionService;

import javax.swing.*;

public class OpenSpecSettingsPanel {

    private final JPanel mainPanel;
    private final JComboBox<String> versionCombo;
    private final TextFieldWithBrowseButton cliPathField;
    private final JComboBox<String> profileCombo;
    private final JBCheckBox autoRefreshCheckbox;
    private final JBCheckBox strictValidationCheckbox;
    private final JBLabel cliStatusLabel;
    private final JComboBox<String> aiProviderCombo;
    private final JPasswordField apiKeyField;
    private final JComboBox<String> aiModelCombo;
    private final JBLabel aiToolsLabel;
    private final JBLabel aiTestResultLabel;

    public OpenSpecSettingsPanel(Project project) {
        versionCombo = new JComboBox<>(new String[]{"", "1.0.0", "1.1.0", "1.2.0"});
        versionCombo.setEditable(true);

        cliPathField = new TextFieldWithBrowseButton();
        cliPathField.addBrowseFolderListener(
                "Select OpenSpec CLI",
                "Choose the openspec executable",
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor());

        JButton detectButton = new JButton("Detect");
        detectButton.addActionListener(e -> detectCli(project));

        JPanel cliPanel = new JPanel();
        cliPanel.setLayout(new BoxLayout(cliPanel, BoxLayout.X_AXIS));
        cliPanel.add(cliPathField);
        cliPanel.add(Box.createHorizontalStrut(4));
        cliPanel.add(detectButton);

        profileCombo = new JComboBox<>(new String[]{"", "spec-driven", "tdd", "rapid"});
        profileCombo.setEditable(true);

        autoRefreshCheckbox = new JBCheckBox("Auto-refresh tool window on file changes");
        strictValidationCheckbox = new JBCheckBox("Strict validation (warnings become errors)");

        cliStatusLabel = new JBLabel("CLI status: unknown");

        // AI Configuration
        aiProviderCombo = new JComboBox<>();
        for (AiProvider p : AiProvider.values()) {
            aiProviderCombo.addItem(p.name());
        }
        aiProviderCombo.addActionListener(e -> onProviderChanged());

        apiKeyField = new JPasswordField(30);
        JButton testButton = new JButton("Test");
        testButton.addActionListener(e -> testApiConnection(project));

        JPanel apiKeyPanel = new JPanel();
        apiKeyPanel.setLayout(new BoxLayout(apiKeyPanel, BoxLayout.X_AXIS));
        apiKeyPanel.add(apiKeyField);
        apiKeyPanel.add(Box.createHorizontalStrut(4));
        apiKeyPanel.add(testButton);

        aiModelCombo = new JComboBox<>();
        aiModelCombo.setEditable(true);

        aiToolsLabel = new JBLabel("Detected AI tools: scanning...");
        aiTestResultLabel = new JBLabel(" ");

        // Detect AI tools
        AiToolDetectionService aiDetection = project.getService(AiToolDetectionService.class);
        if (aiDetection != null) {
            aiDetection.detect();
            aiToolsLabel.setText("Detected: " + aiDetection.getSummary());
        } else {
            aiToolsLabel.setText("Detected: none");
        }

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Version override:"), versionCombo)
                .addLabeledComponent(new JBLabel("CLI path:"), cliPanel)
                .addLabeledComponent(new JBLabel("Schema profile:"), profileCombo)
                .addComponent(autoRefreshCheckbox)
                .addComponent(strictValidationCheckbox)
                .addSeparator()
                .addComponent(cliStatusLabel)
                .addSeparator()
                .addComponent(new JBLabel("AI Configuration"))
                .addLabeledComponent(new JBLabel("AI provider:"), aiProviderCombo)
                .addLabeledComponent(new JBLabel("API key:"), apiKeyPanel)
                .addLabeledComponent(new JBLabel("Model:"), aiModelCombo)
                .addComponent(aiToolsLabel)
                .addComponent(aiTestResultLabel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        updateCliStatus(project);
        onProviderChanged();
    }

    private void detectCli(Project project) {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null) return;

        cliStatusLabel.setText("CLI status: detecting...");
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            detection.detect();
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                if (detection.isAvailable()) {
                    cliPathField.setText(detection.getDetectedPath());
                }
                updateCliStatus(project);
            });
        });
    }

    private void updateCliStatus(Project project) {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null && detection.isAvailable()) {
            String version = detection.getDetectedVersion();
            cliStatusLabel.setText("CLI status: available" + (version != null ? " (v" + version + ")" : ""));
        } else {
            cliStatusLabel.setText("CLI status: not found");
        }
    }

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

    public String getAiProvider() {
        Object selected = aiProviderCombo.getSelectedItem();
        return selected != null ? selected.toString() : "NONE";
    }

    public void setAiProvider(String provider) {
        aiProviderCombo.setSelectedItem(provider != null ? provider : "NONE");
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

    private void onProviderChanged() {
        AiProvider provider = AiProvider.fromString(getAiProvider());
        aiModelCombo.removeAllItems();
        for (String model : provider.getModels()) {
            aiModelCombo.addItem(model);
        }
        boolean enabled = provider != AiProvider.NONE;
        apiKeyField.setEnabled(enabled);
        aiModelCombo.setEnabled(enabled);

        // Load existing key if stored
        if (enabled && AiCredentialStore.hasApiKey(provider)) {
            apiKeyField.setText("••••••••");
        } else if (!enabled) {
            apiKeyField.setText("");
        }
    }

    private void testApiConnection(Project project) {
        aiTestResultLabel.setText("Testing...");
        aiTestResultLabel.setForeground(java.awt.Color.GRAY);

        // Save current key first if changed
        String key = getApiKey();
        AiProvider provider = AiProvider.fromString(getAiProvider());
        if (key != null && !key.isBlank() && !key.equals("••••••••")) {
            AiCredentialStore.storeApiKey(provider, key);
        }

        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService == null) {
            aiTestResultLabel.setText("Service not available");
            aiTestResultLabel.setForeground(java.awt.Color.RED);
            return;
        }

        // Run test in background
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
                    aiTestResultLabel.setForeground(new java.awt.Color(0, 128, 0));
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    aiTestResultLabel.setText("Failed: " + msg);
                    aiTestResultLabel.setForeground(java.awt.Color.RED);
                }
            }
        }.execute();
    }
}
