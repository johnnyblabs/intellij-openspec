package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.MultiFileArtifact;
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
    class TemplateHint {

        @Test
        void scaffoldedDoneArtifact_isNamedButStaysDone() {
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

            assertEquals(java.util.Set.of("proposal"), service.templateLookingArtifacts("c", dag));
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().getFirst().status(), "named as a template, but the CLI's status stands");
            assertTrue(dag.isComplete());
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

            assertEquals(java.util.Set.of(), service.templateLookingArtifacts("c", dag));
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

            assertEquals(java.util.Set.of(), service.templateLookingArtifacts("c", dag));

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

        /**
         * Stubs the CLI for a three-artifact run (proposal, then design, then tasks), each becoming ready as the one
         * before it is written, and the run complete after tasks.
         */
        private void stubThreeArtifactRun(MockedStatic<CliRunner> cli, Path changeDir) {
            AtomicInteger callCount = new AtomicInteger(0);

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
        }

        @Test
        void aFastForwardRequestReachesEveryArtifactInTheRun() throws Exception {
            Path changeDir = tempDir.resolve("openspec/changes/c");
            Files.createDirectories(changeDir);
            List<String> generated = new ArrayList<>();
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                stubThreeArtifactRun(cli, changeDir);
                when(apiService.generate(any(), eq("add login"))).thenAnswer(inv -> {
                    generated.add(inv.<ArtifactInstruction>getArgument(0).artifactId());
                    return "content";
                });

                service.generateAllRemaining("c", apiService, mock(GenerateAllListener.class), "add login");
            }
            assertEquals(List.of("proposal", "design", "tasks"), generated, "not only the first artifact");
            verify(apiService, never()).generate(any());
        }

        @Test
        void aRunWithoutARequestUsesThePlainCall() throws Exception {
            Path changeDir = tempDir.resolve("openspec/changes/c");
            Files.createDirectories(changeDir);
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                stubThreeArtifactRun(cli, changeDir);
                when(apiService.generate(any())).thenReturn("content");

                service.generateAllRemaining("c", apiService, mock(GenerateAllListener.class), "  ");
            }
            verify(apiService, times(3)).generate(any());
            verify(apiService, never()).generate(any(), any());
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

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                stubThreeArtifactRun(cli, changeDir);

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

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (java.io.InputStream is = ArtifactOrchestrationServiceTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@link ArtifactOrchestrationService#completedDownstream} — the DONE artifacts a regeneration
     * could invalidate, which feeds the Regenerate confirmation dialog. Pinned against the real
     * captured 1.6 and 1.7 status DAGs. 1.7 adds the CLI's {@code requires} edges and reorders
     * {@code artifacts[]} to schema order; the derivation must consume the real edges (precise,
     * order-independent) on 1.7 and fall back to the positional heuristic on pre-1.7 output.
     */
    @Nested
    class CompletedDownstream {

        @Test
        void specs_downstreamIsTasksOnly_andInvariantAcrossGenerations() {
            List<ArtifactInfo> a16 = CliOutputParser.parseChangeStatus(fixture("1.6.0/status.json")).getArtifacts();
            List<ArtifactInfo> a17 = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json")).getArtifacts();

            // Guard: the two generations exercise the two code paths (edges present vs absent) and
            // genuinely differ in array order — so this can't silently become a tautology.
            assertTrue(a17.stream().anyMatch(x -> !x.requires().isEmpty()), "1.7 must carry requires edges");
            assertTrue(a16.stream().allMatch(x -> x.requires().isEmpty()), "1.6 must have no requires edges");
            assertNotEquals(a16.stream().map(ArtifactInfo::id).toList(),
                    a17.stream().map(ArtifactInfo::id).toList(), "orders must differ");

            assertEquals(List.of("tasks"),
                    ArtifactOrchestrationService.completedDownstream(a17, "specs"),
                    "1.7: only tasks requires specs");
            assertEquals(List.of("tasks"),
                    ArtifactOrchestrationService.completedDownstream(a16, "specs"),
                    "1.6 fallback: tasks is the only DONE artifact after specs");
        }

        @Test
        void specs_downstreamOn1_7_excludesUnrelatedSiblingDesign() {
            // The precise fix: design requires proposal, NOT specs — the old positional heuristic
            // over-listed it on the 1.7 order. The requires-driven derivation must not.
            List<ArtifactInfo> a17 = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json")).getArtifacts();
            assertFalse(ArtifactOrchestrationService.completedDownstream(a17, "specs").contains("design"),
                    "design does not depend on specs and must not be listed as its downstream");
        }

        @Test
        void proposal_downstreamOn1_7_isTransitiveDoneOnly() {
            // proposal is required (transitively) by specs, design, tasks — but specs is READY, so
            // only the DONE dependents surface, in array order.
            List<ArtifactInfo> a17 = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json")).getArtifacts();
            assertEquals(List.of("design", "tasks"),
                    ArtifactOrchestrationService.completedDownstream(a17, "proposal"));
        }

        @Test
        void noRequiresEdges_usesPositionalFallback() {
            // A pre-1.7-shaped DAG (no requires) must use the original list-order heuristic.
            List<ArtifactInfo> artifacts = List.of(
                    new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of()),
                    new ArtifactInfo("design", "design.md", ArtifactStatus.DONE, List.of()),
                    new ArtifactInfo("tasks", "tasks.md", ArtifactStatus.DONE, List.of()));
            assertEquals(List.of("tasks"),
                    ArtifactOrchestrationService.completedDownstream(artifacts, "design"),
                    "fallback: DONE artifacts appearing after 'design' in the list");
            assertEquals(List.of("design", "tasks"),
                    ArtifactOrchestrationService.completedDownstream(artifacts, "proposal"));
        }
    }

    @Nested
    class WriteArtifactResult {
        // Headless (no Application), so writes take the plain-file path; the glob handling is the same code either way.
        private ArtifactInstruction specs(Path changeDir) {
            return new ArtifactInstruction("c", "specs", changeDir.toString(), "specs/**/*.md", "x", null, null, null);
        }

        private String liveSpecsText() throws Exception {
            try (var in = getClass().getResourceAsStream("/fixtures/ai/claude/messages-specs-multi-file.json")) {
                assertNotNull(in);
                return DirectApiServiceTestAccess.parseClaude(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        @Test
        void globOutputWritesEachMarkedFile(@TempDir Path changeDir) throws Exception {
            List<Path> written = service.writeArtifactResult(specs(changeDir), liveSpecsText());

            Path spec = changeDir.resolve("specs/greeting/spec.md");
            assertEquals(List.of(spec), written);
            String content = Files.readString(spec);
            assertTrue(content.startsWith("# Spec Delta\n") && content.contains("### Requirement: Farewell message"), content);
            // List the directory rather than resolve("specs/**"): Windows rejects '*' in a path, so the resolve itself throws.
            try (var entries = Files.list(changeDir.resolve("specs"))) {
                assertEquals(List.of("greeting"), entries.map(p -> p.getFileName().toString()).toList(),
                        "the glob is never used as a file or directory name");
            }
        }

        @Test
        void rejectedResponseWritesNothing(@TempDir Path changeDir) {
            String response = "<!-- file: specs/ok/spec.md -->\n# Ok\n<!-- file: specs/../escape.md -->\n# Bad\n";
            assertThrows(java.io.IOException.class, () -> service.writeArtifactResult(specs(changeDir), response));
            assertFalse(Files.exists(changeDir.resolve("specs")), "a rejected response must not write its valid files either");
            assertFalse(Files.exists(changeDir.getParent().resolve("escape.md")));
        }

        @Test
        void planListsTheCapturedResponsesFilesWithoutWriting(@TempDir Path changeDir) throws Exception {
            Path spec = changeDir.resolve("specs/greeting/spec.md");

            List<ArtifactOrchestrationService.PlannedWrite> plan = service.planArtifactResult(specs(changeDir), liveSpecsText());

            List<MultiFileArtifact.FileEntry> parsed = MultiFileArtifact.parse(liveSpecsText(), "specs/**/*.md");
            assertEquals(parsed.size(), plan.size());
            assertEquals(spec, plan.getFirst().path());
            assertEquals(parsed.getFirst().content(), plan.getFirst().newText());
            assertTrue(plan.getFirst().isNew(), "no file yet, so no current text");
            assertFalse(Files.exists(changeDir.resolve("specs")), "planning writes nothing");
        }

        @Test
        void planCarriesTheCurrentTextOfExistingFilesAndNullForNewOnes(@TempDir Path changeDir) throws Exception {
            Files.createDirectories(changeDir.resolve("specs/auth"));
            Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
            String response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# Billing\n";

            List<ArtifactOrchestrationService.PlannedWrite> plan = service.planArtifactResult(specs(changeDir), response);

            assertEquals(List.of(changeDir.resolve("specs/auth/spec.md"), changeDir.resolve("specs/billing/spec.md")),
                    plan.stream().map(ArtifactOrchestrationService.PlannedWrite::path).toList());
            assertEquals("# Old auth\n", plan.get(0).currentText());
            assertFalse(plan.get(0).isNew());
            assertNull(plan.get(1).currentText());
            assertEquals("# Old auth\n", Files.readString(changeDir.resolve("specs/auth/spec.md")), "planning writes nothing");
        }

        @Test
        void aRefusedResponseFailsInPlanningWithTheSameMessage(@TempDir Path changeDir) {
            String badPath = "<!-- file: specs/ok/spec.md -->\n# Ok\n<!-- file: specs/../escape.md -->\n# Bad\n";
            String noMarkers = "# Just text\n";
            for (String response : List.of(badPath, noMarkers)) {
                java.io.IOException planned = assertThrows(java.io.IOException.class,
                        () -> service.planArtifactResult(specs(changeDir), response));
                java.io.IOException written = assertThrows(java.io.IOException.class,
                        () -> service.writeArtifactResult(specs(changeDir), response));
                assertEquals(written.getMessage(), planned.getMessage());
            }
        }

        @Test
        void singleFileOutputIsUnchanged(@TempDir Path changeDir) throws Exception {
            ArtifactInstruction design = new ArtifactInstruction("c", "design", changeDir.toString(), "design.md",
                    "x", null, null, null);
            List<Path> written = service.writeArtifactResult(design, "# Design\n<!-- file: specs/a/spec.md -->\n");
            assertEquals(List.of(changeDir.resolve("design.md")), written);
            assertEquals("# Design\n<!-- file: specs/a/spec.md -->\n", Files.readString(changeDir.resolve("design.md")),
                    "a single-file artifact is written verbatim, markers and all");
        }
    }
}
