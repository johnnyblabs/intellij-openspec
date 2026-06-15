package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.dialogs.NewSchemaDialog;
import com.johnnyblabs.openspec.model.ConfigProfileDetail;
import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.SchemaService;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.services.WorkflowProfileSwitchService;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import com.johnnyblabs.openspec.util.OpenSpecTerminalLauncher;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
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
    /**
     * Workflow profile combo. Replaces the pre-1.x "Schema profile:" combo that was
     * populated with {@code ["", "spec-driven"]} (schema-flavored values fed into the
     * workflow profile CLI API). Now populated with {@code ["", "core", "custom"]} —
     * the OpenSpec workflow profile presets. Persisted via
     * {@link OpenSpecSettings#getProfile()}.
     */
    private final JComboBox<String> profileCombo;
    private final JBLabel profileCliUnavailableLabel;
    private final JBLabel profileOrphanHelpLabel;
    private JButton customizeWorkflowsButton;
    private JPanel customizeBanner;
    private JButton customizeDoneButton;
    private TerminalLauncher terminalLauncher = OpenSpecTerminalLauncher::launchCommand;
    private final JSpinner cliTimeoutSpinner;
    private final JBCheckBox autoRefreshCheckbox;
    private final JBCheckBox strictValidationCheckbox;

    /**
     * Workflow profile presets the combo offers as switch targets. Empty string means
     * "use CLI's active profile." Only CLI-accepted presets are listed — selecting an
     * entry triggers {@code openspec config profile <preset>}, and unaccepted values
     * would fail on apply. {@code "custom"} is intentionally absent: the CLI rejects
     * {@code openspec config profile custom} ("Unknown profile preset 'custom'. Available
     * presets: core"). A persisted legacy {@code "custom"} value renders as an orphan
     * (see {@link #setProfile} + {@link WorkflowProfileRenderer}).
     */
    static final java.util.List<String> WORKFLOW_PROFILE_PRESETS =
            java.util.List.of("", "core");

    // Direct API section
    private JComboBox<String> aiProviderCombo;
    private JPasswordField apiKeyField;
    private JComboBox<String> aiModelCombo;
    private JBLabel aiTestResultLabel;


    // Config Profile section
    private JBLabel profileNameLabel;
    private JBLabel profileDescriptionLabel;
    private JPanel workflowListPanel;
    private JBLabel profileFallbackLabel;

    // Schema section
    private JBList<SchemaInfo> schemaList;
    private DefaultListModel<SchemaInfo> schemaListModel;
    private JComboBox<String> defaultSchemaCombo;
    private JButton forkButton;
    private JButton newSchemaButton;
    private JButton refreshSchemasButton;
    private JBLabel schemaUnsupportedLabel;
    private JPanel schemasContentPanel;

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
        if (com.intellij.openapi.util.SystemInfo.isWindows) {
            cliPathField.setToolTipText(
                    "<html>Leave empty to auto-detect, or enter the full path including the <code>.cmd</code> extension.<br>"
                            + "Typical npm install: <code>%APPDATA%\\npm\\openspec.cmd</code></html>");
        } else {
            cliPathField.setToolTipText(
                    "<html>Leave empty to auto-detect, or enter the full path to the openspec executable.<br>"
                            + "Typical install: <code>/opt/homebrew/bin/openspec</code> or <code>/usr/local/bin/openspec</code></html>");
        }

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
        profileCombo = new JComboBox<>(new DefaultComboBoxModel<>(
                WORKFLOW_PROFILE_PRESETS.toArray(new String[0])));
        profileCombo.setEditable(false);
        profileCombo.setRenderer(new WorkflowProfileRenderer());

        // Label combining "Workflow profile:" + ContextHelpLabel "?" icon
        JPanel profileLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        profileLabelPanel.add(new JBLabel("Workflow profile:"));
        profileLabelPanel.add(ContextHelpLabel.createWithLink(
                "Workflow profile",
                "Workflow profiles control which OpenSpec commands are installed for your AI tools. " +
                        "Core ships a small essential set to keep AI context windows lean. " +
                        "To use additional workflows, click \"Customize workflows…\" — " +
                        "the OpenSpec CLI will show you what's available. " +
                        "Switching profiles is a two-step process: change profile, then run " +
                        "`openspec update` to install the corresponding skills.",
                "Read the full guide",
                () -> com.intellij.ide.BrowserUtil.browse(
                        com.johnnyblabs.openspec.statusbar.OpenSpecProfileStatusBarWidget.DOCS_URL)));

        // Inline message shown when the CLI is unavailable
        profileCliUnavailableLabel = new JBLabel("Install OpenSpec CLI to switch profiles.");
        profileCliUnavailableLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        profileCliUnavailableLabel.setVisible(false);

        // Inline recovery help shown when an orphan (legacy persisted preset no longer
        // accepted by the CLI, e.g. "custom" from plugin v0.2.10) is the selected combo
        // value. Paired with Apply being disabled while the orphan is selected — see
        // OpenSpecConfigurable.isModified().
        profileOrphanHelpLabel = new JBLabel(
                "This entry is from a previous plugin version. Pick \"core\" to switch to the supported preset.");
        profileOrphanHelpLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        profileOrphanHelpLabel.setVisible(false);

        // Combo + Customize workflows… button on a single row. The button launches the
        // CLI's interactive picker in IntelliJ's Terminal tool window — see
        // onCustomizeWorkflowsClicked().
        customizeWorkflowsButton = new JButton("Customize workflows…");
        customizeWorkflowsButton.addActionListener(e -> onCustomizeWorkflowsClicked());
        JPanel profileComboRow = new JPanel();
        profileComboRow.setLayout(new BoxLayout(profileComboRow, BoxLayout.X_AXIS));
        profileComboRow.add(profileCombo);
        profileComboRow.add(Box.createHorizontalStrut(JBUI.scale(4)));
        profileComboRow.add(customizeWorkflowsButton);

        customizeBanner = buildCustomizeBanner();

        cliTimeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 3600, 1));

        autoRefreshCheckbox = new JBCheckBox("Auto-refresh tool window on file changes");
        strictValidationCheckbox = new JBCheckBox("Strict validation (warnings become errors)");

        JPanel generalSection = FormBuilder.createFormBuilder()
                .addLabeledComponent(profileLabelPanel, profileComboRow)
                .addComponentToRightColumn(profileCliUnavailableLabel)
                .addComponentToRightColumn(profileOrphanHelpLabel)
                .addComponentToRightColumn(customizeBanner)
                .addLabeledComponent(new JBLabel("CLI Timeout (seconds):"), cliTimeoutSpinner)
                .addComponent(autoRefreshCheckbox)
                .addComponent(strictValidationCheckbox)
                .getPanel();
        generalSection.setBorder(IdeBorderFactory.createTitledBorder("General"));

        // --- Config Profile Section ---
        JPanel configProfileSection = buildConfigProfileSection();

        // Wire profile combo to refresh dependent UI on selection change.
        profileCombo.addActionListener(e -> {
            refreshConfigProfileSection();
            updateOrphanHelpVisibility();
        });

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

        // --- Schemas Section ---
        JPanel schemasSection = buildSchemasSection();

        // --- Assemble main panel ---
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        wizardRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        cliSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        generalSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        configProfileSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemasSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        directApiSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(wizardRow);
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(cliSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(generalSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(configProfileSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(schemasSection);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(directApiSection);

        // Initialize state
        detectCli(); // Auto-detect CLI on panel creation
        onProviderChanged();
    }

    private JPanel buildConfigProfileSection() {
        profileNameLabel = new JBLabel(" ");
        profileDescriptionLabel = new JBLabel(" ");
        profileDescriptionLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);

        workflowListPanel = new JPanel();
        workflowListPanel.setLayout(new BoxLayout(workflowListPanel, BoxLayout.Y_AXIS));

        profileFallbackLabel = new JBLabel("CLI required for profile details");
        profileFallbackLabel.setForeground(new JBColor(new Color(180, 80, 0), new Color(230, 160, 80)));
        profileFallbackLabel.setVisible(false);

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Active profile:"), profileNameLabel)
                .addComponent(profileDescriptionLabel)
                .addComponent(workflowListPanel)
                .addComponent(profileFallbackLabel)
                .getPanel();
        panel.setBorder(IdeBorderFactory.createTitledBorder("Config Profile"));

        // Load initial profile details
        refreshConfigProfileSection();

        return panel;
    }

    /**
     * Refreshes the Config Profile section by querying the CLI for profile details.
     * Falls back to displaying the locally-stored profile name when CLI is unavailable.
     */
    void refreshConfigProfileSection() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null || !detection.isAvailable()) {
            showProfileFallback();
            return;
        }

        String selectedProfile = getProfile();
        profileNameLabel.setText(selectedProfile.isEmpty() ? "(default)" : selectedProfile);
        profileDescriptionLabel.setText("Loading...");
        workflowListPanel.removeAll();
        profileFallbackLabel.setVisible(false);

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String[] args = selectedProfile.isEmpty()
                        ? new String[]{"config", "profile", "--json"}
                        : new String[]{"config", "profile", selectedProfile, "--json"};
                CliRunner.CliResult result = CliRunner.run(project, args);
                ConfigProfileDetail detail;
                if (result.isSuccess()) {
                    detail = ConfigProfileDetail.fromJson(result.stdout());
                } else {
                    detail = ConfigProfileDetail.fallback(selectedProfile);
                }
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    updateProfileDisplay(detail);
                });
            } catch (Exception ex) {
                LOG.debug("Failed to load profile details", ex);
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(this::showProfileFallback);
            }
        });
    }

    private void updateProfileDisplay(ConfigProfileDetail detail) {
        profileNameLabel.setText(detail.getName().isEmpty() ? "(default)" : detail.getName());
        profileDescriptionLabel.setText(detail.getDescription().isEmpty() ? " " : detail.getDescription());
        profileFallbackLabel.setVisible(false);

        workflowListPanel.removeAll();
        if (detail.getWorkflows().isEmpty()) {
            JBLabel noWorkflows = new JBLabel("No workflow information available");
            noWorkflows.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
            workflowListPanel.add(noWorkflows);
        } else {
            for (String workflow : detail.getWorkflows()) {
                JBLabel workflowLabel = new JBLabel("  \u2022 " + workflow);
                workflowListPanel.add(workflowLabel);
            }
        }
        workflowListPanel.revalidate();
        workflowListPanel.repaint();
    }

    private void showProfileFallback() {
        String selectedProfile = getProfile();
        profileNameLabel.setText(selectedProfile.isEmpty() ? "(not set)" : selectedProfile);
        profileDescriptionLabel.setText(" ");
        workflowListPanel.removeAll();
        workflowListPanel.revalidate();
        workflowListPanel.repaint();
        profileFallbackLabel.setVisible(true);
    }

    private JPanel buildSchemasSection() {
        schemaListModel = new DefaultListModel<>();
        schemaList = new JBList<>(schemaListModel);
        schemaList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SchemaInfo info) {
                    String label = info.name();
                    if (!info.description().isEmpty()) {
                        label += " — " + info.description();
                    }
                    if (info.isBuiltIn()) {
                        label += " (built-in)";
                    }
                    setText(label);
                }
                return this;
            }
        });
        schemaList.setVisibleRowCount(4);

        defaultSchemaCombo = new JComboBox<>();
        defaultSchemaCombo.setEditable(true);

        forkButton = new JButton("Fork");
        forkButton.addActionListener(e -> forkSelectedSchema());

        newSchemaButton = new JButton("New...");
        newSchemaButton.addActionListener(e -> createNewSchema());

        refreshSchemasButton = new JButton("Refresh");
        refreshSchemasButton.addActionListener(e -> refreshSchemaList());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        buttonRow.add(forkButton);
        buttonRow.add(newSchemaButton);
        buttonRow.add(refreshSchemasButton);

        schemaUnsupportedLabel = new JBLabel("Schema management requires OpenSpec CLI v1.2.0+");
        schemaUnsupportedLabel.setForeground(new JBColor(new Color(180, 80, 0), new Color(230, 160, 80)));

        schemasContentPanel = new JPanel();
        schemasContentPanel.setLayout(new BoxLayout(schemasContentPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(schemaList);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemasContentPanel.add(scrollPane);

        JPanel defaultRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        defaultRow.add(new JBLabel("Default schema:"));
        defaultRow.add(defaultSchemaCombo);
        defaultRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemasContentPanel.add(defaultRow);

        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemasContentPanel.add(buttonRow);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        schemaUnsupportedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        schemasContentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(schemaUnsupportedLabel);
        panel.add(schemasContentPanel);
        panel.setBorder(IdeBorderFactory.createTitledBorder("Schemas"));

        // Initial state — will be updated after CLI detection
        updateSchemaSectionVisibility();

        return panel;
    }

    private void updateSchemaSectionVisibility() {
        SchemaService schemaService = project.getService(SchemaService.class);
        boolean supported = schemaService != null && schemaService.isSchemaSupported();
        schemaUnsupportedLabel.setVisible(!supported);
        schemasContentPanel.setVisible(supported);
        forkButton.setEnabled(supported);
        newSchemaButton.setEnabled(supported);
        refreshSchemasButton.setEnabled(supported);
        if (supported) {
            loadSchemaList();
        }
    }

    private void loadSchemaList() {
        SchemaService schemaService = project.getService(SchemaService.class);
        if (schemaService == null) return;

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<SchemaInfo> schemas = schemaService.listSchemas();
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                schemaListModel.clear();
                defaultSchemaCombo.removeAllItems();
                for (SchemaInfo info : schemas) {
                    schemaListModel.addElement(info);
                    defaultSchemaCombo.addItem(info.name());
                }
                // Restore default selection
                OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
                String defaultSchema = settings.getDefaultSchema();
                if (defaultSchema != null && !defaultSchema.isEmpty()) {
                    defaultSchemaCombo.setSelectedItem(defaultSchema);
                }
            });
        });
    }

    private void forkSelectedSchema() {
        SchemaInfo selected = schemaList.getSelectedValue();
        if (selected == null) return;

        String forkName = JOptionPane.showInputDialog(mainPanel, "Fork name:", "Fork Schema",
                JOptionPane.PLAIN_MESSAGE);
        if (forkName == null || forkName.isBlank()) return;

        SchemaService schemaService = project.getService(SchemaService.class);
        if (schemaService == null) return;

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String path = schemaService.forkSchema(selected.name(), forkName.trim());
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                if (path != null) {
                    loadSchemaList();
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
                    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
                    if (file != null) {
                        FileEditorManager.getInstance(project).openFile(file, true);
                    }
                }
            });
        });
    }

    private void createNewSchema() {
        NewSchemaDialog dialog = new NewSchemaDialog(project);
        if (!dialog.showAndGet()) return;

        String name = dialog.getSchemaName();
        SchemaService schemaService = project.getService(SchemaService.class);
        if (schemaService == null) return;

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String path = schemaService.initSchema(name);
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                if (path != null) {
                    loadSchemaList();
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
                    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
                    if (file != null) {
                        FileEditorManager.getInstance(project).openFile(file, true);
                    }
                }
            });
        });
    }

    private void refreshSchemaList() {
        SchemaService schemaService = project.getService(SchemaService.class);
        if (schemaService != null) {
            schemaService.clearCache();
        }
        loadSchemaList();
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
        boolean available = detection != null && detection.isAvailable();
        if (available) {
            String version = detection.getDetectedVersion();
            cliStatusLabel.setText("OpenSpec CLI " + (version != null ? "v" + version : "available"));
            cliStatusLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(100, 210, 100)));
        } else {
            cliStatusLabel.setText("OpenSpec CLI not found");
            cliStatusLabel.setForeground(new JBColor(new Color(180, 80, 0), new Color(230, 160, 80)));
        }
        // Workflow profile combo follows CLI availability.
        profileCombo.setEnabled(available);
        profileCliUnavailableLabel.setVisible(!available);
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

        AiProvider provider = getSelectedProvider();
        String key = getApiKey();
        String model = getAiModel();

        // Store the key so the API call can use it
        if (key != null && !key.isBlank() && !key.equals(API_KEY_MASK)) {
            AiCredentialStore.storeApiKey(provider, key);
        } else if (key != null && key.equals(API_KEY_MASK)) {
            // Masked key means use the already-stored one
            key = AiCredentialStore.getApiKey(provider);
        }

        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService == null) {
            aiTestResultLabel.setText("Service not available");
            aiTestResultLabel.setForeground(JBColor.RED);
            return;
        }

        // Pass current UI values directly — don't rely on persisted settings
        final String apiKey = key;
        final String apiModel = model;
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return apiService.testConnection(provider, apiKey, apiModel);
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

    /**
     * Sets the workflow profile. If {@code profile} is not one of the known presets
     * ({@code "", "core", "custom"}), it is added to the combo as an orphan entry
     * (rendered with a "(not found in CLI)" suffix in red) so the user can see it
     * and explicitly revert.
     */
    public void setProfile(String profile) {
        String value = profile != null ? profile : "";
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) profileCombo.getModel();

        // Remove any pre-existing orphan entry from a previous setProfile() call.
        for (int i = model.getSize() - 1; i >= 0; i--) {
            String item = model.getElementAt(i);
            if (!WORKFLOW_PROFILE_PRESETS.contains(item)) {
                model.removeElementAt(i);
            }
        }

        if (!WORKFLOW_PROFILE_PRESETS.contains(value)) {
            model.insertElementAt(value, 0);
        }
        profileCombo.setSelectedItem(value);
        updateOrphanHelpVisibility();
    }

    /**
     * @return {@code true} when the combo's currently selected value is an orphan —
     * a non-empty preset that is not in {@link #WORKFLOW_PROFILE_PRESETS}. Used by
     * {@link OpenSpecConfigurable} to gate Apply (D6 recovery UX), and internally to
     * toggle the orphan recovery help label.
     */
    public boolean isWorkflowProfileOrphanSelected() {
        return isOrphanValue(profileCombo.getSelectedItem());
    }

    /**
     * Pure orphan classifier — testable without instantiating the panel. {@code null}
     * and the empty string are NOT orphan (the empty string is the "use CLI's active
     * profile" sentinel). Any other value not in {@link #WORKFLOW_PROFILE_PRESETS}
     * is orphan.
     */
    static boolean isOrphanValue(Object selected) {
        if (selected == null) return false;
        String value = selected.toString();
        if (value.isEmpty()) return false;
        return !WORKFLOW_PROFILE_PRESETS.contains(value);
    }

    private void updateOrphanHelpVisibility() {
        if (profileOrphanHelpLabel == null) return;
        profileOrphanHelpLabel.setVisible(isWorkflowProfileOrphanSelected());
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

    public String getDefaultSchema() {
        Object selected = defaultSchemaCombo.getSelectedItem();
        return selected != null ? selected.toString() : "";
    }

    public void setDefaultSchema(String schema) {
        defaultSchemaCombo.setSelectedItem(schema != null ? schema : "");
    }

    /**
     * Builds the display text for a workflow profile combo entry. Pure function —
     * testable without instantiating the panel.
     */
    static String renderWorkflowProfileItem(String preset, boolean isOrphan) {
        if (isOrphan) {
            return preset + " (not found in CLI)";
        }
        if (preset == null || preset.isEmpty()) {
            return "(default — uses CLI's active profile)";
        }
        return switch (preset) {
            case "core" -> "core — essentials only";
            default -> preset;
        };
    }

    /**
     * Indirection over the static {@link OpenSpecTerminalLauncher#launchCommand} so tests
     * can stub the terminal launch outcome without standing up the Terminal tool window.
     */
    @FunctionalInterface
    interface TerminalLauncher {
        boolean launch(Project project, String command, String tabName);
    }

    /** Test-only injection point. */
    void setTerminalLauncher(TerminalLauncher launcher) {
        this.terminalLauncher = launcher;
    }

    /** Visible for tests. */
    boolean isCustomizeBannerVisible() {
        return customizeBanner != null && customizeBanner.isVisible();
    }

    /** Visible for tests. */
    void clickCustomizeWorkflowsForTest() {
        onCustomizeWorkflowsClicked();
    }

    /** Visible for tests. */
    void clickImDoneForTest() {
        onImDoneClicked();
    }

    private JPanel buildCustomizeBanner() {
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        JBLabel msg = new JBLabel("Waiting for the workflow picker — click \"I'm done\" when finished.");
        msg.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        customizeDoneButton = new JButton("I'm done");
        customizeDoneButton.addActionListener(e -> onImDoneClicked());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> resetCustomizeBanner());
        banner.add(msg);
        banner.add(customizeDoneButton);
        banner.add(cancelButton);
        banner.setVisible(false);
        return banner;
    }

    private void onCustomizeWorkflowsClicked() {
        String command = "openspec config profile";
        boolean launched = terminalLauncher.launch(project, command, "OpenSpec");
        if (launched) {
            resetCustomizeBanner();
            customizeBanner.setVisible(true);
        } else {
            java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(command);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM,
                    "Customize workflows",
                    OpenSpecTerminalLauncher.fallbackMessage(command),
                    NotificationType.WARNING,
                    aboutProfilesAction());
        }
    }

    /**
     * Handles the "I'm done" click: refreshes {@link WorkflowProfileService} on a pooled
     * thread (so the EDT isn't blocked on the CLI call), then on EDT updates the Config
     * Profile section, hides the banner, surfaces a confirmation toast, and triggers the
     * existing two-step {@code openspec update} prompt — but only when the workflow set
     * actually changed (per {@link WorkflowProfileService#hasChangedSinceLastRefresh}).
     */
    private void onImDoneClicked() {
        if (customizeDoneButton != null) {
            customizeDoneButton.setEnabled(false);
            customizeDoneButton.setText("Refreshing…");
        }
        WorkflowProfileService service = project.getService(WorkflowProfileService.class);
        if (service == null) {
            resetCustomizeBanner();
            customizeBanner.setVisible(false);
            return;
        }
        // Prime the cache so hasChangedSinceLastRefresh() is meaningful on the next call —
        // first refresh() with no prior state always returns false.
        service.getActiveWorkflows();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                service.refresh();
            } catch (Throwable t) {
                LOG.warn("WorkflowProfileService.refresh() failed", t);
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                String newProfile = service.getActiveProfileName();
                java.util.Set<String> newWorkflows = service.getActiveWorkflows();
                boolean changed = service.hasChangedSinceLastRefresh();
                resetCustomizeBanner();
                customizeBanner.setVisible(false);
                refreshConfigProfileSection();
                if (changed) {
                    String label = newProfile == null || newProfile.isEmpty() ? "(default)" : newProfile;
                    OpenSpecNotifier.info(project, "Profile",
                            "Now on " + label + " · " + newWorkflows.size() + " workflows");
                    WorkflowProfileSwitchService switchService = project.getService(WorkflowProfileSwitchService.class);
                    if (switchService != null && newProfile != null) {
                        switchService.promptAndRunUpdateIfConfirmed(newProfile);
                    }
                }
            });
        });
    }

    private void resetCustomizeBanner() {
        if (customizeDoneButton != null) {
            customizeDoneButton.setEnabled(true);
            customizeDoneButton.setText("I'm done");
        }
    }

    private NotificationAction aboutProfilesAction() {
        return NotificationAction.createSimpleExpiring("About profiles…",
                () -> com.intellij.ide.BrowserUtil.browse(
                        com.johnnyblabs.openspec.statusbar.OpenSpecProfileStatusBarWidget.DOCS_URL));
    }

    /** Renderer for {@link #profileCombo}. Highlights orphan entries in red. */
    private static final class WorkflowProfileRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String raw = value == null ? "" : value.toString();
            boolean isOrphan = !WORKFLOW_PROFILE_PRESETS.contains(raw);
            setText(renderWorkflowProfileItem(raw, isOrphan));
            if (isOrphan && !isSelected) {
                setForeground(JBColor.RED);
            }
            return this;
        }
    }
}
