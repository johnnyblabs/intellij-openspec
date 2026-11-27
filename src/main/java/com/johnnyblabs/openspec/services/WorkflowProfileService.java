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
    /** The CLI version the active workflows were resolved for, or {@code null} when no CLI was available. */
    private volatile @Nullable String resolvedForCli;
    /** Test seam: answers {@code config list --json} in place of the CLI, on any thread. */
    private volatile java.util.function.@Nullable Supplier<CliRunner.CliResult> configListForTest;
    /** What runs on the EDT after a re-resolve: updates the profile widget and re-checks toolbar actions. */
    private volatile java.util.function.Consumer<Project> afterCliRefresh = WorkflowProfileService::updateGatingUi;

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
    public synchronized void refresh() {
        Set<String> previous = activeWorkflows;
        Snapshot snapshot = resolve();
        activeWorkflows = snapshot.workflows;
        activeProfileName = snapshot.profileName;
        hasChangedSinceLastRefresh = previous != null && !previous.equals(snapshot.workflows);
    }

    /**
     * Whether a detection that reports {@code available} and {@code version} calls for re-resolving the active
     * workflows: true when they were resolved for a different CLI (another version, or the CLI appearing or
     * disappearing). False before the first resolve, which reads the detected version itself.
     */
    boolean needsRefreshFor(boolean available, @Nullable String version) {
        if (activeWorkflows == null) return false;
        return !java.util.Objects.equals(available ? version : null, resolvedForCli);
    }

    /**
     * Called for every CLI detection ({@link CliDetectionService#TOPIC}, from the detecting thread). When the CLI
     * changed since the active workflows were resolved, re-resolves them on a pooled thread, because resolving may
     * run the CLI, then updates the profile widget and asks toolbars to re-check their actions.
     */
    public void onCliDetected(boolean available, @Nullable String version) {
        if (!needsRefreshFor(available, version)) return;
        var app = com.intellij.openapi.application.ApplicationManager.getApplication();
        app.executeOnPooledThread(() -> {
            if (project.isDisposed() || !needsRefreshFor(available, version)) return;
            refresh();
            app.invokeLater(() -> afterCliRefresh.accept(project),
                    com.intellij.openapi.application.ModalityState.any(), project.getDisposed());
        });
    }

    private static void updateGatingUi(Project project) {
        var bar = com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project);
        if (bar != null) bar.updateWidget(com.johnnyblabs.openspec.statusbar.OpenSpecProfileStatusBarWidget.ID);
        com.intellij.ide.ActivityTracker.getInstance().inc();
    }

    void setConfigListForTest(java.util.function.@Nullable Supplier<CliRunner.CliResult> configList) {
        this.configListForTest = configList;
    }

    void setAfterCliRefreshForTest(java.util.function.Consumer<Project> after) {
        this.afterCliRefresh = after;
    }

    /**
     * Returns true if the most recent {@link #refresh()} call resolved a workflows
     * set that differs from the previously cached set. Always false on initial load
     * (lazy init or first refresh with no prior state).
     */
    public boolean hasChangedSinceLastRefresh() {
        return hasChangedSinceLastRefresh;
    }

    /**
     * The active profile's workflows outside the core set of the CLI it was resolved for, when the profile is
     * {@code custom}; empty otherwise. May run the CLI on first use: call it off the UI thread.
     */
    public Set<String> workflowsBeyondCore() {
        Set<String> active = getActiveWorkflows();
        return beyondCore(getActiveProfileName(), active, coreWorkflows(resolvedForCli));
    }

    /** {@link #workflowsBeyondCore()} for a given profile, its workflows and the core set, in the profile's order. */
    static Set<String> beyondCore(@Nullable String profileName, Set<String> active, Set<String> core) {
        if (!"custom".equals(profileName)) return Set.of();
        java.util.LinkedHashSet<String> beyond = new java.util.LinkedHashSet<>(active);
        beyond.removeAll(core);
        return java.util.Collections.unmodifiableSet(beyond);
    }

    private synchronized void ensureLoaded() {
        if (activeWorkflows != null) return;
        Snapshot snapshot = resolve();
        activeWorkflows = snapshot.workflows;
        activeProfileName = snapshot.profileName;
        hasChangedSinceLastRefresh = false;
    }

    private Snapshot resolve() {
        Snapshot snapshot = resolveFromCli();
        resolvedForCli = snapshot.cli();
        return snapshot;
    }

    /** The detected CLI, read once: {@code null} when none is available. */
    private @Nullable String detectedCli() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable() ? detection.getDetectedVersion() : null;
    }

    private Snapshot resolveFromCli() {
        try {
            var seam = configListForTest;
            CliRunner.CliResult result = seam != null ? seam.get()
                    : CliRunner.run(project, com.johnnyblabs.openspec.util.CliCommands.configList());
            if (CliJson.read(result) instanceof CliJson.Ok ok && ok.object() != null) {
                // Read after the run, which waits for the first detection; the same value resolves and is recorded.
                return parseSnapshot(ok.object(), detectedCli());
            }
            LOG.info("CLI config list gave no readable config (exit " + result.exitCode() + "), using core defaults");
        } catch (CliRunner.CliException e) {
            LOG.info("CLI unavailable for workflow resolution, using core defaults");
        }
        return Snapshot.fallback(detectedCli());
    }

    private Snapshot parseSnapshot(JsonObject config, @Nullable String cli) {
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
                    resolve(profileName, stored, cli), cli);
        } catch (Exception e) {
            LOG.warn("Failed to parse workflows from config JSON", e);
        }
        return Snapshot.fallback(cli);
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

    static Set<String> ordered(String... workflows) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(workflows)));
    }

    /** A resolved profile and the CLI it was resolved for ({@code null}: none available). */
    private record Snapshot(String profileName, Set<String> workflows, @Nullable String cli) {
        static Snapshot fallback(@Nullable String cli) {
            return new Snapshot(FALLBACK_PROFILE_NAME, CURRENT_CORE, cli);
        }
    }
}
