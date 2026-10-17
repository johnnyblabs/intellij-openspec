package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiConfigListener;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.util.ArrayList;
import java.util.List;

public final class SetupWizardModel {

    private String cliPath = "";
    private boolean cliFound = false;
    private String cliVersion = "";
    private List<String> detectedTools = new ArrayList<>();
    private String selectedTool = "";
    private String deliveryMethod = "";
    private AiProvider aiProvider = AiProvider.NONE;
    private String aiModel = "";
    private String apiKey = "";
    private boolean projectInitialized = false;

    public String getCliPath() { return cliPath; }
    public void setCliPath(String cliPath) { this.cliPath = cliPath; }

    public boolean isCliFound() { return cliFound; }
    public void setCliFound(boolean cliFound) { this.cliFound = cliFound; }

    public String getCliVersion() { return cliVersion; }
    public void setCliVersion(String cliVersion) { this.cliVersion = cliVersion; }

    public List<String> getDetectedTools() { return detectedTools; }
    public void setDetectedTools(List<String> detectedTools) { this.detectedTools = detectedTools; }

    public String getSelectedTool() { return selectedTool; }
    public void setSelectedTool(String selectedTool) { this.selectedTool = selectedTool; }

    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public AiProvider getAiProvider() { return aiProvider; }
    public void setAiProvider(AiProvider aiProvider) { this.aiProvider = aiProvider; }

    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public boolean isProjectInitialized() { return projectInitialized; }
    public void setProjectInitialized(boolean projectInitialized) { this.projectInitialized = projectInitialized; }

    /**
     * Persists all wizard state to OpenSpecSettings and AiCredentialStore.
     */
    public void persist(Project project) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);

        if (!cliPath.isBlank()) {
            settings.setCliPath(cliPath);
        }

        if (!selectedTool.isBlank()) {
            settings.setPreferredTool(selectedTool);
        }

        if (!deliveryMethod.isBlank()) {
            settings.setPreferredDeliveryMethod(deliveryMethod);
        }

        if (aiProvider != AiProvider.NONE) {
            settings.setAiProvider(aiProvider.name());
            settings.setAiModel(aiModel);
            if (!apiKey.isBlank()) {
                // persist() runs on the EDT (wizard OK). PasswordSafe.set is blocking
                // (@RequiresBackgroundThread), so persist the key off the EDT. Mark the has-key cache
                // synchronously first (EDT-safe, in-memory) so the tool-window rebuild that fires right
                // after the wizard closes sees Direct API as configured — the async keystore write and
                // the read path (getApiKey) are both off-EDT.
                final AiProvider provider = aiProvider;
                final String key = apiKey;
                AiCredentialStore.markHasApiKey(provider, true);
                ApplicationManager.getApplication().executeOnPooledThread(
                        () -> AiCredentialStore.storeApiKey(provider, key));
            }
        }

        settings.setSetupCompleted(true);

        // Tell open tool-window surfaces the AI config changed so the Direct API delivery option (and
        // its model selector) surfaces immediately when the wizard closes, rather than only on the
        // next unrelated refresh. markHasApiKey above is synchronous, so isConfigured() is current.
        project.getMessageBus().syncPublisher(AiConfigListener.TOPIC).aiConfigChanged();
    }
}
