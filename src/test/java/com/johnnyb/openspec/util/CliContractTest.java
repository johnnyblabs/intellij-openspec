package com.johnnyb.openspec.util;

import com.johnnyb.openspec.model.ArtifactInstruction;
import com.johnnyb.openspec.model.ArtifactStatus;
import com.johnnyb.openspec.model.ChangeArtifactDag;
import com.johnnyb.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests that parse real CLI JSON output captured as fixtures.
 * If the CLI output format changes, update the fixtures under
 * src/test/resources/fixtures/cli/ with fresh output and fix any failures.
 */
class CliContractTest {

    private static String loadFixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = CliContractTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    @Nested
    class StatusContract {

        @Test
        void parsesRealStatusOutput() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertNotNull(dag);
            assertEquals("ensure-ai-component-exist-in-plugin", dag.getChangeName());
            assertEquals("spec-driven", dag.getSchemaName());
            assertFalse(dag.isComplete());
        }

        @Test
        void parsesApplyRequires() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(1, dag.getApplyRequires().size());
            assertEquals("tasks", dag.getApplyRequires().get(0));
        }

        @Test
        void parsesAllArtifacts() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(4, dag.getArtifacts().size());

            // Verify each artifact has id, outputPath, and status
            dag.getArtifacts().forEach(a -> {
                assertNotNull(a.getId(), "artifact id must not be null");
                assertNotNull(a.getOutputPath(), "outputPath must not be null");
                assertNotNull(a.getStatus(), "status must not be null");
                assertNotEquals(ArtifactStatus.UNKNOWN, a.getStatus(),
                        "status '" + a.getId() + "' must deserialize to a known value");
            });
        }

        @Test
        void artifactStatusesDeserializeCorrectly() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).getStatus());   // proposal
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(1).getStatus());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).getStatus());  // specs
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(3).getStatus());   // tasks
        }

        @Test
        void getReadyArtifactsWorksOnRealData() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(1, dag.getReadyArtifacts().size());
            assertEquals("specs", dag.getReadyArtifacts().get(0).getId());
        }
    }

    @Nested
    class InstructionContract {

        @Test
        void parsesProposalWithNoDependencies() {
            String json = loadFixture("instructions-proposal.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertNotNull(inst);
            assertEquals("proposal", inst.getArtifactId());
            assertEquals("proposal.md", inst.getOutputPath());
            assertNotNull(inst.getDependencies());
            assertTrue(inst.getDependencies().isEmpty());
            assertEquals(2, inst.getUnlocks().size());
            assertTrue(inst.getUnlocks().contains("design"));
            assertTrue(inst.getUnlocks().contains("specs"));
        }

        @Test
        void parsesSpecsWithOneDependency() {
            String json = loadFixture("instructions-specs.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("specs", inst.getArtifactId());
            assertEquals(1, inst.getDependencies().size());

            ArtifactInstruction.Dependency dep = inst.getDependencies().get(0);
            assertEquals("proposal", dep.getId());
            assertTrue(dep.isDone());
            assertEquals("proposal.md", dep.getPath());
            assertNotNull(dep.getDescription());
        }

        @Test
        void parsesTasksWithMultipleDependencies() {
            String json = loadFixture("instructions-tasks.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("tasks", inst.getArtifactId());
            assertEquals(2, inst.getDependencies().size());

            // First dep: specs (not done)
            ArtifactInstruction.Dependency specsDep = inst.getDependencies().stream()
                    .filter(d -> "specs".equals(d.getId())).findFirst().orElseThrow();
            assertFalse(specsDep.isDone());
            assertEquals("specs/**/*.md", specsDep.getPath());

            // Second dep: design (done)
            ArtifactInstruction.Dependency designDep = inst.getDependencies().stream()
                    .filter(d -> "design".equals(d.getId())).findFirst().orElseThrow();
            assertTrue(designDep.isDone());
            assertEquals("design.md", designDep.getPath());
        }

        @Test
        void parsesEmptyUnlocksAsEmptyList() {
            String json = loadFixture("instructions-tasks.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertNotNull(inst.getUnlocks());
            assertTrue(inst.getUnlocks().isEmpty());
        }

        @Test
        void parsesAllStringFields() {
            String json = loadFixture("instructions-specs.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("ensure-ai-component-exist-in-plugin", inst.getChangeName());
            assertNotNull(inst.getChangeDir());
            assertNotNull(inst.getInstruction());
            assertFalse(inst.getInstruction().isEmpty());
            assertNotNull(inst.getTemplate());
            assertFalse(inst.getTemplate().isEmpty());
        }

        @Test
        void buildPromptIncludesDependencies() {
            String json = loadFixture("instructions-specs.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("Dependencies:"));
            assertTrue(prompt.contains("### proposal"));
        }
    }

    @Nested
    class ValidateContract {

        @Test
        void parsesRealValidateOutput() {
            String json = loadFixture("validate.json");
            ValidationResult result = CliOutputParser.parseJsonOutput(json);

            assertNotNull(result);
            assertFalse(result.passed(), "should fail when items have errors");
        }

        @Test
        void capturesErrorsFromInvalidItems() {
            String json = loadFixture("validate.json");
            ValidationResult result = CliOutputParser.parseJsonOutput(json);

            assertTrue(result.errorCount() > 0, "should have at least one error");
        }

        @Test
        void ignoresWarningsOnValidItems() {
            // The parser only extracts issues from items with valid:false.
            // Warnings on valid:true items (like the "validation" spec) are skipped.
            String json = loadFixture("validate.json");
            ValidationResult result = CliOutputParser.parseJsonOutput(json);

            assertEquals(0, result.warningCount(),
                    "warnings on valid items are not extracted by parseJsonOutput");
        }
    }
}
