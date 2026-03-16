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
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

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
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

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
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

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
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

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
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

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
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

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

        @Test
        void compareVersions_equal() {
            assertEquals(0, SchemaService.compareVersions("1.2.0", "1.2.0"));
        }

        @Test
        void compareVersions_lessThan() {
            assertTrue(SchemaService.compareVersions("1.1.0", "1.2.0") < 0);
        }

        @Test
        void compareVersions_greaterThan() {
            assertTrue(SchemaService.compareVersions("2.0.0", "1.2.0") > 0);
        }

        @Test
        void compareVersions_patchLevel() {
            assertTrue(SchemaService.compareVersions("1.2.1", "1.2.0") > 0);
        }

        @Test
        void compareVersions_differentLengths() {
            assertTrue(SchemaService.compareVersions("1.2.0.1", "1.2.0") > 0);
        }
    }
}
