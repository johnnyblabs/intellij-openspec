package com.johnnyb.openspec.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VersionSupportTest {

    // --- v1.2.0 is the current spec; verify its contract thoroughly ---

    @Test
    void v1_2_requiresSchemaAndVersion() {
        Set<String> fields = VersionSupport.V1_2.getRequiredConfigFields();
        assertTrue(fields.contains("schema"), "v1.2 must require 'schema'");
        assertTrue(fields.contains("version"), "v1.2 must require 'version'");
    }

    @Test
    void v1_2_requiresAllFourArtifacts() {
        Set<String> artifacts = VersionSupport.V1_2.getRequiredArtifacts();
        assertEquals(Set.of("proposal", "design", "specs", "tasks"), artifacts,
                "v1.2 must require proposal, design, specs, and tasks (IDs without .md)");
    }

    @Test
    void v1_2_artifactIds_doNotContainDotMd() {
        for (String artifact : VersionSupport.V1_2.getRequiredArtifacts()) {
            assertFalse(artifact.endsWith(".md"),
                    "Artifact IDs must not have .md suffix, but got: " + artifact);
        }
    }

    @Test
    void v1_2_supportsThreeSchemas() {
        Set<String> schemas = VersionSupport.V1_2.getValidSchemas();
        assertEquals(Set.of("spec-driven", "tdd", "rapid"), schemas);
    }

    @Test
    void v1_2_versionString() {
        assertEquals("1.2.0", VersionSupport.V1_2.getVersion());
    }

    // --- Version progression: each version adds capabilities ---

    @Test
    void v1_0_hasMinimalArtifacts() {
        assertEquals(Set.of("proposal"), VersionSupport.V1_0.getRequiredArtifacts());
        assertEquals(Set.of("schema"), VersionSupport.V1_0.getRequiredConfigFields());
        assertEquals(Set.of("spec-driven"), VersionSupport.V1_0.getValidSchemas());
    }

    @Test
    void v1_1_addsDesignArtifact() {
        Set<String> artifacts = VersionSupport.V1_1.getRequiredArtifacts();
        assertTrue(artifacts.contains("proposal"));
        assertTrue(artifacts.contains("design"));
        assertFalse(artifacts.contains("tasks"), "v1.1 should not require tasks");
        assertFalse(artifacts.contains("specs"), "v1.1 should not require specs");
    }

    @Test
    void eachVersionAddsCapabilities() {
        assertTrue(VersionSupport.V1_1.getRequiredArtifacts().size()
                > VersionSupport.V1_0.getRequiredArtifacts().size());
        assertTrue(VersionSupport.V1_2.getRequiredArtifacts().size()
                > VersionSupport.V1_1.getRequiredArtifacts().size());
    }

    // --- fromString resolution ---

    @Test
    void fromString_exactMatch() {
        assertEquals(VersionSupport.V1_0, VersionSupport.fromString("1.0.0"));
        assertEquals(VersionSupport.V1_1, VersionSupport.fromString("1.1.0"));
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.2.0"));
    }

    @Test
    void fromString_prefixMatch() {
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.2.1"));
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.2.99"));
        assertEquals(VersionSupport.V1_0, VersionSupport.fromString("1.0.5"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2.0.0", "0.9.0", "xyz", "  "})
    void fromString_unknownDefaultsToLatest(String version) {
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString(version),
                "Unknown version '" + version + "' should default to V1_2 (latest)");
    }

    // --- allVersions ---

    @Test
    void allVersions_returnsAllInOrder() {
        List<String> versions = VersionSupport.allVersions();
        assertEquals(3, versions.size());
        assertTrue(versions.contains("1.0.0"));
        assertTrue(versions.contains("1.1.0"));
        assertTrue(versions.contains("1.2.0"));
    }
}
