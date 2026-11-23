package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Disposing the tool-window panel must stop the workflow panel's artifact watcher, even when the panel was never shown.
 * The watcher's Swing timers used to stop only in {@code removeNotify}, which a headless panel never gets, so a test that
 * delivered a generation left them running and a later test failed the platform's "Not disposed javax.swing.Timer" check.
 */
public class WorkflowPanelDisposalTest extends OpenSpecIntegrationTestBase {

    public void testDisposingToolWindowPanelStopsTheArtifactWatcher() throws Exception {
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        Path changeDir = Files.createTempDirectory("watcher-disposal");
        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        // Let the panel's first async load finish, as the other panel tests do: disposing mid-load trips its Alarm.
        long end = System.currentTimeMillis() + 10_000;
        while (panel.treeModelForTest() != model && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertSame("precondition: the panel loaded its tree", model, panel.treeModelForTest());
        WorkflowActionPanel workflowPanel = panel.workflowPanelForTest();
        try {
            workflowPanel.startFileWatcherForTest(changeDir.toString(), "proposal.md");
            assertTrue("precondition: generating starts the watcher", workflowPanel.hasRunningWatcherForTest());

            Disposer.dispose(panel);

            assertFalse("disposing the tool-window panel must stop the watcher's timers",
                    workflowPanel.hasRunningWatcherForTest());
        } finally {
            // Never leak the timers into the next test, even when the assertion above fails.
            workflowPanel.disposeForTest();
        }
    }
}
