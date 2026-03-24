package com.johnnyblabs.openspec.version;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum VersionSupport {
    V1_0("1.0.0",
            Set.of("schema"),
            Set.of("proposal"),
            Set.of("spec-driven")),
    V1_1("1.1.0",
            Set.of("schema", "version"),
            Set.of("proposal", "design"),
            Set.of("spec-driven")),
    V1_2("1.2.0",
            Set.of("schema", "version"),
            Set.of("proposal", "design", "specs", "tasks"),
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
