package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import com.johnnyblabs.openspec.util.CliOutputParser;

import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The preview's links through the real panel: an artifact previews its change with an open-in-editor link
 * only when its file exists, the link is never cached into the change's deltas, activating it opens the
 * file (and nothing outside the change), and the empty state offers Propose only when there are no changes.
 * The CLI is pinned absent so the CLI-unavailable state is deterministic.
 */
public class PreviewLinksPanelTest extends OpenSpecIntegrationTestBase {

    private File changeDir;
    private File outside;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        File dir = FileUtil.createTempDirectory("preview-links", null, true);
        changeDir = new File(dir, "add-auth");
        assertTrue(changeDir.mkdirs());
        Files.writeString(new File(changeDir, outputPath("proposal")).toPath(), "# Proposal\n");
        outside = new File(dir, "outside.md");
        Files.writeString(outside.toPath(), "# Outside\n");
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    }

    /** An artifact's {@code outputPath}, from captured {@code status --json} output. */
    private static String outputPath(String artifactId) throws Exception {
        try (InputStream is = PreviewLinksPanelTest.class.getResourceAsStream("/fixtures/cli/status-with-context.json")) {
            List<ArtifactInfo> artifacts = CliOutputParser.parseChangeStatus(
                    new String(is.readAllBytes(), StandardCharsets.UTF_8)).getArtifacts();
            return artifacts.stream().filter(a -> artifactId.equals(a.id())).findFirst().orElseThrow().outputPath();
        }
    }

    private DefaultTreeModel model() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changes = new DefaultMutableTreeNode(
                new TreeNodeData("Changes", TreeNodeType.CHANGES, null, null, null, "Active changes"));
        String path = changeDir.getPath();
        DefaultMutableTreeNode change = new DefaultMutableTreeNode(
                new TreeNodeData("add-auth", TreeNodeType.CHANGE, path, "add-auth", null));
        change.add(new DefaultMutableTreeNode(new TreeNodeData("proposal", TreeNodeType.ARTIFACT_DONE,
                path + "/" + outputPath("proposal"), "add-auth", "proposal")));
        // design.md is never written: a ready artifact with no file yet.
        change.add(new DefaultMutableTreeNode(new TreeNodeData("design", TreeNodeType.ARTIFACT_READY,
                path + "/" + outputPath("design"), "add-auth", "design")));
        changes.add(change);
        root.add(changes);
        return new DefaultTreeModel(root);
    }

    private OpenSpecToolWindowPanel panel(DefaultTreeModel model) {
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        waitFor(() -> panel.treeModelForTest() == model, "the model is shown");
        return panel;
    }

    private static DefaultMutableTreeNode node(DefaultTreeModel model, int... indexes) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) model.getRoot();
        for (int i : indexes) node = (DefaultMutableTreeNode) node.getChildAt(i);
        return node;
    }

    private static void select(OpenSpecToolWindowPanel panel, DefaultMutableTreeNode node) {
        panel.treeForTest().setSelectionPath(new TreePath(node.getPath()));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) n++;
        return n;
    }

    private static void waitFor(BooleanSupplier condition, String what) {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end && !condition.getAsBoolean()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        assertTrue("timed out waiting: " + what, condition.getAsBoolean());
    }

    /** Waits for a render of {@code node} that differs from the pane's current content. */
    private static String selectAndRender(OpenSpecToolWindowPanel panel, DefaultMutableTreeNode node, String expectedName) {
        String before = panel.previewHtmlForTest();
        select(panel, node);
        waitFor(() -> expectedName.equals(panel.previewAccessibleNameForTest()) && !before.equals(panel.previewHtmlForTest()),
                "the preview renders " + expectedName);
        return panel.previewHtmlForTest();
    }

    private void activate(OpenSpecToolWindowPanel panel, String href) {
        var pane = panel.previewPaneForTest();
        pane.fireHyperlinkUpdate(new HyperlinkEvent(pane, HyperlinkEvent.EventType.ACTIVATED, null, href));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    public void testAnArtifactWithAFileShowsItsChangeWithAnOpenLink() throws Exception {
        DefaultTreeModel model = model();
        OpenSpecToolWindowPanel panel = panel(model);
        String html = selectAndRender(panel, node(model, 0, 0, 0), OpenSpecToolWindowPanel.PREVIEW_CLI_UNAVAILABLE_NAME);
        assertTrue(html.contains(SpecPreviewRenderer.HEADER_MARKER));
        assertTrue(html.contains("add-auth"));
        assertEquals("one open link", 1, count(html, "openspec-open:proposal.md"));
    }

    public void testAnArtifactWithNoFileShowsItsChangeWithoutALink() throws Exception {
        DefaultTreeModel model = model();
        OpenSpecToolWindowPanel panel = panel(model);
        String html = selectAndRender(panel, node(model, 0, 0, 1), OpenSpecToolWindowPanel.PREVIEW_CLI_UNAVAILABLE_NAME);
        assertTrue(html.contains(SpecPreviewRenderer.CLI_UNAVAILABLE_MARKER));
        assertFalse("design.md doesn't exist, so there is nothing to open", html.contains("openspec-open:"));
    }

    public void testTheOpenLinkIsNeverCachedIntoTheChangesDeltas() throws Exception {
        DefaultTreeModel model = model();
        OpenSpecToolWindowPanel panel = panel(model);
        panel.seedDeltasCacheForTest("add-auth", SpecPreviewRenderer.header("add-auth") + "<h2>auth</h2>");

        String first = selectAndRender(panel, node(model, 0, 0, 0), OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);
        assertEquals(1, count(first, "openspec-open:"));
        String change = selectAndRender(panel, node(model, 0, 0), OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);
        assertFalse("the change itself has no open link", change.contains("openspec-open:"));
        String again = selectAndRender(panel, node(model, 0, 0, 0), OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);
        assertEquals("re-selecting adds the link once", 1, count(again, "openspec-open:"));
    }

    public void testActivatingTheOpenLinkOpensTheFileAndNothingOutsideTheChange() throws Exception {
        DefaultTreeModel model = model();
        OpenSpecToolWindowPanel panel = panel(model);
        selectAndRender(panel, node(model, 0, 0, 0), OpenSpecToolWindowPanel.PREVIEW_CLI_UNAVAILABLE_NAME);
        FileEditorManager editors = FileEditorManager.getInstance(getProject());

        activate(panel, "openspec-open:../outside.md");
        VirtualFile outsideFile = LocalFileSystem.getInstance().findFileByIoFile(outside);
        assertFalse("a path out of the change opens nothing", editors.isFileOpen(outsideFile));

        activate(panel, "openspec-open:proposal.md");
        VirtualFile proposal = LocalFileSystem.getInstance().findFileByIoFile(new File(changeDir, "proposal.md"));
        assertTrue("the artifact opens in the editor", editors.isFileOpen(proposal));
    }

    public void testTheEmptyStateOffersProposeOnlyWithoutChanges() throws Exception {
        OpenSpecToolWindowPanel withChanges = panel(model());
        assertFalse(withChanges.previewHtmlForTest().contains(SpecPreviewRenderer.PROPOSE_HREF));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changes = new DefaultMutableTreeNode(
                new TreeNodeData("Changes", TreeNodeType.CHANGES, null, null, null, "Active changes"));
        changes.add(new DefaultMutableTreeNode(new TreeNodeData("No active changes", TreeNodeType.HINT, null)));
        root.add(changes);
        DefaultTreeModel empty = new DefaultTreeModel(root);
        OpenSpecToolWindowPanel noChanges = panel(empty);
        // The empty state is redrawn for the first model's selection (none), now knowing there are no changes.
        waitFor(() -> noChanges.previewHtmlForTest().contains(SpecPreviewRenderer.PROPOSE_HREF),
                "the empty state offers Propose");
    }
}
