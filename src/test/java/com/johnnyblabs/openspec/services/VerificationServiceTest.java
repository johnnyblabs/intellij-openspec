package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationFinding.Dimension;
import com.johnnyblabs.openspec.model.VerificationFinding.Severity;
import com.johnnyblabs.openspec.model.VerificationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock Project project;
    @TempDir Path tempDir;

    private VerificationService service;

    @BeforeEach
    void setUp() {
        service = new VerificationService(project);
        lenient().when(project.getBasePath()).thenReturn(tempDir.toString());
    }

    private Path createChangeDir(String name) throws IOException {
        Path changeDir = tempDir.resolve("openspec/changes/" + name);
        Files.createDirectories(changeDir);
        return changeDir;
    }

    // --- Report model tests ---

    @Nested
    class ReportModel {

        @Test
        void clean_report() {
            VerificationReport report = new VerificationReport("test");
            assertTrue(report.isClean());
            assertFalse(report.hasCritical());
            assertEquals("All clear — ready to archive", report.getSummary());
        }

        @Test
        void critical_blocks_archive() {
            VerificationReport report = new VerificationReport("test");
            report.addFinding(new VerificationFinding(Severity.CRITICAL, Dimension.COMPLETENESS, "fail"));
            assertTrue(report.hasCritical());
            assertTrue(report.getSummary().contains("NOT ready to archive"));
        }

        @Test
        void filter_by_dimension() {
            VerificationReport report = new VerificationReport("test");
            report.addFinding(new VerificationFinding(Severity.CRITICAL, Dimension.COMPLETENESS, "a"));
            report.addFinding(new VerificationFinding(Severity.WARNING, Dimension.CORRECTNESS, "b"));
            report.addFinding(new VerificationFinding(Severity.SUGGESTION, Dimension.COHERENCE, "c"));

            assertEquals(1, report.getFindings(Dimension.COMPLETENESS).size());
            assertEquals(1, report.getFindings(Dimension.CORRECTNESS).size());
            assertEquals(1, report.getFindings(Dimension.COHERENCE).size());
        }

        @Test
        void summary_counts_all_severities() {
            VerificationReport report = new VerificationReport("test");
            report.addFinding(new VerificationFinding(Severity.CRITICAL, Dimension.COMPLETENESS, "a"));
            report.addFinding(new VerificationFinding(Severity.WARNING, Dimension.CORRECTNESS, "b"));
            report.addFinding(new VerificationFinding(Severity.WARNING, Dimension.CORRECTNESS, "c"));
            report.addFinding(new VerificationFinding(Severity.SUGGESTION, Dimension.COHERENCE, "d"));

            assertEquals(1, report.countBySeverity(Severity.CRITICAL));
            assertEquals(2, report.countBySeverity(Severity.WARNING));
            assertEquals(1, report.countBySeverity(Severity.SUGGESTION));
            String summary = report.getSummary();
            assertTrue(summary.contains("1 critical"));
            assertTrue(summary.contains("2 warnings"));
            assertTrue(summary.contains("1 suggestion"));
        }

        @Test
        void finding_with_file_path() {
            VerificationFinding f = new VerificationFinding(Severity.WARNING, Dimension.COHERENCE,
                    "issue", "/path/to/file.md", 42);
            assertEquals("/path/to/file.md", f.filePath());
            assertEquals(42, f.lineNumber());
        }
    }

    // --- Completeness check tests ---

    @Nested
    class CompletenessChecks {

        @Test
        void missing_change_dir_is_critical() {
            VerificationReport report = service.verify("nonexistent-change");
            assertTrue(report.hasCritical());
            assertTrue(report.getFindings().getFirst().description().contains("not found"));
        }

        @Test
        void missing_artifacts_reported_as_warnings() throws IOException {
            createChangeDir("my-change");
            // No artifacts created — all 3 should be warned about

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> completeness = report.getFindings(Dimension.COMPLETENESS);
            assertEquals(3, completeness.size()); // proposal.md, design.md, tasks.md
            assertTrue(completeness.stream().allMatch(f -> f.severity() == Severity.WARNING));
        }

        @Test
        void present_artifacts_not_warned() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "## Why\nSome reason");
            Files.writeString(changeDir.resolve("design.md"), "## Context\nSome context");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] 1.1 Done task");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> missing = report.getFindings(Dimension.COMPLETENESS).stream()
                    .filter(f -> f.description().startsWith("Missing artifact"))
                    .toList();
            assertEquals(0, missing.size());
        }

        @Test
        void incomplete_tasks_reported_as_critical() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"),
                    "- [x] 1.1 Done\n- [ ] 1.2 Not done\n- [ ] 1.3 Also not done\n");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> taskFindings = report.getFindings(Dimension.COMPLETENESS).stream()
                    .filter(f -> f.description().contains("incomplete task"))
                    .toList();
            assertEquals(1, taskFindings.size());
            assertEquals(Severity.CRITICAL, taskFindings.getFirst().severity());
            assertTrue(taskFindings.getFirst().description().contains("2 incomplete"));
        }

        @Test
        void all_tasks_complete_no_findings() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"),
                    "- [x] 1.1 Done\n- [x] 1.2 Also done\n");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> taskFindings = report.getFindings(Dimension.COMPLETENESS).stream()
                    .filter(f -> f.description().contains("incomplete"))
                    .toList();
            assertEquals(0, taskFindings.size());
        }
    }

    // --- Correctness check tests ---

    @Nested
    class CorrectnessChecks {

        @Test
        void requirement_found_in_source_no_warning() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");

            // Delta spec with a requirement
            Path specDir = changeDir.resolve("specs/auth");
            Files.createDirectories(specDir);
            Files.writeString(specDir.resolve("spec.md"),
                    "## ADDED Requirements\n\n### Requirement: Authentication service\nThe plugin SHALL authenticate.\n\n#### Scenario: Login\n- **WHEN** user logs in\n- **THEN** authenticated\n");

            // Source file containing the keyword
            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("AuthService.java"),
                    "public class AuthService {\n    // authentication logic\n}\n");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> correctness = report.getFindings(Dimension.CORRECTNESS);
            assertEquals(0, correctness.size());
        }

        @Test
        void requirement_not_found_in_source_warns() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");

            Path specDir = changeDir.resolve("specs/billing");
            Files.createDirectories(specDir);
            Files.writeString(specDir.resolve("spec.md"),
                    "## ADDED Requirements\n\n### Requirement: Subscription billing\nThe plugin SHALL bill subscriptions.\n\n#### Scenario: Charge\n- **WHEN** month ends\n- **THEN** charge\n");

            // Source that doesn't mention billing/subscription
            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Main.java"),
                    "public class Main {\n    public static void main(String[] args) {}\n}\n");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> correctness = report.getFindings(Dimension.CORRECTNESS);
            assertTrue(correctness.size() > 0);
            assertTrue(correctness.stream().anyMatch(f ->
                    f.description().contains("Subscription billing")));
        }

        @Test
        void no_delta_specs_no_correctness_findings() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");
            // No specs/ directory

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> correctness = report.getFindings(Dimension.CORRECTNESS);
            assertEquals(0, correctness.size());
        }
    }

    // --- Coherence check tests ---

    @Nested
    class CoherenceChecks {

        @Test
        void design_with_open_questions_warns() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");
            Files.writeString(changeDir.resolve("design.md"),
                    "## Context\nSome context\n\n## Open Questions\n- Should we use REST or GraphQL?\n");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> coherence = report.getFindings(Dimension.COHERENCE);
            assertTrue(coherence.stream().anyMatch(f ->
                    f.description().contains("open questions")));
        }

        @Test
        void design_with_tbd_suggests() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");
            Files.writeString(changeDir.resolve("design.md"),
                    "## Context\nSome context\n\n## Decisions\n- Use TBD approach\n");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> coherence = report.getFindings(Dimension.COHERENCE);
            assertTrue(coherence.stream().anyMatch(f ->
                    f.severity() == Severity.SUGGESTION && f.description().contains("TBD")));
        }

        @Test
        void clean_design_no_findings() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");
            Files.writeString(changeDir.resolve("design.md"),
                    "## Context\nClean design\n\n## Decisions\nUse REST API\n");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> coherence = report.getFindings(Dimension.COHERENCE);
            assertEquals(0, coherence.size());
        }

        @Test
        void no_design_no_coherence_findings() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");
            // No design.md

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> coherence = report.getFindings(Dimension.COHERENCE);
            assertEquals(0, coherence.size());
        }
    }

    // --- End-to-end tests ---

    @Nested
    class EndToEnd {

        @Test
        void fully_complete_change_is_clean() throws IOException {
            Path changeDir = createChangeDir("clean-change");
            Files.writeString(changeDir.resolve("proposal.md"), "## Why\nGood reason");
            Files.writeString(changeDir.resolve("design.md"), "## Context\nClean design\n## Decisions\nDone");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] 1.1 Task one\n- [x] 1.2 Task two");

            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Impl.java"), "class Impl {}");

            VerificationReport report = service.verify("clean-change");
            assertTrue(report.isClean());
            assertEquals("All clear — ready to archive", report.getSummary());
        }

        @Test
        void incomplete_change_has_multiple_findings() throws IOException {
            Path changeDir = createChangeDir("incomplete-change");
            // Missing proposal.md
            Files.writeString(changeDir.resolve("design.md"), "## Open Questions\n- What API?\n");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] 1.1 Done\n- [ ] 1.2 Not done");

            VerificationReport report = service.verify("incomplete-change");
            assertFalse(report.isClean());
            assertTrue(report.hasCritical()); // incomplete task
            assertTrue(report.getFindings(Dimension.COMPLETENESS).size() >= 2); // missing proposal + incomplete task
            assertTrue(report.getFindings(Dimension.COHERENCE).size() >= 1); // open questions
        }
    }
}
