package com.johnnyblabs.openspec.actions;

import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, platform-free transform from a {@link ValidationResult} + target label into an
 * ordered, grouped render plan. Because it holds no platform types, the grouping, ordering,
 * severity counting, and filesystem-vs-pseudo-path classification are all unit-testable on
 * structural markers; {@link OpenSpecValidateAction} walks the resulting {@link RenderPlan}
 * and prints each element through the console panel's typed print/hyperlink helpers.
 *
 * <p>The load-bearing distinction is the issue's path shape: the built-in validator emits an
 * absolute filesystem path with a real 1-based line (a link candidate), while the CLI parser
 * emits a {@code type/id} pseudo-path with line 0 (never a link). Pseudo-path groups sort
 * last and render as plain, non-clickable headers. File headers are grouping keys only — the
 * plan never carries a per-file verdict.</p>
 */
final class ValidationConsoleFormatter {

    private ValidationConsoleFormatter() {
    }

    /** One issue row beneath a file group. */
    record IssueRow(ValidationIssue.Severity severity, int line, String message, String rule) {
    }

    /**
     * A file group. {@code path} is the grouping key (the issue's file path, or a CLI
     * {@code type/id} identifier); {@code displayPath} is its shortened, {@code openspec/}-
     * anchored label; {@code resolvable} marks a filesystem path (link candidate) versus a
     * CLI pseudo-path (always plain text). This is a grouping key, not a per-file verdict.
     */
    record FileGroup(String path, String displayPath, boolean resolvable, List<IssueRow> rows) {
    }

    /** The full plan: verdict + count header lines, then the ordered file groups. */
    record RenderPlan(boolean passed, String verdictLine, String countLine, List<FileGroup> groups) {
    }

    /**
     * A single console emission the render pass performs. {@code hyperlink} records the
     * link-vs-plain decision — the actual point of this change: a resolvable file header and
     * its {@code L<line>} row tokens are hyperlink attempts (carrying {@code path}/{@code line}),
     * everything else is a text run. {@code severity} selects the content type ({@code null} =
     * neutral/normal). Emitting ops from {@link #toRenderOps} keeps that per-group/per-row
     * decision in a pure, headless-testable place; {@code OpenSpecValidateAction} is a thin
     * executor that maps each op onto the console panel's typed helpers.
     */
    record RenderOp(boolean hyperlink, String text, String path, int oneBasedLine,
                    ValidationIssue.Severity severity) {

        static RenderOp link(String text, String path, int oneBasedLine, ValidationIssue.Severity severity) {
            return new RenderOp(true, text, path, oneBasedLine, severity);
        }

        static RenderOp text(String text, ValidationIssue.Severity severity) {
            return new RenderOp(false, text, null, 0, severity);
        }
    }

    /**
     * Flattens a {@link RenderPlan} into the ordered console emission ops, encoding the
     * link-vs-plain decision per group header and per issue row: a resolvable group's header
     * and its rows' {@code L<line>} tokens become hyperlink ops; a CLI pseudo-path group's
     * header and rows (and any row with a non-positive line) become plain text ops. The FAILED
     * verdict carries an ERROR severity so it renders colored; the count line stays neutral.
     */
    static List<RenderOp> toRenderOps(RenderPlan plan) {
        List<RenderOp> ops = new ArrayList<>();
        ops.add(RenderOp.text(plan.verdictLine() + "\n",
                plan.passed() ? null : ValidationIssue.Severity.ERROR));
        ops.add(RenderOp.text(plan.countLine() + "\n\n", null));

        if (plan.groups().isEmpty()) {
            ops.add(RenderOp.text("No issues found.\n", null));
            return ops;
        }

        for (FileGroup group : plan.groups()) {
            // File header — a clickable path for resolvable groups, a plain identifier for CLI
            // pseudo-path groups. Grouping key only, never a per-file verdict.
            if (group.resolvable()) {
                int headerLine = group.rows().isEmpty() ? 0 : group.rows().get(0).line();
                ops.add(RenderOp.link(group.displayPath(), group.path(), headerLine, null));
                ops.add(RenderOp.text("\n", null));
            } else {
                ops.add(RenderOp.text(group.displayPath() + "\n", null));
            }

            for (IssueRow row : group.rows()) {
                String detail = "  " + String.format("%-7s", row.severity()) + "  " + row.message()
                        + (row.rule() != null && !row.rule().isEmpty() ? " [" + row.rule() + "]" : "")
                        + "\n";
                if (group.resolvable() && row.line() > 0) {
                    // "  L<line>  SEVERITY  message [rule]" — the L<line> token is the clickable
                    // link; the severity-colored detail carries the row's color.
                    ops.add(RenderOp.text("  ", null));
                    ops.add(RenderOp.link("L" + row.line(), group.path(), row.line(), row.severity()));
                    ops.add(RenderOp.text(detail, row.severity()));
                } else {
                    ops.add(RenderOp.text(detail, row.severity()));
                }
            }
            ops.add(RenderOp.text("\n", null));
        }
        return ops;
    }

    /**
     * Builds the render plan. {@code targetLabel} is the human target description
     * ({@code describeTarget(target)} — e.g. {@code Change `foo`} or {@code whole project}).
     */
    static RenderPlan format(ValidationResult result, String targetLabel) {
        int errors = 0, warnings = 0, infos = 0;
        for (ValidationIssue issue : result.issues()) {
            switch (issue.severity()) {
                case ERROR -> errors++;
                case WARNING -> warnings++;
                case INFO -> infos++;
            }
        }

        String verdictLine = (result.passed() ? "✓ Validation PASSED — "
                : "✗ Validation FAILED — ") + targetLabel;
        String countLine = count(errors, "error") + ", " + count(warnings, "warning")
                + ", " + count(infos, "info");

        return new RenderPlan(result.passed(), verdictLine, countLine, groupAndOrder(result.issues()));
    }

    /**
     * Groups issues by file path and orders deterministically: resolvable (filesystem-path)
     * groups first and CLI pseudo-path groups last; within each partition by severity tier
     * (error-containing → warning-only → info-only) then path ascending; within a group by
     * line ascending, ties by severity (ERROR &lt; WARNING &lt; INFO).
     */
    private static List<FileGroup> groupAndOrder(List<ValidationIssue> issues) {
        Map<String, List<ValidationIssue>> byPath = new LinkedHashMap<>();
        for (ValidationIssue issue : issues) {
            byPath.computeIfAbsent(issue.filePath() == null ? "" : issue.filePath(),
                    k -> new ArrayList<>()).add(issue);
        }

        List<FileGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<ValidationIssue>> entry : byPath.entrySet()) {
            String path = entry.getKey();
            List<ValidationIssue> groupIssues = new ArrayList<>(entry.getValue());
            groupIssues.sort(Comparator.comparingInt(ValidationIssue::line)
                    .thenComparingInt(i -> i.severity().ordinal()));
            List<IssueRow> rows = new ArrayList<>();
            for (ValidationIssue issue : groupIssues) {
                rows.add(new IssueRow(issue.severity(), issue.line(), issue.message(), issue.rule()));
            }
            groups.add(new FileGroup(path, displayPath(path), isFilesystemPath(path), rows));
        }

        groups.sort(Comparator
                .comparingInt((FileGroup g) -> g.resolvable() ? 0 : 1) // pseudo-paths last
                .thenComparingInt(ValidationConsoleFormatter::tier)     // errors → warnings → infos
                .thenComparing(FileGroup::path));                       // ties by path ascending
        return groups;
    }

    /** Severity tier of a group: 0 = contains an ERROR, 1 = warning-only, 2 = info-only. */
    private static int tier(FileGroup group) {
        boolean hasWarning = false;
        for (IssueRow row : group.rows()) {
            if (row.severity() == ValidationIssue.Severity.ERROR) {
                return 0;
            }
            if (row.severity() == ValidationIssue.Severity.WARNING) {
                hasWarning = true;
            }
        }
        return hasWarning ? 1 : 2;
    }

    /**
     * True when {@code path} is a real filesystem path (a link candidate): an absolute
     * POSIX path or a Windows drive path. CLI {@code type/id} pseudo-paths and empty paths
     * are not, so they degrade to plain text.
     */
    static boolean isFilesystemPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.startsWith("/")) {
            return true;
        }
        return path.matches("^[A-Za-z]:[\\\\/].*");
    }

    /** Shortens an absolute path to its {@code openspec/}-anchored tail; pseudo-paths pass through. */
    static String displayPath(String path) {
        if (path == null || path.isEmpty()) {
            return "(no file)";
        }
        int idx = path.indexOf("openspec/");
        return idx >= 0 ? path.substring(idx) : path;
    }

    private static String count(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }
}
