package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactOrchestrationServiceTest {

    @Mock Project project;
    @Mock ScaffoldingDetectionService scaffoldingService;

    private ArtifactOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new ArtifactOrchestrationService(project);
    }

    @Nested
    class DagParsing {

        @Test
        void parsesCompleteDagFromCliJson() {
            String json = """
                    {
                      "changeName": "test-change",
                      "schemaName": "spec-driven",
                      "isComplete": false,
                      "artifacts": [
                        {"id": "proposal", "outputPath": "proposal.md", "status": "done", "missingDeps": []},
                        {"id": "design", "outputPath": "design.md", "status": "done", "missingDeps": []},
                        {"id": "specs", "outputPath": "specs/**/*.md", "status": "ready", "missingDeps": []},
                        {"id": "tasks", "outputPath": "tasks.md", "status": "blocked", "missingDeps": ["specs"]}
                      ]
                    }
                    """;

            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertNotNull(dag);
            assertEquals(4, dag.getArtifacts().size());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(1).status());
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());
            assertEquals(ArtifactStatus.BLOCKED, dag.getArtifacts().get(3).status());
            assertEquals("proposal", dag.getArtifacts().get(0).id());
            assertEquals("tasks", dag.getArtifacts().get(3).id());
        }

        @Test
        void getArtifactStatus_callsCliAndParsesResult() {
            CliRunner.CliResult cliResult = new CliRunner.CliResult(0,
                    "{\"changeName\":\"c\",\"isComplete\":true,\"artifacts\":[" +
                            "{\"id\":\"p\",\"outputPath\":\"p.md\",\"status\":\"done\",\"missingDeps\":[]}]}",
                    "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(cliResult);
                // No scaffolding service needed since no DONE artifact with non-glob path will trigger it
                // Actually it will try — let's mock it
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);
                when(scaffoldingService.isScaffolding(anyString())).thenReturn(false);

                ChangeArtifactDag result = service.getArtifactStatus("c");

                assertNotNull(result);
                assertEquals(1, result.getArtifacts().size());
                assertEquals("p", result.getArtifacts().getFirst().id());
            }
        }

        @Test
        void cacheInvalidation_reInvokesCli() {
            CliRunner.CliResult cliResult = new CliRunner.CliResult(0,
                    "{\"changeName\":\"c\",\"isComplete\":true,\"artifacts\":[" +
                            "{\"id\":\"p\",\"outputPath\":\"p.md\",\"status\":\"done\",\"missingDeps\":[]}]}",
                    "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(cliResult);
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);
                when(scaffoldingService.isScaffolding(anyString())).thenReturn(false);

                // First call
                service.getArtifactStatus("c");
                // Invalidate
                service.invalidateCache("c");
                // Second call should re-invoke CLI
                service.getArtifactStatus("c");

                cli.verify(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")),
                        times(2));
            }
        }
    }

    @Nested
    class ScaffoldingOverride {

        @Test
        void scaffoldedDoneArtifact_overriddenToReady() {
            when(project.getBasePath()).thenReturn("/tmp/test");
            when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

            // proposal is DONE but is scaffolding
            when(scaffoldingService.isScaffolding("/tmp/test/openspec/changes/c/proposal.md")).thenReturn(true);

            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setComplete(true);
            dag.setArtifacts(List.of(
                    new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of())
            ));

            service.applyScaffoldingOverrides(dag, "c");

            assertEquals(ArtifactStatus.READY, dag.getArtifacts().getFirst().status());
            assertFalse(dag.isComplete());
        }

        @Test
        void nonScaffoldedDoneArtifact_remainsDone() {
            when(project.getBasePath()).thenReturn("/tmp/test");
            when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);
            when(scaffoldingService.isScaffolding(anyString())).thenReturn(false);

            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setComplete(true);
            dag.setArtifacts(List.of(
                    new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of())
            ));

            service.applyScaffoldingOverrides(dag, "c");

            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().getFirst().status());
            assertTrue(dag.isComplete());
        }

        @Test
        void globOutputPath_skippedByScaffoldingDetection() {
            when(project.getBasePath()).thenReturn("/tmp/test");
            when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setArtifacts(List.of(
                    new ArtifactInfo("specs", "specs/**/*.md", ArtifactStatus.DONE, List.of())
            ));

            service.applyScaffoldingOverrides(dag, "c");

            // Glob paths should not be checked for scaffolding
            verify(scaffoldingService, never()).isScaffolding(anyString());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().getFirst().status());
        }
    }

    @Nested
    class NextReadyArtifact {

        @Test
        void returnsFirstReadyArtifact() {
            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setComplete(false);
            dag.setArtifacts(List.of(
                    new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of()),
                    new ArtifactInfo("design", "design.md", ArtifactStatus.READY, List.of()),
                    new ArtifactInfo("tasks", "tasks.md", ArtifactStatus.BLOCKED, List.of("design"))
            ));

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0,
                                "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                "]}", ""));
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                ArtifactInfo next = service.getNextReadyArtifact("c");
                assertNotNull(next);
                assertEquals("design", next.id());
            }
        }

        @Test
        void returnsNull_whenAllComplete() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0,
                                "{\"changeName\":\"c\",\"isComplete\":true,\"artifacts\":[" +
                                "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"done\",\"missingDeps\":[]}" +
                                "]}", ""));
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);
                when(scaffoldingService.isScaffolding(anyString())).thenReturn(false);

                ArtifactInfo next = service.getNextReadyArtifact("c");
                assertNull(next);
            }
        }

        @Test
        void returnsNull_whenAllBlocked() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0,
                                "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}," +
                                "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                "]}", ""));
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                ArtifactInfo next = service.getNextReadyArtifact("c");
                assertNull(next);
            }
        }
    }

    @Nested
    class GenerateAllLoop {

        @Mock DirectApiService apiService;

        @TempDir
        Path tempDir;

        private ChangeArtifactDag makeDag(boolean complete, ArtifactInfo... artifacts) {
            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setComplete(complete);
            dag.setArtifacts(List.of(artifacts));
            return dag;
        }

        @Test
        void generatesInDependencyOrder_firesCallbacksCorrectly() throws Exception {
            // Track listener calls
            List<String> calls = new ArrayList<>();

            GenerateAllListener listener = new GenerateAllListener() {
                @Override public void onArtifactStarted(String id, int index, int total) {
                    calls.add("started:" + id + ":" + index + "/" + total);
                }
                @Override public void onArtifactCompleted(String id) {
                    calls.add("completed:" + id);
                }
                @Override public void onAllComplete() {
                    calls.add("allComplete");
                }
                @Override public void onError(String id, Exception e) {
                    calls.add("error:" + id);
                }
                @Override public void onCancelled(String id) {
                    calls.add("cancelled:" + id);
                }
            };

            // Set up temp change dir for file writes
            Path changeDir = tempDir.resolve("openspec/changes/c");
            Files.createDirectories(changeDir);

            AtomicInteger callCount = new AtomicInteger(0);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                when(project.getBasePath()).thenReturn(tempDir.toString());
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                // Mock status calls — evolving DAG state
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenAnswer(inv -> {
                            int n = callCount.getAndIncrement();
                            String json = switch (n) {
                                case 0 -> // Initial count call: 3 remaining
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                        "]}";
                                case 1 -> // After invalidate, loop iteration 1: proposal still ready
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                        "]}";
                                case 2 -> // After proposal done, iteration 2: design now ready
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                        "]}";
                                case 3 -> // After design done, iteration 3: tasks now ready
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"ready\",\"missingDeps\":[]}" +
                                        "]}";
                                default -> // After tasks done: all complete
                                        "{\"changeName\":\"c\",\"isComplete\":true,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"done\",\"missingDeps\":[]}" +
                                        "]}";
                            };
                            return new CliRunner.CliResult(0, json, "");
                        });

                // Mock instruction calls
                ArtifactInstruction proposalInstr = new ArtifactInstruction("c", "proposal", changeDir.toString(), "proposal.md", "write proposal", null, List.of(), List.of());
                ArtifactInstruction designInstr = new ArtifactInstruction("c", "design", changeDir.toString(), "design.md", "write design", null, List.of(), List.of());
                ArtifactInstruction tasksInstr = new ArtifactInstruction("c", "tasks", changeDir.toString(), "tasks.md", "write tasks", null, List.of(), List.of());

                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("proposal"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"proposal\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"proposal.md\",\"instruction\":\"write proposal\"}", ""));
                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("design"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"design\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"design.md\",\"instruction\":\"write design\"}", ""));
                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("tasks"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"tasks\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"tasks.md\",\"instruction\":\"write tasks\"}", ""));

                // Mock API generate
                when(apiService.generate(any())).thenReturn("generated content");

                service.generateAllRemaining("c", apiService, listener);
            }

            // Verify callback order
            assertEquals(7, calls.size());
            assertEquals("started:proposal:1/3", calls.get(0));
            assertEquals("completed:proposal", calls.get(1));
            assertEquals("started:design:2/3", calls.get(2));
            assertEquals("completed:design", calls.get(3));
            assertEquals("started:tasks:3/3", calls.get(4));
            assertEquals("completed:tasks", calls.get(5));
            assertEquals("allComplete", calls.get(6));

            // Verify files written
            assertTrue(Files.exists(tempDir.resolve("openspec/changes/c/proposal.md")));
            assertTrue(Files.exists(tempDir.resolve("openspec/changes/c/design.md")));
            assertTrue(Files.exists(tempDir.resolve("openspec/changes/c/tasks.md")));
        }

        @Test
        void stopsOnApiError_firesOnError() throws Exception {
            List<String> calls = new ArrayList<>();
            GenerateAllListener listener = new GenerateAllListener() {
                @Override public void onArtifactStarted(String id, int index, int total) {
                    calls.add("started:" + id);
                }
                @Override public void onArtifactCompleted(String id) {
                    calls.add("completed:" + id);
                }
                @Override public void onAllComplete() {
                    calls.add("allComplete");
                }
                @Override public void onError(String id, Exception e) {
                    calls.add("error:" + id + ":" + e.getMessage());
                }
                @Override public void onCancelled(String id) {
                    calls.add("cancelled:" + id);
                }
            };

            Path changeDir = tempDir.resolve("openspec/changes/c");
            Files.createDirectories(changeDir);
            AtomicInteger callCount = new AtomicInteger(0);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                when(project.getBasePath()).thenReturn(tempDir.toString());
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                // Two artifacts: proposal (ready), design (ready after proposal)
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenAnswer(inv -> {
                            int n = callCount.getAndIncrement();
                            String json = switch (n) {
                                case 0, 1 ->
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}" +
                                        "]}";
                                default ->
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"ready\",\"missingDeps\":[]}" +
                                        "]}";
                            };
                            return new CliRunner.CliResult(0, json, "");
                        });

                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("proposal"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"proposal\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"proposal.md\",\"instruction\":\"x\"}", ""));
                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("design"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"design\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"design.md\",\"instruction\":\"x\"}", ""));

                // First generate succeeds, second throws
                when(apiService.generate(any()))
                        .thenReturn("content")
                        .thenThrow(new AiApiException("API rate limit (HTTP 429)"));

                service.generateAllRemaining("c", apiService, listener);
            }

            assertTrue(calls.contains("started:proposal"));
            assertTrue(calls.contains("completed:proposal"));
            assertTrue(calls.contains("started:design"));
            assertTrue(calls.contains("error:design:API rate limit (HTTP 429)"));
            assertFalse(calls.contains("allComplete"));

            // First artifact file was written before error
            assertTrue(Files.exists(tempDir.resolve("openspec/changes/c/proposal.md")));
        }

        @Test
        void respectsCancellation_firesOnCancelled() throws Exception {
            List<String> calls = new ArrayList<>();
            GenerateAllListener listener = new GenerateAllListener() {
                @Override public void onArtifactStarted(String id, int index, int total) {
                    calls.add("started:" + id);
                }
                @Override public void onArtifactCompleted(String id) {
                    calls.add("completed:" + id);
                    // Cancel after first artifact completes
                    service.cancelGenerateAll();
                }
                @Override public void onAllComplete() {
                    calls.add("allComplete");
                }
                @Override public void onError(String id, Exception e) {
                    calls.add("error:" + id);
                }
                @Override public void onCancelled(String id) {
                    calls.add("cancelled:" + id);
                }
            };

            Path changeDir = tempDir.resolve("openspec/changes/c");
            Files.createDirectories(changeDir);
            AtomicInteger callCount = new AtomicInteger(0);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                when(project.getBasePath()).thenReturn(tempDir.toString());
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenAnswer(inv -> {
                            int n = callCount.getAndIncrement();
                            String json = n <= 1
                                    ? "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                      "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                      "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}" +
                                      "]}"
                                    : "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                      "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                      "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"ready\",\"missingDeps\":[]}" +
                                      "]}";
                            return new CliRunner.CliResult(0, json, "");
                        });

                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("proposal"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"proposal\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"proposal.md\",\"instruction\":\"x\"}", ""));

                when(apiService.generate(any())).thenReturn("content");

                service.generateAllRemaining("c", apiService, listener);
            }

            assertTrue(calls.contains("started:proposal"));
            assertTrue(calls.contains("completed:proposal"));
            assertTrue(calls.stream().anyMatch(c -> c.startsWith("cancelled:")));
            assertFalse(calls.contains("allComplete"));
            // Second artifact was never started
            assertFalse(calls.contains("started:design"));

            // First artifact file preserved
            assertTrue(Files.exists(tempDir.resolve("openspec/changes/c/proposal.md")));
        }
    }

    /**
     * JSON-escapes a filesystem path for embedding in hand-built mock CLI JSON.
     * Windows paths contain backslashes, which are invalid JSON escape openers —
     * embedding them raw corrupts the parsed changeDir (the Windows CI leg caught this).
     */
    private static String jsonEscapedPath(java.nio.file.Path p) {
        return p.toString().replace("\\", "\\\\");
    }
}
