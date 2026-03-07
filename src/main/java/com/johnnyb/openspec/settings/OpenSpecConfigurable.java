package com.johnnyb.openspec.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.ai.AiCredentialStore;
import com.johnnyb.openspec.ai.AiProvider;
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
        return panel.getPanel();
    }

    @Override
    public boolean isModified() {
        if (panel == null) return false;
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        return !panel.getVersionOverride().equals(settings.getVersionOverride() != null ? settings.getVersionOverride() : "")
                || !panel.getCliPath().equals(settings.getCliPath() != null ? settings.getCliPath() : "")
                || !panel.getProfile().equals(settings.getProfile() != null ? settings.getProfile() : "")
                || panel.isAutoRefresh() != settings.isAutoRefresh()
                || panel.isStrictValidation() != settings.isStrictValidation()
                || !panel.getAiProvider().equals(settings.getAiProvider() != null ? settings.getAiProvider() : "NONE")
                || !panel.getAiModel().equals(settings.getAiModel() != null ? settings.getAiModel() : "")
                || isApiKeyModified();
    }

    private boolean isApiKeyModified() {
        if (panel == null) return false;
        String key = panel.getApiKey();
        // "••••••••" means the key hasn't been touched
        return key != null && !key.isBlank() && !key.equals("••••••••");
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
        settings.setAiProvider(panel.getAiProvider());
        settings.setAiModel(panel.getAiModel());

        // Store API key securely via PasswordSafe
        String apiKey = panel.getApiKey();
        AiProvider provider = AiProvider.fromString(panel.getAiProvider());
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("••••••••")) {
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
        panel.setAiProvider(settings.getAiProvider());
        panel.setAiModel(settings.getAiModel());
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}
