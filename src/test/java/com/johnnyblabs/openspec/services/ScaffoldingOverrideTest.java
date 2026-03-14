package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScaffoldingOverrideTest {

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
    void allScaffolded_overridesToReadyAndBlocked() throws IOException {
        // proposal is real, design and tasks are scaffolded
        Path changeDir = setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", SCAFFOLDED_DESIGN,
                "tasks.md", SCAFFOLDED_TASKS);

        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));

        ScaffoldingDetectionService detector = new ScaffoldingDetectionService(null);
        applyOverrides(dag, changeDir, detector);

        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "proposal").status());
        assertEquals(ArtifactStatus.READY, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "specs").status()); // glob skipped
        assertEquals(ArtifactStatus.BLOCKED, findArtifact(dag, "tasks").status());
        assertEquals(List.of("design"), findArtifact(dag, "tasks").missingDeps());
        assertFalse(dag.isComplete());
    }

    @Test
    void noneScaffolded_noChanges() throws IOException {
        Path changeDir = setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", REAL_CONTENT,
                "tasks.md", REAL_CONTENT);

        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));

        ScaffoldingDetectionService detector = new ScaffoldingDetectionService(null);
        applyOverrides(dag, changeDir, detector);

        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "proposal").status());
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "tasks").status());
        assertTrue(dag.isComplete());
    }

    @Test
    void someScaffolded_partialOverride() throws IOException {
        // proposal and design are real, tasks is scaffolded
        Path changeDir = setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", REAL_CONTENT,
                "tasks.md", SCAFFOLDED_TASKS);

        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));

        ScaffoldingDetectionService detector = new ScaffoldingDetectionService(null);
        applyOverrides(dag, changeDir, detector);

        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "proposal").status());
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.READY, findArtifact(dag, "tasks").status());
        assertFalse(dag.isComplete());
    }

    @Test
    void globArtifact_skipped() throws IOException {
        Path changeDir = setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT);

        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE));

        ScaffoldingDetectionService detector = new ScaffoldingDetectionService(null);
        applyOverrides(dag, changeDir, detector);

        // specs has glob pattern, should not be checked
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "specs").status());
        assertTrue(dag.isComplete());
    }

    @Test
    void alreadyNotDone_notAffected() throws IOException {
        Path changeDir = setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT);

        ChangeArtifactDag dag = createDag(false,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.READY),
                artifact("tasks", "tasks.md", ArtifactStatus.BLOCKED));

        ScaffoldingDetectionService detector = new ScaffoldingDetectionService(null);
        applyOverrides(dag, changeDir, detector);

        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "proposal").status());
        assertEquals(ArtifactStatus.READY, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.BLOCKED, findArtifact(dag, "tasks").status());
    }

    // --- Helpers ---

    /**
     * Simulates the override logic without needing a real Project instance.
     * Mirrors ArtifactOrchestrationService.applyScaffoldingOverrides but uses
     * the provided changeDir and detector directly.
     */
    private void applyOverrides(ChangeArtifactDag dag, Path changeDir,
                                ScaffoldingDetectionService detector) {
        List<ArtifactInfo> artifacts = dag.getArtifacts();
        java.util.Set<String> scaffoldedIds = new java.util.HashSet<>();

        for (ArtifactInfo artifact : artifacts) {
            if (artifact.status() != ArtifactStatus.DONE) continue;
            String outputPath = artifact.outputPath();
            if (outputPath == null || outputPath.contains("*")) continue;
            String filePath = changeDir.resolve(outputPath).toString();
            if (detector.isScaffolding(filePath)) {
                scaffoldedIds.add(artifact.id());
            }
        }

        if (scaffoldedIds.isEmpty()) return;

        java.util.List<ArtifactInfo> updatedArtifacts = new java.util.ArrayList<>();
        for (ArtifactInfo artifact : artifacts) {
            if (!scaffoldedIds.contains(artifact.id())) {
                updatedArtifacts.add(artifact);
                continue;
            }
            List<String> blockedBy = new java.util.ArrayList<>();
            for (ArtifactInfo earlier : artifacts) {
                if (earlier.id().equals(artifact.id())) break;
                if (scaffoldedIds.contains(earlier.id())) {
                    blockedBy.add(earlier.id());
                }
            }
            if (blockedBy.isEmpty()) {
                updatedArtifacts.add(new ArtifactInfo(artifact.id(), artifact.outputPath(), ArtifactStatus.READY, List.of()));
            } else {
                updatedArtifacts.add(new ArtifactInfo(artifact.id(), artifact.outputPath(), ArtifactStatus.BLOCKED, blockedBy));
            }
        }
        dag.setArtifacts(updatedArtifacts);

        boolean allDone = updatedArtifacts.stream()
                .allMatch(a -> a.status() == ArtifactStatus.DONE);
        dag.setComplete(allDone);
    }

    private Path setupChangeDir(String changeName, String... fileAndContent) throws IOException {
        Path changeDir = tempDir.resolve("openspec/changes/" + changeName);
        Files.createDirectories(changeDir);
        for (int i = 0; i < fileAndContent.length; i += 2) {
            Files.writeString(changeDir.resolve(fileAndContent[i]), fileAndContent[i + 1]);
        }
        return changeDir;
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
}
