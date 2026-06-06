package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowProfileServiceTest {

    @Mock Project project;

    private WorkflowProfileService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowProfileService(project);
    }

    @Nested
    class IsWorkflowEnabled {

        @Test
        void nullWorkflowId_alwaysReturnsTrue() {
            // Utility actions pass null — always enabled
            assertTrue(service.isWorkflowEnabled(null));
        }

        @Test
        void coreWorkflowsEnabled_whenCliReturnsFullConfig() {
            String json = """
                    {
                      "profile": "custom",
                      "workflows": ["propose", "explore", "apply", "archive", "ff", "continue", "verify", "sync", "bulk-archive"]
                    }
                    """;
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("ff"));
                assertTrue(service.isWorkflowEnabled("verify"));
                assertTrue(service.isWorkflowEnabled("bulk-archive"));
            }
        }

        @Test
        void expandedWorkflowsDisabled_whenCliReturnsCoreOnly() {
            // Note: this test simulates a hypothetical CLI returning only the
            // pre-1.2.0 core set (no sync). The plugin's CORE_DEFAULTS fallback
            // includes sync (post-1.2.0); the assertions here reflect what the
            // CLI returned, not the fallback.
            String json = """
                    {
                      "profile": "core",
                      "workflows": ["propose", "explore", "apply", "archive"]
                    }
                    """;
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("explore"));
                assertFalse(service.isWorkflowEnabled("ff"));
                assertFalse(service.isWorkflowEnabled("continue"));
                assertFalse(service.isWorkflowEnabled("verify"));
                assertFalse(service.isWorkflowEnabled("sync"));
                assertFalse(service.isWorkflowEnabled("bulk-archive"));
            }
        }
    }

    @Nested
    class CliUnavailableFallback {

        @Test
        void fallsToCoreDefaults_whenCliThrowsException() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenThrow(new CliRunner.CliException("CLI not found"));

                // OpenSpec 1.2.0+ core: 5 workflows including sync
                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("explore"));
                assertTrue(service.isWorkflowEnabled("apply"));
                assertTrue(service.isWorkflowEnabled("sync"));
                assertTrue(service.isWorkflowEnabled("archive"));
                assertFalse(service.isWorkflowEnabled("ff"));
                assertFalse(service.isWorkflowEnabled("continue"));
            }
        }

        @Test
        void fallsToCoreDefaults_whenCliReturnsNonZeroExit() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(1, "", "error"));

                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("sync"));
                assertFalse(service.isWorkflowEnabled("ff"));
            }
        }

        @Test
        void fallsToCoreDefaults_whenJsonIsMalformed() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, "not json at all", ""));

                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("sync"));
                assertFalse(service.isWorkflowEnabled("ff"));
            }
        }

        @Test
        void fallsToCoreDefaults_whenWorkflowsArrayMissing() {
            String json = """
                    { "profile": "core" }
                    """;
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("sync"));
                assertFalse(service.isWorkflowEnabled("ff"));
            }
        }

        @Test
        void fallsToCoreDefaults_whenWorkflowsArrayEmpty() {
            String json = """
                    { "profile": "core", "workflows": [] }
                    """;
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("sync"));
                assertFalse(service.isWorkflowEnabled("ff"));
            }
        }
    }

    @Nested
    class GetActiveProfileName {

        @Test
        void returnsCliProvidedName_whenAvailable() {
            String json = """
                    { "profile": "custom", "workflows": ["propose"] }
                    """;
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertEquals("custom", service.getActiveProfileName());
            }
        }

        @Test
        void returnsCoreFallback_whenCliUnavailable() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenThrow(new CliRunner.CliException("CLI not found"));

                assertEquals("core", service.getActiveProfileName());
            }
        }

        @Test
        void returnsCoreFallback_whenJsonOmitsProfileField() {
            String json = """
                    { "workflows": ["propose"] }
                    """;
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertEquals("core", service.getActiveProfileName());
            }
        }
    }

    @Nested
    class GetActiveWorkflows {

        @Test
        void returnsCachedSet_whenCliAvailable() {
            String json = """
                    {
                      "profile": "custom",
                      "workflows": ["propose", "explore", "ff"]
                    }
                    """;
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                Set<String> workflows = service.getActiveWorkflows();
                assertEquals(Set.of("propose", "explore", "ff"), workflows);
            }
        }

        @Test
        void returnsCoreDefaults_whenCliUnavailable() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenThrow(new CliRunner.CliException("CLI not found"));

                Set<String> workflows = service.getActiveWorkflows();
                assertEquals(
                        Set.of("propose", "explore", "apply", "sync", "archive"),
                        workflows);
            }
        }
    }

    @Nested
    class Refresh {

        @Test
        void refreshUpdatesCache() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                String coreJson = """
                        { "profile": "core", "workflows": ["propose", "explore", "apply", "sync", "archive"] }
                        """;
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, coreJson, ""));

                assertFalse(service.isWorkflowEnabled("ff"));

                String customJson = """
                        { "profile": "custom", "workflows": ["propose", "explore", "apply", "sync", "archive", "ff", "continue"] }
                        """;
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, customJson, ""));

                service.refresh();

                assertTrue(service.isWorkflowEnabled("ff"));
                assertTrue(service.isWorkflowEnabled("continue"));
                assertEquals("custom", service.getActiveProfileName());
            }
        }
    }

    @Nested
    class HasChangedSinceLastRefresh {

        @Test
        void initialLoad_isNotChange() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                String json = """
                        { "profile": "core", "workflows": ["propose", "explore", "apply", "sync", "archive"] }
                        """;
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                // Trigger lazy init; not a "change"
                service.getActiveWorkflows();

                assertFalse(service.hasChangedSinceLastRefresh(),
                        "Initial lazy load should not be flagged as a change");
            }
        }

        @Test
        void refreshWithSameWorkflows_isNotChange() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                String json = """
                        { "profile": "core", "workflows": ["propose", "explore", "apply", "sync", "archive"] }
                        """;
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                service.getActiveWorkflows(); // lazy init
                service.refresh(); // refresh with same content

                assertFalse(service.hasChangedSinceLastRefresh());
            }
        }

        @Test
        void refreshWithDifferentWorkflows_isChange() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                String coreJson = """
                        { "profile": "core", "workflows": ["propose", "explore", "apply", "sync", "archive"] }
                        """;
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, coreJson, ""));

                service.getActiveWorkflows(); // lazy init populates previous

                String customJson = """
                        { "profile": "custom", "workflows": ["propose", "explore", "apply", "sync", "archive", "ff"] }
                        """;
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, customJson, ""));

                service.refresh();

                assertTrue(service.hasChangedSinceLastRefresh());
            }
        }
    }
}
