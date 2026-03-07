package com.johnnyb.openspec.util;

import com.google.gson.*;
import com.johnnyb.openspec.model.*;
import com.johnnyb.openspec.validation.ValidationIssue;
import com.johnnyb.openspec.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CliOutputParser {

    // Matches JSON item structure from `openspec validate --all --json`
    private static final Pattern JSON_ITEM = Pattern.compile(
            "\"id\"\\s*:\\s*\"([^\"]+)\".*?\"type\"\\s*:\\s*\"([^\"]+)\".*?\"valid\"\\s*:\\s*(true|false).*?\"issues\"\\s*:\\s*\\[([^\\]]*)]",
            Pattern.DOTALL);
    private static final Pattern JSON_ISSUE = Pattern.compile(
            "\"level\"\\s*:\\s*\"([^\"]+)\".*?\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
            Pattern.DOTALL);

    // Matches stderr lines like "✗ spec/actions" or "✓ spec/actions"
    private static final Pattern CHECK_LINE = Pattern.compile("^[✗✓]\\s+(.+)$", Pattern.MULTILINE);

    // Matches totals line: "Totals: 0 passed, 7 failed (7 items)"
    private static final Pattern TOTALS_LINE = Pattern.compile(
            "Totals:\\s*(\\d+)\\s+passed,\\s*(\\d+)\\s+failed");

    // Matches detailed error: "✗ [ERROR] path: message"
    private static final Pattern DETAIL_ERROR = Pattern.compile(
            "✗\\s+\\[(ERROR|WARNING|INFO)]\\s*([^:]*?):\\s*(.+)", Pattern.DOTALL);

    private CliOutputParser() {
    }

    /**
     * Parses the output of `openspec validate --all --json`.
     */
    public static ValidationResult parseJsonOutput(String jsonOutput) {
        List<ValidationIssue> issues = new ArrayList<>();

        Matcher itemMatcher = JSON_ITEM.matcher(jsonOutput);
        while (itemMatcher.find()) {
            String id = itemMatcher.group(1);
            String type = itemMatcher.group(2);
            boolean valid = "true".equals(itemMatcher.group(3));
            String issuesBlock = itemMatcher.group(4);

            if (!valid && !issuesBlock.isBlank()) {
                Matcher issueMatcher = JSON_ISSUE.matcher(issuesBlock);
                while (issueMatcher.find()) {
                    String level = issueMatcher.group(1);
                    String message = issueMatcher.group(2)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"");
                    // Use just the first line of the message for display
                    String shortMessage = message.contains("\n")
                            ? message.substring(0, message.indexOf("\n")) : message;
                    ValidationIssue.Severity severity = parseSeverity(level);
                    issues.add(new ValidationIssue(severity, type + "/" + id, 0,
                            shortMessage, "cli"));
                }
            }
        }

        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "cli");
    }

    /**
     * Parses the stderr/stdout from plain `openspec validate --all`.
     * stderr contains: "- Validating...", "✗ spec/actions", "✓ spec/ok"
     * stdout contains: "Totals: N passed, N failed (N items)"
     */
    public static ValidationResult parseTextOutput(CliRunner.CliResult result) {
        List<ValidationIssue> issues = new ArrayList<>();
        String combined = (result.stderr() != null ? result.stderr() : "")
                + "\n" + (result.stdout() != null ? result.stdout() : "");

        // Check for detailed error format first: ✗ [ERROR] path: message
        Matcher detailMatcher = DETAIL_ERROR.matcher(combined);
        while (detailMatcher.find()) {
            ValidationIssue.Severity severity = parseSeverity(detailMatcher.group(1));
            String path = detailMatcher.group(2).trim();
            String message = detailMatcher.group(3).trim();
            // Take first line only
            if (message.contains("\n")) {
                message = message.substring(0, message.indexOf("\n")).trim();
            }
            issues.add(new ValidationIssue(severity, path, 0, message, "cli"));
        }

        // If no detailed errors found, parse the simple ✗ lines
        if (issues.isEmpty()) {
            Matcher checkMatcher = CHECK_LINE.matcher(combined);
            while (checkMatcher.find()) {
                String item = checkMatcher.group(1).trim();
                String line = combined.substring(checkMatcher.start(), checkMatcher.start() + 1);
                if ("✗".equals(line)) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "", 0,
                            "Validation failed: " + item, "cli"));
                }
            }
        }

        boolean passed = result.isSuccess() &&
                issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "cli");
    }

    private static ValidationIssue.Severity parseSeverity(String text) {
        if (text == null) return ValidationIssue.Severity.ERROR;
        return switch (text.toUpperCase()) {
            case "WARNING", "WARN" -> ValidationIssue.Severity.WARNING;
            case "INFO" -> ValidationIssue.Severity.INFO;
            default -> ValidationIssue.Severity.ERROR;
        };
    }

    // --- Gson-based JSON parsing for artifact orchestration ---

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ArtifactStatus.class, (JsonDeserializer<ArtifactStatus>)
                    (json, typeOfT, context) -> ArtifactStatus.fromString(json.getAsString()))
            .create();

    /**
     * Parses the output of {@code openspec status --change <name> --json}.
     */
    public static ChangeArtifactDag parseChangeStatus(String json) {
        return GSON.fromJson(json, ChangeArtifactDag.class);
    }

    /**
     * Parses the output of {@code openspec instructions <artifact> --change <name> --json}.
     */
    public static ArtifactInstruction parseArtifactInstruction(String json) {
        return GSON.fromJson(json, ArtifactInstruction.class);
    }

}
