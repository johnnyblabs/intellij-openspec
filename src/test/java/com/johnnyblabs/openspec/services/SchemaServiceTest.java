package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

    @Mock Project project;
    @Mock CliDetectionService cliDetection;

    private SchemaService service;

    @BeforeEach
    void setUp() {
        service = new SchemaService(project);
    }

    @Nested
    class ListSchemas {

        @Test
        void parsesJsonArray() {
            String json = """
                    [
                      {"name": "spec-driven", "description": "Default spec-driven workflow", "isBuiltIn": true, "artifactIds": ["proposal", "design", "specs", "tasks"]},
                      {"name": "rapid", "description": "Rapid prototyping", "isBuiltIn": false, "artifactIds": ["proposal", "tasks"]}
                    ]
                    """;

            List<SchemaInfo> schemas = SchemaService.parseSchemaList(json);

            assertEquals(2, schemas.size());
            assertEquals("spec-driven", schemas.get(0).name());
            assertEquals("Default spec-driven workflow", schemas.get(0).description());
            assertTrue(schemas.get(0).isBuiltIn());
            assertEquals(4, schemas.get(0).artifactIds().size());
            assertEquals("rapid", schemas.get(1).name());
            assertFalse(schemas.get(1).isBuiltIn());
            assertEquals(2, schemas.get(1).artifactIds().size());
        }

        @Test
        void parsesEmptyArray() {
            List<SchemaInfo> schemas = SchemaService.parseSchemaList("[]");
            assertTrue(schemas.isEmpty());
        }

        @Test
        void handlesInvalidJson() {
            List<SchemaInfo> schemas = SchemaService.parseSchemaList("not json");
            assertTrue(schemas.isEmpty());
        }

        @Test
        void handlesMissingFields() {
            String json = """
                    [{"name": "minimal"}]
                    """;

            List<SchemaInfo> schemas = SchemaService.parseSchemaList(json);
            assertEquals(1, schemas.size());
            assertEquals("minimal", schemas.get(0).name());
            assertEquals("", schemas.get(0).description());
            assertFalse(schemas.get(0).isBuiltIn());
            assertTrue(schemas.get(0).artifactIds().isEmpty());
        }

        @Test
        void callsCliAndParsesResult() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.3.0");

            CliRunner.CliResult cliResult = new CliRunner.CliResult(0,
                    "[{\"name\":\"spec-driven\",\"description\":\"Default\",\"isBuiltIn\":true,\"artifactIds\":[\"proposal\"]}]",
                    "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")))
                        .thenReturn(cliResult);

                List<SchemaInfo> schemas = service.listSchemas();

                assertEquals(1, schemas.size());
                assertEquals("spec-driven", schemas.get(0).name());
            }
        }

        @Test
        void returnsEmptyList_whenCliUnsupported() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.1.0");

            List<SchemaInfo> schemas = service.listSchemas();

            assertTrue(schemas.isEmpty());
        }

        @Test
        void returnsEmptyList_whenCliUnavailable() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(false);

            List<SchemaInfo> schemas = service.listSchemas();

            assertTrue(schemas.isEmpty());
        }
    }

    /**
     * Pins the declared supported-version floor at exactly {@code 1.3.0}
     * (plugin-core "Supported CLI versions and capability preservation" — the
     * "floor is declared and pinned" scenario). {@code isSchemaSupported()} is a
     * floor-consuming behavior; asserting the boundary here means the declared
     * floor cannot drift up or down without failing the build.
     */
    @Nested
    class SupportedVersionFloor {

        private void detect(String version) {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn(version);
        }

        @Test
        void floorVersionIsSupported() {
            detect("1.3.0");
            assertTrue(service.isSchemaSupported(),
                    "1.3.0 is the declared supported-version floor and MUST be supported");
        }

        @Test
        void justBelowFloorIsNotSupported() {
            detect("1.2.9");
            assertFalse(service.isSchemaSupported(),
                    "1.2.9 is below the 1.3.0 floor and MUST NOT be supported — raising the floor is a deliberate, disclosed change");
        }

        @Test
        void aboveFloorIsSupported() {
            detect("1.4.1");
            assertTrue(service.isSchemaSupported(),
                    "1.4.1 is above the floor and MUST be supported");
        }
    }

    @Nested
    class ForkSchema {

        @Test
        void callsCliWithCorrectArgs() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schema"), eq("fork"), eq("spec-driven"), eq("my-fork")))
                        .thenReturn(new CliRunner.CliResult(0, "/path/to/my-fork.yaml\n", ""));

                String path = service.forkSchema("spec-driven", "my-fork");

                assertEquals("/path/to/my-fork.yaml", path);
                cli.verify(() -> CliRunner.run(eq(project), eq("schema"), eq("fork"), eq("spec-driven"), eq("my-fork")));
            }
        }

        @Test
        void returnsNull_onFailure() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schema"), eq("fork"), eq("spec-driven"), eq("bad")))
                        .thenReturn(new CliRunner.CliResult(1, "", "Schema not found"));

                String path = service.forkSchema("spec-driven", "bad");

                assertNull(path);
            }
        }
    }

    @Nested
    class InitSchema {

        @Test
        void callsCliWithCorrectArgs() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schema"), eq("init"), eq("my-schema")))
                        .thenReturn(new CliRunner.CliResult(0, "/path/to/my-schema.yaml\n", ""));

                String path = service.initSchema("my-schema");

                assertEquals("/path/to/my-schema.yaml", path);
                cli.verify(() -> CliRunner.run(eq(project), eq("schema"), eq("init"), eq("my-schema")));
            }
        }

        @Test
        void returnsNull_onFailure() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schema"), eq("init"), eq("bad")))
                        .thenReturn(new CliRunner.CliResult(1, "", "Init failed"));

                String path = service.initSchema("bad");

                assertNull(path);
            }
        }
    }

    @Nested
    class Caching {

        @Test
        void cachedListReusedOnSecondCall() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.3.0");

            CliRunner.CliResult cliResult = new CliRunner.CliResult(0,
                    "[{\"name\":\"spec-driven\",\"description\":\"Default\",\"isBuiltIn\":true,\"artifactIds\":[]}]",
                    "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")))
                        .thenReturn(cliResult);

                // First call
                service.listSchemas();
                // Second call — should use cache
                service.listSchemas();

                // CLI should only be called once
                cli.verify(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")), times(1));
            }
        }

        @Test
        void cacheInvalidatedOnFork() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.3.0");

            CliRunner.CliResult listResult = new CliRunner.CliResult(0,
                    "[{\"name\":\"spec-driven\",\"description\":\"\",\"isBuiltIn\":true,\"artifactIds\":[]}]",
                    "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")))
                        .thenReturn(listResult);
                cli.when(() -> CliRunner.run(eq(project), eq("schema"), eq("fork"), eq("spec-driven"), eq("my-fork")))
                        .thenReturn(new CliRunner.CliResult(0, "/path/fork.yaml", ""));

                // First call — populates cache
                service.listSchemas();
                // Fork — invalidates cache
                service.forkSchema("spec-driven", "my-fork");
                // Third call — should re-invoke CLI
                service.listSchemas();

                cli.verify(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")), times(2));
            }
        }

        @Test
        void cacheInvalidatedOnInit() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.3.0");

            CliRunner.CliResult listResult = new CliRunner.CliResult(0,
                    "[{\"name\":\"spec-driven\",\"description\":\"\",\"isBuiltIn\":true,\"artifactIds\":[]}]",
                    "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")))
                        .thenReturn(listResult);
                cli.when(() -> CliRunner.run(eq(project), eq("schema"), eq("init"), eq("new-schema")))
                        .thenReturn(new CliRunner.CliResult(0, "/path/new-schema.yaml", ""));

                // First call — populates cache
                service.listSchemas();
                // Init — invalidates cache
                service.initSchema("new-schema");
                // Third call — should re-invoke CLI
                service.listSchemas();

                cli.verify(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")), times(2));
            }
        }

        @Test
        void clearCache_forcesRefresh() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.3.0");

            CliRunner.CliResult listResult = new CliRunner.CliResult(0, "[]", "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")))
                        .thenReturn(listResult);

                service.listSchemas();
                service.clearCache();
                service.listSchemas();

                cli.verify(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")), times(2));
            }
        }
    }

    @Nested
    class VersionCheck {

        @Test
        void supported_whenVersionIsMinimum() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.3.0");

            assertTrue(service.isSchemaSupported());
        }

        @Test
        void supported_whenVersionIsHigher() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("2.0.0");

            assertTrue(service.isSchemaSupported());
        }

        @Test
        void unsupported_whenVersionIsLower() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.1.0");

            assertFalse(service.isSchemaSupported());
        }

        @Test
        void unsupported_whenVersionIs_1_2_0_belowNewFloor() {
            // After v0.3.0 floor bump, schema management requires CLI 1.3.0. The previous
            // floor was 1.2.0, so this test pins the boundary: 1.2.0 is now below floor.
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

            assertFalse(service.isSchemaSupported(),
                    "CLI 1.2.0 must be below floor after v0.3.0 raises it to 1.3.0");
        }

        @Test
        void unsupported_whenVersionIs_1_2_99_belowNewFloor() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.99");

            assertFalse(service.isSchemaSupported());
        }

        @Test
        void unsupported_whenVersionIsNull() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn(null);

            assertFalse(service.isSchemaSupported());
        }

        @Test
        void unsupported_whenCliNotAvailable() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(false);

            assertFalse(service.isSchemaSupported());
        }

        @Test
        void unsupported_whenDetectionServiceNull() {
            when(project.getService(CliDetectionService.class)).thenReturn(null);

            assertFalse(service.isSchemaSupported());
        }

        // Version-comparison tests moved to CliVersionAtLeastTest after the comparison logic
        // was extracted to com.johnnyblabs.openspec.util.CliVersion as part of v0.3.0's
        // bump-cli-floor-to-1-3 change.
    }

    @Nested
    class KnownSchemaNames {

        @Test
        void cliUnavailable_returnsOnlyBuiltIns() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(false);

            java.util.Set<String> known = service.getKnownSchemaNames();

            assertEquals(com.johnnyblabs.openspec.version.VersionSupport.V1_2.getValidSchemas(), known,
                    "CLI unavailable: known-set must equal the built-in fallback");
        }

        @Test
        void cliBelowFloor_returnsOnlyBuiltIns() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

            java.util.Set<String> known = service.getKnownSchemaNames();

            assertEquals(com.johnnyblabs.openspec.version.VersionSupport.V1_2.getValidSchemas(), known,
                    "CLI below 1.3.0 floor: known-set must equal the built-in fallback");
        }

        @Test
        void cliAvailableWithBuiltInsOnly_returnsBuiltIns() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.4.0");

            String json = "[" +
                    "{\"name\":\"spec-driven\",\"description\":\"\",\"isBuiltIn\":true,\"artifactIds\":[]}," +
                    "{\"name\":\"workspace-planning\",\"description\":\"\",\"isBuiltIn\":true,\"artifactIds\":[]}" +
                    "]";
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                java.util.Set<String> known = service.getKnownSchemaNames();

                assertTrue(known.contains("spec-driven"));
                assertTrue(known.contains("workspace-planning"));
                assertEquals(2, known.size(), "Union of built-ins and identical CLI list should be the built-in set");
            }
        }

        @Test
        void cliAvailableWithFork_returnsBuiltInsPlusFork() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.4.0");

            String json = "[" +
                    "{\"name\":\"spec-driven\",\"description\":\"\",\"isBuiltIn\":true,\"artifactIds\":[]}," +
                    "{\"name\":\"workspace-planning\",\"description\":\"\",\"isBuiltIn\":true,\"artifactIds\":[]}," +
                    "{\"name\":\"my-team-flow\",\"description\":\"Forked\",\"isBuiltIn\":false,\"artifactIds\":[]}" +
                    "]";
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                java.util.Set<String> known = service.getKnownSchemaNames();

                assertTrue(known.contains("spec-driven"), "Built-in spec-driven must remain");
                assertTrue(known.contains("workspace-planning"), "Built-in workspace-planning must remain");
                assertTrue(known.contains("my-team-flow"), "Custom forked schema must appear in known-set");
                assertEquals(3, known.size());
            }
        }

        @Test
        void cliListEmpty_stillReturnsBuiltIns() {
            // Even if `openspec schemas --json` returns [], the built-in fallback must remain.
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.4.0");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("schemas"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "[]", ""));

                java.util.Set<String> known = service.getKnownSchemaNames();

                assertEquals(com.johnnyblabs.openspec.version.VersionSupport.V1_2.getValidSchemas(), known,
                        "Empty CLI list collapses the union back to the built-in fallback");
            }
        }

        @Test
        void returnedSetIsImmutable() {
            when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
            when(cliDetection.isAvailable()).thenReturn(false);

            java.util.Set<String> known = service.getKnownSchemaNames();

            assertThrows(UnsupportedOperationException.class, () -> known.add("untrusted-input"),
                    "Returned set must be unmodifiable so callers can't corrupt the internal state");
        }
    }
}
