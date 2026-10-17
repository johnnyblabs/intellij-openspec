package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiConfigListener;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.services.WorkflowProfileSwitchService;
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
        // D6 Apply gate: while an orphan preset is selected, Apply stays disabled
        // regardless of other field changes. The user is forced to pick a non-orphan
        // value first — eliminates the silent no-op trap where clicking Apply with
        // orphan selected used to do nothing visible.
        if (panel.isWorkflowProfileOrphanSelected()) return false;
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        return !panel.getVersionOverride().equals(safe(settings.getVersionOverride()))
                || !panel.getCliPath().equals(safe(settings.getCliPath()))
                || !panel.getProfile().equals(safe(settings.getProfile()))
                || panel.isAutoRefresh() != settings.isAutoRefresh()
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
        settings.setCliTimeoutSeconds(panel.getCliTimeout());
        settings.setAiProvider(panel.getAiProvider());
        settings.setAiModel(panel.getAiModel());
        settings.setDefaultSchema(panel.getDefaultSchema());
        // Store API key securely via PasswordSafe. set() is blocking (@RequiresBackgroundThread) and
        // apply() runs on the EDT, so persist off the EDT; the has-key cache is updated inside storeApiKey.
        String apiKey = panel.getApiKey();
        AiProvider provider = AiProvider.fromString(panel.getAiProvider());
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")) {
            AiCredentialStore.markHasApiKey(provider, true);
            ApplicationManager.getApplication().executeOnPooledThread(
                    () -> AiCredentialStore.storeApiKey(provider, apiKey));
        }

        // The AI config just changed: tell open tool-window surfaces so the Direct API delivery
        // option (and its model selector) appears/withdraws immediately, not only on the next
        // unrelated refresh. markHasApiKey above is synchronous, so isConfigured() is already current.
        project.getMessageBus().syncPublisher(AiConfigListener.TOPIC).aiConfigChanged();

        // D3 fallback refresh: a Settings apply is a likely moment for the user's CLI
        // state to have drifted from cached state (manual CLI switch in another terminal,
        // or a customize handshake the user closed before clicking "I'm done").
        scheduleProfileRefresh();
    }

    /**
     * Delegates profile switch to {@link WorkflowProfileSwitchService}. On CLI failure
     * reverts the panel to the previous value; on success refreshes the Config Profile
     * section and prompts the user to run {@code openspec update} (the OpenSpec
     * two-step profile change process).
     */
    private void applyProfileChange(OpenSpecSettings settings, String newProfile, String oldProfile) {
        WorkflowProfileSwitchService switchService = project.getService(WorkflowProfileSwitchService.class);
        if (switchService == null) {
            LOG.warn("WorkflowProfileSwitchService unavailable; falling back to local persist");
            settings.setProfile(newProfile);
            return;
        }
        WorkflowProfileSwitchService.SwitchResult result = switchService.switchProfile(newProfile);
        switch (result.outcome()) {
            case SWITCHED -> {
                panel.refreshConfigProfileSection();
                switchService.promptAndRunUpdateIfConfirmed(newProfile);
            }
            case CLI_UNAVAILABLE -> panel.refreshConfigProfileSection();
            case CLI_FAILURE -> {
                panel.setProfile(oldProfile);
                if (result.error() != null) {
                    com.johnnyblabs.openspec.util.OpenSpecNotifier.warn(project, "Profile Switch",
                            "Failed to apply profile: " + result.error());
                }
            }
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
        panel.setCliTimeout(settings.getCliTimeoutSeconds());
        panel.setAiProvider(settings.getAiProvider());
        panel.setAiModel(settings.getAiModel());
        panel.setDefaultSchema(settings.getDefaultSchema());

        // D3 fallback refresh: catches the case where the user customized via the CLI
        // (or terminal handshake), closed Settings without confirming, and reopens.
        scheduleProfileRefresh();
    }

    /**
     * D3 fallback refresh trigger: re-renders the Config Profile section from the CLI — catching the
     * case where the user customized via the CLI/terminal, closed Settings without confirming, and
     * reopens. Delegates to {@link OpenSpecSettingsPanel#refreshConfigProfileSection()}, which does the
     * CLI read (`config list --json`) on a pooled thread via {@code WorkflowProfileService} and
     * re-renders on the EDT. This method must NOT refresh the service itself: the panel already does,
     * and doing both ran the CLI command twice per Settings Apply/Reset.
     */
    private void scheduleProfileRefresh() {
        if (panel != null) {
            panel.refreshConfigProfileSection();
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
