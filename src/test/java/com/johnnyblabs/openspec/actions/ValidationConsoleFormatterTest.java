package com.johnnyblabs.openspec.actions;

import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationIssue.Severity;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural unit tests for the pure {@link ValidationConsoleFormatter}. Assertions target
 * structural markers (verdict/count/group order/classification), never exact markup, so the
 * cosmetic wording can evolve without churning these tests. Being platform-free, it needs no
 * {@code BasePlatformTestCase}.
 */
class ValidationConsoleFormatterTest {

    private static ValidationIssue issue(Severity severity, String path, int line) {
        return new ValidationIssue(severity, path, line, severity + "@" + path + ":" + line, "rule");
    }

    private static ValidationResult failing(List<ValidationIssue> issues) {
        return new ValidationResult(false, issues, "merged");
    }

    // --- Header: verdict, target, counts -------------------------------------------------

    @Test
    void headerCarriesFailedVerdictTargetAndCounts() {
        ValidationResult result = failing(List.of(
                issue(Severity.ERROR, "/a/spec.md", 3),
                issue(Severity.WARNING, "/a/spec.md", 5),
                issue(Severity.WARNING, "/b/spec.md", 1),
                issue(Severity.INFO, "/b/spec.md", 2)));

        ValidationConsoleFormatter.RenderPlan plan =
                ValidationConsoleFormatter.format(result, "Change `foo`");

        assertFalse(plan.passed());
        assertTrue(plan.verdictLine().contains("FAILED"), plan.verdictLine());
        assertTrue(plan.verdictLine().contains("Change `foo`"), plan.verdictLine());
        assertTrue(plan.countLine().contains("1 error"), plan.countLine());
        assertTrue(plan.countLine().contains("2 warnings"), plan.countLine());
        assertTrue(plan.countLine().contains("1 info"), plan.countLine());
    }

    @Test
    void countLinePluralizesOnCountOfOne() {
        ValidationConsoleFormatter.RenderPlan plan = ValidationConsoleFormatter.format(
                failing(List.of(issue(Severity.ERROR, "/a/spec.md", 1))), "whole project");
        // exactly "1 error" (singular), never "1 errors"
        assertTrue(plan.countLine().matches(".*\\b1 error\\b.*"), plan.countLine());
        assertFalse(plan.countLine().contains("1 errors"), plan.countLine());
    }

    // --- Grouping & ordering -------------------------------------------------------------

    @Test
    void groupsErrorContainingFilesBeforeWarningOnlyThenByPath() {
        ValidationResult result = failing(List.of(
                issue(Severity.WARNING, "/z/warn-only.md", 4),  // warning-only tier
                issue(Severity.ERROR, "/m/has-error.md", 9),    // error tier
                issue(Severity.WARNING, "/m/has-error.md", 2),
                issue(Severity.ERROR, "/a/also-error.md", 1))); // error tier, sorts before /m

        List<ValidationConsoleFormatter.FileGroup> groups =
                ValidationConsoleFormatter.format(result, "whole project").groups();

        assertEquals(3, groups.size());
        assertEquals("/a/also-error.md", groups.get(0).path(), "error tier, path asc");
        assertEquals("/m/has-error.md", groups.get(1).path(), "error tier, path asc");
        assertEquals("/z/warn-only.md", groups.get(2).path(), "warning-only tier last");
    }

    @Test
    void withinFileOrdersByLineThenSeverity() {
        ValidationResult result = failing(List.of(
                issue(Severity.ERROR, "/a/spec.md", 9),
                issue(Severity.WARNING, "/a/spec.md", 3),
                issue(Severity.WARNING, "/a/spec.md", 3), // same line, ties broken by severity
                issue(Severity.ERROR, "/a/spec.md", 3)));

        List<ValidationConsoleFormatter.IssueRow> rows =
                ValidationConsoleFormatter.format(result, "x").groups().get(0).rows();

        assertEquals(3, rows.get(0).line());
        assertEquals(Severity.ERROR, rows.get(0).severity(), "same line: ERROR before WARNING");
        assertEquals(3, rows.get(1).line());
        assertEquals(Severity.WARNING, rows.get(1).severity());
        assertEquals(3, rows.get(2).line());
        assertEquals(9, rows.get(3).line(), "line 9 sorts after the line-3 issues");
    }

    @Test
    void cliPseudoPathGroupsSortLastAndAreNotResolvable() {
        ValidationResult result = failing(List.of(
                issue(Severity.ERROR, "spec/some-cap", 0),        // CLI pseudo-path, ERROR
                issue(Severity.WARNING, "/real/spec.md", 4)));    // filesystem, warning-only

        List<ValidationConsoleFormatter.FileGroup> groups =
                ValidationConsoleFormatter.format(result, "x").groups();

        assertEquals(2, groups.size());
        // Even though the pseudo-path group has an ERROR (higher severity tier), it sorts LAST
        // because pseudo-paths always follow resolvable groups.
        assertTrue(groups.get(0).resolvable(), "resolvable filesystem group first");
        assertEquals("/real/spec.md", groups.get(0).path());
        assertFalse(groups.get(1).resolvable(), "CLI pseudo-path group is not resolvable");
        assertEquals("spec/some-cap", groups.get(1).path());
    }

    @Test
    void classifiesFilesystemVersusPseudoPaths() {
        assertTrue(ValidationConsoleFormatter.isFilesystemPath("/abs/spec.md"));
        assertTrue(ValidationConsoleFormatter.isFilesystemPath("C:\\proj\\spec.md"));
        assertFalse(ValidationConsoleFormatter.isFilesystemPath("spec/actions"));
        assertFalse(ValidationConsoleFormatter.isFilesystemPath(""));
        assertFalse(ValidationConsoleFormatter.isFilesystemPath(null));
    }

    @Test
    void displayPathAnchorsAtOpenspecAndPassesPseudoPathsThrough() {
        assertEquals("openspec/specs/auth/spec.md",
                ValidationConsoleFormatter.displayPath("/home/u/proj/openspec/specs/auth/spec.md"));
        assertEquals("spec/actions", ValidationConsoleFormatter.displayPath("spec/actions"));
    }

    // --- Clean pass & pass-with-warnings -------------------------------------------------

    @Test
    void cleanPassHasNoGroupsAndAPassVerdict() {
        ValidationConsoleFormatter.RenderPlan plan = ValidationConsoleFormatter.format(
                new ValidationResult(true, List.of(), "built-in"), "whole project");

        assertTrue(plan.passed());
        assertTrue(plan.verdictLine().contains("PASSED"), plan.verdictLine());
        assertTrue(plan.verdictLine().contains("whole project"), plan.verdictLine());
        assertTrue(plan.groups().isEmpty(), "a clean pass lists no groups");
        assertTrue(plan.countLine().contains("0 errors"), plan.countLine());
    }

    @Test
    void passWithWarningsStillListsTheWarningsGroupedByFile() {
        ValidationResult result = new ValidationResult(true, List.of(
                issue(Severity.WARNING, "/a/spec.md", 5),
                issue(Severity.INFO, "/a/spec.md", 6)), "built-in");

        ValidationConsoleFormatter.RenderPlan plan =
                ValidationConsoleFormatter.format(result, "Spec `auth`");

        assertTrue(plan.passed());
        assertTrue(plan.verdictLine().contains("PASSED"), plan.verdictLine());
        assertEquals(1, plan.groups().size(), "warnings/infos are still enumerated on a pass");
        assertEquals(2, plan.groups().get(0).rows().size());
    }

    // --- Contract: real CLI fixture + synthesized built-in issue -------------------------

    @Test
    void resolvableBuiltInLinksWhileCliPseudoPathDegrades() throws IOException {
        // Parse a REAL captured CLI validate envelope — do not hand-author the type/id shape.
        // The invalid single-change fixture yields one ERROR whose filePath is "change/broken-change".
        ValidationResult cli = CliOutputParser.parseJsonOutput(
                loadFixture("1.6.0/validate-single-change-invalid.json"));
        assertTrue(cli.issues().stream().anyMatch(i -> "change/broken-change".equals(i.filePath())),
                "fixture must supply the CLI type/id pseudo-path issue");

        // A built-in issue carries an absolute filesystem path with a real 1-based line.
        // Deliberately WARNING-tier while the CLI pseudo-path issue is ERROR-tier, so the
        // pseudo-last ordering is pinned by the resolvable-vs-pseudo sort key ALONE: on severity
        // tier the ERROR pseudo group would otherwise sort BEFORE the warning-only built-in group.
        ValidationIssue builtIn = new ValidationIssue(Severity.WARNING,
                "/proj/openspec/specs/auth/spec.md", 12, "Purpose section is too brief", "spec-format");
        ValidationResult merged = ValidationResult.merge(
                new ValidationResult(false, List.of(builtIn), "built-in"), cli);

        List<ValidationConsoleFormatter.FileGroup> groups =
                ValidationConsoleFormatter.format(merged, "whole project").groups();

        ValidationConsoleFormatter.FileGroup abs = groups.stream()
                .filter(g -> g.path().equals("/proj/openspec/specs/auth/spec.md")).findFirst().orElseThrow();
        assertTrue(abs.resolvable(), "absolute built-in path is a link candidate");
        assertEquals(12, abs.rows().get(0).line());

        ValidationConsoleFormatter.FileGroup pseudo = groups.stream()
                .filter(g -> g.path().equals("change/broken-change")).findFirst().orElseThrow();
        assertFalse(pseudo.resolvable(), "CLI type/id path must degrade to plain text");
        assertSame(groups.get(groups.size() - 1), pseudo,
                "the CLI pseudo-path group sorts last even though its severity tier is higher");
    }

    // --- Render-decision seam: link-vs-plain per group header and per row ------------------

    @Test
    void renderOpsChooseHyperlinkForResolvableGroupsAndPlainForPseudoPaths() {
        // One resolvable ERROR group (absolute path, real line) and one CLI pseudo-path group.
        ValidationConsoleFormatter.RenderPlan plan = ValidationConsoleFormatter.format(failing(List.of(
                new ValidationIssue(Severity.ERROR, "/proj/openspec/specs/auth/spec.md", 12,
                        "Requirement must contain SHALL", "spec-format"),
                new ValidationIssue(Severity.ERROR, "change/broken-change", 0,
                        "Change must have at least one delta", "cli"))), "whole project");

        List<ValidationConsoleFormatter.RenderOp> ops = ValidationConsoleFormatter.toRenderOps(plan);

        // The resolvable file header is a hyperlink op carrying its filesystem path.
        assertTrue(ops.stream().anyMatch(o -> o.hyperlink()
                        && "/proj/openspec/specs/auth/spec.md".equals(o.path())),
                "a resolvable group's header must be a hyperlink attempt");
        // The resolvable row emits a clickable "L<line>" token at the reported 1-based line.
        assertTrue(ops.stream().anyMatch(o -> o.hyperlink() && "L12".equals(o.text())
                        && "/proj/openspec/specs/auth/spec.md".equals(o.path()) && o.oneBasedLine() == 12),
                "a resolvable row must emit an L<line> hyperlink token at its 1-based line");

        // The CLI pseudo-path group produces NO hyperlink op — header and rows are plain text,
        // so a regression that linkified (or crashed on) pseudo-paths would fail here.
        assertTrue(ops.stream().noneMatch(o -> o.hyperlink() && "change/broken-change".equals(o.path())),
                "a CLI pseudo-path must never become a hyperlink");
        assertTrue(ops.stream().anyMatch(o -> !o.hyperlink() && o.text().contains("change/broken-change")),
                "the pseudo-path identifier renders as a plain header");

        // Severity-labeled rows render with their severity content type (colored), for both groups.
        assertTrue(ops.stream().anyMatch(o -> !o.hyperlink()
                        && o.severity() == Severity.ERROR && o.text().contains("ERROR")),
                "an ERROR row must render a severity-labeled, ERROR-typed text run");
        // The verdict op is present and colored on failure.
        assertTrue(ops.stream().anyMatch(o -> o.text().contains("Validation FAILED")
                        && o.severity() == Severity.ERROR),
                "the FAILED verdict op must render in the ERROR content type");
    }

    @Test
    void renderOpsForCleanPassCarryNoHyperlinksAndANoIssuesLine() {
        ValidationConsoleFormatter.RenderPlan plan = ValidationConsoleFormatter.format(
                new ValidationResult(true, List.of(), "built-in"), "whole project");

        List<ValidationConsoleFormatter.RenderOp> ops = ValidationConsoleFormatter.toRenderOps(plan);

        assertTrue(ops.stream().noneMatch(ValidationConsoleFormatter.RenderOp::hyperlink),
                "a clean pass has nothing to link");
        assertTrue(ops.stream().anyMatch(o -> o.text().contains("No issues found")));
        assertTrue(ops.stream().anyMatch(o -> o.text().contains("PASSED") && o.severity() == null),
                "the PASSED verdict renders neutral, not error-colored");
    }

    private static String loadFixture(String name) throws IOException {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = ValidationConsoleFormatterTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
