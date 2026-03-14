package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OpenSpecConfigurable implements Configurable {

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
        settings.setVersionOverride(panel.getVersionOverride());
        settings.setCliPath(panel.getCliPath());
        settings.setProfile(panel.getProfile());
        settings.setAutoRefresh(panel.isAutoRefresh());
        settings.setStrictValidation(panel.isStrictValidation());
        settings.setCliTimeoutSeconds(panel.getCliTimeout());
        settings.setAiProvider(panel.getAiProvider());
        settings.setAiModel(panel.getAiModel());
        // Store API key securely via PasswordSafe
        String apiKey = panel.getApiKey();
        AiProvider provider = AiProvider.fromString(panel.getAiProvider());
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")) {
            AiCredentialStore.storeApiKey(provider, apiKey);
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
