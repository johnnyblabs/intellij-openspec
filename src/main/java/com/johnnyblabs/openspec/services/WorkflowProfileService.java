package com.johnnyblabs.openspec.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.util.CliRunner;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves and caches the active workflows from the global OpenSpec profile.
 * Used by {@link com.johnnyblabs.openspec.actions.OpenSpecBaseAction} to enable/disable
 * actions based on the user's configured profile.
 */
@Service(Service.Level.PROJECT)
public final class WorkflowProfileService {
    private static final Logger LOG = Logger.getInstance(WorkflowProfileService.class);

    private static final Set<String> CORE_DEFAULTS = Set.of("propose", "explore", "apply", "archive");

    private final Project project;
    private volatile Set<String> activeWorkflows;

    public WorkflowProfileService(Project project) {
        this.project = project;
    }

    /**
     * Returns true if the given workflow ID is enabled in the active profile.
     * Lazily initializes the cache on first call.
     */
    public boolean isWorkflowEnabled(String workflowId) {
        if (workflowId == null) return true;
        return getActiveWorkflows().contains(workflowId);
    }

    /**
     * Refreshes the cached workflows list from CLI or fallback.
     * Call this when the profile changes in settings.
     */
    public void refresh() {
        activeWorkflows = resolveWorkflows();
    }

    private Set<String> getActiveWorkflows() {
        Set<String> cached = activeWorkflows;
        if (cached == null) {
            cached = resolveWorkflows();
            activeWorkflows = cached;
        }
        return cached;
    }

    private Set<String> resolveWorkflows() {
        try {
            CliRunner.CliResult result = CliRunner.run(project, "config", "list", "--json");
            if (result.isSuccess()) {
                return parseWorkflows(result.stdout());
            }
            LOG.info("CLI config list failed (exit " + result.exitCode() + "), using core defaults");
        } catch (CliRunner.CliException e) {
            LOG.info("CLI unavailable for workflow resolution, using core defaults");
        }
        return CORE_DEFAULTS;
    }

    private static Set<String> parseWorkflows(String json) {
        try {
            JsonObject config = JsonParser.parseString(json).getAsJsonObject();
            JsonArray workflows = config.getAsJsonArray("workflows");
            if (workflows != null && !workflows.isEmpty()) {
                Set<String> result = new LinkedHashSet<>();
                for (JsonElement el : workflows) {
                    result.add(el.getAsString());
                }
                return Collections.unmodifiableSet(result);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse workflows from config JSON", e);
        }
        return CORE_DEFAULTS;
    }
}
