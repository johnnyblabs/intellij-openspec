package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Exercises the <b>real</b> {@link ArtifactOrchestrationService#templateLookingArtifacts} (a mocked {@link Project} plus
 * a real {@link ScaffoldingDetectionService} reading {@code @TempDir} files). The plugin no longer changes the status
 * {@code openspec status} reports: these cases pin that the DAG is left exactly as given and that the template-looking
 * files are only named, for the chips' hint.
 */
@ExtendWith(MockitoExtension.class)
class TemplateLookingArtifactsTest {

    @Mock Project project;

    @TempDir
    Path tempDir;

    private static final String SCAFFOLDED_DESIGN = """
            # Design: my-change

            ## Approach

            <!-- Describe the technical approach -->

            ## Components Affected

            <!-- List affected components -->
            """;

    private static final String SCAFFOLDED_TASKS = """
            # Tasks: my-change

            ## Implementation Tasks

            - [ ] Task 1
            - [ ] Task 2
            - [ ] Task 3

            ## Testing Tasks

            - [ ] Write unit tests
            - [ ] Integration testing
            """;

    private static final String REAL_CONTENT = """
            ## Approach

            Use a ScaffoldingDetectionService to check file content.
            Override status in ArtifactOrchestrationService after CLI parsing.
            """;

    @Test
    void templatesAreNamedButTheirStatusIsLeftAsTheCliReported() throws IOException {
        // proposal is real, design and tasks are the plugin's old templates
        setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", SCAFFOLDED_DESIGN,
                "tasks.md", SCAFFOLDED_TASKS);
        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));
        Map<String, ArtifactStatus> before = statusById(dag);

        assertEquals(java.util.Set.of("design", "tasks"), hints(dag, "test-change"));
        assertEquals(before, statusById(dag), "the CLI's status is never changed");
        assertTrue(dag.isComplete());
    }

    @Test
    void realContentIsNotHinted() throws IOException {
        setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", REAL_CONTENT,
                "tasks.md", REAL_CONTENT);
        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));

        assertEquals(java.util.Set.of(), hints(dag, "test-change"));
    }

    @Test
    void onlyTheTemplateFileIsHinted() throws IOException {
        setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", REAL_CONTENT,
                "tasks.md", SCAFFOLDED_TASKS);
        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));

        assertEquals(java.util.Set.of("tasks"), hints(dag, "test-change"));
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "tasks").status());
    }

    @Test
    void globArtifact_skipped() throws IOException {
        setupChangeDir("test-change", "proposal.md", REAL_CONTENT);
        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE));

        assertEquals(java.util.Set.of(), hints(dag, "test-change"));
    }

    @Test
    void artifactsTheCliReportsNotDoneAreNotHinted() throws IOException {
        setupChangeDir("test-change", "proposal.md", REAL_CONTENT, "design.md", SCAFFOLDED_DESIGN);
        ChangeArtifactDag dag = createDag(false,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.READY),
                artifact("tasks", "tasks.md", ArtifactStatus.BLOCKED));

        assertEquals(java.util.Set.of(), hints(dag, "test-change"));
        assertEquals(ArtifactStatus.READY, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.BLOCKED, findArtifact(dag, "tasks").status());
    }

    /** The 1.6 and 1.7 status orderings name the same template files and leave both DAGs as parsed. */
    @Test
    void reorderInvariant_scaffoldedDag_identicalUnder16And17Order() throws IOException {
        setupChangeDir("demo-change",
                "proposal.md", REAL_CONTENT,
                "design.md", SCAFFOLDED_DESIGN,
                "tasks.md", SCAFFOLDED_TASKS);
        ChangeArtifactDag dag16 = CliOutputParser.parseChangeStatus(fixture("1.6.0/status.json"));
        ChangeArtifactDag dag17 = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json"));
        List<String> order16 = dag16.getArtifacts().stream().map(ArtifactInfo::id).toList();
        List<String> order17 = dag17.getArtifacts().stream().map(ArtifactInfo::id).toList();
        assertNotEquals(order16, order17, "guard: the 1.6 and 1.7 fixtures must actually differ in order");
        Map<String, ArtifactStatus> before16 = statusById(dag16);
        Map<String, ArtifactStatus> before17 = statusById(dag17);

        assertEquals(hints(dag16, "demo-change"), hints(dag17, "demo-change"));
        assertEquals(before16, statusById(dag16));
        assertEquals(before17, statusById(dag17));
    }

    // --- Helpers ---

    private java.util.Set<String> hints(ChangeArtifactDag dag, String changeName) {
        when(project.getBasePath()).thenReturn(tempDir.toString());
        when(project.getService(ScaffoldingDetectionService.class))
                .thenReturn(new ScaffoldingDetectionService(null));
        return new ArtifactOrchestrationService(project).templateLookingArtifacts(changeName, dag);
    }

    private void setupChangeDir(String changeName, String... fileAndContent) throws IOException {
        Path changeDir = tempDir.resolve("openspec/changes/" + changeName);
        Files.createDirectories(changeDir);
        for (int i = 0; i < fileAndContent.length; i += 2) {
            Files.writeString(changeDir.resolve(fileAndContent[i]), fileAndContent[i + 1]);
        }
    }

    private static Map<String, ArtifactStatus> statusById(ChangeArtifactDag dag) {
        Map<String, ArtifactStatus> m = new LinkedHashMap<>();
        dag.getArtifacts().forEach(a -> m.put(a.id(), a.status()));
        return m;
    }

    private static ArtifactInfo artifact(String id, String outputPath, ArtifactStatus status) {
        return new ArtifactInfo(id, outputPath, status, List.of());
    }

    private static ChangeArtifactDag createDag(boolean isComplete, ArtifactInfo... artifacts) {
        ChangeArtifactDag dag = new ChangeArtifactDag();
        dag.setChangeName("test-change");
        dag.setSchemaName("spec-driven");
        dag.setComplete(isComplete);
        dag.setArtifacts(List.of(artifacts));
        return dag;
    }

    private static ArtifactInfo findArtifact(ChangeArtifactDag dag, String id) {
        return dag.getArtifacts().stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Artifact not found: " + id));
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = TemplateLookingArtifactsTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
