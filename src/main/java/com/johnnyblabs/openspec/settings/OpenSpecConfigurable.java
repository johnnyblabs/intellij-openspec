package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OpenSpecConfigurable implements Configurable {

    private static final Logger LOG = Logger.getInstance(OpenSpecConfigurable.class);

    private final Project project;
    private OpenSpecSettingsPanel panel;

    public OpenSpecConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "OpenSpec";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new OpenSpecSettingsPanel(project);
        reset();
        return panel.getPanel();
    }

    @Override
    public boolean isModified() {
        if (panel == null) return false;
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        return !panel.getVersionOverride().equals(safe(settings.getVersionOverride()))
                || !panel.getCliPath().equals(safe(settings.getCliPath()))
                || !panel.getProfile().equals(safe(settings.getProfile()))
                || panel.isAutoRefresh() != settings.isAutoRefresh()
                || panel.isStrictValidation() != settings.isStrictValidation()
                || panel.getCliTimeout() != settings.getCliTimeoutSeconds()
                || !panel.getAiProvider().equals(safe(settings.getAiProvider(), "NONE"))
                || !panel.getAiModel().equals(safe(settings.getAiModel()))
                || !panel.getDefaultSchema().equals(safe(settings.getDefaultSchema()))
                || isApiKeyModified();
    }

    private boolean isApiKeyModified() {
        if (panel == null) return false;
        String key = panel.getApiKey();
        return key != null && !key.isBlank() && !key.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
    }

    @Override
    public void apply() {
        if (panel == null) return;
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);

        // Handle profile change via CLI delegation
        String newProfile = panel.getProfile();
        String oldProfile = safe(settings.getProfile());
        if (!newProfile.equals(oldProfile)) {
            applyProfileChange(settings, newProfile, oldProfile);
        }

        settings.setVersionOverride(panel.getVersionOverride());
        settings.setCliPath(panel.getCliPath());
        settings.setAutoRefresh(panel.isAutoRefresh());
        settings.setStrictValidation(panel.isStrictValidation());
        settings.setCliTimeoutSeconds(panel.getCliTimeout());
        settings.setAiProvider(panel.getAiProvider());
        settings.setAiModel(panel.getAiModel());
        settings.setDefaultSchema(panel.getDefaultSchema());
        // Store API key securely via PasswordSafe
        String apiKey = panel.getApiKey();
        AiProvider provider = AiProvider.fromString(panel.getAiProvider());
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")) {
            AiCredentialStore.storeApiKey(provider, apiKey);
        }
    }

    /**
     * Delegates profile switch to the CLI when available. On CLI failure, reverts the
     * panel to the previous value. When CLI is unavailable, persists locally with a
     * notification that the change is local-only.
     */
    private void applyProfileChange(OpenSpecSettings settings, String newProfile, String oldProfile) {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null && detection.isAvailable()) {
            try {
                CliRunner.CliResult result = CliRunner.run(project,
                        "config", "profile", newProfile);
                if (result.isSuccess()) {
                    settings.setProfile(newProfile);
                    panel.refreshConfigProfileSection();
                } else {
                    // CLI failed — revert and warn
                    panel.setProfile(oldProfile);
                    String errorMsg = result.stderr().isEmpty()
                            ? "exit code " + result.exitCode()
                            : result.stderr().trim();
                    OpenSpecNotifier.warn(project, "Profile Switch",
                            "Failed to switch profile via CLI: " + errorMsg);
                }
            } catch (CliRunner.CliException e) {
                LOG.warn("CLI profile switch failed", e);
                panel.setProfile(oldProfile);
                OpenSpecNotifier.warn(project, "Profile Switch",
                        "Failed to switch profile: " + e.getMessage());
            }
        } else {
            // CLI unavailable — persist locally with informational notification
            settings.setProfile(newProfile);
            OpenSpecNotifier.info(project, "Profile",
                    "Profile set to '" + newProfile + "' locally. Install OpenSpec CLI to sync globally.");
        }
    }

    @Override
    public void reset() {
        if (panel == null) return;
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        panel.setVersionOverride(settings.getVersionOverride());
        panel.setCliPath(settings.getCliPath());
        panel.setProfile(settings.getProfile());
        panel.setAutoRefresh(settings.isAutoRefresh());
        panel.setStrictValidation(settings.isStrictValidation());
        panel.setCliTimeout(settings.getCliTimeoutSeconds());
        panel.setAiProvider(settings.getAiProvider());
        panel.setAiModel(settings.getAiModel());
        panel.setDefaultSchema(settings.getDefaultSchema());

    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }

    private static String safe(@Nullable String value) {
        return value != null ? value : "";
    }

    private static String safe(@Nullable String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
