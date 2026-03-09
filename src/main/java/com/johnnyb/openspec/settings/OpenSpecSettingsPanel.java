package com.johnnyb.openspec.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.johnnyb.openspec.ai.AiCredentialStore;
import com.johnnyb.openspec.ai.AiProvider;
import com.johnnyb.openspec.ai.DeliveryMode;
import com.johnnyb.openspec.ai.DirectApiService;
import com.johnnyb.openspec.services.AiToolDetectionService;
import com.johnnyb.openspec.services.CliDetectionService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

    // Tools & Delivery tab
    private JBLabel detectedToolsLabel;
    private JComboBox<String> deliveryCombo;
    private JBLabel deliveryStatusLabel;
    private final List<DeliveryOption> deliveryOptions = new ArrayList<>();

    // Direct API tab
    private JComboBox<String> aiProviderCombo;
    private JPasswordField apiKeyField;
    private JComboBox<String> aiModelCombo;
    private JBLabel aiTestResultLabel;

    // State
    private final Project project;
    private List<String> detectedTools = List.of();

    /**
     * Maps a single dropdown item to both a delivery mode and an optional tool name.
     */
    private record DeliveryOption(String label, DeliveryMode mode, String toolName) {
    }

    public OpenSpecSettingsPanel(Project project) {
        this.project = project;

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

        // --- Detect AI tools ---
        AiToolDetectionService aiDetection = project.getService(AiToolDetectionService.class);
        if (aiDetection != null) {
            aiDetection.detect();
            detectedTools = aiDetection.getDetectedTools();
        }

        // --- Tools & Delivery Tab ---
        JPanel toolsTab = buildToolsAndDeliveryTab();

        // --- Direct API Tab ---
        JPanel apiTab = buildDirectApiTab();

        // --- Tabbed Pane ---
        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.addTab("Tools & Delivery", toolsTab);
        tabbedPane.addTab("Direct API", apiTab);

        // --- Assemble main panel ---
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        cliSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        generalSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabbedPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(cliSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(generalSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(tabbedPane);

        // Initialize state
        detectCli(); // Auto-detect CLI on panel creation
        onProviderChanged();
        updateDeliveryStatus();
    }

    private JPanel buildToolsAndDeliveryTab() {
        JBLabel helpLabel = new JBLabel(
                "<html><body style='width:400px'>" +
                "Choose how OpenSpec delivers generated content. " +
                "Each detected AI tool gets a copy option, or use your own API key on the Direct API tab." +
                "</body></html>");
        helpLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);

        // Detected tools display
        detectedToolsLabel = new JBLabel(buildDetectedToolsText());

        // Unified delivery dropdown
        buildDeliveryOptions();
        deliveryCombo = new JComboBox<>();
        for (DeliveryOption opt : deliveryOptions) {
            deliveryCombo.addItem(opt.label);
        }
        deliveryCombo.addActionListener(e -> updateDeliveryStatus());

        deliveryStatusLabel = new JBLabel(" ");
        deliveryStatusLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);

        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(helpLabel)
                .addVerticalGap(8)
                .addComponent(detectedToolsLabel)
                .addVerticalGap(4)
                .addLabeledComponent(new JBLabel("Deliver via:"), deliveryCombo)
                .addComponent(deliveryStatusLabel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(8));
        return panel;
    }

    private void buildDeliveryOptions() {
        deliveryOptions.clear();

        // One "Copy for X" entry per detected tool
        for (String tool : detectedTools) {
            deliveryOptions.add(new DeliveryOption(
                    "Copy for " + tool,
                    DeliveryMode.CLIPBOARD,
                    tool));
        }

        // Generic clipboard if no tools, or as a fallback
        if (detectedTools.isEmpty()) {
            deliveryOptions.add(new DeliveryOption(
                    DeliveryMode.CLIPBOARD.getDisplayName(),
                    DeliveryMode.CLIPBOARD,
                    ""));
        }

        deliveryOptions.add(new DeliveryOption(
                DeliveryMode.EDITOR_TAB.getDisplayName(),
                DeliveryMode.EDITOR_TAB,
                ""));
        deliveryOptions.add(new DeliveryOption(
                DeliveryMode.DIRECT_API.getDisplayName(),
                DeliveryMode.DIRECT_API,
                ""));
    }

    private JPanel buildDirectApiTab() {
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
        aiProviderCombo.addActionListener(e -> {
            onProviderChanged();
            updateDeliveryStatus();
        });

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
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(8));
        return panel;
    }

    private String buildDetectedToolsText() {
        if (detectedTools.isEmpty()) {
            return "<html>No AI tools detected. Install a tool or configure an API key to get started.</html>";
        }
        StringBuilder sb = new StringBuilder("<html>Detected: ");
        for (int i = 0; i < detectedTools.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("<b>").append(detectedTools.get(i)).append("</b>");
        }
        sb.append("</html>");
        return sb.toString();
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

    // --- Delivery status ---

    private void updateDeliveryStatus() {
        DeliveryOption selected = getSelectedDeliveryOption();
        if (selected == null) {
            deliveryStatusLabel.setText(" ");
            return;
        }

        if (selected.mode == DeliveryMode.DIRECT_API && !isApiConfigured()) {
            deliveryStatusLabel.setText(
                    "<html>Requires an API key \u2014 configure on the <b>Direct API</b> tab.</html>");
            deliveryStatusLabel.setForeground(new Color(180, 80, 0));
        } else if (selected.mode == DeliveryMode.DIRECT_API) {
            AiProvider provider = getSelectedProvider();
            deliveryStatusLabel.setText("Artifacts will be generated via " + provider.getDisplayName() + " API.");
            deliveryStatusLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        } else if (selected.mode == DeliveryMode.EDITOR_TAB) {
            deliveryStatusLabel.setText("Generated content opens in a new editor tab for review.");
            deliveryStatusLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        } else if (selected.mode == DeliveryMode.CLIPBOARD && !selected.toolName.isBlank()) {
            deliveryStatusLabel.setText("Prompt copied to clipboard, ready to paste into " + selected.toolName + ".");
            deliveryStatusLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        } else {
            deliveryStatusLabel.setText(" ");
        }
    }

    private DeliveryOption getSelectedDeliveryOption() {
        int idx = deliveryCombo.getSelectedIndex();
        if (idx >= 0 && idx < deliveryOptions.size()) {
            return deliveryOptions.get(idx);
        }
        return null;
    }

    private boolean isApiConfigured() {
        AiProvider provider = getSelectedProvider();
        return provider != AiProvider.NONE && AiCredentialStore.hasApiKey(provider);
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

    /**
     * Returns the preferred tool derived from the unified delivery selection.
     * Empty string if the selection is not tool-specific.
     */
    public String getPreferredTool() {
        DeliveryOption opt = getSelectedDeliveryOption();
        return opt != null ? opt.toolName : "";
    }

    /**
     * Returns the delivery method as the DeliveryMode display name.
     */
    public String getDeliveryMethod() {
        DeliveryOption opt = getSelectedDeliveryOption();
        return opt != null ? opt.mode.getDisplayName() : "";
    }

    /**
     * Selects the unified delivery option that matches the saved tool and method.
     */
    public void setDelivery(String savedTool, String savedMethod) {
        // Try to find an exact match for saved preferences
        if (savedMethod != null && !savedMethod.isBlank()) {
            for (int i = 0; i < deliveryOptions.size(); i++) {
                DeliveryOption opt = deliveryOptions.get(i);
                if (opt.mode.getDisplayName().equals(savedMethod)
                        || opt.mode.name().equals(savedMethod)) {
                    // For clipboard mode, also match by tool name
                    if (opt.mode == DeliveryMode.CLIPBOARD) {
                        if (opt.toolName.equals(savedTool != null ? savedTool : "")) {
                            deliveryCombo.setSelectedIndex(i);
                            updateDeliveryStatus();
                            return;
                        }
                    } else {
                        deliveryCombo.setSelectedIndex(i);
                        updateDeliveryStatus();
                        return;
                    }
                }
            }
            // Fallback: match mode only (tool may have changed)
            for (int i = 0; i < deliveryOptions.size(); i++) {
                DeliveryOption opt = deliveryOptions.get(i);
                if (opt.mode.getDisplayName().equals(savedMethod)
                        || opt.mode.name().equals(savedMethod)) {
                    deliveryCombo.setSelectedIndex(i);
                    updateDeliveryStatus();
                    return;
                }
            }
        }

        // Smart default: first detected tool, or API if configured, or generic clipboard
        if (!detectedTools.isEmpty()) {
            deliveryCombo.setSelectedIndex(0); // "Copy for {first tool}"
        } else if (isApiConfigured()) {
            // Select "Generate via API"
            for (int i = 0; i < deliveryOptions.size(); i++) {
                if (deliveryOptions.get(i).mode == DeliveryMode.DIRECT_API) {
                    deliveryCombo.setSelectedIndex(i);
                    break;
                }
            }
        } else {
            deliveryCombo.setSelectedIndex(0); // "Copy to Clipboard"
        }
        updateDeliveryStatus();
    }
}
