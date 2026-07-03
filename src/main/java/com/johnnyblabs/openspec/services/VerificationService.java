package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationFinding.Dimension;
import com.johnnyblabs.openspec.model.VerificationFinding.Severity;
import com.johnnyblabs.openspec.model.VerificationReport;
import com.johnnyblabs.openspec.model.WorkflowSchemaContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class VerificationService {
    private static final Logger LOG = Logger.getInstance(VerificationService.class);

    private static final Pattern TASK_INCOMPLETE = Pattern.compile("^\\s*-\\s*\\[\\s*]", Pattern.MULTILINE);
    private static final Pattern TASK_COMPLETE = Pattern.compile("^\\s*-\\s*\\[x]", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    // `[~]` marks an in-progress task (OpenSpec 1.4). It is not-done for the "ready to archive?"
    // question, so it counts toward the total and blocks archive like `[ ]` — never dropped.
    private static final Pattern TASK_IN_PROGRESS = Pattern.compile("^\\s*-\\s*\\[~]", Pattern.MULTILINE);
    private static final Pattern REQUIREMENT_HEADER = Pattern.compile("^###\\s+Requirement:\\s+(.+)$", Pattern.MULTILINE);

    private final Project project;

    public VerificationService(Project project) {
        this.project = project;
    }

    /**
     * Runs verification on a change. Must NOT be called on EDT.
     *
     * <p>Drives off the resolved {@link WorkflowSchemaContext}: for a non-default mode
     * (e.g. {@code workspace-planning}) it explains that repo-local verification does not
     * apply and stops. For the default spec-driven repo-local case it checks completeness
     * (local, deterministic) and correctness/coherence (semantic, language-agnostic,
     * delegated to the AI bridge).
     */
    public VerificationReport verify(String changeName) {
        VerificationReport report = new VerificationReport(changeName);
        String basePath = project.getBasePath();
        if (basePath == null) return report;

        Path changeDir = Path.of(basePath, "openspec", "changes", changeName);
        if (!Files.isDirectory(changeDir)) {
            report.addFinding(new VerificationFinding(Severity.CRITICAL, Dimension.COMPLETENESS,
                    "Change directory not found: " + changeDir));
            return report;
        }

        // Mode gate — repo-local verification only applies to the default spec-driven layout.
        // Guard against any failure resolving the context (e.g. malformed status JSON): degrade
        // to running the default spec-driven checks rather than aborting Verify.
        try {
            WorkflowSchemaContextService contextService = project.getService(WorkflowSchemaContextService.class);
            if (contextService != null) {
                WorkflowSchemaContext ctx = contextService.getContext(changeName);
                if (ctx != null && ctx.isNonDefaultMode()) {
                    report.addFinding(new VerificationFinding(Severity.SUGGESTION, Dimension.COMPLETENESS,
                            "Verify skipped: this change uses '" + ctx.mode()
                                    + "' mode — repo-local verification does not apply."));
                    return report;
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Failed to resolve workflow schema context; proceeding with spec-driven checks", e);
        }

        checkCompleteness(report, changeDir);
        checkCorrectness(report, changeDir);
        checkCoherence(report, changeDir);

        return report;
    }

    private void checkCompleteness(VerificationReport report, Path changeDir) {
        // Check required artifacts exist
        for (String artifact : List.of("proposal.md", "design.md", "tasks.md")) {
            Path artifactPath = changeDir.resolve(artifact);
            if (!Files.exists(artifactPath)) {
                report.addFinding(new VerificationFinding(Severity.WARNING, Dimension.COMPLETENESS,
                        "Missing artifact: " + artifact, artifactPath.toString(), -1));
            }
        }

        // Check task completion
        Path tasksPath = changeDir.resolve("tasks.md");
        if (Files.exists(tasksPath)) {
            try {
                String content = Files.readString(tasksPath, StandardCharsets.UTF_8);
                Matcher incomplete = TASK_INCOMPLETE.matcher(content);
                Matcher complete = TASK_COMPLETE.matcher(content);
                Matcher inProgress = TASK_IN_PROGRESS.matcher(content);
                int incompleteCount = 0;
                int completeCount = 0;
                int inProgressCount = 0;
                while (incomplete.find()) incompleteCount++;
                while (complete.find()) completeCount++;
                while (inProgress.find()) inProgressCount++;

                int notDoneCount = incompleteCount + inProgressCount;
                int totalCount = completeCount + notDoneCount;
                if (notDoneCount > 0) {
                    // Keep the original "incomplete" wording when there are no in-progress tasks;
                    // surface the in-progress count distinctly when present.
                    String detail = inProgressCount > 0
                            ? notDoneCount + " task(s) not done (" + inProgressCount + " in progress) out of " + totalCount
                            : incompleteCount + " incomplete task(s) out of " + totalCount;
                    report.addFinding(new VerificationFinding(Severity.CRITICAL, Dimension.COMPLETENESS,
                            detail, tasksPath.toString(), -1));
                }
            } catch (IOException e) {
                LOG.warn("Failed to read tasks.md", e);
            }
        }
    }

    /**
     * Correctness/coherence is a <b>semantic, language-agnostic</b> check delegated to the
     * AI bridge — never a single-language code grep. When no AI provider is configured it
     * degrades to "not assessed" rather than a false pass or fail, and never blocks archive.
     */
    private void checkCorrectness(VerificationReport report, Path changeDir) {
        Path specsDir = changeDir.resolve("specs");
        if (!Files.isDirectory(specsDir)) return;

        List<String> requirementNames = new ArrayList<>();
        try (DirectoryStream<Path> domains = Files.newDirectoryStream(specsDir)) {
            for (Path domain : domains) {
                if (!Files.isDirectory(domain)) continue;
                Path specFile = domain.resolve("spec.md");
                if (!Files.exists(specFile)) continue;

                String content = Files.readString(specFile, StandardCharsets.UTF_8);
                Matcher matcher = REQUIREMENT_HEADER.matcher(content);
                while (matcher.find()) {
                    requirementNames.add(matcher.group(1).trim());
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to scan delta specs", e);
        }

        if (requirementNames.isEmpty()) return;

        DirectApiService ai = project.getService(DirectApiService.class);
        if (ai == null || !ai.isConfigured()) {
            report.addFinding(new VerificationFinding(Severity.SUGGESTION, Dimension.CORRECTNESS,
                    "Correctness/coherence not assessed (AI provider not configured)"));
            return;
        }

        try {
            // The AI call is an unbounded network round-trip — honor cancellation and show progress.
            checkCanceledIfPossible("Verifying correctness via AI…");
            String response = ai.generateRaw(buildCorrectnessPrompt(requirementNames, changeDir));
            for (String line : response.split("\\R")) {
                String trimmed = line.strip();
                if (trimmed.regionMatches(true, 0, "GAP:", 0, 4)) {
                    String detail = trimmed.substring(4).strip();
                    if (!detail.isEmpty()) {
                        report.addFinding(new VerificationFinding(Severity.WARNING, Dimension.CORRECTNESS, detail));
                    }
                }
            }
        } catch (AiApiException e) {
            report.addFinding(new VerificationFinding(Severity.SUGGESTION, Dimension.CORRECTNESS,
                    "Correctness/coherence check unavailable: " + e.getMessage()));
        }
    }

    private String buildCorrectnessPrompt(List<String> requirementNames, Path changeDir) {
        String design = readOrEmpty(changeDir.resolve("design.md"));
        String tasks = readOrEmpty(changeDir.resolve("tasks.md"));
        StringBuilder reqs = new StringBuilder();
        for (String r : requirementNames) reqs.append("- ").append(r).append('\n');

        return "You are verifying an OpenSpec change for coverage and coherence with its design. "
                + "Using only the requirements, design, and task list below, identify any requirement "
                + "that appears unaddressed by the design/tasks or incoherent with the design. "
                + "Do not assume any particular programming language.\n\n"
                + "Respond with one line per problem, each starting with \"GAP: \" followed by the "
                + "requirement name and a short reason. If everything appears addressed and coherent, "
                + "respond with exactly \"OK\".\n\n"
                + "Requirements:\n" + reqs + "\n"
                + "Design:\n" + design + "\n\n"
                + "Tasks:\n" + tasks + "\n";
    }

    private String readOrEmpty(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Honors task cancellation and updates the progress indicator before a slow step.
     * No-op outside a running IntelliJ Application (unit-test context).
     */
    private void checkCanceledIfPossible(String statusText) {
        if (ApplicationManager.getApplication() == null) return;
        ProgressManager.checkCanceled();
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) indicator.setText(statusText);
    }

    private void checkCoherence(VerificationReport report, Path changeDir) {
        Path designPath = changeDir.resolve("design.md");
        if (!Files.exists(designPath)) return;

        try {
            String design = Files.readString(designPath, StandardCharsets.UTF_8);

            // Check for unresolved open questions
            if (design.contains("## Open Questions") || design.contains("## open questions")) {
                // Extract content after Open Questions header
                int idx = design.indexOf("## Open Questions");
                if (idx < 0) idx = design.indexOf("## open questions");
                if (idx >= 0) {
                    String questionsSection = design.substring(idx);
                    // Check if there's actual content (not just the header)
                    String afterHeader = questionsSection.substring(questionsSection.indexOf('\n') + 1).trim();
                    if (!afterHeader.isEmpty() && !afterHeader.startsWith("##")) {
                        report.addFinding(new VerificationFinding(Severity.WARNING, Dimension.COHERENCE,
                                "Design has unresolved open questions",
                                designPath.toString(), -1));
                    }
                }
            }

            // Check for TBD/TODO markers in design
            if (design.contains("TBD") || design.contains("TODO")) {
                report.addFinding(new VerificationFinding(Severity.SUGGESTION, Dimension.COHERENCE,
                        "Design contains TBD/TODO markers",
                        designPath.toString(), -1));
            }
        } catch (IOException e) {
            LOG.warn("Failed to read design.md", e);
        }
    }
}
