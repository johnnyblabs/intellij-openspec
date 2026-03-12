package com.johnnyb.openspec.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.services.ConfigService;
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

    // --- Forgejo tracker settings ---

    public boolean isForgejoEnabled() { return state.forgejoEnabled; }
    public void setForgejoEnabled(boolean enabled) { state.forgejoEnabled = enabled; }

    public String getForgejoUrl() { return state.forgejoUrl; }
    public void setForgejoUrl(String url) { state.forgejoUrl = url; }

    public String getForgejoOwner() { return state.forgejoOwner; }
    public void setForgejoOwner(String owner) { state.forgejoOwner = owner; }

    public String getForgejoRepo() { return state.forgejoRepo; }
    public void setForgejoRepo(String repo) { state.forgejoRepo = repo; }

    // --- Plane tracker settings ---

    public boolean isPlaneEnabled() { return state.planeEnabled; }
    public void setPlaneEnabled(boolean enabled) { state.planeEnabled = enabled; }

    public String getPlaneUrl() { return state.planeUrl; }
    public void setPlaneUrl(String url) { state.planeUrl = url; }

    public String getPlaneWorkspace() { return state.planeWorkspace; }
    public void setPlaneWorkspace(String workspace) { state.planeWorkspace = workspace; }

    public String getPlaneProject() { return state.planeProject; }
    public void setPlaneProject(String projectId) { state.planeProject = projectId; }

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

        // Issue tracking
        public boolean forgejoEnabled = false;
        public String forgejoUrl = "";
        public String forgejoOwner = "";
        public String forgejoRepo = "";
        public boolean planeEnabled = false;
        public String planeUrl = "";
        public String planeWorkspace = "";
        public String planeProject = "";
    }
}
