package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationFinding.Dimension;
import com.johnnyblabs.openspec.model.VerificationFinding.Severity;
import com.johnnyblabs.openspec.model.VerificationReport;
import com.johnnyblabs.openspec.model.WorkflowSchemaContext;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock Project project;
    @Mock DirectApiService aiService;
    @Mock WorkflowSchemaContextService schemaContextService;
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

        @Test
        void in_progress_tasks_counted_as_not_done() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            // One done, one in-progress: the [~] must count toward not-done and the total, not vanish.
            Files.writeString(changeDir.resolve("tasks.md"),
                    "- [x] 1.1 Done\n- [~] 1.2 In progress\n");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> taskFindings = report.getFindings(Dimension.COMPLETENESS).stream()
                    .filter(f -> f.description().contains("not done"))
                    .toList();
            assertEquals(1, taskFindings.size());
            assertEquals(Severity.CRITICAL, taskFindings.getFirst().severity());
            assertTrue(taskFindings.getFirst().description().contains("1 task(s) not done"));
            assertTrue(taskFindings.getFirst().description().contains("1 in progress"));
            assertTrue(taskFindings.getFirst().description().contains("out of 2"));
        }

        @Test
        void mixed_checkbox_states_counted_correctly() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            // 2 done, 1 unstarted, 2 in-progress => 3 not done out of 5
            Files.writeString(changeDir.resolve("tasks.md"),
                    "- [x] 1.1\n- [x] 1.2\n- [ ] 1.3\n- [~] 1.4\n- [~] 1.5\n");

            VerificationReport report = service.verify("my-change");
            VerificationFinding f = report.getFindings(Dimension.COMPLETENESS).stream()
                    .filter(x -> x.description().contains("not done"))
                    .findFirst().orElseThrow();
            assertEquals(Severity.CRITICAL, f.severity());
            assertTrue(f.description().contains("3 task(s) not done"), f.description());
            assertTrue(f.description().contains("2 in progress"), f.description());
            assertTrue(f.description().contains("out of 5"), f.description());
        }

        @Test
        void in_progress_alone_blocks_archive() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            // Previously [~] was dropped, so this would have reported clean. It must now block.
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] 1.1\n- [~] 1.2\n");

            VerificationReport report = service.verify("my-change");
            assertTrue(report.hasCritical(), "an in-progress [~] task must block archive");
        }
    }

    // --- Mode gate tests ---

    @Nested
    class ModeGate {

        @Test
        void non_default_mode_explains_and_stops() throws IOException {
            Path changeDir = createChangeDir("ws-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            // An incomplete task would normally be a CRITICAL completeness finding — but the
            // mode gate must stop before any spec-driven check runs.
            Files.writeString(changeDir.resolve("tasks.md"), "- [ ] 1.1 not done");

            WorkflowSchemaContext ctx = new WorkflowSchemaContext(
                    "workspace-planning", "workspace-planning", "workspace",
                    List.of(), "1.4.0", "1.2.0", true);
            when(project.getService(WorkflowSchemaContextService.class)).thenReturn(schemaContextService);
            when(schemaContextService.getContext("ws-change")).thenReturn(ctx);

            VerificationReport report = service.verify("ws-change");

            assertFalse(report.hasCritical(), "mode gate must not run the incomplete-task check");
            assertEquals(1, report.getFindings().size());
            VerificationFinding only = report.getFindings().getFirst();
            assertEquals(Severity.SUGGESTION, only.severity());
            assertTrue(only.description().contains("workspace-planning"));
        }
    }

    // --- Correctness check tests (semantic, language-agnostic, AI-delegated) ---

    @Nested
    class CorrectnessChecks {

        private void changeWithRequirement(String name, String domain, String reqName) throws IOException {
            Path changeDir = createChangeDir(name);
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "## Context\nx");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");
            Path specDir = changeDir.resolve("specs/" + domain);
            Files.createDirectories(specDir);
            Files.writeString(specDir.resolve("spec.md"),
                    "## ADDED Requirements\n\n### Requirement: " + reqName
                            + "\nThe plugin SHALL do it.\n\n#### Scenario: S\n- **WHEN** x\n- **THEN** y\n");
        }

        @Test
        void no_ai_provider_degrades_to_not_assessed() throws IOException {
            changeWithRequirement("my-change", "auth", "Authentication service");
            lenient().when(project.getService(DirectApiService.class)).thenReturn(aiService);
            when(aiService.isConfigured()).thenReturn(false);

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> correctness = report.getFindings(Dimension.CORRECTNESS);
            assertEquals(1, correctness.size());
            assertEquals(Severity.SUGGESTION, correctness.getFirst().severity());
            assertTrue(correctness.getFirst().description().contains("not assessed"));
        }

        @Test
        void ai_ok_response_no_findings() throws Exception {
            changeWithRequirement("my-change", "auth", "Authentication service");
            lenient().when(project.getService(DirectApiService.class)).thenReturn(aiService);
            when(aiService.isConfigured()).thenReturn(true);
            when(aiService.generateRaw(anyString())).thenReturn("OK");

            VerificationReport report = service.verify("my-change");
            assertEquals(0, report.getFindings(Dimension.CORRECTNESS).size());
        }

        @Test
        void ai_gap_lines_become_warnings() throws Exception {
            changeWithRequirement("my-change", "auth", "Authentication service");
            lenient().when(project.getService(DirectApiService.class)).thenReturn(aiService);
            when(aiService.isConfigured()).thenReturn(true);
            when(aiService.generateRaw(anyString())).thenReturn(
                    "GAP: Authentication service — no token validation described\nnoise line");

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> correctness = report.getFindings(Dimension.CORRECTNESS);
            assertEquals(1, correctness.size());
            assertEquals(Severity.WARNING, correctness.getFirst().severity());
            assertTrue(correctness.getFirst().description().contains("Authentication service"));
        }

        @Test
        void ai_failure_degrades_to_suggestion() throws Exception {
            changeWithRequirement("my-change", "auth", "Authentication service");
            lenient().when(project.getService(DirectApiService.class)).thenReturn(aiService);
            when(aiService.isConfigured()).thenReturn(true);
            when(aiService.generateRaw(anyString())).thenThrow(new AiApiException("rate limited"));

            VerificationReport report = service.verify("my-change");
            List<VerificationFinding> correctness = report.getFindings(Dimension.CORRECTNESS);
            assertEquals(1, correctness.size());
            assertEquals(Severity.SUGGESTION, correctness.getFirst().severity());
            assertTrue(correctness.getFirst().description().contains("unavailable"));
        }

        @Test
        void language_agnostic_not_skewed_by_non_java_source() throws Exception {
            // A Kotlin-only project: the old .java grep would have falsely reported "no evidence";
            // the AI-delegated path is language-agnostic and never scans by extension.
            changeWithRequirement("kt-change", "auth", "Authentication service");
            Path srcDir = tempDir.resolve("src/main/kotlin");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("AuthService.kt"), "class AuthService { /* auth */ }");
            lenient().when(project.getService(DirectApiService.class)).thenReturn(aiService);
            when(aiService.isConfigured()).thenReturn(true);
            when(aiService.generateRaw(anyString())).thenReturn("OK");

            VerificationReport report = service.verify("kt-change");
            assertEquals(0, report.getFindings(Dimension.CORRECTNESS).size());
        }

        @Test
        void no_delta_specs_no_correctness_findings() throws IOException {
            Path changeDir = createChangeDir("my-change");
            Files.writeString(changeDir.resolve("proposal.md"), "x");
            Files.writeString(changeDir.resolve("design.md"), "x");
            Files.writeString(changeDir.resolve("tasks.md"), "- [x] done");
            // No specs/ directory — correctness check returns before any AI lookup.

            VerificationReport report = service.verify("my-change");
            assertEquals(0, report.getFindings(Dimension.CORRECTNESS).size());
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
