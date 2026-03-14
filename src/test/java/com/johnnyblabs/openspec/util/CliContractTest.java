package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.validation.ValidationResult;
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
                assertNotNull(a.id(), "artifact id must not be null");
                assertNotNull(a.outputPath(), "outputPath must not be null");
                assertNotNull(a.status(), "status must not be null");
                assertNotEquals(ArtifactStatus.UNKNOWN, a.status(),
                        "status '" + a.id() + "' must deserialize to a known value");
            });
        }

        @Test
        void artifactStatusesDeserializeCorrectly() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());   // proposal
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(1).status());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());  // specs
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(3).status());   // tasks
        }

        @Test
        void getReadyArtifactsWorksOnRealData() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(1, dag.getReadyArtifacts().size());
            assertEquals("specs", dag.getReadyArtifacts().get(0).id());
        }
    }

    @Nested
    class InstructionContract {

        @Test
        void parsesProposalWithNoDependencies() {
            String json = loadFixture("instructions-proposal.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertNotNull(inst);
            assertEquals("proposal", inst.artifactId());
            assertEquals("proposal.md", inst.outputPath());
            assertNotNull(inst.dependencies());
            assertTrue(inst.dependencies().isEmpty());
            assertEquals(2, inst.unlocks().size());
            assertTrue(inst.unlocks().contains("design"));
            assertTrue(inst.unlocks().contains("specs"));
        }

        @Test
        void parsesSpecsWithOneDependency() {
            String json = loadFixture("instructions-specs.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("specs", inst.artifactId());
            assertEquals(1, inst.dependencies().size());

            ArtifactInstruction.Dependency dep = inst.dependencies().get(0);
            assertEquals("proposal", dep.id());
            assertTrue(dep.done());
            assertEquals("proposal.md", dep.path());
            assertNotNull(dep.description());
        }

        @Test
        void parsesTasksWithMultipleDependencies() {
            String json = loadFixture("instructions-tasks.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("tasks", inst.artifactId());
            assertEquals(2, inst.dependencies().size());

            // First dep: specs (not done)
            ArtifactInstruction.Dependency specsDep = inst.dependencies().stream()
                    .filter(d -> "specs".equals(d.id())).findFirst().orElseThrow();
            assertFalse(specsDep.done());
            assertEquals("specs/**/*.md", specsDep.path());

            // Second dep: design (done)
            ArtifactInstruction.Dependency designDep = inst.dependencies().stream()
                    .filter(d -> "design".equals(d.id())).findFirst().orElseThrow();
            assertTrue(designDep.done());
            assertEquals("design.md", designDep.path());
        }

        @Test
        void parsesEmptyUnlocksAsEmptyList() {
            String json = loadFixture("instructions-tasks.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertNotNull(inst.unlocks());
            assertTrue(inst.unlocks().isEmpty());
        }

        @Test
        void parsesAllStringFields() {
            String json = loadFixture("instructions-specs.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("ensure-ai-component-exist-in-plugin", inst.changeName());
            assertNotNull(inst.changeDir());
            assertNotNull(inst.instruction());
            assertFalse(inst.instruction().isEmpty());
            assertNotNull(inst.template());
            assertFalse(inst.template().isEmpty());
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
