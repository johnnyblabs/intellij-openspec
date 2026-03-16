package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationFinding.Dimension;
import com.johnnyblabs.openspec.model.VerificationFinding.Severity;
import com.johnnyblabs.openspec.model.VerificationReport;

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
    private static final Pattern REQUIREMENT_HEADER = Pattern.compile("^###\\s+Requirement:\\s+(.+)$", Pattern.MULTILINE);

    private final Project project;

    public VerificationService(Project project) {
        this.project = project;
    }

    /**
     * Runs full verification on a change. Must NOT be called on EDT.
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

        checkCompleteness(report, changeDir);
        checkCorrectness(report, changeDir, basePath);
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
                int incompleteCount = 0;
                int completeCount = 0;
                while (incomplete.find()) incompleteCount++;
                while (complete.find()) completeCount++;

                if (incompleteCount > 0) {
                    report.addFinding(new VerificationFinding(Severity.CRITICAL, Dimension.COMPLETENESS,
                            incompleteCount + " incomplete task(s) out of " + (completeCount + incompleteCount),
                            tasksPath.toString(), -1));
                }
            } catch (IOException e) {
                LOG.warn("Failed to read tasks.md", e);
            }
        }
    }

    private void checkCorrectness(VerificationReport report, Path changeDir, String basePath) {
        // Find delta specs and check if requirements are referenced in codebase
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

        // Search source code for evidence of each requirement
        Path srcDir = Path.of(basePath, "src");
        if (!Files.isDirectory(srcDir)) return;

        for (String reqName : requirementNames) {
            // Extract key words from requirement name for search
            String[] keywords = reqName.toLowerCase()
                    .replaceAll("[^a-z0-9\\s]", "")
                    .split("\\s+");
            if (keywords.length == 0) continue;

            // Use the most distinctive keyword (longest)
            String searchTerm = "";
            for (String kw : keywords) {
                if (kw.length() > searchTerm.length()) searchTerm = kw;
            }

            if (searchTerm.length() < 3) continue;

            boolean found = searchSourceTree(srcDir, searchTerm);
            if (!found) {
                report.addFinding(new VerificationFinding(Severity.WARNING, Dimension.CORRECTNESS,
                        "No codebase evidence found for requirement: " + reqName));
            }
        }
    }

    private boolean searchSourceTree(Path dir, String keyword) {
        try (var stream = Files.walk(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8).toLowerCase();
                            return content.contains(keyword);
                        } catch (IOException e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
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
