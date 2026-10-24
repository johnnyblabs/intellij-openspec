package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.treeStructure.Tree;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;

import javax.accessibility.AccessibleContext;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A tool-window refresh keeps the user's place: the real refresh path (pooled build, EDT swap) with models the test
 * supplies. Each test fails against the old refresh, which swapped models without restoring the selection and keyed
 * expansion on node labels.
 */
public class TreeRefreshStateTest extends OpenSpecIntegrationTestBase {

    private File dir;
    private final AtomicReference<Supplier<DefaultTreeModel>> next = new AtomicReference<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dir = FileUtil.createTempDirectory("tree-refresh", null, true);
        for (String change : List.of("add-auth", "other-change")) {
            File changeDir = new File(dir, change);
            assertTrue(changeDir.mkdirs());
            Files.writeString(new File(changeDir, "tasks.md").toPath(), "# Tasks\n\n- [ ] 1.1 Do it\n");
        }
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    }

    private OpenSpecToolWindowPanel panel(DefaultTreeModel first) {
        next.set(() -> first);
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> next.get().get());
        Disposer.register(getTestRootDisposable(), panel);
        panel.setRefreshRetryDelayForTest(100);
        waitFor(() -> panel.treeModelForTest() == first, "the first model is shown");
        return panel;
    }

    private DefaultTreeModel model(String... changes) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changesNode = new DefaultMutableTreeNode(
                new TreeNodeData("Changes", TreeNodeType.CHANGES, null, null, null, "Active changes"));
        root.add(changesNode);
        for (String spec : changes) {
            // "name:label:changeType:artifactType"
            String[] p = spec.split(":");
            String path = new File(dir, p[0]).getPath();
            DefaultMutableTreeNode change = new DefaultMutableTreeNode(
                    new TreeNodeData(p[1], TreeNodeType.valueOf(p[2]), path, p[0], null, "tip"));
            change.add(new DefaultMutableTreeNode(new TreeNodeData("tasks", TreeNodeType.valueOf(p[3]),
                    path + "/tasks.md", p[0], "tasks", "tip")));
            changesNode.add(change);
        }
        return new DefaultTreeModel(root);
    }

    private static DefaultMutableTreeNode find(DefaultTreeModel model, String changeName, boolean artifact) {
        DefaultMutableTreeNode changes = (DefaultMutableTreeNode) ((DefaultMutableTreeNode) model.getRoot()).getChildAt(0);
        for (int i = 0; i < changes.getChildCount(); i++) {
            DefaultMutableTreeNode change = (DefaultMutableTreeNode) changes.getChildAt(i);
            if (changeName.equals(((TreeNodeData) change.getUserObject()).changeName())) {
                return artifact ? (DefaultMutableTreeNode) change.getChildAt(0) : change;
            }
        }
        throw new AssertionError("no change " + changeName);
    }

    private static TreePath pathTo(DefaultMutableTreeNode node) {
        return new TreePath(node.getPath());
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

    /** Lets pending alarms (preview, retry) run, then settles the event queue. */
    private static void settle(long millis) {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static List<String> recordPreviewNames(OpenSpecToolWindowPanel panel) {
        List<String> names = new ArrayList<>();
        // The preview pane's accessible context fires on every name change; the panel exposes the pane's name.
        panel.previewAccessibleContextForTest().addPropertyChangeListener(e -> {
            if (AccessibleContext.ACCESSIBLE_NAME_PROPERTY.equals(e.getPropertyName())) {
                names.add(String.valueOf(e.getNewValue()));
            }
        });
        return names;
    }

    public void testSelectionExpansionAndPreviewSurviveALabelAndStatusChange() {
        DefaultTreeModel before = model("add-auth:add-auth 1/3:CHANGE:ARTIFACT_DONE");
        OpenSpecToolWindowPanel panel = panel(before);
        Tree tree = panel.treeForTest();
        tree.expandPath(pathTo(find(before, "add-auth", false)));
        tree.setSelectionPath(pathTo(find(before, "add-auth", true)));
        waitFor(() -> OpenSpecToolWindowPanel.PREVIEW_RENDERED_NAME.equals(panel.previewAccessibleNameForTest()),
                "the selected artifact's preview renders");

        List<String> names = recordPreviewNames(panel);
        DefaultTreeModel after = model("add-auth:add-auth 3/3:CHANGE_ARCHIVE_READY:ARTIFACT_DONE");
        next.set(() -> after);
        panel.refresh();
        waitFor(() -> panel.treeModelForTest() == after, "the rebuilt model is shown");
        settle(400);

        DefaultMutableTreeNode change = find(after, "add-auth", false);
        assertTrue("the change stays expanded although its label and status changed",
                tree.isExpanded(pathTo(change)));
        assertNotNull("something is selected after the refresh", tree.getSelectionPath());
        assertSame("the same artifact is selected, in the new model",
                find(after, "add-auth", true), tree.getSelectionPath().getLastPathComponent());
        assertEquals(OpenSpecToolWindowPanel.PREVIEW_RENDERED_NAME, panel.previewAccessibleNameForTest());
        assertFalse("the preview never showed the empty state during the refresh: " + names,
                names.contains(OpenSpecToolWindowPanel.PREVIEW_EMPTY_NAME));
    }

    public void testSelectionOfAChangeThatIsGoneIsClearedNotMovedToASibling() {
        DefaultTreeModel before = model("add-auth:add-auth:CHANGE:ARTIFACT_READY",
                "other-change:other-change:CHANGE:ARTIFACT_READY");
        OpenSpecToolWindowPanel panel = panel(before);
        Tree tree = panel.treeForTest();
        tree.setSelectionPath(pathTo(find(before, "add-auth", false)));

        DefaultTreeModel after = model("other-change:other-change:CHANGE:ARTIFACT_READY");
        next.set(() -> after);
        panel.refresh();
        waitFor(() -> panel.treeModelForTest() == after, "the rebuilt model is shown");
        settle(400);

        assertNull("the archived change's selection is cleared, not handed to a sibling", tree.getSelectionPath());
        assertEquals(OpenSpecToolWindowPanel.PREVIEW_EMPTY_NAME, panel.previewAccessibleNameForTest());
    }

    public void testAFailedRebuildKeepsTheTreeSilentlyAndRetriesOnce() {
        DefaultTreeModel before = model("add-auth:add-auth 1/3:CHANGE:ARTIFACT_READY");
        OpenSpecToolWindowPanel panel = panel(before);
        Tree tree = panel.treeForTest();
        tree.expandPath(pathTo(find(before, "add-auth", false)));
        tree.setSelectionPath(pathTo(find(before, "add-auth", true)));
        String status = panel.cliStatusTextForTest();

        AtomicInteger builds = new AtomicInteger();
        next.set(() -> {
            builds.incrementAndGet();
            throw new IllegalStateException("change directory removed mid-scan");
        });
        panel.refresh();
        waitFor(() -> builds.get() >= 1, "the failing build ran");
        assertSame("a failed build keeps the tree", before, panel.treeModelForTest());
        assertSame(find(before, "add-auth", true), tree.getSelectionPath().getLastPathComponent());
        assertTrue(tree.isExpanded(pathTo(find(before, "add-auth", false))));
        assertEquals("nothing is shown for a failed build", status, panel.cliStatusTextForTest());

        waitFor(() -> builds.get() >= 2, "the single retry ran");
        settle(600);
        assertEquals("exactly one retry: a retry that fails doesn't schedule another", 2, builds.get());
        assertSame(before, panel.treeModelForTest());
    }

    public void testTheRetryOfAFailedRebuildRestoresState() {
        DefaultTreeModel before = model("add-auth:add-auth 1/3:CHANGE:ARTIFACT_READY");
        OpenSpecToolWindowPanel panel = panel(before);
        Tree tree = panel.treeForTest();
        tree.setSelectionPath(pathTo(find(before, "add-auth", true)));

        DefaultTreeModel after = model("add-auth:add-auth 2/3:CHANGE:ARTIFACT_DONE");
        AtomicInteger builds = new AtomicInteger();
        next.set(() -> {
            if (builds.incrementAndGet() == 1) throw new IllegalStateException("file caught half-written");
            return after;
        });
        panel.refresh();
        waitFor(() -> panel.treeModelForTest() == after, "the retry's model is shown");
        assertSame(find(after, "add-auth", true), tree.getSelectionPath().getLastPathComponent());
    }

    public void testAFailedFirstLoadShowsTheErrorNode() {
        next.set(() -> {
            throw new IllegalStateException("broken");
        });
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> next.get().get());
        Disposer.register(getTestRootDisposable(), panel);
        waitFor(() -> {
            Object root = panel.treeModelForTest().getRoot();
            return root instanceof DefaultMutableTreeNode n && n.getChildCount() == 1
                    && String.valueOf(((DefaultMutableTreeNode) n.getChildAt(0)).getUserObject()).startsWith("Error loading");
        }, "the error node is shown");
        assertEquals("OpenSpec", ((DefaultMutableTreeNode) panel.treeModelForTest().getRoot()).getUserObject());
    }

    public void testAnOlderRefreshThatFinishesLastIsDiscarded() throws Exception {
        DefaultTreeModel first = model("add-auth:add-auth:CHANGE:ARTIFACT_READY");
        OpenSpecToolWindowPanel panel = panel(first);

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch slowStarted = new CountDownLatch(1);
        DefaultTreeModel slow = model("add-auth:add-auth 1/3:CHANGE:ARTIFACT_READY");
        DefaultTreeModel fast = model("add-auth:add-auth 2/3:CHANGE:ARTIFACT_READY");
        next.set(() -> {
            slowStarted.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return slow;
        });
        panel.refresh();
        assertTrue(slowStarted.await(10, TimeUnit.SECONDS));

        next.set(() -> fast);
        panel.refresh();
        waitFor(() -> panel.treeModelForTest() == fast, "the newer refresh's model is shown");

        release.countDown();
        settle(400);
        assertSame("the older refresh, finishing last, is discarded", fast, panel.treeModelForTest());
    }

    public void testTheSelectedArtifactSurvivesItsOwnStatusAndLabelChange() {
        DefaultTreeModel before = model("add-auth:add-auth 0/3:CHANGE:ARTIFACT_BLOCKED");
        relabelArtifact(before, "add-auth", "tasks (needs: specs, design)");
        OpenSpecToolWindowPanel panel = panel(before);
        Tree tree = panel.treeForTest();
        tree.setSelectionPath(pathTo(find(before, "add-auth", true)));

        DefaultTreeModel after = model("add-auth:add-auth 1/3:CHANGE:ARTIFACT_DONE");
        next.set(() -> after);
        panel.refresh();
        waitFor(() -> panel.treeModelForTest() == after, "the rebuilt model is shown");
        settle(300);
        assertNotNull(tree.getSelectionPath());
        assertSame("blocked -> done, and the (needs: ...) suffix gone: still the same artifact",
                find(after, "add-auth", true), tree.getSelectionPath().getLastPathComponent());
    }

    public void testStateChangedWhileARefreshIsBuildingIsKept() throws Exception {
        DefaultTreeModel before = model("add-auth:add-auth:CHANGE:ARTIFACT_DONE",
                "other-change:other-change:CHANGE:ARTIFACT_DONE");
        OpenSpecToolWindowPanel panel = panel(before);
        Tree tree = panel.treeForTest();
        tree.expandPath(pathTo(find(before, "add-auth", false)));
        tree.setSelectionPath(pathTo(find(before, "add-auth", true)));

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch building = new CountDownLatch(1);
        DefaultTreeModel after = model("add-auth:add-auth 1/3:CHANGE:ARTIFACT_DONE",
                "other-change:other-change 1/3:CHANGE:ARTIFACT_DONE");
        next.set(() -> {
            building.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return after;
        });
        panel.refresh();
        assertTrue(building.await(10, TimeUnit.SECONDS));
        // While the build runs, the user expands and selects something else.
        tree.expandPath(pathTo(find(before, "other-change", false)));
        tree.setSelectionPath(pathTo(find(before, "other-change", true)));
        release.countDown();
        waitFor(() -> panel.treeModelForTest() == after, "the rebuilt model is shown");
        settle(300);

        assertTrue("an expansion made during the build is kept", tree.isExpanded(pathTo(find(after, "other-change", false))));
        assertSame("a selection made during the build is kept",
                find(after, "other-change", true), tree.getSelectionPath().getLastPathComponent());
    }

    public void testClearingTheSelectionDoesNotReactivateTheRemovedChange() {
        DefaultTreeModel model = model("add-auth:add-auth:CHANGE:ARTIFACT_DONE",
                "other-change:other-change:CHANGE:ARTIFACT_DONE");
        OpenSpecToolWindowPanel panel = panel(model);
        Tree tree = panel.treeForTest();
        tree.setSelectionPath(pathTo(find(model, "add-auth", false)));
        assertEquals("add-auth", panel.workflowPanelForTest().activeChangeForTest());

        panel.workflowPanelForTest().setActiveChange("other-change");
        tree.clearSelection();
        assertEquals("a clear fires with the removed path; it must not switch the workflow back",
                "other-change", panel.workflowPanelForTest().activeChangeForTest());
    }

    private static void relabelArtifact(DefaultTreeModel model, String changeName, String label) {
        DefaultMutableTreeNode node = find(model, changeName, true);
        TreeNodeData d = (TreeNodeData) node.getUserObject();
        node.setUserObject(new TreeNodeData(label, d.type(), d.filePath(), d.changeName(), d.artifactId(), d.tooltip()));
    }
}
