package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.scaffolding.ScaffoldingService;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.intellij.openapi.util.IconLoader;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SetupWizardDialog extends DialogWrapper {

    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_HEIGHT = 400;
    private static final Icon BRAND_ICON = IconLoader.getIcon("/icons/openspec-brand.svg", SetupWizardDialog.class);

    private final Project project;
    private final SetupWizardModel model = new SetupWizardModel();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);

    private int currentStep = 0;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_CLI = 1;
    private static final int STEP_AI = 2;
    private static final int STEP_INIT = 3;
    private static final int STEP_DONE = 4;
    private static final int TOTAL_STEPS = 5;

    private static final String[] STEP_NAMES = {"welcome", "cli", "ai", "init", "done"};

    // CLI step components
    private JBLabel cliStatusLabel;
    private JBTextField cliPathField;

    // AI step components
    private ComboBox<String> toolCombo;
    private ComboBox<String> deliveryCombo;
    private ComboBox<AiProvider> providerCombo;
    private JBPasswordField apiKeyField;
    private ComboBox<String> modelCombo;
    private JBLabel apiTestLabel;
    private JPanel directApiPanel;

    // Init step components
    private JBLabel initStatusLabel;
    private JButton initButton;

    // Done step components
    private JBLabel summaryLabel;

    private boolean openProposeOnClose = false;

    public SetupWizardDialog(Project project) {
        super(project, false);
        this.project = project;
        setTitle("OpenSpec Setup");
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);

        buildSteps();
        init();
        updateButtons();
    }

    private void buildSteps() {
        cardPanel.add(createWelcomeStep(), STEP_NAMES[STEP_WELCOME]);
        cardPanel.add(createCliStep(), STEP_NAMES[STEP_CLI]);
        cardPanel.add(createAiStep(), STEP_NAMES[STEP_AI]);
        cardPanel.add(createInitStep(), STEP_NAMES[STEP_INIT]);
        cardPanel.add(createDoneStep(), STEP_NAMES[STEP_DONE]);
    }

    // --- Step panels ---

    private JPanel createWelcomeStep() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(8);

        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel(BRAND_ICON), gbc);

        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JBLabel title = new JBLabel("Welcome to OpenSpec Plugin");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(title, gbc);

        gbc.gridy = 2;
        JBLabel desc = new JBLabel("<html><body style='width:" + JBUI.scale(400) + "px'>" +
                "Spec-driven development for your project. Define requirements, " +
                "generate artifacts, and track changes with AI assistance.<br><br>" +
                "This wizard will help you set up:" +
                "<ul>" +
                "<li>OpenSpec CLI detection</li>" +
                "<li>AI tool and delivery method</li>" +
                "<li>Project initialization</li>" +
                "</ul></body></html>");
        panel.add(desc, gbc);

        gbc.gridy = 3;
        JBLabel attribution = new JBLabel("<html><body style='width:" + JBUI.scale(400) +
                "px;color:gray;font-size:small'>" +
                "Built to support the amazing work of " +
                "<b>Fission AI</b>, the creators of OpenSpec." +
                "</body></html>");
        panel.add(attribution, gbc);

        return panel;
    }

    private JPanel createCliStep() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insets(8);

        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JBLabel title = new JBLabel("CLI Detection");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, gbc);

        gbc.gridy = 1;
        cliStatusLabel = new JBLabel("Detecting...");
        panel.add(cliStatusLabel, gbc);

        gbc.gridy = 2;
        gbc.gridwidth = 1;
        panel.add(new JBLabel("Manual path:"), gbc);

        gbc.gridx = 1;
        cliPathField = new JBTextField();
        panel.add(cliPathField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JBLabel hint = new JBLabel("<html><body style='width:" + JBUI.scale(400) + "px;color:gray'>" +
                "The CLI is optional. Built-in features work without it. " +
                "Install with: <code>npm i -g @fission-ai/openspec</code></body></html>");
        panel.add(hint, gbc);

        return panel;
    }

    private JPanel createAiStep() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insets(6);
        gbc.gridwidth = 2;

        gbc.gridy = 0;
        JBLabel title = new JBLabel("AI Tool Configuration");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(new JBLabel("Preferred tool:"), gbc);
        gbc.gridx = 1;
        toolCombo = new ComboBox<>();
        panel.add(toolCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JBLabel("Delivery method:"), gbc);
        gbc.gridx = 1;
        deliveryCombo = new ComboBox<>(new String[]{"Clipboard", "Editor Tab", "Direct API"});
        deliveryCombo.addActionListener(e -> updateDirectApiVisibility());
        panel.add(deliveryCombo, gbc);

        // Direct API sub-panel
        directApiPanel = new JPanel(new GridBagLayout());
        directApiPanel.setBorder(BorderFactory.createTitledBorder("Direct API"));
        GridBagConstraints apiGbc = new GridBagConstraints();
        apiGbc.fill = GridBagConstraints.HORIZONTAL;
        apiGbc.weightx = 1.0;
        apiGbc.insets = JBUI.insets(4);

        apiGbc.gridx = 0; apiGbc.gridy = 0;
        directApiPanel.add(new JBLabel("Provider:"), apiGbc);
        apiGbc.gridx = 1;
        providerCombo = new ComboBox<>(new AiProvider[]{AiProvider.CLAUDE, AiProvider.OPENAI, AiProvider.GEMINI});
        providerCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof AiProvider p) setText(p.getDisplayName());
                return this;
            }
        });
        providerCombo.addActionListener(e -> updateModelCombo());
        directApiPanel.add(providerCombo, apiGbc);

        apiGbc.gridx = 0; apiGbc.gridy = 1;
        directApiPanel.add(new JBLabel("API Key:"), apiGbc);
        apiGbc.gridx = 1;
        apiKeyField = new JBPasswordField();
        directApiPanel.add(apiKeyField, apiGbc);

        apiGbc.gridx = 0; apiGbc.gridy = 2;
        directApiPanel.add(new JBLabel("Model:"), apiGbc);
        apiGbc.gridx = 1;
        modelCombo = new ComboBox<>();
        directApiPanel.add(modelCombo, apiGbc);

        apiGbc.gridx = 0; apiGbc.gridy = 3;
        apiGbc.gridwidth = 2;
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton testBtn = new JButton("Test Connection");
        testBtn.addActionListener(e -> testApiConnection());
        testRow.add(testBtn);
        apiTestLabel = new JBLabel("");
        testRow.add(Box.createHorizontalStrut(8));
        testRow.add(apiTestLabel);
        directApiPanel.add(testRow, apiGbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(directApiPanel, gbc);

        updateModelCombo();
        directApiPanel.setVisible(false);

        return panel;
    }

    private JPanel createInitStep() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(8);

        gbc.gridy = 0;
        JBLabel title = new JBLabel("Project Initialization");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, gbc);

        gbc.gridy = 1;
        initStatusLabel = new JBLabel("Checking...");
        panel.add(initStatusLabel, gbc);

        gbc.gridy = 2;
        initButton = new JButton("Initialize OpenSpec");
        initButton.addActionListener(e -> doInitialize());
        panel.add(initButton, gbc);

        gbc.gridy = 3;
        JBLabel hint = new JBLabel("<html><body style='width:" + JBUI.scale(400) + "px;color:gray'>" +
                "Creates the <code>openspec/</code> directory with config, specs, and changes folders." +
                "</body></html>");
        panel.add(hint, gbc);

        return panel;
    }

    private JPanel createDoneStep() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(8);

        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel(BRAND_ICON), gbc);

        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JBLabel title = new JBLabel("Setup Complete");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(title, gbc);

        gbc.gridy = 2;
        summaryLabel = new JBLabel("");
        panel.add(summaryLabel, gbc);

        gbc.gridy = 3;
        JButton proposeBtn = new JButton("Create Your First Change");
        proposeBtn.addActionListener(e -> {
            openProposeOnClose = true;
            doOKAction();
        });
        panel.add(proposeBtn, gbc);

        return panel;
    }

    // --- Step lifecycle ---

    private void onEnterStep(int step) {
        switch (step) {
            case STEP_CLI -> detectCli();
            case STEP_AI -> populateAiTools();
            case STEP_INIT -> checkInitialization();
            case STEP_DONE -> buildSummary();
        }
    }

    private void detectCli() {
        cliStatusLabel.setIcon(AllIcons.General.InlineRefreshHover);
        cliStatusLabel.setText("Detecting CLI...");
        com.intellij.openapi.application.ModalityState modality =
                com.intellij.openapi.application.ModalityState.stateForComponent(getWindow());
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CliDetectionService cliService = project.getService(CliDetectionService.class);
            cliService.detect();
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                if (cliService.isAvailable()) {
                    model.setCliFound(true);
                    model.setCliPath(cliService.getDetectedPath());
                    model.setCliVersion(cliService.getDetectedVersion());
                    cliStatusLabel.setIcon(AllIcons.General.InspectionsOK);
                    cliStatusLabel.setText("Found: " + cliService.getDetectedPath() + " (v" + cliService.getDetectedVersion() + ")");
                    cliPathField.setText(cliService.getDetectedPath());
                } else {
                    model.setCliFound(false);
                    cliStatusLabel.setIcon(AllIcons.General.Warning);
                    cliStatusLabel.setText("Not found — built-in features will be used");
                }
            }, modality);
        });
    }

    private void populateAiTools() {
        AiToolDetectionService toolService = project.getService(AiToolDetectionService.class);
        toolService.detect();
        List<String> detected = toolService.getDetectedTools();
        model.setDetectedTools(detected);

        toolCombo.removeAllItems();

        // Show detected tools first
        for (String tool : detected) {
            toolCombo.addItem(tool + " (detected)");
        }

        // Then all other known tools the user might want to configure
        java.util.Set<String> detectedSet = new java.util.HashSet<>(detected);
        for (String tool : AiToolDetectionService.getAllToolNames()) {
            if (!detectedSet.contains(tool)) {
                toolCombo.addItem(tool);
            }
        }

        toolCombo.setEnabled(true);
    }

    private void checkInitialization() {
        boolean initialized = OpenSpecFileUtil.isOpenSpecProject(project);
        model.setProjectInitialized(initialized);
        if (initialized) {
            initStatusLabel.setIcon(AllIcons.General.InspectionsOK);
            initStatusLabel.setText("Already initialized — openspec/ directory found");
            initButton.setVisible(false);
        } else {
            initStatusLabel.setIcon(AllIcons.General.Warning);
            initStatusLabel.setText("Project is not initialized");
            initButton.setVisible(true);
        }
    }

    private void doInitialize() {
        try {
            ScaffoldingService scaffolding = project.getService(ScaffoldingService.class);
            scaffolding.initOpenSpec();
            model.setProjectInitialized(true);
            initStatusLabel.setIcon(AllIcons.General.InspectionsOK);
            initStatusLabel.setText("Initialized successfully");
            initButton.setVisible(false);
        } catch (java.io.IOException ex) {
            initStatusLabel.setIcon(AllIcons.General.Error);
            initStatusLabel.setText("Failed to initialize: " + ex.getMessage());
        }
    }

    private void buildSummary() {
        // Collect model state before showing summary
        collectCurrentStepData();

        StringBuilder sb = new StringBuilder("<html><body style='width:" + JBUI.scale(400) + "px'><table>");
        sb.append("<tr><td><b>CLI:</b></td><td>").append(model.isCliFound()
                ? model.getCliPath() + " (v" + model.getCliVersion() + ")"
                : "Not configured").append("</td></tr>");
        sb.append("<tr><td><b>AI Tool:</b></td><td>").append(
                model.getSelectedTool().isBlank() ? "Not configured" : model.getSelectedTool()).append("</td></tr>");
        sb.append("<tr><td><b>Delivery:</b></td><td>").append(
                model.getDeliveryMethod().isBlank() ? "Not configured" : model.getDeliveryMethod()).append("</td></tr>");
        if (model.getAiProvider() != AiProvider.NONE) {
            sb.append("<tr><td><b>API Provider:</b></td><td>").append(model.getAiProvider().getDisplayName()).append("</td></tr>");
        }
        sb.append("<tr><td><b>Project:</b></td><td>").append(model.isProjectInitialized()
                ? "Initialized" : "Not initialized").append("</td></tr>");
        sb.append("</table></body></html>");
        summaryLabel.setText(sb.toString());
    }

    private void collectCurrentStepData() {
        // Collect data from whatever step the user is leaving
        if (!cliPathField.getText().isBlank()) {
            model.setCliPath(cliPathField.getText().trim());
        }
        if (toolCombo.isEnabled() && toolCombo.getSelectedItem() != null) {
            String toolSelection = toolCombo.getSelectedItem().toString()
                    .replace(" (detected)", "");
            model.setSelectedTool(toolSelection);
        }
        if (deliveryCombo.getSelectedItem() != null) {
            // Map display names to enum names for persistence
            String selected = deliveryCombo.getSelectedItem().toString();
            String enumName = switch (selected) {
                case "Clipboard" -> "CLIPBOARD";
                case "Editor Tab" -> "EDITOR_TAB";
                case "Direct API" -> "DIRECT_API";
                default -> selected;
            };
            model.setDeliveryMethod(enumName);
        }
        if ("Direct API".equals(deliveryCombo.getSelectedItem())) {
            AiProvider selected = (AiProvider) providerCombo.getSelectedItem();
            if (selected != null) {
                model.setAiProvider(selected);
            }
            String key = new String(apiKeyField.getPassword());
            if (!key.isBlank()) {
                model.setApiKey(key);
            }
            if (modelCombo.getSelectedItem() != null) {
                model.setAiModel(modelCombo.getSelectedItem().toString());
            }
        }
    }

    // --- AI step helpers ---

    private void updateDirectApiVisibility() {
        boolean show = "Direct API".equals(deliveryCombo.getSelectedItem());
        directApiPanel.setVisible(show);
    }

    private void updateModelCombo() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        modelCombo.removeAllItems();
        if (provider != null) {
            for (String m : provider.getModels()) {
                modelCombo.addItem(m);
            }
        }
    }

    private void testApiConnection() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        String key = new String(apiKeyField.getPassword());
        if (provider == null || key.isBlank()) {
            apiTestLabel.setText("Enter an API key first");
            apiTestLabel.setForeground(JBColor.RED);
            return;
        }

        apiTestLabel.setText("Testing...");
        apiTestLabel.setIcon(null);
        apiTestLabel.setForeground(JBColor.foreground());

        // Store key temporarily so DirectApiService can use it
        AiCredentialStore.storeApiKey(provider, key);
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String prevProvider = settings.getAiProvider();
        String prevModel = settings.getAiModel();
        settings.setAiProvider(provider.name());
        String selectedModel = modelCombo.getSelectedItem() != null ? modelCombo.getSelectedItem().toString() : provider.getDefaultModel();
        settings.setAiModel(selectedModel);

        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService == null) {
            apiTestLabel.setText("Service not available");
            apiTestLabel.setForeground(JBColor.RED);
            settings.setAiProvider(prevProvider);
            settings.setAiModel(prevModel);
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
                    apiTestLabel.setText(result);
                    apiTestLabel.setIcon(AllIcons.General.InspectionsOK);
                    apiTestLabel.setForeground(JBColor.foreground());
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    apiTestLabel.setText("Failed: " + msg);
                    apiTestLabel.setIcon(AllIcons.General.Error);
                    apiTestLabel.setForeground(JBColor.RED);
                    // Restore previous settings on failure
                    settings.setAiProvider(prevProvider);
                    settings.setAiModel(prevModel);
                }
            }
        }.execute();
    }

    // --- Navigation ---

    @Override
    protected JComponent createCenterPanel() {
        cardPanel.setPreferredSize(new Dimension(DIALOG_WIDTH, DIALOG_HEIGHT));
        return cardPanel;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getCancelAction(), getOKAction()};
    }

    @Override
    protected void doOKAction() {
        if (currentStep < TOTAL_STEPS - 1) {
            collectCurrentStepData();
            currentStep++;
            cardLayout.show(cardPanel, STEP_NAMES[currentStep]);
            onEnterStep(currentStep);
            updateButtons();
        } else {
            // Final step — persist and close
            model.persist(project);
            super.doOKAction();
            if (openProposeOnClose) {
                SwingUtilities.invokeLater(() -> {
                    com.intellij.openapi.actionSystem.AnAction proposeAction =
                            com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("OpenSpec.Propose");
                    if (proposeAction != null) {
                        com.intellij.openapi.actionSystem.DataContext context =
                                dataId -> com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.is(dataId) ? project : null;
                        com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(
                                proposeAction, context, "SetupWizard", null, null);
                    }
                });
            }
        }
    }

    public void doBackAction() {
        if (currentStep > 0) {
            currentStep--;
            cardLayout.show(cardPanel, STEP_NAMES[currentStep]);
            updateButtons();
        }
    }

    @Override
    public void doCancelAction() {
        // Skip setup — still mark completed so wizard doesn't re-show
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        settings.setSetupCompleted(true);
        super.doCancelAction();
    }

    private void updateButtons() {
        if (currentStep == STEP_WELCOME) {
            setOKButtonText("Let's get set up");
            setCancelButtonText("Skip Setup");
        } else if (currentStep == STEP_DONE) {
            setOKButtonText("Finish");
            setCancelButtonText("Close");
        } else {
            setOKButtonText("Next >");
            setCancelButtonText("Skip");
        }
    }

    public boolean shouldOpenPropose() {
        return openProposeOnClose;
    }
}
