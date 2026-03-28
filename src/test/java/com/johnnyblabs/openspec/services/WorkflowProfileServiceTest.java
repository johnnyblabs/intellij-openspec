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

                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("explore"));
                assertTrue(service.isWorkflowEnabled("apply"));
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
                assertFalse(service.isWorkflowEnabled("ff"));
            }
        }

        @Test
        void fallsToCoreDefaults_whenJsonIsMalformed() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, "not json at all", ""));

                assertTrue(service.isWorkflowEnabled("propose"));
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
                assertFalse(service.isWorkflowEnabled("ff"));
            }
        }
    }

    @Nested
    class Refresh {

        @Test
        void refreshUpdatesCache() {
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                // First call: core only
                String coreJson = """
                        { "workflows": ["propose", "explore", "apply", "archive"] }
                        """;
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, coreJson, ""));

                assertFalse(service.isWorkflowEnabled("ff"));

                // Switch to expanded
                String expandedJson = """
                        { "workflows": ["propose", "explore", "apply", "archive", "ff", "continue"] }
                        """;
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, expandedJson, ""));

                service.refresh();

                assertTrue(service.isWorkflowEnabled("ff"));
                assertTrue(service.isWorkflowEnabled("continue"));
            }
        }
    }
}
