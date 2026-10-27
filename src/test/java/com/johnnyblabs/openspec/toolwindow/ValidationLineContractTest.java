package com.johnnyblabs.openspec.toolwindow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.model.ChangeDeltaModel;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Change Deltas pane's validation line and operation legend. The CLI line is read from real
 * {@code validate <change> --type change --json} runs captured from CLI 1.15.0 (with their exit codes); the wording
 * itself is checked over hand-built {@link ValidationResult}s, an internal type.
 */
class ValidationLineContractTest {

    private static String fixture(String path) {
        try (InputStream is = ValidationLineContractTest.class.getResourceAsStream("/fixtures/cli/" + path)) {
            assertNotNull(is, "missing fixture " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    /** The captured run of {@code validate} for {@code name} as the CLI result the pane gets. */
    private static CliRunner.CliResult run(String name) {
        String base = "1.15.0/validate-change/" + name;
        return new CliRunner.CliResult(Integer.parseInt(fixture(base + ".exit").strip()), fixture(base + ".json"), "");
    }

    private static String line(String name) {
        return SpecPreviewRenderer.cliValidationLine("add-auth", run(name));
    }

    private static String text(String line) {
        return line.replaceAll("<[^>]+>", "");
    }

    @Nested
    class CapturedVerdicts {

        @Test
        void aValidChangePasses() {
            String line = line("valid-change");
            assertEquals("Validate: passed · Show issues", text(line));
            assertTrue(line.contains("href=\"openspec-validate:add-auth\""), line);
        }

        @Test
        void anErrorIsCountedInTheSingular() {
            assertEquals("Validate: 1 error, 0 warnings · Show issues", text(line("error-change")));
        }

        @Test
        void errorsAndWarningsAreBothCounted() {
            assertEquals("Validate: 1 error, 1 warning · Show issues", text(line("error-and-warning-change")));
        }

        @Test
        void aChangeWithNoDeltasFails() {
            assertEquals("Validate: 1 error, 0 warnings · Show issues", text(line("no-deltas-change")));
        }

        @Test
        void aWarningOnAValidChangeReadsAsAPlainPassLikeValidate() {
            // The capture really carries a warning on a valid item...
            JsonObject item = JsonParser.parseString(fixture("1.15.0/validate-change/warning-change.json"))
                    .getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
            assertTrue(item.get("valid").getAsBoolean());
            assertEquals("WARNING", item.getAsJsonArray("issues").get(0).getAsJsonObject().get("level").getAsString());
            // ...which Validate's parser drops for a valid item. If that ever changes, the line's wording must be revisited.
            ValidationResult parsed = CliOutputParser.parseJsonOutput(run("warning-change"));
            assertTrue(parsed.passed());
            assertTrue(parsed.issues().isEmpty(), parsed.issues().toString());
            assertEquals("Validate: passed · Show issues", text(line("warning-change")));
        }

        @Test
        void anErrorResponseIsNoVerdictSoNoLine() {
            assertNull(line("invalid-name-envelope"));
        }

        @Test
        void unreadableOutputIsNoVerdict() {
            assertNull(SpecPreviewRenderer.cliValidationLine("add-auth", new CliRunner.CliResult(1, "", "boom")));
            assertNull(SpecPreviewRenderer.cliValidationLine("add-auth", new CliRunner.CliResult(0, "{\"items\":", "")));
        }
    }

    @Nested
    class Wording {

        private ValidationIssue issue(ValidationIssue.Severity severity) {
            return new ValidationIssue(severity, "x", 0, "m", "r");
        }

        @Test
        void theBuiltInCheckCanPassWithWarningsAndHasNoLinkWithoutAName() {
            ValidationResult result = new ValidationResult(true,
                    List.of(issue(ValidationIssue.Severity.WARNING), issue(ValidationIssue.Severity.WARNING)), "built-in");
            String line = SpecPreviewRenderer.validationLine(SpecPreviewRenderer.BUILT_IN_VALIDATION_LABEL, result, null);
            assertEquals("Built-in check: passed, 2 warnings", text(line));
            assertFalse(line.contains("openspec-validate:"), line);
        }

        @Test
        void pluralsAndTheChangeNameIsEscaped() {
            ValidationResult result = new ValidationResult(false, List.of(issue(ValidationIssue.Severity.ERROR),
                    issue(ValidationIssue.Severity.ERROR), issue(ValidationIssue.Severity.WARNING)), "cli");
            String line = SpecPreviewRenderer.validationLine("Validate", result, "a\"b<c");
            assertTrue(text(line).startsWith("Validate: 2 errors, 1 warning"), line);
            assertTrue(line.contains("href=\"openspec-validate:a&quot;b&lt;c\""), line);
        }

        @Test
        void theHrefNamesItsChange() {
            assertEquals("add-auth", SpecPreviewRenderer.validateChangeFromHref("openspec-validate:add-auth"));
            assertNull(SpecPreviewRenderer.validateChangeFromHref("openspec-validate:"));
            assertNull(SpecPreviewRenderer.validateChangeFromHref("openspec-open:add-auth"));
            assertNull(SpecPreviewRenderer.validateChangeFromHref(null));
        }
    }

    @Nested
    class Placement {

        private final String line = "<p class=\"" + SpecPreviewRenderer.VALIDATION_LINE_CLASS + "\">L</p>";

        @Test
        void theLineGoesAfterTheHeaderAndAfterTheOpenLinkWhicheverComesFirst() {
            String body = "<div>body</div>";
            String header = SpecPreviewRenderer.header("add-auth");
            String lineFirst = SpecPreviewRenderer.withOpenLink(
                    SpecPreviewRenderer.withValidationLine(header + body, line), "proposal.md");
            String linkFirst = SpecPreviewRenderer.withValidationLine(
                    SpecPreviewRenderer.withOpenLink(header + body, "proposal.md"), line);
            assertEquals(lineFirst, linkFirst);
            assertTrue(lineFirst.startsWith(header), lineFirst);
            int link = lineFirst.indexOf(SpecPreviewRenderer.OPEN_LINK_CLASS);
            int at = lineFirst.indexOf(SpecPreviewRenderer.VALIDATION_LINE_CLASS);
            assertTrue(link > 0 && at > link && at < lineFirst.indexOf(body), lineFirst);
        }

        @Test
        void aFragmentWithoutTheHeaderGetsTheLineInFront() {
            assertEquals(line + "<p>x</p>", SpecPreviewRenderer.withValidationLine("<p>x</p>", line));
        }
    }

    @Nested
    class Legend {

        private static final String LEGEND = "class=\"" + SpecPreviewRenderer.DELTA_LEGEND_CLASS + "\"";

        @Test
        void aCapturedChangeWithDeltasShowsTheLegendOnce() {
            String html = SpecPreviewRenderer.renderChangeDeltas(
                    ChangeDeltaModel.parse(fixture("1.14.3/additive-shapes/show-change.json")));
            assertEquals(1, html.split(LEGEND, -1).length - 1, html);
            assertTrue(html.contains("MODIFIED replaces the whole requirement, so keep its scenarios"), html);
            assertTrue(html.indexOf(LEGEND) > html.indexOf(SpecPreviewRenderer.DELTA_SUMMARY_CLASS), html);
        }

        @Test
        void aCapturedChangeWithoutDeltasHasNoLegend() {
            String html = SpecPreviewRenderer.renderChangeDeltas(
                    ChangeDeltaModel.parse(fixture("1.7.0/change-deltas/empty.show.json")));
            assertTrue(html.contains(SpecPreviewRenderer.EMPTY_STATE_MARKER), html);
            assertFalse(html.contains(LEGEND), html);
        }
    }
}
