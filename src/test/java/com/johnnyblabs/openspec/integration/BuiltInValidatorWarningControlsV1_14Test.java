package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.actions.OpenSpecValidateAction;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.util.List;

/**
 * The built-in fallback adopts none of CLI 1.14's new WARNINGs (unknown {@code .openspec.yaml} key, over-long
 * requirement, text that archive would drop). Each is non-failing in the CLI's default mode, so the fallback must
 * pass every control's input with no ERROR, which keeps it never more restrictive than the 1.3–1.14 clients it
 * serves. The inputs are the exact project trees the 1.14.3 captures under {@code fixtures/cli/1.14.3/} were taken
 * from, so the CLI side of each comparison is real output (asserted in {@code CliContractTest}).
 */
public class BuiltInValidatorWarningControlsV1_14Test extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/fixtures/cli/1.14.3";
    }

    private ValidationResult validateControl(String control) {
        myFixture.copyDirectoryToProject(control + "/openspec", "openspec");
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        assertNotNull("control " + control + " must copy an openspec/ root", root);
        VfsUtil.markDirtyAndRefresh(false, true, true, root);
        // Anti-vacuity: the validator must actually see the control's change and its delta.
        assertNotNull(myFixture.findFileInTempDir("openspec/changes/c/specs/demo/spec.md"));
        return getProject().getService(BuiltInValidator.class).validateAll();
    }

    private static void assertDefaultPassWithNoError(String control, ValidationResult result) {
        List<ValidationIssue> errors = result.issues().stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR).toList();
        assertTrue(control + ": the fallback must not raise an ERROR the CLI doesn't — got " + errors, errors.isEmpty());
        assertTrue(control + ": the CLI passes this in default mode, so the fallback must too", result.passed());
    }

    public void testUnknownMetadataKeyIsNotAFallbackError() {
        assertDefaultPassWithNoError("unknown-meta", validateControl("unknown-meta"));
    }

    public void testOverlongRequirementIsNotAFallbackError() {
        assertDefaultPassWithNoError("too-long", validateControl("too-long"));
    }

    public void testArchiveDroppedTextIsNotAFallbackError() {
        assertDefaultPassWithNoError("archive-dropped-text", validateControl("archive-dropped-text"));
    }

    public void testXKeyOnlyPassesTheFallbackInBothModes() {
        // The CLI reports this valid even under --strict, so the strict fallback verdict must pass too.
        ValidationResult result = validateControl("xkey-only");
        assertDefaultPassWithNoError("xkey-only", result);
        assertTrue("xkey-only: the CLI is strict-valid, so the strict fallback must not fail it",
                OpenSpecValidateAction.applyStrictFallbackVerdict(result, true).passed());
    }
}
