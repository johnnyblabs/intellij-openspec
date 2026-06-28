package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.model.WorkflowSchemaContext;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliVersion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and caches the {@link WorkflowSchemaContext} for a change — the mode/schema
 * and version axes that workflow surfaces consult instead of assuming a spec-driven,
 * repo-local layout.
 *
 * <p>The context is derived from {@code openspec status --json} (reusing
 * {@link ArtifactOrchestrationService}'s status call so the CLI is not invoked twice).
 * When the CLI is unavailable or below the {@value #CLI_FLOOR} floor — where
 * {@code actionContext} is not exposed — the context falls back to the default
 * spec-driven repo-local assumption, preserving current behavior.
 *
 * <p>The cache is invalidated in lockstep with {@link ArtifactOrchestrationService}: that
 * service clears this one on every {@code invalidateCache}/{@code invalidateAllCaches},
 * so propose/apply/archive and change-selection changes all refresh the context.
 */
@Service(Service.Level.PROJECT)
public final class WorkflowSchemaContextService {
    private static final Logger LOG = Logger.getInstance(WorkflowSchemaContextService.class);

    /** CLI floor at which {@code openspec status} exposes {@code actionContext}. */
    public static final String CLI_FLOOR = "1.3.0";

    private final Project project;
    private final Map<String, WorkflowSchemaContext> cache = new ConcurrentHashMap<>();

    public WorkflowSchemaContextService(Project project) {
        this.project = project;
    }

    /**
     * Returns the cached context without spawning a CLI process. Safe to call from EDT.
     * Returns null if no context has been resolved for the change yet.
     */
    public WorkflowSchemaContext getCachedContext(String changeName) {
        return cache.get(changeName);
    }

    /**
     * Resolves the schema/version context for a change by reading the CLI status.
     * <b>Must NOT be called on EDT</b> — may spawn an external process.
     */
    public WorkflowSchemaContext getContext(String changeName) {
        String configVersion = configFormatVersion();
        String cliVersion = cliVersion();

        WorkflowSchemaContext resolved = cliActionContextAvailable(cliVersion)
                ? fromStatus(changeName, cliVersion, configVersion)
                : WorkflowSchemaContext.fallback(configVersion, cliVersion);

        cache.put(changeName, resolved);
        return resolved;
    }

    private WorkflowSchemaContext fromStatus(String changeName, String cliVersion, String configVersion) {
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        ChangeArtifactDag dag = orchestration != null ? orchestration.getArtifactStatus(changeName) : null;
        ChangeArtifactDag.ActionContext ac = dag != null ? dag.getActionContext() : null;
        if (ac == null) {
            // CLI is at the floor but did not surface actionContext — keep current behavior.
            return WorkflowSchemaContext.fallback(configVersion, cliVersion);
        }
        String schema = dag.getSchemaName() != null ? dag.getSchemaName() : WorkflowSchemaContext.DEFAULT_SCHEMA;
        String mode = ac.getMode() != null ? ac.getMode() : WorkflowSchemaContext.DEFAULT_MODE;
        String source = ac.getSourceOfTruth() != null ? ac.getSourceOfTruth() : WorkflowSchemaContext.DEFAULT_SOURCE_OF_TRUTH;
        return new WorkflowSchemaContext(schema, mode, source, ac.getAllowedEditRoots(),
                cliVersion, configVersion, true);
    }

    /** Clears the cached context for one change. */
    public void invalidateCache(String changeName) {
        cache.remove(changeName);
    }

    /** Clears all cached contexts. */
    public void invalidateAllCaches() {
        cache.clear();
    }

    private boolean cliActionContextAvailable(String cliVersion) {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        boolean available = detection != null && detection.isAvailable();
        return available && CliVersion.atLeast(cliVersion, CLI_FLOOR);
    }

    private String cliVersion() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null ? detection.getDetectedVersion() : null;
    }

    private String configFormatVersion() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        return settings != null ? settings.getEffectiveVersion(project) : null;
    }
}
