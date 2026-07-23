package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

/**
 * Tests the Project-View Validate action: the pure {@link ValidateTarget#resolveTarget}
 * path mapping, the {@code update()} visibility gate, and the scoped built-in routing in
 * {@link OpenSpecValidateAction#builtInValidate}.
 *
 * <p>Uses the {@code testProject} fixture (an {@code openspec/} tree with a
 * {@code specs/actions} spec and a {@code changes/test-change} change).</p>
 */
public class OpenSpecValidateFromProjectViewActionTest extends OpenSpecIntegrationTestBase {

    private VirtualFile file(String relUnderOpenspec) {
        VirtualFile f = getOpenSpecRoot().findFileByRelativePath(relUnderOpenspec);
        assertNotNull("fixture file must exist: openspec/" + relUnderOpenspec, f);
        return f;
    }

    private VirtualFile[] sel(VirtualFile... files) {
        return files;
    }

    private void refreshVfs() {
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        if (root != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, root);
        }
    }

    // ---------------------------------------------------------------
    // resolveTarget — both directions
    // ---------------------------------------------------------------

    public void testSpecFileResolvesToSpec() {
        ValidateTarget t = ValidateTarget.resolveTarget(sel(file("specs/actions/spec.md")), getProject());
        assertEquals(ValidateTarget.Kind.SPEC, t.kind());
        assertEquals("actions", t.id());
    }

    public void testSpecDirResolvesToSpec() {
        ValidateTarget t = ValidateTarget.resolveTarget(sel(file("specs/actions")), getProject());
        assertEquals(ValidateTarget.Kind.SPEC, t.kind());
        assertEquals("actions", t.id());
    }

    public void testActiveChangeFileResolvesToChange() {
        ValidateTarget t = ValidateTarget.resolveTarget(sel(file("changes/test-change/proposal.md")), getProject());
        assertEquals(ValidateTarget.Kind.CHANGE, t.kind());
        assertEquals("test-change", t.id());
    }

    public void testChangeDirResolvesToChange() {
        ValidateTarget t = ValidateTarget.resolveTarget(sel(file("changes/test-change")), getProject());
        assertEquals(ValidateTarget.Kind.CHANGE, t.kind());
        assertEquals("test-change", t.id());
    }

    public void testConfigResolvesToWholeProject() {
        ValidateTarget t = ValidateTarget.resolveTarget(sel(file("config.yaml")), getProject());
        assertTrue(t.isWholeProject());
    }

    public void testOpenSpecRootResolvesToWholeProject() {
        ValidateTarget t = ValidateTarget.resolveTarget(sel(getOpenSpecRoot()), getProject());
        assertTrue(t.isWholeProject());
    }

    public void testArchivedChangeResolvesToWholeProject() {
        myFixture.addFileToProject("openspec/changes/archive/old-change/proposal.md",
                "# Archived\n\n## What\nDone.\n");
        refreshVfs();
        ValidateTarget t = ValidateTarget.resolveTarget(
                sel(file("changes/archive/old-change/proposal.md")), getProject());
        assertTrue("archived change must never be forwarded as a CHANGE target", t.isWholeProject());
        assertNull(t.id());
    }

    public void testMultiItemSelectionResolvesToWholeProject() {
        ValidateTarget t = ValidateTarget.resolveTarget(
                sel(file("specs/actions/spec.md"), file("changes/test-change/proposal.md")), getProject());
        assertTrue("a selection spanning distinct items falls back to whole-project", t.isWholeProject());
    }

    public void testSameItemMultiSelectionResolvesToThatItem() {
        ValidateTarget t = ValidateTarget.resolveTarget(
                sel(file("specs/actions/spec.md"), file("specs/actions")), getProject());
        assertEquals(ValidateTarget.Kind.SPEC, t.kind());
        assertEquals("actions", t.id());
    }

    public void testEmptySelectionResolvesToWholeProject() {
        assertTrue(ValidateTarget.resolveTarget(new VirtualFile[0], getProject()).isWholeProject());
    }

    public void testNullSelectionResolvesToWholeProject() {
        assertTrue(ValidateTarget.resolveTarget(null, getProject()).isWholeProject());
    }

    public void testFileOutsideOpenspecResolvesToWholeProject() {
        VirtualFile outside = myFixture.addFileToProject("outside.txt", "hi").getVirtualFile();
        assertTrue(ValidateTarget.resolveTarget(sel(outside), getProject()).isWholeProject());
    }

    // ---------------------------------------------------------------
    // update() visibility
    // ---------------------------------------------------------------

    private AnActionEvent eventFor(VirtualFile[] files) {
        SimpleDataContext.Builder b = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject());
        if (files != null) {
            b.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files);
        }
        return TestActionEvent.createTestEvent(new OpenSpecValidateFromProjectViewAction(), b.build());
    }

    public void testUpdateVisibleUnderOpenspec() {
        OpenSpecValidateFromProjectViewAction action = new OpenSpecValidateFromProjectViewAction();
        AnActionEvent e = eventFor(sel(file("specs/actions/spec.md")));
        action.update(e);
        assertTrue("Validate must be visible when a selected file is under openspec/",
                e.getPresentation().isVisible());
        assertTrue(e.getPresentation().isEnabled());
    }

    public void testUpdateHiddenOutsideOpenspec() {
        VirtualFile outside = myFixture.addFileToProject("elsewhere.txt", "x").getVirtualFile();
        OpenSpecValidateFromProjectViewAction action = new OpenSpecValidateFromProjectViewAction();
        AnActionEvent e = eventFor(sel(outside));
        action.update(e);
        assertFalse("Validate must be hidden when no selected file is under openspec/",
                e.getPresentation().isVisible());
    }

    public void testUpdateHiddenOnEmptySelection() {
        OpenSpecValidateFromProjectViewAction action = new OpenSpecValidateFromProjectViewAction();
        AnActionEvent e = eventFor(new VirtualFile[0]);
        action.update(e);
        assertFalse(e.getPresentation().isVisible());
    }

    public void testUpdateHiddenOnNullSelection() {
        OpenSpecValidateFromProjectViewAction action = new OpenSpecValidateFromProjectViewAction();
        AnActionEvent e = eventFor(null);
        action.update(e);
        assertFalse(e.getPresentation().isVisible());
    }

    // ---------------------------------------------------------------
    // builtInValidate routing (scoped vs whole-project)
    // ---------------------------------------------------------------

    private BuiltInValidator validator() {
        return getProject().getService(BuiltInValidator.class);
    }

    private boolean hasBrokenSpecError(ValidationResult r) {
        return r.issues().stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.filePath() != null && i.filePath().contains("broken"));
    }

    public void testWholeProjectRoutesToValidateAll() {
        myFixture.addFileToProject("openspec/specs/broken/spec.md", "# Broken\n\nNo requirements here.\n");
        refreshVfs();
        ValidationResult r = OpenSpecValidateAction.builtInValidate(
                getProject(), validator(), ValidateTarget.wholeProject());
        assertTrue("whole-project validate must surface the broken spec's error", hasBrokenSpecError(r));
    }

    public void testSpecTargetScopesToThatSpecOnly() {
        myFixture.addFileToProject("openspec/specs/broken/spec.md", "# Broken\n\nNo requirements here.\n");
        refreshVfs();
        // Scoping to the good spec must NOT surface the broken spec's error.
        ValidationResult good = OpenSpecValidateAction.builtInValidate(
                getProject(), validator(), ValidateTarget.spec("actions"));
        assertFalse("scoping to spec `actions` must not report the broken spec", hasBrokenSpecError(good));
        // Scoping to the broken spec DOES surface it.
        ValidationResult broken = OpenSpecValidateAction.builtInValidate(
                getProject(), validator(), ValidateTarget.spec("broken"));
        assertTrue("scoping to spec `broken` must report its error", hasBrokenSpecError(broken));
    }

    public void testChangeTargetMatchesValidateChange() {
        myFixture.addFileToProject("openspec/specs/broken/spec.md", "# Broken\n\nNo requirements here.\n");
        refreshVfs();
        ValidationResult scoped = OpenSpecValidateAction.builtInValidate(
                getProject(), validator(), ValidateTarget.change("test-change"));
        // A change target validates the change, not the specs tree — the broken spec is invisible.
        assertFalse("change scope must not report unrelated spec errors", hasBrokenSpecError(scoped));
        // And it matches the validator's own single-change entry point.
        ValidationResult direct = validator().validateChange("test-change");
        assertEquals(direct.issues().size(), scoped.issues().size());
    }
}
