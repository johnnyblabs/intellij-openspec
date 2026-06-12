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
            Set.of("schema", "version"),
            Set.of("proposal", "design", "specs", "tasks"),
            Set.of("spec-driven", "workspace-planning"));

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
