package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * The deltas preview through the real panel when the CLI is installed but can't read the change: the panel shows the
 * rejected placeholder (not "install the CLI"), and doesn't cache it, so once the CLI can read the change, selecting it
 * again shows its deltas. The "CLI" is a shell script that prints captured output, so this runs on macOS and Linux only.
 */
public class ChangeDeltasRejectionPanelTest extends OpenSpecIntegrationTestBase {

    private File script;
    private File changeDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (SystemInfo.isWindows) return;
        File dir = FileUtil.createTempDirectory("deltas-rejection", null, true);
        // The CLI runs in the project's base dir, which a light project doesn't create on disk.
        Files.createDirectories(Path.of(getProject().getBasePath()));
        changeDir = new File(dir, "nosuch");
        assertTrue(changeDir.mkdirs());
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
        script = new File(dir, "openspec");
        answerWith("1.15.0/errors/show-change-missing.stdout", 1);
        CliDetectionService detection = getProject().getService(CliDetectionService.class);
        CliDetectionTestSupport.fakeAvailable(detection, "1.15.0");
        // The version probe is faked; the show itself runs this script.
        assertTrue(detection.tryPath(script.getPath()));
    }

    /** Makes the fake CLI print the captured {@code fixture} and exit with {@code exitCode}. */
    private void answerWith(String fixture, int exitCode) throws Exception {
        Path source = Path.of(getClass().getResource("/fixtures/cli/" + fixture).toURI());
        Files.writeString(script.toPath(), "#!/bin/sh\ncat '" + source + "'\nexit " + exitCode + "\n");
        assertTrue(script.setExecutable(true));
    }

    private DefaultTreeModel model() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changes = new DefaultMutableTreeNode(
                new TreeNodeData("Changes", TreeNodeType.CHANGES, null, null, null, "Active changes"));
        changes.add(new DefaultMutableTreeNode(new TreeNodeData("nosuch", TreeNodeType.CHANGE, changeDir.getPath(), "nosuch", null)));
        changes.add(new DefaultMutableTreeNode(new TreeNodeData("other", TreeNodeType.CHANGES, null, null, null, "spacer")));
        root.add(changes);
        return new DefaultTreeModel(root);
    }

    private static void waitFor(BooleanSupplier condition, String what) {
        long end = System.currentTimeMillis() + 15_000;
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

    private static String classOf(String marker) {
        return "class=\"" + marker + "\"";
    }

    private static void select(OpenSpecToolWindowPanel panel, DefaultMutableTreeNode node) {
        panel.treeForTest().setSelectionPath(new TreePath(node.getPath()));
    }

    public void testARejectedChangeShowsTheClisReasonAndIsNotCached() throws Exception {
        if (SystemInfo.isWindows) return;
        DefaultTreeModel model = model();
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        waitFor(() -> panel.treeModelForTest() == model, "the model is shown");
        DefaultMutableTreeNode changes = (DefaultMutableTreeNode) ((DefaultMutableTreeNode) model.getRoot()).getChildAt(0);
        DefaultMutableTreeNode change = (DefaultMutableTreeNode) changes.getChildAt(0);

        select(panel, change);
        waitFor(() -> OpenSpecToolWindowPanel.PREVIEW_CLI_REJECTED_NAME.equals(panel.previewAccessibleNameForTest()),
                "the rejected placeholder");
        String rejected = panel.previewHtmlForTest();
        // The pane's CSS names every marker, so match the element's class, not the bare name.
        assertTrue(rejected, rejected.contains(classOf(SpecPreviewRenderer.CLI_REJECTED_MARKER)));
        assertTrue(rejected, rejected.contains("not found"));
        assertFalse(rejected, rejected.contains(classOf(SpecPreviewRenderer.CLI_UNAVAILABLE_MARKER)));

        // The CLI can read the change now; a cached rejection would keep showing the placeholder.
        answerWith("1.14.3/additive-shapes/show-change.json", 0);
        select(panel, (DefaultMutableTreeNode) changes.getChildAt(1));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        select(panel, change);
        waitFor(() -> panel.previewAccessibleNameForTest() != null
                        && panel.previewAccessibleNameForTest().startsWith(OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME),
                "the change's deltas after the CLI can read it");
        assertFalse(panel.previewHtmlForTest().contains(classOf(SpecPreviewRenderer.CLI_REJECTED_MARKER)));
    }
}
