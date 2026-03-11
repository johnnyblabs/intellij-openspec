package com.johnnyb.openspec.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for model classes that back the OpenSpec data model.
 * Verifies constructors, defaults, and business logic.
 */
class ModelTest {

    @Nested
    class ChangeTest {

        @Test
        void constructorSetsNameAndPath() {
            Change change = new Change("my-feature", "/project/openspec/changes/my-feature");
            assertEquals("my-feature", change.getName());
            assertEquals("/project/openspec/changes/my-feature", change.getPath());
        }

        @Test
        void artifactFilesStartsEmpty() {
            Change change = new Change("test", "/path");
            assertNotNull(change.getArtifactFiles());
            assertTrue(change.getArtifactFiles().isEmpty());
        }

        @Test
        void metadataIsNullByDefault() {
            Change change = new Change("test", "/path");
            assertNull(change.getMetadata());
        }

        @Test
        void canAddArtifactFiles() {
            Change change = new Change("test", "/path");
            change.getArtifactFiles().add("proposal.md");
            change.getArtifactFiles().add("design.md");
            change.getArtifactFiles().add("tasks.md");
            assertEquals(3, change.getArtifactFiles().size());
            assertTrue(change.getArtifactFiles().contains("proposal.md"));
        }
    }

    @Nested
    class ChangeMetadataTest {

        @Test
        void defaultConstructorHasNullFields() {
            ChangeMetadata meta = new ChangeMetadata();
            assertNull(meta.getSchema());
            assertNull(meta.getStatus());
            assertNull(meta.getCreated());
        }

        @Test
        void settersAndGettersWork() {
            ChangeMetadata meta = new ChangeMetadata();
            meta.setSchema("openspec-change");
            meta.setStatus("proposed");
            meta.setCreated("2026-03-06");

            assertEquals("openspec-change", meta.getSchema());
            assertEquals("proposed", meta.getStatus());
            assertEquals("2026-03-06", meta.getCreated());
        }
    }

    @Nested
    class OpenSpecConfigTest {

        @Test
        void defaultConstructorProvidesNullsafeDefaults() {
            OpenSpecConfig config = new OpenSpecConfig();
            assertNotNull(config.getProfile(), "profile must default to empty map");
            assertTrue(config.getProfile().isEmpty());
            assertEquals("", config.getContext(), "context must default to empty string");
            assertNotNull(config.getRules(), "rules must default to empty map");
            assertTrue(config.getRules().isEmpty());
        }

        @Test
        void contextIsString_notList() {
            OpenSpecConfig config = new OpenSpecConfig();
            config.setContext("IntelliJ plugin context");
            assertEquals("IntelliJ plugin context", config.getContext());
        }

        @Test
        void rulesIsMap_notList() {
            OpenSpecConfig config = new OpenSpecConfig();
            config.setRules(Map.of("services", "SHALL be registered", "testing", "SHALL pass"));
            assertEquals(2, config.getRules().size());
            assertEquals("SHALL be registered", config.getRules().get("services"));
        }
    }

    @Nested
    class SpecFileTest {

        @Test
        void constructorSetsDomainAndPath() {
            SpecFile spec = new SpecFile("authentication", "/specs/auth/spec.md");
            assertEquals("authentication", spec.getDomain());
            assertEquals("/specs/auth/spec.md", spec.getFilePath());
        }

        @Test
        void requirementsStartEmpty() {
            SpecFile spec = new SpecFile("auth", "/path");
            assertNotNull(spec.getRequirements());
            assertTrue(spec.getRequirements().isEmpty());
        }

        @Test
        void canAddRequirements() {
            SpecFile spec = new SpecFile("auth", "/path");
            spec.addRequirement(new Requirement("Login"));
            spec.addRequirement(new Requirement("Logout"));
            assertEquals(2, spec.getRequirements().size());
        }
    }

    @Nested
    class RequirementTest {

        @Test
        void constructorSetsName() {
            Requirement req = new Requirement("Login Required");
            assertEquals("Login Required", req.getName());
            assertEquals("", req.getBody());
            assertTrue(req.getScenarios().isEmpty());
        }

        @Test
        void canSetKeyword() {
            Requirement req = new Requirement("Test");
            req.setKeyword("SHALL");
            assertEquals("SHALL", req.getKeyword());
        }

        @Test
        void canAddScenarios() {
            Requirement req = new Requirement("Test");
            req.addScenario(new Scenario("Happy path"));
            req.addScenario(new Scenario("Error case"));
            assertEquals(2, req.getScenarios().size());
        }
    }

    @Nested
    class ScenarioTest {

        @Test
        void constructorSetsName() {
            Scenario scenario = new Scenario("User logs in");
            assertEquals("User logs in", scenario.getName());
            assertTrue(scenario.getClauses().isEmpty());
        }

        @Test
        void canAddGivenWhenThenClauses() {
            Scenario scenario = new Scenario("Test");
            scenario.addClause("GIVEN a user");
            scenario.addClause("WHEN they log in");
            scenario.addClause("THEN they see dashboard");
            assertEquals(3, scenario.getClauses().size());
            assertTrue(scenario.getClauses().get(0).startsWith("GIVEN"));
            assertTrue(scenario.getClauses().get(1).startsWith("WHEN"));
            assertTrue(scenario.getClauses().get(2).startsWith("THEN"));
        }
    }

    @Nested
    class ArtifactInfoTest {

        @Test
        void defaultsAreNullsafe() {
            ArtifactInfo info = new ArtifactInfo(null, null, null, null);
            assertEquals(ArtifactStatus.UNKNOWN, info.status());
            assertNotNull(info.missingDeps());
            assertTrue(info.missingDeps().isEmpty());
        }

        @Test
        void fullConstructorWorks() {
            ArtifactInfo info = new ArtifactInfo("design", "/path/design.md",
                    ArtifactStatus.READY, List.of("proposal"));
            assertEquals("design", info.id());
            assertEquals("/path/design.md", info.outputPath());
            assertEquals(ArtifactStatus.READY, info.status());
            assertEquals(List.of("proposal"), info.missingDeps());
        }
    }

    @Nested
    class ArtifactInstructionTest {

        @Test
        void defaultsAreNullsafe() {
            ArtifactInstruction inst = new ArtifactInstruction(null, null, null, null, null, null, null, null);
            assertNotNull(inst.dependencies());
            assertTrue(inst.dependencies().isEmpty());
            assertNotNull(inst.unlocks());
            assertTrue(inst.unlocks().isEmpty());
        }

        @Test
        void buildPrompt_withInstructionOnly() {
            ArtifactInstruction inst = new ArtifactInstruction(null, null, null, null,
                    "Write the design document", null, null, null);
            assertEquals("Write the design document", inst.buildPrompt());
        }

        @Test
        void buildPrompt_withInstructionAndTemplate() {
            ArtifactInstruction inst = new ArtifactInstruction(null, null, null, null,
                    "Write the design", "# Design\n## Approach", null, null);
            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("Write the design"));
            assertTrue(prompt.contains("---"));
            assertTrue(prompt.contains("Template:"));
            assertTrue(prompt.contains("# Design"));
        }

        @Test
        void buildPrompt_withDependencies() {
            ArtifactInstruction.Dependency dep = new ArtifactInstruction.Dependency(
                    "proposal", false, "proposal.md", "Proposal content");
            ArtifactInstruction inst = new ArtifactInstruction(null, null, null, null,
                    "Write tasks", null, List.of(dep), null);
            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("Dependencies:"));
            assertTrue(prompt.contains("### proposal"));
            assertTrue(prompt.contains("Proposal content"));
        }

        @Test
        void buildPrompt_emptyIsEmpty() {
            ArtifactInstruction inst = new ArtifactInstruction(null, null, null, null,
                    null, null, null, null);
            assertEquals("", inst.buildPrompt());
        }

        @Test
        void buildPrompt_includesChangeNameHeader() {
            ArtifactInstruction inst = new ArtifactInstruction("my-change", "design", "/some/dir", "design.md",
                    "Write the design", null, null, null);
            String prompt = inst.buildPrompt();
            assertTrue(prompt.startsWith("# Change: my-change\n\n"));
            assertTrue(prompt.contains("Write the design"));
        }

        @Test
        void buildPrompt_omitsHeaderWhenChangeNameNull() {
            ArtifactInstruction inst = new ArtifactInstruction(null, "design", null, "design.md",
                    "Write the design", null, null, null);
            String prompt = inst.buildPrompt();
            assertFalse(prompt.contains("# Change:"));
            assertEquals("Write the design", prompt);
        }

        @Test
        void buildPrompt_inlinesDependencyContent(@TempDir Path tempDir) throws IOException {
            Path proposalFile = tempDir.resolve("proposal.md");
            Files.writeString(proposalFile, "# Proposal\nThis is the proposal content.");

            ArtifactInstruction.Dependency dep = new ArtifactInstruction.Dependency(
                    "proposal", true, "proposal.md", "The proposal");
            ArtifactInstruction inst = new ArtifactInstruction("test-change", "design",
                    tempDir.toString(), "design.md",
                    "Write the design", null, List.of(dep), null);

            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("```\n# Proposal\nThis is the proposal content.\n```"));
            assertFalse(prompt.contains("Path: proposal.md"));
        }

        @Test
        void buildPrompt_fallsBackToPathWhenFileMissing() {
            ArtifactInstruction.Dependency dep = new ArtifactInstruction.Dependency(
                    "proposal", true, "proposal.md", null);
            ArtifactInstruction inst = new ArtifactInstruction("test-change", "design",
                    "/nonexistent/dir", "design.md",
                    "Write the design", null, List.of(dep), null);

            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("Path: proposal.md"));
            assertFalse(prompt.contains("```"));
        }

        @Test
        void buildPrompt_showsNotYetCompletedForPendingDeps() {
            ArtifactInstruction.Dependency dep = new ArtifactInstruction.Dependency(
                    "specs", false, "specs/**/*.md", "Specification files");
            ArtifactInstruction inst = new ArtifactInstruction("test-change", "tasks",
                    "/some/dir", "tasks.md",
                    "Write tasks", null, List.of(dep), null);

            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("specs/**/*.md (not yet completed)"));
        }

        @Test
        void buildPrompt_skipsInlineWhenChangeDirNull() {
            ArtifactInstruction.Dependency dep = new ArtifactInstruction.Dependency(
                    "proposal", true, "proposal.md", "The proposal");
            ArtifactInstruction inst = new ArtifactInstruction("test-change", "design",
                    null, "design.md",
                    "Write the design", null, List.of(dep), null);

            String prompt = inst.buildPrompt();
            // changeDir is null so even though dep is done, can't read file — falls through
            assertFalse(prompt.contains("```"));
            assertTrue(prompt.contains("Path: proposal.md (not yet completed)"));
        }
    }

    @Nested
    class ChangeArtifactDagTest {

        @Test
        void defaultsAreNullsafe() {
            ChangeArtifactDag dag = new ChangeArtifactDag();
            assertNotNull(dag.getApplyRequires());
            assertTrue(dag.getApplyRequires().isEmpty());
            assertNotNull(dag.getArtifacts());
            assertTrue(dag.getArtifacts().isEmpty());
        }

        @Test
        void getReadyArtifacts_filtersCorrectly() {
            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setArtifacts(List.of(
                    new ArtifactInfo("proposal", "/p.md", ArtifactStatus.DONE, List.of()),
                    new ArtifactInfo("design", "/d.md", ArtifactStatus.READY, List.of()),
                    new ArtifactInfo("tasks", "/t.md", ArtifactStatus.BLOCKED, List.of("design")),
                    new ArtifactInfo("specs", "/s.md", ArtifactStatus.READY, List.of())
            ));

            List<ArtifactInfo> ready = dag.getReadyArtifacts();
            assertEquals(2, ready.size());
            assertTrue(ready.stream().allMatch(a -> a.status() == ArtifactStatus.READY));
            assertTrue(ready.stream().anyMatch(a -> "design".equals(a.id())));
            assertTrue(ready.stream().anyMatch(a -> "specs".equals(a.id())));
        }

        @Test
        void getReadyArtifacts_emptyWhenNoneReady() {
            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setArtifacts(List.of(
                    new ArtifactInfo("proposal", "/p.md", ArtifactStatus.DONE, List.of()),
                    new ArtifactInfo("design", "/d.md", ArtifactStatus.BLOCKED, List.of("proposal"))
            ));
            assertTrue(dag.getReadyArtifacts().isEmpty());
        }
    }
}
