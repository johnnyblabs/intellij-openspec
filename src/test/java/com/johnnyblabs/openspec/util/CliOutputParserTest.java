package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliOutputParserTest {

    // --- parseJsonOutput ---

    @Nested
    class ParseJsonOutputTest {

        @Test
        void parsesValidItems() {
            String json = """
                    {
                      "items": [
                        {"id": "config", "type": "config", "valid": true, "issues": []},
                        {"id": "auth", "type": "spec", "valid": false, "issues": [
                          {"level": "ERROR", "message": "Missing title heading"}
                        ]}
                      ]
                    }
                    """;
            ValidationResult result = CliOutputParser.parseJsonOutput(json);
            assertFalse(result.passed());
            assertEquals(1, result.errorCount());
            assertEquals("cli", result.source());
        }

        @Test
        void passesWhenAllValid() {
            String json = """
                    {
                      "items": [
                        {"id": "config", "type": "config", "valid": true, "issues": []},
                        {"id": "auth", "type": "spec", "valid": true, "issues": []}
                      ]
                    }
                    """;
            ValidationResult result = CliOutputParser.parseJsonOutput(json);
            assertTrue(result.passed());
            assertEquals(0, result.errorCount());
        }

        @Test
        void parsesWarnings() {
            String json = """
                    {
                      "items": [
                        {"id": "auth", "type": "spec", "valid": false, "issues": [
                          {"level": "WARNING", "message": "Missing RFC keywords"}
                        ]}
                      ]
                    }
                    """;
            ValidationResult result = CliOutputParser.parseJsonOutput(json);
            // Only warnings, no errors → passed
            assertTrue(result.passed());
            assertEquals(1, result.warningCount());
        }

        @Test
        void handlesEmptyJson() {
            ValidationResult result = CliOutputParser.parseJsonOutput("{}");
            assertTrue(result.passed());
            assertEquals(0, result.issues().size());
        }
    }

    // --- parseTextOutput ---

    @Nested
    class ParseTextOutputTest {

        @Test
        void parsesDetailedError() {
            var result = new CliRunner.CliResult(1,
                    "",
                    "✗ [ERROR] openspec/specs/auth: Spec file must have a '# Title' heading\n");

            ValidationResult parsed = CliOutputParser.parseTextOutput(result);
            assertFalse(parsed.passed());
            assertEquals(1, parsed.errorCount());
            assertEquals("cli", parsed.source());
        }

        @Test
        void parsesDetailedWarning() {
            var result = new CliRunner.CliResult(1,
                    "",
                    "✗ [WARNING] openspec/config.yaml: Missing profile\n");

            ValidationResult parsed = CliOutputParser.parseTextOutput(result);
            // Exit code 1 means failure even if only warnings
            assertFalse(parsed.passed());
            assertEquals(0, parsed.errorCount());
            assertEquals(1, parsed.warningCount());
        }

        @Test
        void parsesSimpleCheckLines() {
            var result = new CliRunner.CliResult(1,
                    "Totals: 3 passed, 2 failed (5 items)",
                    "✓ spec/auth\n✓ spec/editor\n✓ config\n✗ spec/validation\n✗ change/my-feature\n");

            ValidationResult parsed = CliOutputParser.parseTextOutput(result);
            assertFalse(parsed.passed());
            assertEquals(2, parsed.errorCount());
        }

        @Test
        void passesOnSuccessfulResult() {
            var result = new CliRunner.CliResult(0,
                    "Totals: 5 passed, 0 failed (5 items)",
                    "✓ spec/auth\n✓ spec/editor\n✓ config\n");

            ValidationResult parsed = CliOutputParser.parseTextOutput(result);
            assertTrue(parsed.passed());
            assertEquals(0, parsed.errorCount());
        }

        @Test
        void handlesNullOutput() {
            var result = new CliRunner.CliResult(0, null, null);
            ValidationResult parsed = CliOutputParser.parseTextOutput(result);
            assertTrue(parsed.passed());
        }
    }

    // --- parseChangeStatus ---

    @Nested
    class ParseChangeStatusTest {

        @Test
        void parsesFullDag() {
            String json = """
                    {
                      "changeName": "my-feature",
                      "schemaName": "spec-driven",
                      "isComplete": false,
                      "applyRequires": ["proposal", "design"],
                      "artifacts": [
                        {"id": "proposal", "outputPath": "/changes/my-feature/proposal.md", "status": "done", "missingDeps": []},
                        {"id": "design", "outputPath": "/changes/my-feature/design.md", "status": "ready", "missingDeps": []},
                        {"id": "tasks", "outputPath": "/changes/my-feature/tasks.md", "status": "blocked", "missingDeps": ["design"]}
                      ]
                    }
                    """;

            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);
            assertEquals("my-feature", dag.getChangeName());
            assertEquals("spec-driven", dag.getSchemaName());
            assertFalse(dag.isComplete());
            assertEquals(2, dag.getApplyRequires().size());
            assertEquals(3, dag.getArtifacts().size());

            // Verify artifact statuses are deserialized correctly
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(1).status());
            assertEquals(ArtifactStatus.BLOCKED, dag.getArtifacts().get(2).status());
        }

        @Test
        void parsesOutputPathsAsCamelCase() {
            // CLI returns camelCase JSON — verify Gson handles it without LOWER_CASE_WITH_UNDERSCORES
            String json = """
                    {
                      "changeName": "test",
                      "schemaName": "spec-driven",
                      "isComplete": true,
                      "artifacts": [
                        {"id": "proposal", "outputPath": "/path/proposal.md", "status": "done"}
                      ]
                    }
                    """;

            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);
            assertNotNull(dag.getArtifacts().get(0).outputPath(),
                    "outputPath must not be null — Gson must parse camelCase correctly");
            assertEquals("/path/proposal.md", dag.getArtifacts().get(0).outputPath());
        }

        @Test
        void getReadyArtifacts_fromParsedDag() {
            String json = """
                    {
                      "changeName": "test",
                      "artifacts": [
                        {"id": "proposal", "status": "done"},
                        {"id": "design", "status": "ready"},
                        {"id": "specs", "status": "ready"},
                        {"id": "tasks", "status": "blocked"}
                      ]
                    }
                    """;

            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);
            assertEquals(2, dag.getReadyArtifacts().size());
        }
    }

    // --- parseArtifactInstruction ---

    @Nested
    class ParseArtifactInstructionTest {

        @Test
        void parsesFullInstruction() {
            String json = """
                    {
                      "changeName": "my-feature",
                      "artifactId": "design",
                      "changeDir": "/changes/my-feature",
                      "outputPath": "/changes/my-feature/design.md",
                      "instruction": "Write the design document",
                      "template": "# Design\\n## Approach",
                      "dependencies": [{"id": "proposal", "done": true, "path": "proposal.md", "description": "Proposal content"}],
                      "unlocks": ["tasks", "specs"]
                    }
                    """;

            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);
            assertEquals("my-feature", inst.changeName());
            assertEquals("design", inst.artifactId());
            assertEquals("/changes/my-feature/design.md", inst.outputPath());
            assertEquals("Write the design document", inst.instruction());
            assertEquals(1, inst.dependencies().size());
            assertEquals(2, inst.unlocks().size());
        }

        @Test
        void parsesMinimalInstruction() {
            String json = """
                    {
                      "changeName": "test",
                      "artifactId": "proposal",
                      "outputPath": "/path/proposal.md"
                    }
                    """;

            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);
            assertEquals("test", inst.changeName());
            assertEquals("proposal", inst.artifactId());
            assertNotNull(inst.dependencies());
            assertTrue(inst.dependencies().isEmpty());
            assertNotNull(inst.unlocks());
            assertTrue(inst.unlocks().isEmpty());
        }
    }
}
