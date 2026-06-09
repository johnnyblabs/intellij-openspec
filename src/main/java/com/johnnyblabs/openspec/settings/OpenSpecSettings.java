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

    /**
     * Returns the active <b>workflow profile</b> (per-user; switched via
     * {@code openspec config profile <preset>}). One of {@code "core"}, {@code "custom"},
     * or empty string ({@code ""}) for "use the CLI's active profile."
     *
     * <p>Distinct from:
     * <ul>
     *   <li>{@link #getDefaultSchema()} — the OpenSpec schema concept (e.g. {@code spec-driven});
     *       per-project, used by {@code WorkflowActionPanel} and {@code ProposeChangeDialog}.</li>
     *   <li>{@code OpenSpecConfig.profile} (a {@link java.util.Map}) — the per-project metadata
     *       block from {@code openspec/config.yaml}'s {@code profile:} section
     *       (name, description, language, framework); displayed in the spec tree.</li>
     * </ul>
     *
     * <p>The field name {@code profile} predates the OpenSpec 1.2.0+ "workflow profile" /
     * "schema" / "project profile" three-way split. The semantic intent of this field has
     * always been the workflow profile.
     */
    public String getProfile() {
        return state.profile;
    }

    /** @see #getProfile() */
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

    /**
     * Returns the raw Default schema setting — may be empty (user hasn't chosen one).
     *
     * <p><b>When to use this vs {@link #getEffectiveSchema(Project)}:</b>
     * <ul>
     *   <li><b>Combo / dropdown population</b> (e.g., {@code ProposeChangeDialog},
     *       {@code WorkflowActionPanel}): call this. An empty result lets the combo
     *       keep its first option highlighted; forcing the {@code "spec-driven"} fallback
     *       would silently overwrite the user's "no preference yet" state.</li>
     *   <li><b>Write paths</b> (e.g., {@code ScaffoldingService.initBuiltIn} writing
     *       {@code openspec/config.yaml}): call {@code getEffectiveSchema(project)}. Init
     *       MUST write a schema string — there is no blank option — so the fallback is
     *       required.</li>
     * </ul>
     *
     * @return the raw setting value (possibly empty string, never null in practice
     *         since {@link State#defaultSchema} initializes to empty)
     */
    public String getDefaultSchema() {
        return state.defaultSchema;
    }

    public void setDefaultSchema(String schema) {
        state.defaultSchema = schema;
    }

    /**
     * Returns the effective default schema for write paths (e.g. built-in init).
     *
     * <p>When the user has chosen a Default schema in Settings → Tools → OpenSpec, returns
     * that value. Otherwise falls back to the literal {@code "spec-driven"}, which is the
     * historical default and matches the upstream CLI's {@code DEFAULT_SCHEMA} constant
     * ({@code @fission-ai/openspec/dist/core/init.js}). The fallback is intentionally a
     * literal — not computed from {@link com.johnnyblabs.openspec.version.VersionSupport}'s
     * valid-schemas set — so the default stays stable across upstream schema additions.
     *
     * <p>Sibling to {@link #getEffectiveVersion(Project)} — callers writing config.yaml
     * should resolve both fields through these helpers so the setting-vs-fallback contract
     * is encapsulated near the field, not duplicated at every call site.
     *
     * @param project the current project (currently unused; reserved for a future config.yaml
     *                fallback if the helper expands beyond init-time use, mirroring
     *                {@code getEffectiveVersion}'s reach into {@code ConfigService}).
     *                TODO(post-v0.4.0): if no caller reaches into ConfigService through this
     *                helper by then, drop the parameter and the @NotNull contract.
     * @return non-null, non-empty schema name
     */
    public String getEffectiveSchema(@NotNull Project project) {
        if (state.defaultSchema != null && !state.defaultSchema.isEmpty()) {
            return state.defaultSchema;
        }
        return "spec-driven";
    }

    public static class State {
        public String versionOverride = "";
        public String cliPath = "";
        /** Workflow profile preset: {@code "core"}, {@code "custom"}, or {@code ""} (CLI default). See {@link OpenSpecSettings#getProfile()}. */
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
