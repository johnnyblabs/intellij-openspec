package com.johnnyblabs.openspec.services;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Confirm, then write the marker as one undoable, saved edit; or write nothing. */
public class RetireMarkerWriteTest extends BasePlatformTestCase {

    private static final String BEFORE = "# kept\nschema: spec-driven\ncreated: 2026-11-27\n";
    private Path file;
    private final List<String> asked = new ArrayList<>();
    private final List<Notification> raised = new ArrayList<>();
    private final AtomicInteger previews = new AtomicInteger();
    private final RetireMarkerFix.Offer offer = new RetireMarkerFix.Offer("c", List.of("foo", "zoo"));

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Path dir = Files.createTempDirectory("retire-marker").toRealPath();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), dir.toString());
        file = dir.resolve(".openspec.yaml");
        Files.writeString(file, BEFORE);
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT);
        } finally {
            super.tearDown();
        }
    }

    private boolean run(int answer, String before) {
        TestDialogManager.setTestDialog(message -> {
            asked.add(message);
            return answer;
        });
        return RetireMarkerFix.confirmAndWrite(getProject(), "c", file, before, offer, previews::incrementAndGet);
    }

    public void testConfirmingWritesTheMarkerSavesItAndCanBeUndone() throws Exception {
        var vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
        FileEditor editor = FileEditorManager.getInstance(getProject()).openFile(vf, false)[0];
        try {
            assertTrue(run(Messages.YES, BEFORE));
            assertEquals(List.of(RetireMarkerFix.confirmation(offer)), asked);
            assertTrue("names both specs", asked.getFirst().contains("specs/foo/spec.md, specs/zoo/spec.md"));
            assertEquals("saved to disk: the CLI reads it", BEFORE + "retire_capabilities: true\n", Files.readString(file));
            assertEquals(1, previews.get());
            UndoManager undo = UndoManager.getInstance(getProject());
            assertTrue("the write is undoable", undo.isUndoAvailable(editor));
            undo.undo(editor);
            assertEquals(BEFORE, com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vf).getText());
        } finally {
            FileEditorManager.getInstance(getProject()).closeFile(vf);
        }
    }

    public void testCancellingWritesNothing() throws Exception {
        assertFalse(run(Messages.NO, BEFORE));
        assertEquals(BEFORE, Files.readString(file));
        assertEquals(0, previews.get());
    }

    public void testAFileChangedSinceTheCheckIsNotWritten() throws Exception {
        assertFalse(run(Messages.YES, "schema: spec-driven\n"));
        assertEquals(BEFORE, Files.readString(file));
        assertEquals(0, previews.get());
        assertTrue(raised.stream().anyMatch(n -> n.getContent().contains("changed meanwhile")));
    }

    public void testUnsavedEditsAreNotOverwritten() throws Exception {
        var vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
        var documents = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance();
        var document = documents.getDocument(vf);
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(),
                () -> document.setText(BEFORE + "goal: unsaved\n"));
        try {
            assertFalse(run(Messages.YES, BEFORE));
            assertEquals(BEFORE + "goal: unsaved\n", document.getText());
            assertEquals(BEFORE, Files.readString(file));
            assertTrue(raised.stream().anyMatch(n -> n.getContent().contains("unsaved edits")));
        } finally {
            documents.reloadFromDisk(document);
        }
    }

    public void testAFileChangedOutsideTheIdeSinceItWasLoadedIsStillWritten() throws Exception {
        var vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vf);
        String external = "\uFEFFschema: spec-driven\n";
        Files.writeString(file, external);
        assertTrue(run(Messages.YES, external));
        assertEquals("\uFEFFschema: spec-driven\nretire_capabilities: true\n", Files.readString(file));
    }

    public void testAnEditThatWouldNotCountAsksNothingAndWritesNothing() throws Exception {
        TestDialogManager.setTestDialog(message -> {
            throw new AssertionError("no confirmation: " + message);
        });
        assertFalse(RetireMarkerFix.confirmAndWrite(getProject(), "c", file, BEFORE, null, previews::incrementAndGet));
        assertEquals(BEFORE, Files.readString(file));
        assertTrue(raised.stream().anyMatch(n -> n.getContent().contains("can't be added automatically")));
    }
}
