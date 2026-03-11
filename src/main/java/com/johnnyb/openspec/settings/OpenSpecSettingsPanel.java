package com.johnnyb.openspec.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.johnnyb.openspec.ai.AiCredentialStore;
import com.johnnyb.openspec.ai.AiProvider;
import com.johnnyb.openspec.ai.DirectApiService;
import com.johnnyb.openspec.services.CliDetectionService;
import com.johnnyb.openspec.tracking.ForgejoService;
import com.johnnyb.openspec.tracking.PlaneService;
import com.johnnyb.openspec.tracking.TrackerCredentialStore;
import com.johnnyb.openspec.tracking.TrackerType;

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
    private final JBCheckBox autoRefreshCheckbox;
    private final JBCheckBox strictValidationCheckbox;

    // Direct API section
    private JComboBox<String> aiProviderCombo;
    private JPasswordField apiKeyField;
    private JComboBox<String> aiModelCombo;
    private JBLabel aiTestResultLabel;

    // Issue Tracking - Forgejo
    private final JBCheckBox forgejoEnabledCheckbox;
    private final JTextField forgejoUrlField;
    private final JTextField forgejoOwnerField;
    private final JTextField forgejoRepoField;
    private final JPasswordField forgejoTokenField;
    private final JBLabel forgejoTestResultLabel;

    // Issue Tracking - Plane
    private final JBCheckBox planeEnabledCheckbox;
    private final JTextField planeUrlField;
    private final JTextField planeWorkspaceField;
    private final JTextField planeProjectField;
    private final JPasswordField planeApiKeyField;
    private final JBLabel planeTestResultLabel;

    // State
    private final Project project;

    public OpenSpecSettingsPanel(Project project) {
        this.project = project;

        // Pre-init issue tracking fields
        forgejoEnabledCheckbox = new JBCheckBox("Enable Forgejo integration");
        forgejoUrlField = new JTextField(30);
        forgejoOwnerField = new JTextField(20);
        forgejoRepoField = new JTextField(20);
        forgejoTokenField = new JPasswordField(30);
        forgejoTestResultLabel = new JBLabel(" ");

        planeEnabledCheckbox = new JBCheckBox("Enable Plane integration");
        planeUrlField = new JTextField(30);
        planeWorkspaceField = new JTextField(20);
        planeProjectField = new JTextField(20);
        planeApiKeyField = new JPasswordField(30);
        planeTestResultLabel = new JBLabel(" ");

        // --- CLI Section ---
        cliPathField = new TextFieldWithBrowseButton();
        cliPathField.addBrowseFolderListener(
                "Select OpenSpec CLI",
                "Choose the openspec executable",
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor());

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

        autoRefreshCheckbox = new JBCheckBox("Auto-refresh tool window on file changes");
        strictValidationCheckbox = new JBCheckBox("Strict validation (warnings become errors)");

        JPanel generalSection = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Schema profile:"), profileCombo)
                .addComponent(autoRefreshCheckbox)
                .addComponent(strictValidationCheckbox)
                .getPanel();
        generalSection.setBorder(IdeBorderFactory.createTitledBorder("General"));

        // --- Direct API Section ---
        JPanel directApiSection = buildDirectApiSection();

        // --- Issue Tracking Section ---
        JPanel issueTrackingSection = buildIssueTrackingSection();

        // --- Assemble main panel ---
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        cliSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        generalSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        directApiSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        issueTrackingSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(cliSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(generalSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(directApiSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(issueTrackingSection);

        // Initialize state
        detectCli(); // Auto-detect CLI on panel creation
        onProviderChanged();
        updateForgejoFieldsEnabled();
        updatePlaneFieldsEnabled();
    }

    private JPanel buildDirectApiSection() {
        JBLabel helpLabel = new JBLabel(
                "<html><body style='width:400px'>" +
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

    private JPanel buildIssueTrackingSection() {
        // Forgejo sub-group
        forgejoEnabledCheckbox.addActionListener(e -> updateForgejoFieldsEnabled());

        JButton forgejoTestButton = new JButton("Test Connection");
        forgejoTestButton.addActionListener(e -> testForgejoConnection());

        JPanel forgejoTokenRow = new JPanel();
        forgejoTokenRow.setLayout(new BoxLayout(forgejoTokenRow, BoxLayout.X_AXIS));
        forgejoTokenRow.add(forgejoTokenField);
        forgejoTokenRow.add(Box.createHorizontalStrut(4));
        forgejoTokenRow.add(forgejoTestButton);

        JPanel forgejoGroup = FormBuilder.createFormBuilder()
                .addComponent(forgejoEnabledCheckbox)
                .addLabeledComponent(new JBLabel("Server URL:"), forgejoUrlField)
                .addLabeledComponent(new JBLabel("Repository owner:"), forgejoOwnerField)
                .addLabeledComponent(new JBLabel("Repository name:"), forgejoRepoField)
                .addLabeledComponent(new JBLabel("Token:"), forgejoTokenRow)
                .addComponent(forgejoTestResultLabel)
                .getPanel();
        forgejoGroup.setBorder(IdeBorderFactory.createTitledBorder("Forgejo"));

        // Plane sub-group
        planeEnabledCheckbox.addActionListener(e -> updatePlaneFieldsEnabled());

        JButton planeTestButton = new JButton("Test Connection");
        planeTestButton.addActionListener(e -> testPlaneConnection());

        JPanel planeApiKeyRow = new JPanel();
        planeApiKeyRow.setLayout(new BoxLayout(planeApiKeyRow, BoxLayout.X_AXIS));
        planeApiKeyRow.add(planeApiKeyField);
        planeApiKeyRow.add(Box.createHorizontalStrut(4));
        planeApiKeyRow.add(planeTestButton);

        JPanel planeGroup = FormBuilder.createFormBuilder()
                .addComponent(planeEnabledCheckbox)
                .addLabeledComponent(new JBLabel("Server URL:"), planeUrlField)
                .addLabeledComponent(new JBLabel("Workspace slug:"), planeWorkspaceField)
                .addLabeledComponent(new JBLabel("Project identifier:"), planeProjectField)
                .addLabeledComponent(new JBLabel("API key:"), planeApiKeyRow)
                .addComponent(planeTestResultLabel)
                .getPanel();
        planeGroup.setBorder(IdeBorderFactory.createTitledBorder("Plane"));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        forgejoGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        planeGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(forgejoGroup);
        panel.add(Box.createVerticalStrut(4));
        panel.add(planeGroup);
        panel.setBorder(IdeBorderFactory.createTitledBorder("Issue Tracking"));
        return panel;
    }

    private void updateForgejoFieldsEnabled() {
        boolean enabled = forgejoEnabledCheckbox.isSelected();
        forgejoUrlField.setEnabled(enabled);
        forgejoOwnerField.setEnabled(enabled);
        forgejoRepoField.setEnabled(enabled);
        forgejoTokenField.setEnabled(enabled);
    }

    private void updatePlaneFieldsEnabled() {
        boolean enabled = planeEnabledCheckbox.isSelected();
        planeUrlField.setEnabled(enabled);
        planeWorkspaceField.setEnabled(enabled);
        planeProjectField.setEnabled(enabled);
        planeApiKeyField.setEnabled(enabled);
    }

    private void testForgejoConnection() {
        forgejoTestResultLabel.setText("Testing...");
        forgejoTestResultLabel.setForeground(Color.GRAY);

        String token = new String(forgejoTokenField.getPassword());
        if (token != null && !token.isBlank() && !token.equals(API_KEY_MASK)) {
            TrackerCredentialStore.storeToken(TrackerType.FORGEJO, token);
        }

        ForgejoService forgejoService = project.getService(ForgejoService.class);
        if (forgejoService == null) {
            forgejoTestResultLabel.setText("Service not available");
            forgejoTestResultLabel.setForeground(Color.RED);
            return;
        }

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return forgejoService.testConnection(
                        forgejoUrlField.getText(),
                        forgejoOwnerField.getText(),
                        forgejoRepoField.getText());
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    forgejoTestResultLabel.setText(result);
                    forgejoTestResultLabel.setForeground(new Color(0, 128, 0));
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    forgejoTestResultLabel.setText("Failed: " + msg);
                    forgejoTestResultLabel.setForeground(Color.RED);
                }
            }
        }.execute();
    }

    private void testPlaneConnection() {
        planeTestResultLabel.setText("Testing...");
        planeTestResultLabel.setForeground(Color.GRAY);

        String apiKey = new String(planeApiKeyField.getPassword());
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals(API_KEY_MASK)) {
            TrackerCredentialStore.storeToken(TrackerType.PLANE, apiKey);
        }

        PlaneService planeService = project.getService(PlaneService.class);
        if (planeService == null) {
            planeTestResultLabel.setText("Service not available");
            planeTestResultLabel.setForeground(Color.RED);
            return;
        }

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return planeService.testConnection(
                        planeUrlField.getText(),
                        planeWorkspaceField.getText(),
                        planeProjectField.getText());
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    planeTestResultLabel.setText(result);
                    planeTestResultLabel.setForeground(new Color(0, 128, 0));
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    planeTestResultLabel.setText("Failed: " + msg);
                    planeTestResultLabel.setForeground(Color.RED);
                }
            }
        }.execute();
    }

    // --- CLI detection ---

    private void detectCli() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null) return;

        cliStatusLabel.setText("Detecting...");
        cliStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
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
                });
            } catch (Exception e) {
                LOG.warn("CLI detection failed unexpectedly", e);
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    cliStatusLabel.setText("Detection failed: " + e.getMessage());
                    cliStatusLabel.setForeground(Color.RED);
                    cliStatusLabel.repaint();
                });
            }
        });
    }

    private void updateCliStatus() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null && detection.isAvailable()) {
            String version = detection.getDetectedVersion();
            cliStatusLabel.setText("OpenSpec CLI " + (version != null ? "v" + version : "available"));
            cliStatusLabel.setForeground(new Color(0, 128, 0));
        } else {
            cliStatusLabel.setText("OpenSpec CLI not found");
            cliStatusLabel.setForeground(new Color(180, 80, 0));
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
        aiTestResultLabel.setForeground(Color.GRAY);

        String key = getApiKey();
        AiProvider provider = getSelectedProvider();
        if (key != null && !key.isBlank() && !key.equals(API_KEY_MASK)) {
            AiCredentialStore.storeApiKey(provider, key);
        }

        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService == null) {
            aiTestResultLabel.setText("Service not available");
            aiTestResultLabel.setForeground(Color.RED);
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
                    aiTestResultLabel.setForeground(new Color(0, 128, 0));
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    aiTestResultLabel.setText("Failed: " + msg);
                    aiTestResultLabel.setForeground(Color.RED);
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

    // --- Issue Tracking accessors ---

    public boolean isForgejoEnabled() { return forgejoEnabledCheckbox.isSelected(); }
    public void setForgejoEnabled(boolean enabled) {
        forgejoEnabledCheckbox.setSelected(enabled);
        updateForgejoFieldsEnabled();
    }
    public String getForgejoUrl() { return forgejoUrlField.getText(); }
    public void setForgejoUrl(String url) { forgejoUrlField.setText(url != null ? url : ""); }
    public String getForgejoOwner() { return forgejoOwnerField.getText(); }
    public void setForgejoOwner(String owner) { forgejoOwnerField.setText(owner != null ? owner : ""); }
    public String getForgejoRepo() { return forgejoRepoField.getText(); }
    public void setForgejoRepo(String repo) { forgejoRepoField.setText(repo != null ? repo : ""); }
    public String getForgejoToken() { return new String(forgejoTokenField.getPassword()); }
    public void setForgejoToken(String token) { forgejoTokenField.setText(token != null ? token : ""); }

    public boolean isPlaneEnabled() { return planeEnabledCheckbox.isSelected(); }
    public void setPlaneEnabled(boolean enabled) {
        planeEnabledCheckbox.setSelected(enabled);
        updatePlaneFieldsEnabled();
    }
    public String getPlaneUrl() { return planeUrlField.getText(); }
    public void setPlaneUrl(String url) { planeUrlField.setText(url != null ? url : ""); }
    public String getPlaneWorkspace() { return planeWorkspaceField.getText(); }
    public void setPlaneWorkspace(String workspace) { planeWorkspaceField.setText(workspace != null ? workspace : ""); }
    public String getPlaneProjectId() { return planeProjectField.getText(); }
    public void setPlaneProjectId(String projectId) { planeProjectField.setText(projectId != null ? projectId : ""); }
    public String getPlaneApiKey() { return new String(planeApiKeyField.getPassword()); }
    public void setPlaneApiKey(String key) { planeApiKeyField.setText(key != null ? key : ""); }
}
