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
 * Resolves and caches the active workflow profile (name + workflows) from the global
 * OpenSpec config. Used by {@link com.johnnyblabs.openspec.actions.OpenSpecBaseAction}
 * to enable/disable actions based on the user's configured profile, and by the
 * status bar widget and Settings panel to display the active profile.
 *
 * <p>OpenSpec 1.2.0+ defines the core profile as 5 workflows:
 * {@code propose, explore, apply, sync, archive}. Custom profiles add any subset of
 * the expanded workflows: {@code new, continue, ff, verify, bulk-archive, onboard}.
 */
@Service(Service.Level.PROJECT)
public final class WorkflowProfileService {
    private static final Logger LOG = Logger.getInstance(WorkflowProfileService.class);

    /** OpenSpec 1.2.0+ core profile workflow set. */
    static final Set<String> CORE_DEFAULTS =
            Set.of("propose", "explore", "apply", "sync", "archive");

    /** Fallback profile name when the CLI is unavailable. */
    static final String FALLBACK_PROFILE_NAME = "core";

    private final Project project;
    private volatile Set<String> activeWorkflows;
    private volatile String activeProfileName;
    private volatile boolean hasChangedSinceLastRefresh;

    public WorkflowProfileService(Project project) {
        this.project = project;
    }

    /**
     * Returns true if the given workflow ID is enabled in the active profile.
     * Lazily initializes the cache on first call. Null workflow IDs always return true
     * (utility actions are not gated).
     */
    public boolean isWorkflowEnabled(String workflowId) {
        if (workflowId == null) return true;
        return getActiveWorkflows().contains(workflowId);
    }

    /**
     * Returns the cached active workflows set. Lazily resolves on first call.
     * Public so UI surfaces (status bar widget popup) can compute the diff against
     * the full workflow set.
     */
    public Set<String> getActiveWorkflows() {
        Set<String> cached = activeWorkflows;
        if (cached == null) {
            ensureLoaded();
            cached = activeWorkflows;
        }
        return cached;
    }

    /**
     * Returns the active profile name (e.g. {@code "core"} or {@code "custom"}).
     * Lazily resolves on first call. Returns {@link #FALLBACK_PROFILE_NAME} when the
     * CLI is unavailable.
     */
    public String getActiveProfileName() {
        if (activeWorkflows == null) {
            ensureLoaded();
        }
        String name = activeProfileName;
        return name != null ? name : FALLBACK_PROFILE_NAME;
    }

    /**
     * Refreshes the cached profile from CLI or fallback. Compares the resolved
     * workflows against the previously cached set; {@link #hasChangedSinceLastRefresh()}
     * reflects the result of that comparison.
     *
     * <p>The diff signal is established for future cache-coherence work (notifying
     * users of external profile changes); v1 does not consume it.
     */
    public void refresh() {
        Set<String> previous = activeWorkflows;
        Snapshot snapshot = resolve();
        activeWorkflows = snapshot.workflows;
        activeProfileName = snapshot.profileName;
        hasChangedSinceLastRefresh = previous != null && !previous.equals(snapshot.workflows);
    }

    /**
     * Returns true if the most recent {@link #refresh()} call resolved a workflows
     * set that differs from the previously cached set. Always false on initial load
     * (lazy init or first refresh with no prior state).
     */
    public boolean hasChangedSinceLastRefresh() {
        return hasChangedSinceLastRefresh;
    }

    private synchronized void ensureLoaded() {
        if (activeWorkflows != null) return;
        Snapshot snapshot = resolve();
        activeWorkflows = snapshot.workflows;
        activeProfileName = snapshot.profileName;
        hasChangedSinceLastRefresh = false;
    }

    private Snapshot resolve() {
        try {
            CliRunner.CliResult result = CliRunner.run(project, "config", "list", "--json");
            if (result.isSuccess()) {
                return parseSnapshot(result.stdout());
            }
            LOG.info("CLI config list failed (exit " + result.exitCode() + "), using core defaults");
        } catch (CliRunner.CliException e) {
            LOG.info("CLI unavailable for workflow resolution, using core defaults");
        }
        return Snapshot.fallback();
    }

    private static Snapshot parseSnapshot(String json) {
        try {
            JsonObject config = JsonParser.parseString(json).getAsJsonObject();
            String profileName = null;
            JsonElement profileEl = config.get("profile");
            if (profileEl != null && !profileEl.isJsonNull()) {
                profileName = profileEl.getAsString();
            }
            JsonArray workflows = config.getAsJsonArray("workflows");
            if (workflows != null && !workflows.isEmpty()) {
                Set<String> result = new LinkedHashSet<>();
                for (JsonElement el : workflows) {
                    result.add(el.getAsString());
                }
                return new Snapshot(profileName, Collections.unmodifiableSet(result));
            }
            // workflows missing/empty — keep parsed profile name if present, else fallback name
            return new Snapshot(profileName != null ? profileName : FALLBACK_PROFILE_NAME, CORE_DEFAULTS);
        } catch (Exception e) {
            LOG.warn("Failed to parse workflows from config JSON", e);
        }
        return Snapshot.fallback();
    }

    private record Snapshot(String profileName, Set<String> workflows) {
        static Snapshot fallback() {
            return new Snapshot(FALLBACK_PROFILE_NAME, CORE_DEFAULTS);
        }
    }
}
