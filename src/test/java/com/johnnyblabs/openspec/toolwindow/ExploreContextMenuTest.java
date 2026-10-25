package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Disposer;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.List;

/**
 * The tree's right-click menus offer Explore first: before Propose on Changes, and "Explore this change..." on a change,
 * followed by "Revise Change...".
 */
public class ExploreContextMenuTest extends OpenSpecIntegrationTestBase {

    private OpenSpecToolWindowPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        // The panel loads its model asynchronously; let it finish so nothing is scheduled after teardown disposes it.
        long end = System.currentTimeMillis() + 10_000;
        while (panel.treeModelForTest() != model && System.currentTimeMillis() < end) {
            com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertSame("the model is shown", model, panel.treeModelForTest());
    }

    private List<String> ids(DefaultActionGroup group) {
        List<String> ids = new ArrayList<>();
        for (AnAction child : group.getChildActionsOrStubs()) {
            String id = ActionManager.getInstance().getId(child);
            ids.add(id != null ? id : child.getClass().getSimpleName());
        }
        return ids;
    }

    public void testAChangeOffersExploreThisChangeFirst() {
        for (TreeNodeType type : List.of(TreeNodeType.CHANGE, TreeNodeType.CHANGE_DONE, TreeNodeType.CHANGE_ARCHIVE_READY)) {
            List<String> menu = ids(panel.contextMenuFor(new TreeNodeData("add-auth", type, null, "add-auth", null)));

            assertEquals(type + ": " + menu, "OpenSpec.ExploreChange", menu.get(0));
            assertTrue(type + ": " + menu, menu.contains("OpenSpec.Apply"));
        }
    }

    public void testChangesOffersExploreBeforePropose() {
        List<String> menu = ids(panel.contextMenuFor(
                new TreeNodeData("Changes", TreeNodeType.CHANGES, null, null, null, "Active changes")));

        assertEquals(List.of("OpenSpec.Explore", "OpenSpec.Propose"), menu);
    }

    public void testAChangeOffersReviseRightAfterExploreThenApplyAfterASeparator() {
        for (TreeNodeType type : List.of(TreeNodeType.CHANGE, TreeNodeType.CHANGE_DONE, TreeNodeType.CHANGE_ARCHIVE_READY)) {
            List<String> menu = ids(panel.contextMenuFor(new TreeNodeData("add-auth", type, null, "add-auth", null)));

            assertEquals(type + ": " + menu, List.of("OpenSpec.ExploreChange", "OpenSpec.ReviseChange"), menu.subList(0, 2));
            assertEquals(type + ": " + menu, "OpenSpec.Apply", menu.get(menu.indexOf("OpenSpec.ReviseChange") + 2));
        }
    }

    public void testADeltaSpecDoesNotOfferRevise() {
        List<String> menu = ids(panel.contextMenuFor(
                new TreeNodeData("spec.md", TreeNodeType.DELTA_SPEC, "/tmp/add-auth/specs/a/spec.md", "add-auth", null)));

        assertFalse(menu.toString(), menu.contains("OpenSpec.ReviseChange"));
    }

    public void testADeltaSpecDoesNotOfferExploreThisChange() {
        List<String> menu = ids(panel.contextMenuFor(
                new TreeNodeData("spec.md", TreeNodeType.DELTA_SPEC, "/tmp/add-auth/specs/a/spec.md", "add-auth", null)));

        assertFalse(menu.toString(), menu.contains("OpenSpec.ExploreChange"));
    }
}
