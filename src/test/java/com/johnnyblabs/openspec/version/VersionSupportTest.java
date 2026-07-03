package com.johnnyblabs.openspec.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VersionSupport}. As of v0.3.0 ({@code bump-cli-floor-to-1-3}), V1_0 and V1_1
 * were removed; V1_2 is the only baseline. Legacy {@code version: 1.0.0} and {@code 1.1.0}
 * config files continue to function — they route to V1_2 via the fallback in {@code fromString}.
 */
class VersionSupportTest {

    // --- v1.2.0 contract — the only baseline ---

    @Test
    void v1_2_requiresOnlySchema() {
        // Schema is the only field upstream's Zod schema requires (z.string().min(1)).
        // `version` was previously listed here but is plugin-internal — upstream strips it.
        // See align-config-contract-with-cli archive.
        Set<String> fields = VersionSupport.V1_2.getRequiredConfigFields();
        assertEquals(Set.of("schema"), fields, "v1.2 must require ONLY 'schema'");
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
    void v1_2_builtInSchemasAreSpecDrivenOnly() {
        // OpenSpec CLI 1.5.0 removed workspace-planning; the built-in fallback set must not
        // advertise a schema the supported CLI no longer recognizes. workspace-planning stays valid
        // on a 1.4.x CLI via the live `openspec schemas` list joined in by
        // SchemaService.getKnownSchemaNames() — it is just no longer a built-in.
        Set<String> schemas = VersionSupport.V1_2.getValidSchemas();
        assertEquals(Set.of("spec-driven"), schemas);
        assertFalse(schemas.contains("workspace-planning"),
                "workspace-planning must not be a built-in schema after 1.5.0 removed it");
    }

    @Test
    void v1_2_versionString() {
        assertEquals("1.2.0", VersionSupport.V1_2.getVersion());
    }

    // --- Legacy baselines (V1_0, V1_1) deleted; their version strings route to V1_2 ---

    @Test
    void legacyVersion_1_0_0_routesToV1_2() {
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.0.0"),
                "Legacy version 1.0.0 must route to V1_2 baseline after v0.3.0 floor bump");
    }

    @Test
    void legacyVersion_1_1_0_routesToV1_2() {
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.1.0"),
                "Legacy version 1.1.0 must route to V1_2 baseline after v0.3.0 floor bump");
    }

    @Test
    void legacyVersion_1_0_x_routesToV1_2() {
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.0.5"));
    }

    @Test
    void legacyVersion_1_1_x_routesToV1_2() {
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.1.99"));
    }

    // --- fromString resolution for current and forward-compatible versions ---

    @Test
    void fromString_exactMatch_v1_2() {
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.2.0"));
    }

    @Test
    void fromString_prefixMatch_v1_2() {
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.2.1"));
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString("1.2.99"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2.0.0", "0.9.0", "xyz", "  ", "1.3.0", "1.4.0", "1.4.1"})
    void fromString_unknownOrFutureDefaultsToLatest(String version) {
        // The config-format version baseline is still V1_2 (1.2.0) as of CLI 1.4.x.
        // Any newer config-format version (1.3.x, 1.4.x) will also route to V1_2 until
        // upstream introduces a new config-format that requires a new V1_X enum entry.
        assertEquals(VersionSupport.V1_2, VersionSupport.fromString(version),
                "Unknown or future version '" + version + "' should default to V1_2 (latest known)");
    }

    // --- allVersions reflects the V1_2-only baseline ---

    @Test
    void allVersions_returnsOnlyV1_2() {
        List<String> versions = VersionSupport.allVersions();
        assertEquals(1, versions.size(), "After v0.3.0 floor bump, V1_2 is the only baseline");
        assertTrue(versions.contains("1.2.0"));
        assertFalse(versions.contains("1.0.0"), "1.0.0 baseline was removed");
        assertFalse(versions.contains("1.1.0"), "1.1.0 baseline was removed");
    }
}
