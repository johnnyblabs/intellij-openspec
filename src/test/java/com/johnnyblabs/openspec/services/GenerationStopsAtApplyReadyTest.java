package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where a generation run stops, against real CLI output ({@code fixtures/cli/<v>/new-change-past-apply/}): a change on
 * a schema with one artifact, {@code retro}, after the apply gate. A Propose or Fast-Forward run stops once the change
 * is ready to apply, as upstream's propose and ff do; Generate All goes on to complete it. The status the fake CLI
 * reports follows the files the run has written, so the loop reads the captured status of each step.
 */
class GenerationStopsAtApplyReadyTest {

    private static final String CAPTURED_CHANGE_DIR = "/fixture/demo-project/openspec/changes/retro-demo";

    @TempDir Path changeDir;

    private static String fixture(String version, String name) throws IOException {
        String path = "/fixtures/cli/" + version + "/new-change-past-apply/" + name;
        try (InputStream in = GenerationStopsAtApplyReadyTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** How many artifacts are on disk, in schema order: proposal, specs, design, tasks, retro. */
    private int written() throws IOException {
        int n = 0;
        if (Files.exists(changeDir.resolve("proposal.md"))) n++;
        Path specs = changeDir.resolve("specs");
        if (Files.isDirectory(specs)) {
            try (var files = Files.walk(specs)) {
                if (files.anyMatch(f -> f.toString().endsWith(".md"))) n++;
            }
        }
        if (Files.exists(changeDir.resolve("design.md"))) n++;
        if (Files.exists(changeDir.resolve("tasks.md"))) n++;
        if (Files.exists(changeDir.resolve("retro.md"))) n++;
        return n;
    }

    private List<String> run(String version, boolean untilApplyReady) throws Exception {
        ArtifactOrchestrationService orchestration = new ArtifactOrchestrationService(mock(Project.class));
        String dir = changeDir.toString().replace('\\', '/');
        orchestration.setStatusCliForTest((p, cliArgs) -> {
            List<String> args = cliArgs.requested();
            try {
                if (args.get(0).equals("status")) {
                    return new CliRunner.CliResult(0, fixture(version, "status-step" + written() + ".json"), "");
                }
                if (args.get(0).equals("instructions")) {
                    return new CliRunner.CliResult(0, fixture(version, "instructions-" + args.get(1) + ".json")
                            .replace(CAPTURED_CHANGE_DIR, dir), "");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new CliRunner.CliResult(1, "", "not fed");
        });
        List<String> generated = new ArrayList<>();
        DirectApiService api = mock(DirectApiService.class);
        when(api.generate(any(), any())).thenAnswer(inv -> {
            String id = ((com.johnnyblabs.openspec.model.ArtifactInstruction) inv.getArgument(0)).artifactId();
            generated.add(id);
            return id.equals("specs") ? "<!-- file: specs/demo/spec.md -->\n## ADDED Requirements\n" : "# " + id + "\n";
        });
        List<String> outcome = new ArrayList<>();
        orchestration.generateAllRemaining("retro-demo", api, new GenerateAllListener() {
            @Override public void onArtifactStarted(String artifactId, int index, int total) { }
            @Override public void onArtifactCompleted(String artifactId) { }
            @Override public void onAllComplete() { outcome.add("complete"); }
            @Override public void onError(String artifactId, Exception e) { outcome.add("error " + artifactId + ": " + e); }
            @Override public void onCancelled(String nextArtifactId) { outcome.add("cancelled"); }
        }, "Add a retro step", untilApplyReady);
        assertEquals(List.of("complete"), outcome);
        return generated;
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.17.0", "2.1.1"})
    void aProposeRunStopsOnceTheChangeIsReadyToApply(String version) throws Exception {
        assertEquals(List.of("proposal", "specs", "design", "tasks"), run(version, true));
        assertFalse(Files.exists(changeDir.resolve("retro.md")), "the artifact past the apply gate is left");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.17.0", "2.1.1"})
    void generateAllGoesOnToCompleteTheChange(String version) throws Exception {
        assertEquals(List.of("proposal", "specs", "design", "tasks", "retro"), run(version, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.17.0", "2.1.1"})
    void theCapturedStatusIsApplyReadyButNotComplete(String version) throws Exception {
        ChangeArtifactDag dag = com.johnnyblabs.openspec.util.CliOutputParser.parseChangeStatus(
                fixture(version, "status-step4.json"));
        assertTrue(dag.isApplyReady(), "tasks, the apply gate, is done");
        assertFalse(dag.isComplete(), "retro is still to write");
        ChangeArtifactDag done = com.johnnyblabs.openspec.util.CliOutputParser.parseChangeStatus(
                fixture(version, "status-step5.json"));
        assertTrue(done.isComplete(), "sanity: the last step is complete");
    }
}
