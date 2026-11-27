package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.services.AiToolRegistry;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Verify's hand-off is delivered in each mode outside Direct API, and says what's missing without a verify skill. */
public class VerifyHandoffDeliveryTest extends BasePlatformTestCase {

    private final List<Notification> raised = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
        CopyPasteManager.getInstance().setContents(new StringSelection("clipboard-sentinel"));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            FileEditorManager editors = FileEditorManager.getInstance(getProject());
            Arrays.stream(editors.getOpenFiles()).forEach(editors::closeFile);
        } finally {
            super.tearDown();
        }
    }

    private static WorkflowInvocation.Found claude() {
        return new WorkflowInvocation.Found(AiToolRegistry.byId("claude"), "/openspec-verify-change");
    }

    public void testClipboardCopiesTheVerifyInvocationForTheChange() throws Exception {
        VerifyHandoff.deliver(getProject(), "demo", new VerifyHandoff.Target(DeliveryMode.CLIPBOARD, claude()));
        assertEquals("/openspec-verify-change demo",
                CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
        assertTrue(raised.stream().anyMatch(n -> n.getContent().contains("/openspec-verify-change demo")));
    }

    public void testEditorTabOpensTheVerifyInvocation() throws Exception {
        VerifyHandoff.deliver(getProject(), "demo", new VerifyHandoff.Target(DeliveryMode.EDITOR_TAB, claude()));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        var open = FileEditorManager.getInstance(getProject()).getOpenFiles();
        assertEquals(1, open.length);
        assertEquals(VerifyHandoff.EDITOR_TAB_FILE_NAME, open[0].getName());
        assertEquals("/openspec-verify-change demo", new String(open[0].contentsToByteArray()));
        assertEquals("the clipboard is untouched", "clipboard-sentinel",
                CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
    }

    public void testWithoutAVerifySkillItSaysSoAndOffersUpdate() throws Exception {
        VerifyHandoff.deliver(getProject(), "demo", new VerifyHandoff.Target(DeliveryMode.CLIPBOARD, null));
        assertEquals(1, raised.size());
        assertEquals(VerifyHandoff.NOT_INSTALLED_NOTICE, raised.get(0).getContent());
        assertTrue(raised.get(0).getActions().stream().anyMatch(a -> "Update OpenSpec…".equals(a.getTemplateText())));
        assertEquals("nothing copied", "clipboard-sentinel",
                CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
    }
}
