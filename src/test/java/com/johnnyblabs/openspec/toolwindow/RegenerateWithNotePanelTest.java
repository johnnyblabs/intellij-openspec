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
import com.johnnyblabs.openspec.services.ArtifactLocalHistory;
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
    /** A Local History label as it was put: its name, and design.md on disk and in the editor at that moment. */
    private record LabelSeen(String name, String designOnDisk, boolean designUnsaved) {
    }

    private final List<LabelSeen> labels = new ArrayList<>();
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
        ArtifactLocalHistory.setLabelerForTest((project, name) -> labels.add(new LabelSeen(name, designOnDisk(),
                designUnsaved())), getTestRootDisposable());
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

    private String designOnDisk() {
        try {
            Path path = changeDir.resolve("design.md");
            return Files.exists(path) ? Files.readString(path) : null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean designUnsaved() {
        VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(changeDir.resolve("design.md"));
        Document document = file == null ? null : FileDocumentManager.getInstance().getCachedDocument(file);
        return document != null && FileDocumentManager.getInstance().isDocumentUnsaved(document);
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

    public void testANewFileIsWrittenAsGeneratedWhateverTheProjectsLineSeparator() throws Exception {
        com.intellij.psi.codeStyle.CodeStyleSettings settings = com.intellij.application.options.CodeStyle.createTestSettings();
        settings.LINE_SEPARATOR = "\r\n";
        com.intellij.application.options.CodeStyle.setTemporarySettings(getProject(), settings);
        try {
            Files.createDirectories(changeDir.resolve("specs/auth"));
            Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
            response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# Billing\n";

            regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

            assertEquals("# Billing\n", Files.readString(changeDir.resolve("specs/billing/spec.md")));
            assertEquals("an existing file keeps its own separator", "# New auth\n",
                    Files.readString(changeDir.resolve("specs/auth/spec.md")));
        } finally {
            com.intellij.application.options.CodeStyle.dropTemporarySettings(getProject());
        }
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
        Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
        // A plain file where the second file's directory would go: it can't be created, whoever runs the test.
        Files.writeString(changeDir.resolve("specs/billing"), "not a directory\n");
        response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# Billing\n";

        regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

        assertEquals("the first file is not written", "# Old auth\n",
                Files.readString(changeDir.resolve("specs/auth/spec.md")));
        assertEquals("not a directory\n", Files.readString(changeDir.resolve("specs/billing")));
        assertTrue(raisedContaining("Failed to write artifact"));
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

    // --- Local History ---

    private void editDesignWithoutSaving(String text) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir.resolve("design.md"));
        Document document = FileDocumentManager.getInstance().getDocument(file);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(text));
    }

    private List<String> linkTexts() {
        return workflowPanel.lastGuidanceLinksForTest().stream().map(WorkflowActionPanel.GuidanceLink::text).toList();
    }

    public void testAcceptedRegenerateIsLabelledWithTheOldContentStillOnDisk() throws Exception {
        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals(List.of(new LabelSeen("OpenSpec: before regenerating design", CURRENT_DESIGN, false)), labels);
        assertEquals(GENERATED_DESIGN, design());
    }

    public void testARejectedRegeneratePutsNoLabel() {
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> null);

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertTrue(labels.toString(), labels.isEmpty());
    }

    public void testARegenerateRefusedBecauseTheFileChangedPutsNoLabel() throws Exception {
        workflowPanel.setReviewerForTest((project, artifactId, dir, writes) -> {
            try {
                Files.writeString(changeDir.resolve("design.md"), "# Design\n\nEdited outside.\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return writes;
        });

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertTrue(labels.toString(), labels.isEmpty());
        assertEquals("# Design\n\nEdited outside.\n", design());
    }

    public void testAnUnsavedEditIsSavedBeforeTheLabelSoTheLabelHoldsIt() throws Exception {
        editDesignWithoutSaving("# Design\n\nUnsaved before.\n");

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals(List.of(new LabelSeen("OpenSpec: before regenerating design", "# Design\n\nUnsaved before.\n",
                false)), labels);
        assertEquals(GENERATED_DESIGN, design());
    }

    public void testGeneratingFromAReadyChipIsLabelledBeforeTheFileExists() throws Exception {
        Files.delete(changeDir.resolve("design.md"));
        workflowPanel.setDeliveryModeForTest(DeliveryMode.DIRECT_API);
        JPopupMenu menu = workflowPanel.chipContextMenu(
                new ArtifactInfo("design", "design.md", ArtifactStatus.READY, List.of()), new JLabel("design"));
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item && "Generate".equals(item.getText())) item.doClick();
        }
        dispatch();

        assertEquals(List.of(new LabelSeen("OpenSpec: before generating design", null, false)), labels);
        assertEquals(GENERATED_DESIGN, design());
    }

    public void testCompareWithPreviousShowsTheReviewedTextAgainstTheWrittenFile() throws Exception {
        editDesignWithoutSaving("# Design\n\nUnsaved before.\n");
        List<List<com.intellij.diff.requests.SimpleDiffRequest>> shown = new ArrayList<>();
        workflowPanel.setCompareShowerForTest(shown::add);

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);
        assertEquals(List.of(WorkflowActionPanel.COMPARE_WITH_PREVIOUS_LABEL), linkTexts());
        workflowPanel.lastGuidanceLinksForTest().getFirst().action().run();

        assertEquals(1, shown.size());
        com.intellij.diff.requests.SimpleDiffRequest request = shown.getFirst().getFirst();
        var left = (com.intellij.diff.contents.DocumentContent) request.getContents().get(0);
        var right = (com.intellij.diff.contents.DocumentContent) request.getContents().get(1);
        assertEquals("the left side is what the user reviewed", "# Design\n\nUnsaved before.\n",
                left.getDocument().getText());
        VirtualFile design = LocalFileSystem.getInstance().findFileByNioFile(changeDir.resolve("design.md"));
        assertEquals("the right side is the file itself", design,
                FileDocumentManager.getInstance().getFile(right.getDocument()));
        assertEquals(GENERATED_DESIGN, right.getDocument().getText());
    }

    public void testARegenerateThatOnlyCreatedFilesHasNoCompareLink() throws Exception {
        Files.delete(changeDir.resolve("design.md"));
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir);

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals(GENERATED_DESIGN, design());
        assertFalse(linkTexts().toString(), linkTexts().contains(WorkflowActionPanel.COMPARE_WITH_PREVIOUS_LABEL));
    }

    public void testRegeneratedSpecsCompareOnlyTheReplacedFileAndKeepTheirDiffLink() throws Exception {
        Files.createDirectories(changeDir.resolve("specs/auth"));
        Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Old auth\n");
        response = "<!-- file: specs/auth/spec.md -->\n# New auth\n<!-- file: specs/billing/spec.md -->\n# Billing\n";
        List<List<com.intellij.diff.requests.SimpleDiffRequest>> shown = new ArrayList<>();
        workflowPanel.setCompareShowerForTest(shown::add);

        regenerate("specs", "specs/**/*.md", DeliveryMode.DIRECT_API);

        assertEquals(List.of(WorkflowActionPanel.COMPARE_WITH_PREVIOUS_LABEL, DiffVsCurrentSpec.LABEL), linkTexts());
        workflowPanel.lastGuidanceLinksForTest().getFirst().action().run();
        assertEquals("the new billing spec has nothing to compare", 1, shown.getFirst().size());
        var left = (com.intellij.diff.contents.DocumentContent) shown.getFirst().getFirst().getContents().get(0);
        assertEquals("# Old auth\n", left.getDocument().getText());
    }

    // --- Show Local History ---

    private List<String> doneMenu(String artifactId, String outputPath, List<java.awt.Component> launchedFrom) {
        workflowPanel.setLauncherForTest((action, source) -> launchedFrom.add(source));
        JPopupMenu menu = workflowPanel.chipContextMenu(
                new ArtifactInfo(artifactId, outputPath, ArtifactStatus.DONE, List.of()), new JLabel(artifactId));
        List<String> items = new ArrayList<>();
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item) items.add(item.getText());
            else if (c instanceof JSeparator) items.add("---");
        }
        return items;
    }

    private void clickItem(String artifactId, String outputPath, String text) {
        JPopupMenu menu = workflowPanel.chipContextMenu(
                new ArtifactInfo(artifactId, outputPath, ArtifactStatus.DONE, List.of()), new JLabel(artifactId));
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item && text.equals(item.getText())) {
                item.doClick();
                return;
            }
        }
        fail("no " + text + " item");
    }

    private static com.intellij.openapi.actionSystem.DataContext snapshot(java.awt.Component source) {
        assertTrue(String.valueOf(source), source instanceof com.intellij.openapi.actionSystem.UiDataProvider);
        var provider = (com.intellij.openapi.actionSystem.UiDataProvider) source;
        return com.intellij.openapi.actionSystem.CustomizedDataContext.withSnapshot(
                com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT, provider::uiDataSnapshot);
    }

    public void testTheRealShowHistoryActionIdIsRegistered() {
        assertNotNull(com.intellij.openapi.actionSystem.ActionManager.getInstance()
                .getAction(WorkflowActionPanel.SHOW_LOCAL_HISTORY_ACTION_ID));
    }

    public void testADoneChipShowsTheLocalHistoryOfItsFile() {
        VirtualFile design = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir.resolve("design.md"));
        assertTrue("a real file on disk is tracked by Local History", ArtifactLocalHistory.isTracked(design));
        assertFalse("an in-memory file isn't", ArtifactLocalHistory.isTracked(new LightVirtualFile("x.md", "x")));
        List<java.awt.Component> launched = new ArrayList<>();

        assertEquals(List.of("Open file", "Regenerate", "Copy prompt", WorkflowActionPanel.SHOW_LOCAL_HISTORY_LABEL),
                doneMenu("design", "design.md", launched));
        clickItem("design", "design.md", WorkflowActionPanel.SHOW_LOCAL_HISTORY_LABEL);

        assertEquals(1, launched.size());
        var context = snapshot(launched.getFirst());
        assertEquals(getProject(), context.getData(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT));
        assertOrderedEquals(context.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY), design);
        assertEquals(List.of(design), context.getData(WorkflowActionPanel.LocalHistorySource.VCS_VIRTUAL_FILES));
    }

    public void testTheSpecsChipShowsTheLocalHistoryOfTheSpecsFolder() throws Exception {
        Files.createDirectories(changeDir.resolve("specs/auth"));
        Files.writeString(changeDir.resolve("specs/auth/spec.md"), "# Auth\n");
        VirtualFile specs = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir.resolve("specs"));
        List<java.awt.Component> launched = new ArrayList<>();

        assertTrue(doneMenu("specs", "specs/**/*.md", launched).contains(WorkflowActionPanel.SHOW_LOCAL_HISTORY_LABEL));
        clickItem("specs", "specs/**/*.md", WorkflowActionPanel.SHOW_LOCAL_HISTORY_LABEL);

        assertEquals(List.of(specs), snapshot(launched.getFirst())
                .getData(WorkflowActionPanel.LocalHistorySource.VCS_VIRTUAL_FILES));
    }

    public void testNoShowLocalHistoryWithoutAFileOrWithoutTheView() throws Exception {
        List<java.awt.Component> launched = new ArrayList<>();
        assertFalse(doneMenu("tasks", "tasks.md", launched).contains(WorkflowActionPanel.SHOW_LOCAL_HISTORY_LABEL));

        workflowPanel.setShowHistoryActionForTest(() -> null);
        assertFalse(doneMenu("design", "design.md", launched).contains(WorkflowActionPanel.SHOW_LOCAL_HISTORY_LABEL));
    }

    public void testHistoryPathsNameTheFileOrTheFolderBeforeTheGlob() {
        assertEquals("design.md", WorkflowActionPanel.historyRelativePath("design.md"));
        assertEquals("specs", WorkflowActionPanel.historyRelativePath("specs/**/*.md"));
        assertNull(WorkflowActionPanel.historyRelativePath("**/*.md"));
        assertNull(WorkflowActionPanel.historyRelativePath(null));
    }

    public void testSavingOffTheEdtRunsOnTheEdtBeforeTheLabel() throws Exception {
        editDesignWithoutSaving("# Design\n\nUnsaved off EDT.\n");
        var future = com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() ->
                ArtifactLocalHistory.beforeWrite(getProject(), "label", List.of(changeDir.resolve("design.md"),
                        changeDir.resolve("missing.md"))));
        long end = System.currentTimeMillis() + 10_000;
        while (!future.isDone() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        assertTrue(future.isDone());
        assertEquals(List.of(new LabelSeen("label", "# Design\n\nUnsaved off EDT.\n", false)), labels);
        assertFalse("a missing file is not created", Files.exists(changeDir.resolve("missing.md")));
    }

    public void testALabelThatCannotBePutNeverStopsTheWrite() throws Exception {
        ArtifactLocalHistory.setLabelerForTest((project, name) -> {
            throw new IllegalStateException("no Local History");
        }, getTestRootDisposable());

        regenerate("design", "design.md", DeliveryMode.DIRECT_API);

        assertEquals(GENERATED_DESIGN, design());
        assertTrue(raisedContaining("Regenerated design"));
    }

    public void testWithNothingUnsavedTheLabelNeedsNoUiThread() throws Exception {
        // The test thread is the EDT and blocks on get(): a hop to the EDT would time out here.
        var future = com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() ->
                ArtifactLocalHistory.beforeWrite(getProject(), "label", List.of(changeDir.resolve("design.md"),
                        changeDir.resolve("new.md"))));

        future.get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(List.of(new LabelSeen("label", CURRENT_DESIGN, false)), labels);
    }

    public void testShowLocalHistoryDoesNotRefreshTheFileSystemToFindTheFile() throws Exception {
        // tasks.md appears on disk without the VFS being told; the menu must not refresh on the EDT to find it. Load the
        // folder's children first, so the VFS holds a snapshot and only a refresh would show the new file.
        VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir);
        assertNotNull(dir);
        dir.getChildren();
        Files.writeString(changeDir.resolve("tasks.md"), "# Tasks\n");
        assertNull("precondition: the VFS doesn't know the file yet",
                LocalFileSystem.getInstance().findFileByNioFile(changeDir.resolve("tasks.md")));

        assertFalse(doneMenu("tasks", "tasks.md", new ArrayList<>()).contains(WorkflowActionPanel.SHOW_LOCAL_HISTORY_LABEL));
        assertNull("opening the menu didn't refresh it into the VFS",
                LocalFileSystem.getInstance().findFileByNioFile(changeDir.resolve("tasks.md")));
    }
}
