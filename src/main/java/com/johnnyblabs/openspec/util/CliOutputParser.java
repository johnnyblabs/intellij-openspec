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
     * on valid items (warnings, the 1.6 INFO level) are not surfaced as failures.
     *
     * <p>The verdict is derived from each item's own {@code valid} field — the CLI is
     * authoritative, and this mirrors the client's own rule
     * ({@code valid = strictMode ? errors===0 && warnings===0 : errors===0}). Deriving it
     * from the severities of the extracted issues instead would mis-read a warning-only-invalid
     * item (possible under {@code --strict}) as passing, since such an item carries no ERROR.
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

        if (root != null && !(root.has("items") && root.get("items").isJsonArray())) {
            String error = firstErrorStatus(root);
            if (error != null) {
                // An item-less error response (CLI 1.13.1+ `invalid_item`, `unknown_item`): the CLI rejected the
                // run itself, so there is no verdict to trust — never report it as a pass with zero items.
                throw new CliErrorResponseException(error);
            }
        }

        boolean allValid = true;
        if (root != null && root.has("items") && root.get("items").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("items")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                boolean valid = item.has("valid") && item.get("valid").getAsBoolean();
                if (!valid) {
                    allValid = false;
                }
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

        return new ValidationResult(allValid, issues, "cli");
    }

    /** The message of the first {@code status} entry with severity {@code error}, or {@code null}. */
    private static String firstErrorStatus(JsonObject root) {
        if (!root.has("status") || !root.get("status").isJsonArray()) {
            return null;
        }
        for (JsonElement element : root.getAsJsonArray("status")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("severity") && "error".equalsIgnoreCase(entry.get("severity").getAsString())) {
                return entry.has("message") ? entry.get("message").getAsString() : "CLI reported an error";
            }
        }
        return null;
    }

    /**
     * The CLI answered {@code validate --json} with an error response instead of a verdict. Callers fall back to
     * the built-in validator, exactly as for a run that failed to execute.
     */
    public static final class CliErrorResponseException extends RuntimeException {
        public CliErrorResponseException(String message) {
            super(message);
        }
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
