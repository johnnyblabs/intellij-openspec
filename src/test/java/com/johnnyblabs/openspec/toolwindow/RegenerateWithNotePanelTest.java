package com.johnnyblabs.openspec.toolwindow;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService.PlannedWrite;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regenerate from the workflow panel: one dialog asks for an optional note, the note reaches the prompt in every
 * delivery mode and is never saved, and over Direct API the result is reviewed before anything is written. Runs against
 * a real change directory on disk, with captured CLI output and a stubbed Direct API call.
 */
public class RegenerateWithNotePanelTest extends OpenSpecIntegrationTestBase {

    private static final String CHANGE = "test-change";
    private static final String NOTE = "drop the caching section";
    private static final String CURRENT_DESIGN = "# Design\n\nUse a cache.\n";
    private static final String GENERATED_DESIGN = "# Design\n\nNo cache.\n";

    private Path changeDir;
    private WorkflowActionPanel workflowPanel;
    private DirectApiService api;
    private final List<String> sentPrompts = new ArrayList<>();
    private final List<WorkflowActionPanel.RegenerateRequest> asked = new ArrayList<>();
    private final List<List<PlannedWrite>> reviewed = new ArrayList<>();
    private final List<Notification> raised = new ArrayList<>();
    /** What the stubbed Direct API call returns. */
    private String response = GENERATED_DESIGN;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Path root = Files.createTempDirectory("openspec-regenerate").toRealPath();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), root.toString());
        changeDir = root.resolve(CHANGE);
        Files.createDirectories(changeDir);
        Files.writeString(changeDir.resolve("proposal.md"), "# Proposal\n\n## Why\n\nDemo.\n");
        Files.writeString(changeDir.resolve("design.md"), CURRENT_DESIGN);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir);

        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(List.of(new Change(CHANGE, changeDir.toString())));
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());

        PanelStatusTestSupport.feedCapturedStatus(getProject(), CHANGE, getTestRootDisposable(), this::instructionsJson);

        api = mock(DirectApiService.class);
        when(api.generateRaw(anyString())).thenAnswer(inv -> {
            sentPrompts.add(inv.getArgument(0));
            return response;
        });
        ServiceContainerUtil.replaceService(getProject(), DirectApiService.class, api, getTestRootDisposable());

        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });

        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        long end = System.currentTimeMillis() + 10_000;
        while (panel.treeModelForTest() != model && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        workflowPanel = panel.workflowPanelForTest();
        workflowPanel.setActiveChange(CHANGE);
        end = System.currentTimeMillis() + 10_000;
        while (!CHANGE.equals(workflowPanel.activeChangeForTest()) && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertEquals(CHANGE, workflowPanel.activeChangeForTest());
        dispatch();
        sentPrompts.clear();
        raised.clear();
        answerWith(NOTE);
        acceptReviews();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (workflowPanel != null) workflowPanel.disposeForTest();
            // A write refreshes the panel on a pooled thread. Let those refreshes finish against the fed status before
            // the seam is reset (PanelStatusTestSupport does that when the test's disposable is disposed).
            long end = System.currentTimeMillis() + 1_500;
            while (System.currentTimeMillis() < end) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(50);
            }
            if (changeDir != null) {
                deleteTree(changeDir.getParent());
                // Tell the VFS now, while the root is still allowed, so no later refresh in another test reports it.
                LocalFileSystem.getInstance().refreshNioFiles(List.of(changeDir.getParent()), false, true, null);
            }
        } finally {
            super.tearDown();
        }
    }

    // --- helpers ---

    /** The captured 1.15.0 instructions for the artifact, pointed at the on-disk change directory. */
    private String instructionsJson(String artifactId) {
        String name = artifactId.equals("specs") ? "instructions-specs.json" : "instructions-design.json";
        String json = fixture("1.15.0/instructions/" + name);
        return json.replace("/fixture/demo-project/openspec/changes/demo", changeDir.toString().replace('\\', '/'));
    }

    private static String fixture(String name) {
        return PanelStatusTestSupport.fixture(name);
    }

    private void answerWith(String note) {
        workflowPanel.setRegenerateDialogForTest(request -> {
            asked.add(request);
            return note;
        });
    }

    private void acceptReviews() {
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> {
            reviewed.add(writes);
            return writes;
        });
    }

    private void regenerate(String artifactId, String outputPath, DeliveryMode mode) {
        workflowPanel.setDeliveryModeForTest(mode);
        JPopupMenu menu = workflowPanel.chipContextMenu(
                new ArtifactInfo(artifactId, outputPath, ArtifactStatus.DONE, List.of()), new JLabel(artifactId));
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item && "Regenerate".equals(item.getText())) {
                item.doClick();
                dispatch();
                return;
            }
        }
        fail("no Regenerate item");
    }

    private static void dispatch() {
        for (int i = 0; i < 5; i++) PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private String design() throws IOException {
        return Files.readString(changeDir.resolve("design.md"));
    }

    /** Every file under the temp root, path to content. */
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(changeDir.getParent())) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                files.put(changeDir.getParent().relativize(p).toString(), Files.readString(p));
            }
        }
        return files;
    }

    private boolean raisedContaining(String text) {
        return raised.stream().anyMatch(n -> n.getContent().contains(text));
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }

    // --- the dialog ---

    public void testTheDialogIsAskedOnceWithTheCompletedDownstreamArtifacts() throws Exception {
        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals(1, asked.size());
        assertEquals("design", asked.getFirst().artifactId());
        // In the captured status, tasks requires design and is done.
        assertEquals(List.of("tasks"), asked.getFirst().downstream());
    }

    public void testCancelGeneratesCopiesAndWritesNothing() throws Exception {
        workflowPanel.setRegenerateDialogForTest(request -> null);
        Map<String, String> before = snapshot();

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);
        regenerate("design", "design.md", DeliveryMode.CLIPBOARD);

        verify(api, never()).generateRaw(anyString());
        assertNull("nothing was copied", workflowPanel.lastPromptForTest());
        assertTrue(reviewed.isEmpty());
        assertEquals(before, snapshot());
    }

    public void testCopyPromptOpensNoDialogAndCopiesThePlainPrompt() throws Exception {
        workflowPanel.setDeliveryModeForTest(DeliveryMode.CLIPBOARD);
        JPopupMenu menu = workflowPanel.chipContextMenu(
                new ArtifactInfo("design", "design.md", ArtifactStatus.DONE, List.of()), new JLabel("design"));
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item && "Copy prompt".equals(item.getText())) item.doClick();
        }
        dispatch();

        assertTrue("Copy prompt asks nothing", asked.isEmpty());
        ArtifactInstruction instruction = getProject().getService(ArtifactOrchestrationService.class)
                .getInstruction(CHANGE, "design");
        assertTrue(workflowPanel.lastPromptForTest().startsWith(instruction.buildPrompt()));
        assertFalse(workflowPanel.lastPromptForTest().contains(ArtifactInstruction.NOTE_HEADING));
    }

    // --- the note in each delivery mode ---

    public void testTheNoteReachesTheDirectApiPromptOnceWithTheCurrentFile() throws Exception {
        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals(1, sentPrompts.size());
        String prompt = sentPrompts.getFirst();
        assertEquals(1, count(prompt, ArtifactInstruction.NOTE_HEADING + "\n" + NOTE));
        assertTrue(prompt.contains(ArtifactInstruction.CURRENT_VERSION_HEADING));
        assertTrue(prompt.indexOf("Use a cache.") < prompt.indexOf(ArtifactInstruction.NOTE_HEADING));
    }

    public void testTheNoteReachesTheClipboardPrompt() throws Exception {
        regenerate("design", "design.md", DeliveryMode.CLIPBOARD);

        assertEquals(1, count(workflowPanel.lastPromptForTest(), ArtifactInstruction.NOTE_HEADING + "\n" + NOTE));
        verify(api, never()).generateRaw(anyString());
    }

    public void testTheNoteReachesTheEditorTabPromptWhichIsNeverSaved() throws Exception {
        Path tmpPrompt = Path.of(System.getProperty("java.io.tmpdir"), "openspec-design-prompt.md");
        Files.deleteIfExists(tmpPrompt);

        regenerate("design", "design.md", DeliveryMode.EDITOR_TAB);

        LightVirtualFile tab = workflowPanel.lastEditorTabPromptForTest();
        assertNotNull(tab);
        assertEquals(1, count(tab.getContent().toString(), ArtifactInstruction.NOTE_HEADING + "\n" + NOTE));
        assertFalse("the prompt is never written to disk", Files.exists(tmpPrompt));
    }

    public void testTheNoteIsWrittenNowhereButThePrompt() throws Exception {
        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        for (Map.Entry<String, String> file : snapshot().entrySet()) {
            assertFalse(file.getKey(), file.getValue().contains(NOTE));
        }
        String settings = com.intellij.openapi.util.JDOMUtil.writeElement(
                com.intellij.util.xmlb.XmlSerializer.serialize(OpenSpecSettings.getInstance(getProject()).getState()));
        assertFalse("the note is not saved in settings: " + settings, settings.contains(NOTE));
        assertTrue("anti-vacuity: the note was sent", sentPrompts.getFirst().contains(NOTE));
    }

    // --- review before writing ---

    public void testAcceptWritesTheResult() throws Exception {
        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals(1, reviewed.size());
        PlannedWrite shown = reviewed.getFirst().getFirst();
        assertEquals(changeDir.resolve("design.md"), shown.path());
        assertEquals(CURRENT_DESIGN, shown.currentText());
        assertEquals(GENERATED_DESIGN, shown.newText());
        assertEquals(GENERATED_DESIGN, design());
        assertTrue(raisedContaining("Regenerated design"));
    }

    public void testRejectWritesNothing() throws Exception {
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> null);
        Map<String, String> before = snapshot();

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals(before, snapshot());
        assertTrue(raisedContaining("Discarded the regenerated design"));
    }

    public void testAMultiFileResultIsReviewedAndWrittenAsOne() throws Exception {
        Files.createDirectories(changeDir.resolve("specs/auth"));
        Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
        response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# Billing\n";

        regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

        assertEquals(1, reviewed.size());
        List<PlannedWrite> writes = reviewed.getFirst();
        assertEquals(List.of(false, true), writes.stream().map(PlannedWrite::isNew).toList());
        assertEquals("# New auth\n", Files.readString(changeDir.resolve("specs/auth/spec.md")));
        assertEquals("# Billing\n", Files.readString(changeDir.resolve("specs/billing/spec.md")));
    }

    public void testARejectedMultiFileResultWritesNeitherFile() throws Exception {
        Files.createDirectories(changeDir.resolve("specs/auth"));
        Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
        response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# Billing\n";
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> null);

        regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

        assertEquals("# Old auth\n", Files.readString(changeDir.resolve("specs/auth/spec.md")));
        assertFalse(Files.exists(changeDir.resolve("specs/billing")));
    }

    public void testARefusedResponseIsNotReviewed() throws Exception {
        response = "# no file markers\n";
        Map<String, String> before = snapshot();

        regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

        assertTrue(reviewed.isEmpty());
        assertEquals(before, snapshot());
        assertTrue(raisedContaining("Failed to write artifact"));
    }

    public void testAFileChangedOnDiskDuringTheReviewIsNotOverwritten() throws Exception {
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> {
            try {
                Files.writeString(changeDir.resolve("design.md"), "# Design\n\nEdited outside.\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return writes;
        });

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals("# Design\n\nEdited outside.\n", design());
        assertTrue(raisedContaining("changed since the review"));
    }

    public void testAFileCreatedOutsideDuringTheReviewIsNotOverwritten() throws Exception {
        Files.createDirectories(changeDir.resolve("specs/auth"));
        Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
        response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# Billing\n";
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> {
            try {
                Files.createDirectories(changeDir.resolve("specs/billing"));
                Files.writeString(changeDir.resolve("specs/billing/spec.md"), "# Billing by hand\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return writes;
        });

        regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

        assertEquals("# Billing by hand\n", Files.readString(changeDir.resolve("specs/billing/spec.md")));
        assertEquals("# Old auth\n", Files.readString(changeDir.resolve("specs/auth/spec.md")));
        assertTrue(raisedContaining("changed since the review"));
    }

    public void testAMultiFileWriteThatCannotFinishWritesNoFile() throws Exception {
        Files.createDirectories(changeDir.resolve("specs/auth"));
        Files.createDirectories(changeDir.resolve("specs/billing"));
        Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
        Path billing = changeDir.resolve("specs/billing/spec.md");
        Files.writeString(billing, "# Old billing\n");
        assertTrue(billing.toFile().setWritable(false));
        try {
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(billing).refresh(false, false);
            response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# New billing\n";

            regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

            assertEquals("the first file is not written", "# Old auth\n",
                    Files.readString(changeDir.resolve("specs/auth/spec.md")));
            assertEquals("# Old billing\n", Files.readString(billing));
            assertTrue(raisedContaining("read-only"));
        } finally {
            billing.toFile().setWritable(true);
        }
    }

    public void testASecondFileChangedDuringTheReviewWritesNoFile() throws Exception {
        Files.createDirectories(changeDir.resolve("specs/auth"));
        Files.createDirectories(changeDir.resolve("specs/billing"));
        Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
        Files.writeString(changeDir.resolve("specs/billing/spec.md"), "# Old billing\n");
        response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# New billing\n";
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> {
            try {
                Files.writeString(changeDir.resolve("specs/billing/spec.md"), "# Billing by hand\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return writes;
        });

        regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

        assertEquals("the unchanged first file is not written either", "# Old auth\n",
                Files.readString(changeDir.resolve("specs/auth/spec.md")));
        assertEquals("# Billing by hand\n", Files.readString(changeDir.resolve("specs/billing/spec.md")));
        assertTrue(raisedContaining("changed since the review"));
    }

    public void testTheReviewShowsAnUnsavedEditMadeBeforeRegenerating() throws Exception {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir.resolve("design.md"));
        Document document = FileDocumentManager.getInstance().getDocument(file);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("# Design\n\nUnsaved before.\n"));

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals("the review compares with the editor's text", "# Design\n\nUnsaved before.\n",
                reviewed.getFirst().getFirst().currentText());
        assertEquals("and Accept writes, since nothing changed after the review", GENERATED_DESIGN, design());
    }

    public void testAnUnsavedEditDuringTheReviewIsNotOverwritten() throws Exception {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir.resolve("design.md"));
        Document document = FileDocumentManager.getInstance().getDocument(file);
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> {
            WriteCommandAction.runWriteCommandAction(project, () -> document.setText("# Design\n\nUnsaved edit.\n"));
            return writes;
        });

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals("the unsaved edit is kept", "# Design\n\nUnsaved edit.\n", document.getText());
        assertEquals("the file on disk is untouched", CURRENT_DESIGN, design());
        assertTrue(raisedContaining("changed since the review"));
    }

    public void testAcceptingCanBeUndone() throws Exception {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir.resolve("design.md"));
        com.intellij.openapi.fileEditor.FileEditor editor = com.intellij.openapi.fileEditor.FileEditorManager
                .getInstance(getProject()).openFile(file, false)[0];

        try {
            regenerate("design", "design.md", DeliveryMode.DIRECT_API);

            com.intellij.openapi.command.undo.UndoManager undo =
                    com.intellij.openapi.command.undo.UndoManager.getInstance(getProject());
            assertTrue("the write is undoable", undo.isUndoAvailable(editor));
            assertEquals("Regenerate design", undo.getUndoActionNameAndDescription(editor).first.replace("Undo ", "").replace("_", "").trim());
        } finally {
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).closeFile(file);
        }
    }

    public void testACancelledRunIsNeverReviewed() throws Exception {
        boolean[] returned = new boolean[1];
        when(api.generateRaw(anyString())).thenAnswer(inv -> {
            com.intellij.openapi.progress.ProgressIndicator indicator =
                    ProgressManager.getInstance().getProgressIndicator();
            assertNotNull("the call runs under the task's indicator", indicator);
            indicator.cancel();
            returned[0] = true;
            return GENERATED_DESIGN;
        });

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertTrue("the response came back, cancelled", returned[0]);
        assertTrue(reviewed.isEmpty());
        assertEquals(CURRENT_DESIGN, design());
    }

    public void testGeneratingFromAReadyChipWritesWithoutAReview() throws Exception {
        Files.delete(changeDir.resolve("design.md"));
        workflowPanel.setDeliveryModeForTest(DeliveryMode.DIRECT_API);
        JPopupMenu menu = workflowPanel.chipContextMenu(
                new ArtifactInfo("design", "design.md", ArtifactStatus.READY, List.of()), new JLabel("design"));
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item && "Generate".equals(item.getText())) item.doClick();
        }
        dispatch();

        assertTrue("no review for a new artifact", reviewed.isEmpty());
        assertTrue("no note dialog either", asked.isEmpty());
        assertEquals(GENERATED_DESIGN, design());
        verify(api).generateRaw(any());
    }
}
