package com.johnnyblabs.openspec.model;

import java.util.List;

/**
 * Resolved, immutable view of the active OpenSpec mode/schema and version axes
 * for a change, derived from {@code openspec status --json}. This is the single
 * source of truth that workflow surfaces consult for mode- and version-dependent
 * behavior, instead of inferring layout from the on-disk directory structure.
 *
 * <p>The two version axes are kept deliberately separate:
 * <ul>
 *   <li>{@code cliVersion} — the installed OpenSpec client version (floor 1.3, baseline 1.4),
 *       which gates whether commands/schemas exist.</li>
 *   <li>{@code configFormatVersion} — the {@code openspec/config.yaml} {@code version:} field,
 *       which is stable across CLI lines and is plugin-internally required.</li>
 * </ul>
 * Conflating these is a known incident; callers select the axis that actually governs
 * the behavior in question.
 */
public record WorkflowSchemaContext(
        String schemaName,
        String mode,
        String sourceOfTruth,
        List<String> allowedEditRoots,
        String cliVersion,
        String configFormatVersion,
        boolean cliActionContextAvailable) {

    public static final String DEFAULT_SCHEMA = "spec-driven";
    public static final String DEFAULT_MODE = "repo-local";
    public static final String DEFAULT_SOURCE_OF_TRUTH = "repo";

    public WorkflowSchemaContext {
        allowedEditRoots = allowedEditRoots != null ? List.copyOf(allowedEditRoots) : List.of();
    }

    /**
     * The default {@code spec-driven} / {@code repo-local} context used when the CLI is
     * unavailable or below the floor (so {@code actionContext} is not available). This
     * preserves the plugin's current behavior for the common case.
     */
    public static WorkflowSchemaContext fallback(String configFormatVersion, String cliVersion) {
        return new WorkflowSchemaContext(DEFAULT_SCHEMA, DEFAULT_MODE, DEFAULT_SOURCE_OF_TRUTH,
                List.of(), cliVersion, configFormatVersion, false);
    }

    /**
     * True when this is the default spec-driven, repo-local layout — surfaces should
     * behave exactly as they did before schema/version awareness was introduced.
     */
    public boolean isSpecDrivenRepoLocal() {
        return DEFAULT_SCHEMA.equals(schemaName) && DEFAULT_MODE.equals(mode);
    }

    /**
     * True for a non-default mode (e.g. {@code workspace-planning}) where spec-driven-only
     * affordances do not apply and surfaces should reflect the mode instead.
     */
    public boolean isNonDefaultMode() {
        return mode != null && !DEFAULT_MODE.equals(mode);
    }
}
