package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests that parse real CLI JSON output captured as fixtures.
 * If the CLI output format changes, update the fixtures under
 * src/test/resources/fixtures/cli/ with fresh output and fix any failures.
 *
 * <p>Fixtures are per CLI generation: the versionless root files are frozen legacy
 * captures (see {@code fixtures/cli/README.md}), and the {@code 1.6.0/} twins are
 * asserted by the {@code ...V16} nests below. Legacy assertions stay untouched while
 * their generation remains supported.
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

    private static String fixture16(String name) {
        return loadFixture("1.6.0/" + name);
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

    /**
     * Contract for the status shape Verify's completeness gate consumes: a captured
     * status with a mix of done/ready/blocked artifacts and a populated actionContext
     * block (the pre-existing status.json fixture predates actionContext).
     */
    @Nested
    class StatusWithContextContract {

        @Test
        void parsesMixedArtifactStatuses() {
            String json = loadFixture("status-with-context.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertNotNull(dag);
            assertEquals("demo-change", dag.getChangeName());
            assertEquals("spec-driven", dag.getSchemaName());
            assertFalse(dag.isComplete());

            assertEquals(4, dag.getArtifacts().size());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());    // proposal
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(1).status());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());   // specs
            assertEquals(ArtifactStatus.BLOCKED, dag.getArtifacts().get(3).status()); // tasks
        }

        @Test
        void parsesMissingDepsOnBlockedArtifact() {
            String json = loadFixture("status-with-context.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            ArtifactInfo tasks = dag.getArtifacts().get(3);
            assertEquals("tasks", tasks.id());
            assertEquals(List.of("design", "specs"), tasks.missingDeps());
        }

        @Test
        void parsesApplyRequires() {
            String json = loadFixture("status-with-context.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(List.of("tasks"), dag.getApplyRequires());
        }

        @Test
        void parsesActionContext() {
            String json = loadFixture("status-with-context.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            ChangeArtifactDag.ActionContext ac = dag.getActionContext();
            assertNotNull(ac, "captured 1.3+ status must surface actionContext");
            assertEquals("repo-local", ac.getMode());
            assertEquals("repo", ac.getSourceOfTruth());
            assertEquals(List.of("/home/user/demo-project"), ac.getAllowedEditRoots());
            assertFalse(ac.isRequiresAffectedAreaSelection());
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

    /**
     * 1.6-generation status contract. The capture (a seeded {@code demo-change} with
     * proposal/design/tasks written, no specs delta) reproduces the legacy per-index
     * statuses while carrying the additive 1.6 keys ({@code planningHome},
     * {@code changeRoot}, {@code artifactPaths}, {@code nextSteps}) — parsing it with
     * exact-value assertions proves the parser tolerates the 1.6 envelope.
     */
    @Nested
    class StatusContractV16 {

        @Test
        void parsesRealStatusOutputWithAdditive16Keys() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture16("status.json"));

            assertNotNull(dag);
            assertEquals("demo-change", dag.getChangeName());
            assertEquals("spec-driven", dag.getSchemaName());
            assertFalse(dag.isComplete());
            assertEquals(List.of("tasks"), dag.getApplyRequires());
        }

        @Test
        void artifactStatusesDeserializeCorrectly() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture16("status.json"));

            assertEquals(4, dag.getArtifacts().size());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());   // proposal
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(1).status());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());  // specs
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(3).status());   // tasks
        }

        @Test
        void getReadyArtifactsWorksOnRealData() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture16("status.json"));

            assertEquals(1, dag.getReadyArtifacts().size());
            assertEquals("specs", dag.getReadyArtifacts().get(0).id());
        }

        @Test
        void parsesMixedStatusesAndMissingDeps() {
            ChangeArtifactDag dag =
                    CliOutputParser.parseChangeStatus(fixture16("status-with-context.json"));

            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());    // proposal
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(1).status());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());   // specs
            assertEquals(ArtifactStatus.BLOCKED, dag.getArtifacts().get(3).status()); // tasks

            ArtifactInfo tasks = dag.getArtifacts().get(3);
            assertEquals("tasks", tasks.id());
            assertEquals(List.of("design", "specs"), tasks.missingDeps());
        }

        @Test
        void parsesActionContext() {
            ChangeArtifactDag dag =
                    CliOutputParser.parseChangeStatus(fixture16("status-with-context.json"));

            ChangeArtifactDag.ActionContext ac = dag.getActionContext();
            assertNotNull(ac, "captured 1.6 status must surface actionContext");
            assertEquals("repo-local", ac.getMode());
            assertEquals("repo", ac.getSourceOfTruth());
            assertEquals(List.of("/fixture/demo-project"), ac.getAllowedEditRoots());
            assertFalse(ac.isRequiresAffectedAreaSelection());
        }

        /**
         * The apply-ready path: a captured status from a change whose proposal/design/specs/tasks
         * are all done, so {@code isComplete} is true. The other status fixtures only carry
         * {@code isComplete:false}, leaving the done rollup ({@link ChangeArtifactDag#isComplete()},
         * which drives the CHANGE_DONE tree badge) otherwise unproven against real CLI output.
         */
        @Test
        void parsesCompleteStatusWithIsCompleteTrue() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture16("status-complete.json"));

            assertNotNull(dag);
            assertEquals("demo-change", dag.getChangeName());
            assertTrue(dag.isComplete(), "a fully-complete change must report isComplete=true");

            assertEquals(4, dag.getArtifacts().size());
            dag.getArtifacts().forEach(a ->
                    assertEquals(ArtifactStatus.DONE, a.status(),
                            "every artifact in a complete change is done: " + a.id()));
            assertTrue(dag.getReadyArtifacts().isEmpty(), "nothing is merely ready when all are done");
        }
    }

    /** 1.6-generation instructions contract (staged DAG states of the same seed). */
    @Nested
    class InstructionContractV16 {

        @Test
        void parsesProposalWithNoDependencies() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-proposal.json"));

            assertNotNull(inst);
            assertEquals("proposal", inst.artifactId());
            assertEquals("proposal.md", inst.outputPath());
            assertTrue(inst.dependencies().isEmpty());
            assertEquals(List.of("design", "specs"), inst.unlocks());
        }

        @Test
        void parsesSpecsWithOneDependency() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-specs.json"));

            assertEquals("specs", inst.artifactId());
            assertEquals("demo-change", inst.changeName());
            assertEquals(1, inst.dependencies().size());

            ArtifactInstruction.Dependency dep = inst.dependencies().get(0);
            assertEquals("proposal", dep.id());
            assertTrue(dep.done());
            assertEquals("proposal.md", dep.path());
            assertNotNull(dep.description());
        }

        @Test
        void parsesTasksWithMultipleDependencies() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-tasks.json"));

            assertEquals("tasks", inst.artifactId());
            assertEquals(2, inst.dependencies().size());

            ArtifactInstruction.Dependency specsDep = inst.dependencies().stream()
                    .filter(d -> "specs".equals(d.id())).findFirst().orElseThrow();
            assertFalse(specsDep.done());
            assertEquals("specs/**/*.md", specsDep.path());

            ArtifactInstruction.Dependency designDep = inst.dependencies().stream()
                    .filter(d -> "design".equals(d.id())).findFirst().orElseThrow();
            assertTrue(designDep.done());
            assertEquals("design.md", designDep.path());
        }

        @Test
        void parsesEmptyUnlocksAsEmptyList() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-tasks.json"));

            assertNotNull(inst.unlocks());
            assertTrue(inst.unlocks().isEmpty());
        }

        @Test
        void buildPromptIncludesDependencies() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-specs.json"));

            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("Dependencies:"));
            assertTrue(prompt.contains("### proposal"));
        }
    }

    /**
     * 1.6-generation validate contract. The capture was seeded to exercise every issue
     * class 1.6 emits: a valid spec, a valid spec with a WARNING, a missing-SHALL spec
     * (the re-pathed {@code requirements[0]} ERROR with the reworded message), a
     * delta-less change (ERROR), and a valid change whose delta carries a non-canonical
     * level-3 header (the new INFO-level issue with a {@code line} field — 1.6 emits it
     * on {@code valid: true} items, so it must never surface from the parser).
     */
    @Nested
    class ValidateContractV16 {

        @Test
        void parsesRealValidateOutput() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate.json"));

            assertNotNull(result);
            assertFalse(result.passed(), "capture contains two invalid items");
            assertEquals(2, result.errorCount(),
                    "missing-SHALL spec + delta-less change are the only invalid items");
        }

        @Test
        void extractsRepathedMissingShallError() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate.json"));

            assertTrue(result.issues().stream().anyMatch(i ->
                            i.severity() == ValidationIssue.Severity.ERROR
                                    && i.message().contains("must contain SHALL or MUST")),
                    "the 1.6 requirements[0] missing-SHALL error must surface with its reworded message");
        }

        @Test
        void extractsDeltaLessChangeError() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate.json"));

            assertTrue(result.issues().stream().anyMatch(i ->
                            i.severity() == ValidationIssue.Severity.ERROR
                                    && i.message().contains("Change must have at least one delta")),
                    "the delta-less change error must surface");
        }

        @Test
        void skipsWarningAndInfoIssuesOnValidItems() {
            // The seeded capture has a WARNING on a valid spec and the new 1.6 INFO
            // (with a line field) on a valid change. parseJsonOutput only extracts
            // issues from valid:false items, so neither may appear.
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate.json"));

            assertEquals(0, result.warningCount(),
                    "warnings on valid items are not extracted by parseJsonOutput");
            assertTrue(result.issues().stream()
                            .noneMatch(i -> i.severity() == ValidationIssue.Severity.INFO),
                    "the 1.6 INFO issue rides a valid:true item and must be skipped");
        }
    }

    /**
     * Single-item validate contract — {@code openspec validate <id> --type spec|change --json}.
     * The single-item envelope ({@code {items:[{id,type,valid,issues,durationMs}], summary,
     * version, root}}) differs from the bulk {@code --all} shape: it carries top-level
     * {@code summary} and {@code root} objects and a one-element {@code items} array. The
     * Project-View scoped Validate parses this shape with the same {@link CliOutputParser#parseJsonOutput}.
     * Captures are real 1.6.0 output (isolated env, {@code root.path} sanitized to {@code /fixture}).
     */
    @Nested
    class SingleItemValidateContractV16 {

        @Test
        void parsesValidSpecEnvelope() {
            // A valid spec whose only issue is a WARNING (Purpose too brief) — valid:true,
            // so parseJsonOutput extracts nothing and the result passes.
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate-single-spec.json"));

            assertNotNull(result);
            assertTrue(result.passed(), "a valid spec's single-item envelope must parse as passed");
            assertEquals(0, result.warningCount(),
                    "the WARNING rides a valid:true item and must be skipped");
            assertTrue(result.issues().isEmpty(), "no issues extracted from a valid item");
        }

        @Test
        void parsesValidChangeEnvelope() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate-single-change.json"));

            assertNotNull(result);
            assertTrue(result.passed(), "a valid change's single-item envelope must parse as passed");
            assertTrue(result.issues().isEmpty());
        }

        @Test
        void extractsErrorFromInvalidChangeEnvelope() {
            // A delta-less change: valid:false with one ERROR issue.
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture16("validate-single-change-invalid.json"));

            assertNotNull(result);
            assertFalse(result.passed(), "an invalid change must parse as failed");
            assertEquals(1, result.errorCount(), "the single delta-missing ERROR must surface");
            assertTrue(result.issues().stream().anyMatch(i ->
                            i.severity() == ValidationIssue.Severity.ERROR
                                    && i.message().contains("at least one delta")),
                    "the no-deltas error message must surface from the single-item shape");
        }

        @Test
        void tagsIssueWithTypeAndId() {
            // The parser labels each extracted issue "<type>/<id>" — here "change/broken-change".
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture16("validate-single-change-invalid.json"));

            assertTrue(result.issues().stream()
                            .anyMatch(i -> "change/broken-change".equals(i.filePath())),
                    "single-item issue must carry its type/id path from the envelope");
        }
    }
}
