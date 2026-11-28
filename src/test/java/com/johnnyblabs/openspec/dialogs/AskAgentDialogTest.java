package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** The request field: OK only once something is typed, upstream's examples as the placeholder, the text trimmed. */
public class AskAgentDialogTest extends BasePlatformTestCase {

    public void testOkOnlyForARequestAndTheTextAsTyped() {
        AskAgentDialog dialog = new AskAgentDialog(getProject());
        try {
            assertFalse("blank: nothing to send", dialog.okEnabledForTest());
            dialog.setRequestForTest("  where does add-auth stand?  ");
            assertTrue(dialog.okEnabledForTest());
            assertEquals("where does add-auth stand?", dialog.getRequest());
            dialog.setRequestForTest("   ");
            assertFalse(dialog.okEnabledForTest());
            assertTrue(AskAgentDialog.PLACEHOLDER.contains("where does add-auth stand?"));
            assertTrue(AskAgentDialog.PLACEHOLDER.contains("verify add-auth, then archive it"));
        } finally {
            Disposer.dispose(dialog.getDisposable());
        }
    }
}
