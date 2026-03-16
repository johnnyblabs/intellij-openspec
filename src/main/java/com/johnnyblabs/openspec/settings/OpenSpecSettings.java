package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.ConfigService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(name = "OpenSpecSettings", storages = @Storage("openspec.xml"))
public final class OpenSpecSettings implements PersistentStateComponent<OpenSpecSettings.State> {

    private State state = new State();

    public static OpenSpecSettings getInstance(@NotNull Project project) {
        return project.getService(OpenSpecSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getVersionOverride() {
        return state.versionOverride;
    }

    public void setVersionOverride(String version) {
        state.versionOverride = version;
    }

    public String getCliPath() {
        return state.cliPath;
    }

    public void setCliPath(String path) {
        state.cliPath = path;
    }

    public String getProfile() {
        return state.profile;
    }

    public void setProfile(String profile) {
        state.profile = profile;
    }

    public boolean isAutoRefresh() {
        return state.autoRefresh;
    }

    public void setAutoRefresh(boolean autoRefresh) {
        state.autoRefresh = autoRefresh;
    }

    public boolean isStrictValidation() {
        return state.strictValidation;
    }

    public void setStrictValidation(boolean strict) {
        state.strictValidation = strict;
    }

    public String getAiProvider() {
        return state.aiProvider;
    }

    public void setAiProvider(String provider) {
        state.aiProvider = provider;
    }

    public String getAiModel() {
        return state.aiModel;
    }

    public void setAiModel(String model) {
        state.aiModel = model;
    }

    public String getPreferredDeliveryMethod() {
        return state.preferredDeliveryMethod;
    }

    public void setPreferredDeliveryMethod(String method) {
        state.preferredDeliveryMethod = method;
    }

    public String getPreferredTool() {
        return state.preferredTool;
    }

    public void setPreferredTool(String tool) {
        state.preferredTool = tool;
    }

    public boolean isSetupCompleted() {
        return state.setupCompleted;
    }

    public void setSetupCompleted(boolean completed) {
        state.setupCompleted = completed;
    }

    public boolean isFirstProposalCompleted() {
        return state.firstProposalCompleted;
    }

    public void setFirstProposalCompleted(boolean completed) {
        state.firstProposalCompleted = completed;
    }

    /**
     * Returns the effective version: settings override if set, else config.yaml version.
     */
    public String getEffectiveVersion(@NotNull Project project) {
        if (state.versionOverride != null && !state.versionOverride.isEmpty()) {
            return state.versionOverride;
        }
        ConfigService configService = project.getService(ConfigService.class);
        if (configService != null && configService.getConfig() != null) {
            return configService.getConfig().getVersion();
        }
        return null;
    }

    public int getCliTimeoutSeconds() {
        return state.cliTimeoutSeconds;
    }

    public void setCliTimeoutSeconds(int timeout) {
        state.cliTimeoutSeconds = timeout;
    }

    public String getDefaultSchema() {
        return state.defaultSchema;
    }

    public void setDefaultSchema(String schema) {
        state.defaultSchema = schema;
    }

    public static class State {
        public String versionOverride = "";
        public String cliPath = "";
        public String profile = "";
        public boolean autoRefresh = true;
        public boolean strictValidation = false;
        public String aiProvider = "NONE";
        public String aiModel = "";
        public String preferredDeliveryMethod = "";
        public String preferredTool = "";
        public boolean setupCompleted = false;
        public boolean firstProposalCompleted = false;
        public int cliTimeoutSeconds = 30;
        public String defaultSchema = "";
    }
}
