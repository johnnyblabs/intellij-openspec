package com.johnnyblabs.openspec.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;

/**
 * The notification "Open File" action opens its file in a focused editor tab. It is one of the file-opening sites
 * migrated from the deprecated {@code FileEditorManager.openFile(file, boolean)} to
 * {@code OpenFileDescriptor.navigate(true)}, and the one reachable without a UI.
 */
public class OpenSpecNotifierOpenFileActionTest extends BasePlatformTestCase {

    private void perform(NotificationAction action, Notification notification) {
        var context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject()).build();
        action.actionPerformed(TestActionEvent.createTestEvent(action, context), notification);
    }

    public void testOpenFileActionOpensAndSelectsTheFile() {
        VirtualFile spec = myFixture.addFileToProject("openspec/specs/demo/spec.md", "# demo\n").getVirtualFile();
        FileEditorManager editors = FileEditorManager.getInstance(getProject());
        assertFalse("precondition: the file starts closed", editors.isFileOpen(spec));
        Notification notification = new Notification("OpenSpec", "content", NotificationType.INFORMATION);

        perform(OpenSpecNotifier.openFileAction(spec), notification);

        assertTrue("the action must open the file", editors.isFileOpen(spec));
        assertTrue("the opened file must be the selected editor",
                Arrays.asList(editors.getSelectedFiles()).contains(spec));
        assertTrue("the notification is expired after opening", notification.isExpired());
    }

    public void testWithoutAProjectNothingOpensButTheNotificationStillExpires() {
        VirtualFile spec = myFixture.addFileToProject("openspec/specs/other/spec.md", "# other\n").getVirtualFile();
        Notification notification = new Notification("OpenSpec", "content", NotificationType.INFORMATION);
        NotificationAction action = OpenSpecNotifier.openFileAction(spec);

        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.builder().build()), notification);

        assertFalse(FileEditorManager.getInstance(getProject()).isFileOpen(spec));
        assertTrue(notification.isExpired());
    }
}
