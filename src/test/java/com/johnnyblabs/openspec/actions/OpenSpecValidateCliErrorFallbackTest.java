package com.johnnyblabs.openspec.actions;

import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.util.CliFixtures;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The Validate action's CLI routing ({@link OpenSpecValidateAction#cliOrFallback}) driven with captured real
 * CLI 1.13.2 output. An item-less error response ({@code invalid_item}) must yield the built-in verdict —
 * with the per-run strict flip — never the blind PASS the parser used to report for it.
 */
public class OpenSpecValidateCliErrorFallbackTest extends OpenSpecIntegrationTestBase {

    private static String fixture(String path) throws IOException {
        try (InputStream in = OpenSpecValidateCliErrorFallbackTest.class.getResourceAsStream(
                "/fixtures/cli/1.13.2/" + path)) {
            assertNotNull("missing fixture " + path, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ValidationResult route(ValidateTarget target, String capturedStdout, int exitCode) {
        return OpenSpecValidateAction.cliOrFallback(getProject(), getProject().getService(BuiltInValidator.class),
                target, () -> new CliRunner.CliResult(exitCode, capturedStdout, ""));
    }

    private void refreshVfs() {
        com.intellij.openapi.vfs.VirtualFile root = myFixture.findFileInTempDir("openspec");
        if (root != null) {
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, root);
        }
    }

    public void testInvalidItemResponseFallsBackToTheFailingBuiltInVerdict() throws IOException {
        // A change the built-in validator fails (ADDED requirement with no scenario) — the CLI verdict for a
        // CHANGE target is used alone, so the old blind PASS would have hidden this error.
        myFixture.addFileToProject("openspec/changes/test-change/specs/broken/spec.md",
                "## ADDED Requirements\n\n### Requirement: Broken\nThe system SHALL break.\n");
        refreshVfs();

        ValidationResult r = route(ValidateTarget.change("test-change"), fixture("validate-invalid-item.json"), 1);

        assertFalse("an invalid_item response must not report a pass", r.passed());
        assertFalse("the verdict must be the built-in fallback, not the CLI's", "cli".equals(r.source()));
    }

    public void testFallbackAppliesTheStrictFlip() throws IOException {
        // A spec whose only built-in issue is the CLI-mirroring missing-keyword WARNING: passes by default,
        // fails under --strict.
        myFixture.addFileToProject("openspec/specs/warny/spec.md", """
                # warny Specification

                ## Purpose
                A spec whose only issue is a requirement body without a normative keyword.

                ## Requirements

                ### Requirement: Soft
                The system allows soft wording.

                #### Scenario: Soft wording
                - **WHEN** it runs
                - **THEN** it works
                """);
        refreshVfs();
        String captured = fixture("validate-invalid-item.json");

        assertTrue("default fallback passes on a warning-only spec",
                route(ValidateTarget.spec("warny"), captured, 1).passed());
        assertFalse("strict fallback fails on the same warning",
                route(ValidateTarget.spec("warny").withStrict(), captured, 1).passed());
    }

    public void testANormalItemsResponseStillUsesTheCliVerdict() throws IOException {
        ValidationResult r = route(ValidateTarget.change("c"), fixture("renamed-star/validate-change.json"), 0);

        assertTrue(r.passed());
        assertEquals("cli", r.source());
    }

    private void addChangeTheBuiltInFails() {
        myFixture.addFileToProject("openspec/changes/test-change/specs/broken/spec.md",
                "## ADDED Requirements\n\n### Requirement: Broken\nThe system SHALL break.\n");
        refreshVfs();
    }

    private void assertBuiltInFailingVerdict(ValidationResult r) {
        assertFalse("unreadable CLI output must not report a pass", r.passed());
        assertFalse("the verdict must be the built-in fallback, not the CLI's", "cli".equals(r.source()));
    }

    public void testNoRootResponseFallsBack() {
        // Regression lock: the 1.14.3 no-root envelope already fell back through the error-response path.
        addChangeTheBuiltInFails();
        CliRunner.CliResult noRoot = CliFixtures.error("validate-noroot");
        assertBuiltInFailingVerdict(route(ValidateTarget.change("test-change"), noRoot.stdout(), noRoot.exitCode()));
    }

    public void testTruncatedJsonFallsBack() {
        // Used to swallow the parse error and report PASS with zero issues.
        addChangeTheBuiltInFails();
        String report = CliFixtures.text("1.14.3/validate-parity-corpus.json");
        assertBuiltInFailingVerdict(route(ValidateTarget.change("test-change"), CliFixtures.truncated(report), 0));
    }

    public void testJsonWithoutAVerdictFallsBack() {
        // Synthetic: any JSON object with neither items nor an error status. Used to report PASS with zero issues.
        addChangeTheBuiltInFails();
        assertBuiltInFailingVerdict(route(ValidateTarget.change("test-change"), "{}", 0));
    }

    public void testTextBeforeTheJsonKeepsTheCliIssues() {
        // Used to route anything not starting with "{" to the text parser, which found none of the issues.
        String report = CliFixtures.text("1.14.3/validate-parity-corpus.json");
        ValidationResult plain = route(ValidateTarget.wholeProject(), report, 1);
        ValidationResult prefixed = route(ValidateTarget.wholeProject(), "Note: something printed first\n" + report, 1);
        assertTrue("the plain run used the CLI's report", "cli".equals(plain.source()) || "merged".equals(plain.source()));
        assertTrue("capture precondition: the report has issues", plain.issues().size() > 0);
        assertEquals(plain.source(), prefixed.source());
        assertEquals(plain.issues().size(), prefixed.issues().size());
        assertEquals(plain.passed(), prefixed.passed());
    }

    // ---- CLI 1.16 configuration error response: a failure, never a fallback ----

    private void breakConfig() throws IOException {
        com.intellij.openapi.vfs.VirtualFile config = getConfigFile();
        com.intellij.openapi.application.WriteAction.run(() -> config.setBinaryContent(
                "schema: spec-driven\ncontext: [unclosed\n".getBytes(StandardCharsets.UTF_8)));
        refreshVfs();
    }

    private void assertConfigErrorResult(String fixture, ValidateTarget target) {
        CliRunner.CliResult run = CliFixtures.error("1.16.0", fixture);
        com.google.gson.JsonObject status = com.google.gson.JsonParser.parseString(run.stdout()).getAsJsonObject()
                .getAsJsonArray("status").get(0).getAsJsonObject();

        ValidationResult r = route(target, run.stdout(), run.exitCode());

        String where = fixture + " " + target;
        assertFalse(where + ": a broken config fails Validate", r.passed());
        assertEquals(where + ": the CLI's verdict, not the built-in fallback", "cli", r.source());
        // Exactly one issue: merging the built-in result would add its config-unparseable warning.
        assertEquals(where + ": " + r.issues(), 1, r.issues().size());
        com.johnnyblabs.openspec.validation.ValidationIssue issue = r.issues().get(0);
        assertEquals(com.johnnyblabs.openspec.validation.ValidationIssue.Severity.ERROR, issue.severity());
        assertTrue(where, issue.message().contains(status.get("message").getAsString()));
        assertTrue(where, issue.message().contains(status.get("fix").getAsString()));
        assertTrue(where + ": " + issue.filePath(), issue.filePath().endsWith("openspec/config.yaml"));
        assertEquals(1, issue.line());
        assertEquals("cli", issue.rule());
    }

    public void testConfigErrorResponseFailsEveryTargetWithTheCliFix() throws IOException {
        breakConfig();
        for (String fixture : new String[]{"validate-unparseable-config", "validate-unreadable-config"}) {
            assertConfigErrorResult(fixture, ValidateTarget.wholeProject());
            assertConfigErrorResult(fixture, ValidateTarget.wholeProject().withStrict());
            assertConfigErrorResult(fixture + "-change", ValidateTarget.change("test-change"));
            assertConfigErrorResult(fixture + "-strict", ValidateTarget.change("test-change").withStrict());
        }
    }

    public void testWithABrokenConfigOtherErrorResponsesStillFallBack() throws IOException {
        // Same broken config, but the CLI answered with a non-config error response: the built-in verdict is used,
        // and it carries the built-in warning for the config.
        breakConfig();
        CliRunner.CliResult noRoot = CliFixtures.error("validate-noroot");
        ValidationResult r = route(ValidateTarget.wholeProject(), noRoot.stdout(), noRoot.exitCode());
        assertFalse("cli".equals(r.source()));
        assertTrue(r.issues().toString(), r.issues().stream().anyMatch(i -> "config-unparseable".equals(i.rule())));
    }
}
