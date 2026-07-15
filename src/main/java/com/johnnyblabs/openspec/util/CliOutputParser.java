package com.johnnyblabs.openspec.util;

import com.google.gson.*;
import com.johnnyblabs.openspec.model.*;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CliOutputParser {

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
     *
     * <p>Structural JSON parsing, not regex: 1.6 issue paths like {@code requirements[0]}
     * carry a {@code ]} that breaks any bracket-delimited scan of the issues array.
     * Issues are extracted only from {@code valid: false} items; issues the CLI reports
     * on valid items (warnings, the 1.6 INFO level) never flip the result.
     */
    public static ValidationResult parseJsonOutput(String jsonOutput) {
        List<ValidationIssue> issues = new ArrayList<>();

        JsonObject root = null;
        try {
            JsonElement parsed = JsonParser.parseString(
                    CliJson.extractJsonPayload(jsonOutput == null ? "" : jsonOutput));
            if (parsed.isJsonObject()) {
                root = parsed.getAsJsonObject();
            }
        } catch (JsonSyntaxException ignored) {
            // Not JSON at all — treat as no reported items, matching the previous
            // regex behavior of finding nothing.
        }

        if (root != null && root.has("items") && root.get("items").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("items")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                boolean valid = item.has("valid") && item.get("valid").getAsBoolean();
                if (valid || !item.has("issues") || !item.get("issues").isJsonArray()) {
                    continue;
                }
                String id = item.has("id") ? item.get("id").getAsString() : "";
                String type = item.has("type") ? item.get("type").getAsString() : "";
                for (JsonElement issueElement : item.getAsJsonArray("issues")) {
                    if (!issueElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject issue = issueElement.getAsJsonObject();
                    String level = issue.has("level") ? issue.get("level").getAsString() : null;
                    String message = issue.has("message") ? issue.get("message").getAsString() : "";
                    // Use just the first line of the message for display
                    String shortMessage = message.contains("\n")
                            ? message.substring(0, message.indexOf("\n")) : message;
                    issues.add(new ValidationIssue(parseSeverity(level), type + "/" + id, 0,
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
