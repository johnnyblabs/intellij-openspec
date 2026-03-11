package com.johnnyb.openspec.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.ai.AiCredentialStore;
import com.johnnyb.openspec.ai.AiProvider;
import com.johnnyb.openspec.tracking.TrackerCredentialStore;
import com.johnnyb.openspec.tracking.TrackerType;
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
                || !panel.getAiProvider().equals(safe(settings.getAiProvider(), "NONE"))
                || !panel.getAiModel().equals(safe(settings.getAiModel()))
                || isApiKeyModified()
                || panel.isForgejoEnabled() != settings.isForgejoEnabled()
                || !panel.getForgejoUrl().equals(safe(settings.getForgejoUrl()))
                || !panel.getForgejoOwner().equals(safe(settings.getForgejoOwner()))
                || !panel.getForgejoRepo().equals(safe(settings.getForgejoRepo()))
                || isTrackerTokenModified(panel.getForgejoToken())
                || panel.isPlaneEnabled() != settings.isPlaneEnabled()
                || !panel.getPlaneUrl().equals(safe(settings.getPlaneUrl()))
                || !panel.getPlaneWorkspace().equals(safe(settings.getPlaneWorkspace()))
                || !panel.getPlaneProjectId().equals(safe(settings.getPlaneProject()))
                || isTrackerTokenModified(panel.getPlaneApiKey());
    }

    private boolean isApiKeyModified() {
        if (panel == null) return false;
        String key = panel.getApiKey();
        return key != null && !key.isBlank() && !key.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
    }

    private boolean isTrackerTokenModified(String token) {
        return token != null && !token.isBlank() && !token.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
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
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")) {
            AiCredentialStore.storeApiKey(provider, apiKey);
        }

        // Issue tracking settings
        settings.setForgejoEnabled(panel.isForgejoEnabled());
        settings.setForgejoUrl(panel.getForgejoUrl());
        settings.setForgejoOwner(panel.getForgejoOwner());
        settings.setForgejoRepo(panel.getForgejoRepo());
        String forgejoToken = panel.getForgejoToken();
        if (forgejoToken != null && !forgejoToken.isBlank() && !forgejoToken.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")) {
            TrackerCredentialStore.storeToken(TrackerType.FORGEJO, forgejoToken);
        } else if (forgejoToken != null && forgejoToken.isBlank()) {
            TrackerCredentialStore.removeToken(TrackerType.FORGEJO);
        }

        settings.setPlaneEnabled(panel.isPlaneEnabled());
        settings.setPlaneUrl(panel.getPlaneUrl());
        settings.setPlaneWorkspace(panel.getPlaneWorkspace());
        settings.setPlaneProject(panel.getPlaneProjectId());
        String planeKey = panel.getPlaneApiKey();
        if (planeKey != null && !planeKey.isBlank() && !planeKey.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")) {
            TrackerCredentialStore.storeToken(TrackerType.PLANE, planeKey);
        } else if (planeKey != null && planeKey.isBlank()) {
            TrackerCredentialStore.removeToken(TrackerType.PLANE);
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

        // Issue tracking
        panel.setForgejoEnabled(settings.isForgejoEnabled());
        panel.setForgejoUrl(settings.getForgejoUrl());
        panel.setForgejoOwner(settings.getForgejoOwner());
        panel.setForgejoRepo(settings.getForgejoRepo());
        if (TrackerCredentialStore.hasToken(TrackerType.FORGEJO)) {
            panel.setForgejoToken("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
        }

        panel.setPlaneEnabled(settings.isPlaneEnabled());
        panel.setPlaneUrl(settings.getPlaneUrl());
        panel.setPlaneWorkspace(settings.getPlaneWorkspace());
        panel.setPlaneProjectId(settings.getPlaneProject());
        if (TrackerCredentialStore.hasToken(TrackerType.PLANE)) {
            panel.setPlaneApiKey("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
        }
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
