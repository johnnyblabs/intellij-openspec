package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The startup race: the tool window is built while the project-open CLI detection is still running. The status must
 * read "detecting" rather than "not found", and switch to "available" when detection completes — with no manual
 * refresh. Detection is held mid-way through the real process-runner seam.
 */
public class ToolWindowCliStatusStartupTest extends OpenSpecIntegrationTestBase {

    public void testStatusFollowsDetectionThatCompletesAfterThePanelIsBuilt() throws Exception {
        CliDetectionService detection = new CliDetectionService(getProject());
        detection.setIsWindowsForTest(false);
        CountDownLatch probing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CliDetectionTestSupport.setRunner(detection, cmd -> {
            probing.countDown();
            release.await(10, TimeUnit.SECONDS);
            return cmd.getParametersList().getList().contains("--version") ? "openspec 1.14.3" : null;
        });
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());

        // Startup detection begins (as the post-startup activity does) and is held at its first probe.
        Future<?> startup = ApplicationManager.getApplication().executeOnPooledThread(detection::detect);
        assertTrue(probing.await(10, TimeUnit.SECONDS));

        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject());
        Disposer.register(getTestRootDisposable(), panel);
        assertEquals("while startup detection runs, the CLI is not reported missing",
                "CLI: detecting…", panel.cliStatusTextForTest());

        // Let the panel's own initial refresh finish first (it also redraws the status when its tree lands), so the
        // completed detection below can only reach the label through the detection announcement — the real race.
        javax.swing.tree.TreeModel initialModel = panel.treeModelForTest();
        long refreshEnd = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < refreshEnd && panel.treeModelForTest() == initialModel) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertNotSame("the panel's initial refresh must have completed", initialModel, panel.treeModelForTest());
        assertEquals("still detecting after the panel's own refresh", "CLI: detecting…", panel.cliStatusTextForTest());

        release.countDown();
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end
                && !(startup.isDone() && panel.cliStatusTextForTest().startsWith("CLI: available"))) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertEquals("the completed detection redraws the status, with no refresh",
                "CLI: available v1.14.3", panel.cliStatusTextForTest());
    }
}
