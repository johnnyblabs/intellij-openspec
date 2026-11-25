package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.vfs.VfsUtil;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The release-gated issues where they land and what they decide: each issue's line is its header's line in the corpus
 * file, and the Validate action's fallback fails a strict run on them with the release it followed (the corpus is
 * {@code fixtures/cli/2.1.1/parity-corpus-2x/}).
 */
public class ReleaseGatedIssuesTest extends OpenSpecIntegrationTestBase {

    static final Path CORPUS = Path.of("src/test/resources/fixtures/cli/2.1.1/parity-corpus-2x");

    private BuiltInValidator validator;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        validator = getProject().getService(BuiltInValidator.class);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            validator.clearReleaseForTest();
        } finally {
            super.tearDown();
        }
    }

    private String add(String rel) throws IOException {
        String text = new String(Files.readAllBytes(CORPUS.resolve(rel)), StandardCharsets.UTF_8);
        myFixture.addFileToProject(rel, text);
        return text;
    }

    /** The 1-based line of the first line of {@code text} that starts with {@code prefix}. */
    private static int lineOf(String text, String prefix) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) if (lines[i].startsWith(prefix)) return i + 1;
        throw new AssertionError("no line starts with " + prefix);
    }

    private List<ValidationIssue> issues(String rule) {
        VfsUtil.markDirtyAndRefresh(false, true, true, myFixture.findFileInTempDir("openspec"));
        List<ValidationIssue> all = new java.util.ArrayList<>(validator.validateSpecs().issues());
        all.addAll(validator.validateChanges().issues());
        return all.stream().filter(i -> rule.equals(i.rule())).toList();
    }

    public void testEachIssueSitsOnItsHeadersLine() throws IOException {
        String steps = add("openspec/specs/step-missing-when/spec.md");
        String tooLong = add("openspec/specs/len-501/spec.md");
        add("openspec/specs/base-short/spec.md");
        add("openspec/changes/grow-short/proposal.md");
        String grow = add("openspec/changes/grow-short/specs/base-short/spec.md");
        add("openspec/changes/step-delta/proposal.md");
        String stepDelta = add("openspec/changes/step-delta/specs/demo-step/spec.md");
        validator.followReleaseForTest("2.1.1");

        List<ValidationIssue> stepIssues = issues("spec-scenario-steps");
        assertEquals(lineOf(steps, "#### Scenario:"), stepIssues.stream()
                .filter(i -> i.filePath().contains("/step-missing-when/")).findFirst().orElseThrow().line());
        assertEquals(lineOf(stepDelta, "#### Scenario:"), stepIssues.stream()
                .filter(i -> i.filePath().contains("/step-delta/")).findFirst().orElseThrow().line());
        assertEquals(lineOf(tooLong, "### Requirement:"), issues("spec-requirement-too-long").stream()
                .filter(i -> i.filePath().contains("/len-501/")).findFirst().orElseThrow().line());
        assertEquals(lineOf(grow, "### Requirement:"), issues("delta-modified-too-long").get(0).line());
    }

    /** The action's fallback passes the release it followed to the strict flip, so a 2.1.1-only warning fails strict. */
    public void testTheFallbackFailsStrictOnTheReleaseItFollowed() throws IOException {
        add("openspec/specs/base-short/spec.md");
        add("openspec/changes/grow-short/proposal.md");
        add("openspec/changes/grow-short/specs/base-short/spec.md");
        VfsUtil.markDirtyAndRefresh(false, true, true, myFixture.findFileInTempDir("openspec"));
        java.util.concurrent.Callable<com.johnnyblabs.openspec.util.CliRunner.CliResult> failingCli = () -> {
            throw new IllegalStateException("the CLI run failed");
        };
        validator.followReleaseForTest("2.1.1");
        ValidateTarget strict = ValidateTarget.change("grow-short").withStrict();
        assertFalse("2.1.1 fails strict on the growth warning",
                OpenSpecValidateAction.cliOrFallback(getProject(), validator, strict, failingCli).passed());
        assertTrue("default mode passes",
                OpenSpecValidateAction.cliOrFallback(getProject(), validator, ValidateTarget.change("grow-short"),
                        failingCli).passed());
        validator.followReleaseForTest("2.1.0");
        assertTrue("2.1.0 has no growth rule",
                OpenSpecValidateAction.cliOrFallback(getProject(), validator, strict, failingCli).passed());
    }
}
