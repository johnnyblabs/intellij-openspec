package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.SpecSyncResult;

import java.util.List;

/** The sync preview offers Apply only when OpenSpec's archive would accept the change. */
public class SyncPreviewDialogTest extends BasePlatformTestCase {

    private SyncPreviewDialog dialog(List<SpecSyncResult> results) {
        SyncPreviewDialog dialog = new SyncPreviewDialog(getProject(), results);
        Disposer.register(getTestRootDisposable(), dialog.getDisposable());
        return dialog;
    }

    public void testApplyIsEnabledWhenNothingIsRefused() {
        SyncPreviewDialog dialog = dialog(List.of(new SpecSyncResult("demo", "/x/demo/spec.md", "a\n", "b\n",
                List.of(), List.of("demo - REMOVED requirement \"Gone\" is not in the current spec; treating it as already removed."),
                List.of())));

        assertTrue("a warning alone doesn't stop the sync", dialog.applyEnabledForTest());
    }

    public void testApplyIsDisabledAndTheRefusalShownWhenAnyCapabilityIsRefused() {
        String refusal = "other MODIFIED failed for header \"### Requirement: Missing\" - not found";
        SyncPreviewDialog dialog = dialog(List.of(
                new SpecSyncResult("demo", "/x/demo/spec.md", "a\n", "b\n", List.of(), List.of(), List.of()),
                new SpecSyncResult("other", "/x/other/spec.md", "a\n", "a\n", List.of(), List.of(), List.of(refusal))));

        assertFalse("one refused capability refuses the whole change", dialog.applyEnabledForTest());
        assertEquals(List.of(refusal), dialog.refusalsShownForTest());
    }
}
