package com.johnnyblabs.openspec.actions;

import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
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
}
