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
        void customProfileEnablesExactlyTheWorkflowsTheCliLists() {
            // Real 1.14.3 `config list --json` after `config set profile custom` + `config set workflows [...]`.
            String json = fixture("1.14.3/errors/config-list-custom.stdout");
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertTrue(service.isWorkflowEnabled("propose"));
                assertTrue(service.isWorkflowEnabled("verify"));
                assertFalse(service.isWorkflowEnabled("ff"), "not in the captured list");
                assertFalse(service.isWorkflowEnabled("sync"), "not in the captured list, though in the core fallback");
                assertEquals("custom", service.getActiveProfileName());
            }
        }

        @Test
        void defaultProfileOmitsWorkflowsSoTheCoreSetApplies() {
            // Real 1.14.3 `config list --json` on a fresh config: profile core and NO workflows key at all (the inline
            // JSON this replaced assumed one).
            String json = fixture("1.14.3/errors/config-list-default.stdout");
            assertFalse(json.contains("workflows"), "capture precondition: no workflows key");
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                for (String id : WorkflowProfileService.CURRENT_CORE) {
                    assertTrue(service.isWorkflowEnabled(id), id);
                }
                assertFalse(service.isWorkflowEnabled("ff"));
                assertFalse(service.isWorkflowEnabled("verify"));
                assertEquals("core", service.getActiveProfileName());
            }
        }

        @Test
        void textBeforeTheConfigJsonIsTolerated() {
            String json = fixture("1.14.3/errors/config-list-custom.stdout");
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, "Note: printed first\n" + json, ""));
                assertTrue(service.isWorkflowEnabled("verify"), "used to fail to parse and fall back to core");
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

                // No CLI: the current core set, six workflows including update
                assertTrue(service.isWorkflowEnabled("update"));
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
                        Set.of("propose", "explore", "apply", "update", "sync", "archive"),
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

    /** Contract against captured real {@code openspec config list --json} (1.7). */
    @Nested
    class ConfigListContract {

        @Test
        void coreConfigResolvesToTheCoreSet() {
            String json = fixture("1.7.0/config-list.json");
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertEquals("core", service.getActiveProfileName());
                Set<String> wf = service.getActiveWorkflows();
                assertEquals(Set.of("propose", "explore", "apply", "update", "sync", "archive"), wf);
                // A core profile resolves to the core set whatever list is stored; the stale-core contract test below
                // proves the stored list is ignored.
                assertTrue(wf.contains("update"));
            }
        }

        @Test
        void toleratesDefaultStoreKeyInConfigList() {
            // 1.7's global `defaultStore` surfaces in `config list --json`; the parser must ignore it
            // (it reads only `profile`/`workflows`) and never false-flag or crash. This fixture has no
            // `workflows`, so the profile parses and the workflow set is the current core set.
            String json = fixture("1.7.0/config-validation/global-config-defaultstore.json");
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));

                assertEquals("core", service.getActiveProfileName());
                assertEquals(WorkflowProfileService.CURRENT_CORE, service.getActiveWorkflows(),
                        "no workflows array → the core set; defaultStore is ignored");
            }
        }
    }

    // ---------- Resolution mirrors the CLI's getProfileWorkflows (fixtures/cli/1.15.0/config-list/) ----------

    @Nested
    class InstalledWorkflowsContract {

        /** The workflows the real CLI installed for the case: the oracle the resolution must match. */
        private Set<String> installed(String name) {
            return Set.copyOf(fixture("1.15.0/config-list/" + name + ".installed.txt").lines()
                    .map(String::strip).filter(l -> !l.isEmpty()).toList());
        }

        private Set<String> resolvedAt115(String name) {
            CliDetectionService detection = mock(CliDetectionService.class);
            when(detection.getDetectedVersion()).thenReturn("1.15.0");
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            String json = fixture("1.15.0/config-list/" + name + ".json");
            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "list", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json, ""));
                return service.getActiveWorkflows();
            }
        }

        @Test
        void aStaleCoreListIsIgnored() {
            assertTrue(fixture("1.15.0/config-list/stale-core.json").contains("\"workflows\""),
                    "capture precondition: a stored list");
            assertEquals(installed("stale-core"), resolvedAt115("stale-core"));
            assertEquals(6, installed("stale-core").size(), "the CLI installs the six core workflows");
        }

        @Test
        void aFreshConfigIsTheCoreSet() {
            assertEquals(installed("fresh"), resolvedAt115("fresh"));
            assertEquals("core", service.getActiveProfileName());
        }

        @Test
        void customArchivingPullsInSync() {
            Set<String> resolved = resolvedAt115("custom-archive-no-sync");
            assertEquals(installed("custom-archive-no-sync"), resolved);
            assertEquals(java.util.List.of("propose", "apply", "sync", "archive"), java.util.List.copyOf(resolved),
                    "sync goes before archive, as the CLI inserts it");
        }
    }

    @Nested
    class VersionBoundaries {

        private final java.util.List<String> staleCore = java.util.List.of("propose", "explore", "apply", "archive");

        @Test
        void theCoreSetFollowsTheCliVersion() {
            assertEquals(WorkflowProfileService.CORE_1_3, WorkflowProfileService.resolve("core", staleCore, "1.3.1"));
            assertEquals(Set.of("propose", "explore", "apply", "archive"), WorkflowProfileService.CORE_1_3);
            assertEquals(WorkflowProfileService.CORE_1_4, WorkflowProfileService.resolve("core", null, "1.4.0"));
            assertEquals(WorkflowProfileService.CORE_1_4, WorkflowProfileService.resolve("core", null, "1.5.0"));
            assertEquals(Set.of("propose", "explore", "apply", "sync", "archive"), WorkflowProfileService.CORE_1_4);
            for (String v : java.util.List.of("1.6.0", "1.9.0", "1.15.0", "2.0.0")) {
                assertEquals(WorkflowProfileService.CURRENT_CORE, WorkflowProfileService.resolve("core", staleCore, v), v);
            }
        }

        @Test
        void noOrAnUnknownProfileIsCore() {
            assertEquals(WorkflowProfileService.CURRENT_CORE, WorkflowProfileService.resolve(null, staleCore, "1.15.0"));
            assertEquals(WorkflowProfileService.CURRENT_CORE, WorkflowProfileService.resolve("expanded", staleCore, "1.15.0"));
        }

        @Test
        void anUnknownVersionGetsTheCurrentSet() {
            assertEquals(WorkflowProfileService.CURRENT_CORE, WorkflowProfileService.resolve("core", null, null));
            assertEquals(WorkflowProfileService.CURRENT_CORE, WorkflowProfileService.resolve("core", null, ""));
            assertEquals(WorkflowProfileService.CURRENT_CORE, WorkflowProfileService.resolve("core", null, "unknown"));
        }

        @Test
        void theCustomSyncRuleStartsAt110() {
            java.util.List<String> list = java.util.List.of("propose", "archive");
            assertEquals(java.util.List.of("propose", "archive"),
                    java.util.List.copyOf(WorkflowProfileService.resolve("custom", list, "1.9.0")));
            assertEquals(java.util.List.of("propose", "sync", "archive"),
                    java.util.List.copyOf(WorkflowProfileService.resolve("custom", list, "1.10.0")));
        }

        @Test
        void syncGoesBeforeBulkArchiveAndIsNeverDuplicated() {
            assertEquals(java.util.List.of("propose", "sync", "bulk-archive", "archive"), java.util.List.copyOf(
                    WorkflowProfileService.resolve("custom", java.util.List.of("propose", "bulk-archive", "archive"), "1.15.0")));
            assertEquals(java.util.List.of("propose", "sync", "archive"), java.util.List.copyOf(
                    WorkflowProfileService.resolve("custom", java.util.List.of("propose", "sync", "archive"), "1.15.0")));
            assertEquals(java.util.List.of("propose", "verify"), java.util.List.copyOf(
                    WorkflowProfileService.resolve("custom", java.util.List.of("propose", "verify"), "1.15.0")),
                    "no archiving, no sync");
        }

        @Test
        void anEmptyCustomListStaysEmpty() {
            assertTrue(WorkflowProfileService.resolve("custom", null, "1.15.0").isEmpty());
            assertTrue(WorkflowProfileService.resolve("custom", java.util.List.of(), "1.15.0").isEmpty());
        }
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (java.io.InputStream is = WorkflowProfileServiceTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
