package com.johnnyblabs.openspec.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.util.CliJson;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves and caches the active workflow profile (name + workflows) from the global
 * OpenSpec config. Used by {@link com.johnnyblabs.openspec.actions.OpenSpecBaseAction}
 * to enable/disable actions based on the user's configured profile, and by the
 * status bar widget and Settings panel to display the active profile.
 *
 * <p>Workflows are resolved the way the CLI resolves them ({@code getProfileWorkflows} in
 * {@code dist/core/profiles.js}), see {@link #resolve}: the {@code custom} profile uses its stored list,
 * and every other profile uses the core set of the detected CLI version, whatever list is stored.
 * {@code config list --json} reports the stored list, so it can't be shown as is.
 */
@Service(Service.Level.PROJECT)
public final class WorkflowProfileService {
    private static final Logger LOG = Logger.getInstance(WorkflowProfileService.class);

    /** Core workflows from CLI 1.6.0: the set used when the CLI version is unknown. */
    static final Set<String> CURRENT_CORE = ordered("propose", "explore", "apply", "update", "sync", "archive");
    /** Core workflows in CLI 1.4.x and 1.5.x. */
    static final Set<String> CORE_1_4 = ordered("propose", "explore", "apply", "sync", "archive");
    /** Core workflows before CLI 1.4.0. */
    static final Set<String> CORE_1_3 = ordered("propose", "explore", "apply", "archive");
    /** From this CLI version, a custom list that archives without {@code sync} gets {@code sync} added. */
    static final String CUSTOM_SYNC_RULE_FROM = "1.10.0";

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
            if (CliJson.read(result) instanceof CliJson.Ok ok && ok.object() != null) {
                return parseSnapshot(ok.object());
            }
            LOG.info("CLI config list gave no readable config (exit " + result.exitCode() + "), using core defaults");
        } catch (CliRunner.CliException e) {
            LOG.info("CLI unavailable for workflow resolution, using core defaults");
        }
        return Snapshot.fallback();
    }

    private Snapshot parseSnapshot(JsonObject config) {
        try {
            String profileName = null;
            JsonElement profileEl = config.get("profile");
            if (profileEl != null && !profileEl.isJsonNull()) {
                profileName = profileEl.getAsString();
            }
            List<String> stored = null;
            JsonElement workflowsEl = config.get("workflows");
            if (workflowsEl != null && workflowsEl.isJsonArray()) {
                stored = new ArrayList<>();
                for (JsonElement el : workflowsEl.getAsJsonArray()) {
                    stored.add(el.getAsString());
                }
            }
            return new Snapshot(profileName != null ? profileName : FALLBACK_PROFILE_NAME,
                    resolve(profileName, stored, detectedCliVersion()));
        } catch (Exception e) {
            LOG.warn("Failed to parse workflows from config JSON", e);
        }
        return Snapshot.fallback();
    }

    private @Nullable String detectedCliVersion() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null ? detection.getDetectedVersion() : null;
    }

    /**
     * The workflows the CLI installs for {@code profile}, mirroring upstream's {@code getProfileWorkflows}:
     * <ul>
     *   <li>{@code custom}: the {@code stored} list (empty when there is none). From CLI 1.10.0, {@code sync} is
     *       inserted before the first {@code archive} or {@code bulk-archive} when the list lacks it.</li>
     *   <li>anything else, including no profile: {@link #coreWorkflows} for {@code cliVersion}. A stored list is
     *       ignored; upstream reads it only for custom.</li>
     * </ul>
     */
    static Set<String> resolve(@Nullable String profile, @Nullable List<String> stored, @Nullable String cliVersion) {
        if (!"custom".equals(profile)) {
            return coreWorkflows(cliVersion);
        }
        List<String> workflows = new ArrayList<>(stored == null ? List.of() : stored);
        if (knownVersion(cliVersion) && CliVersion.atLeast(cliVersion, CUSTOM_SYNC_RULE_FROM)
                && !workflows.contains("sync")) {
            for (int i = 0; i < workflows.size(); i++) {
                String w = workflows.get(i);
                if (w.equals("archive") || w.equals("bulk-archive")) {
                    workflows.add(i, "sync");
                    break;
                }
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(workflows));
    }

    /** The core set for {@code cliVersion}: four workflows before 1.4.0, five before 1.6.0, then six. Unknown: six. */
    static Set<String> coreWorkflows(@Nullable String cliVersion) {
        if (!knownVersion(cliVersion)) {
            return CURRENT_CORE;
        }
        if (!CliVersion.atLeast(cliVersion, "1.4.0")) {
            return CORE_1_3;
        }
        if (!CliVersion.atLeast(cliVersion, "1.6.0")) {
            return CORE_1_4;
        }
        return CURRENT_CORE;
    }

    /** A version {@link CliVersion} can order; a blank or unparseable one counts as unknown. */
    private static boolean knownVersion(@Nullable String version) {
        return version != null && version.strip().matches("\\d+\\.\\d+.*");
    }

    private static Set<String> ordered(String... workflows) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(workflows)));
    }

    private record Snapshot(String profileName, Set<String> workflows) {
        static Snapshot fallback() {
            return new Snapshot(FALLBACK_PROFILE_NAME, CURRENT_CORE);
        }
    }
}
