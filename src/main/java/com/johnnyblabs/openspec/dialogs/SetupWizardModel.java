package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
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
                AiCredentialStore.storeApiKey(aiProvider, apiKey);
            }
        }

        settings.setSetupCompleted(true);
    }
}
