package com.johnnyblabs.openspec.version;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum VersionSupport {
    // V1_0 and V1_1 were removed in v0.3.0 when the plugin's minimum supported OpenSpec CLI
    // floor was raised to 1.3.0. Legacy `version: 1.0.0` or `1.1.0` config files continue to
    // function — they're routed to V1_2 via the fallback in fromString. The config-format
    // version baseline (V1_2 → openspec/config.yaml version 1.2.0) is independent of the
    // CLI-version axis and unchanged across CLI 1.2.x, 1.3.x, and 1.4.x.
    V1_2("1.2.0",
            // `schema` is the only field upstream's Zod schema requires (z.string().min(1)).
            // `version` is plugin-internal — upstream strips it; absence is not an error.
            // See align-config-contract-with-cli archive for the contract-alignment rationale.
            Set.of("schema"),
            Set.of("proposal", "design", "specs", "tasks"),
            // `workspace-planning` was removed here when OpenSpec CLI 1.5.0 dropped the
            // workspace/context-store/initiative model, matching `openspec schemas` on 1.5.0 (which
            // lists only `spec-driven`). On a 1.4.x CLI the schema is still recognized via the live
            // list that SchemaService.getKnownSchemaNames() joins in, so this removal doesn't strand
            // 1.4 users — it just stops the built-in fallback from advertising a removed schema.
            Set.of("spec-driven"));

    private final String version;
    private final Set<String> requiredConfigFields;
    private final Set<String> requiredArtifacts;
    private final Set<String> validSchemas;

    VersionSupport(String version, Set<String> requiredConfigFields,
                   Set<String> requiredArtifacts, Set<String> validSchemas) {
        this.version = version;
        this.requiredConfigFields = requiredConfigFields;
        this.requiredArtifacts = requiredArtifacts;
        this.validSchemas = validSchemas;
    }

    public String getVersion() {
        return version;
    }

    public Set<String> getRequiredConfigFields() {
        return requiredConfigFields;
    }

    public Set<String> getRequiredArtifacts() {
        return requiredArtifacts;
    }

    /**
     * Returns the built-in fallback set of schema names recognized by this baseline.
     *
     * <p><b>Role shift (v0.3.0):</b> this is the <b>built-in fallback</b> portion of the
     * canonical "is this schema recognized" check, NOT the canonical valid-set itself.
     * The validator joins this set with the CLI-runtime list from
     * {@code SchemaService.listSchemas()} via {@code SchemaService.getKnownSchemaNames()}
     * — see that method for the canonical check.
     *
     * <p>This method continues to exist (with its hardcoded value) for two reasons:
     * <ol>
     *   <li>Callers without project context (scaffolding templates, sync-time defaults)
     *       can't invoke {@code SchemaService} — they need a synchronous, project-free
     *       way to ask "what schemas does the plugin natively know about?"</li>
     *   <li>It documents which schemas the plugin natively supports (currently only
     *       {@code spec-driven}); upstream additions land here via the existing enum-update
     *       pattern. ({@code workspace-planning}, added for the 1.4 line, was removed when CLI
     *       1.5.0 dropped it; a 1.4.x CLI still surfaces it through the live
     *       {@code openspec schemas} list.)</li>
     * </ol>
     *
     * <p>Callers needing the broader "all currently-known schema names" set (including
     * the user's custom forks) should call {@code SchemaService.getKnownSchemaNames()}
     * instead.
     *
     * @return the built-in schema name set; immutable
     */
    public Set<String> getValidSchemas() {
        return validSchemas;
    }

    public static VersionSupport fromString(String version) {
        if (version == null) return V1_2; // default to latest
        for (VersionSupport v : values()) {
            if (v.version.equals(version) || version.startsWith(v.version.substring(0, 3))) {
                return v;
            }
        }
        return V1_2; // default to latest
    }

    public static List<String> allVersions() {
        return Arrays.stream(values()).map(VersionSupport::getVersion).toList();
    }
}
