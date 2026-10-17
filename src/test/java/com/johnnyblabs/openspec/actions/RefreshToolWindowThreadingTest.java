package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.concurrent.Future;

/**
 * Actions refresh the tool window from background tasks (Sync applies its specs in one). The synchronous VFS refresh
 * needs the UI thread's write-intent lock, so calling it from a pooled thread threw "Access is allowed from write
 * thread only" after every Sync Apply. The helper must be safe to call from any thread.
 */
public class RefreshToolWindowThreadingTest extends BasePlatformTestCase {

    public void testRefreshFromABackgroundThreadDoesNotThrow() throws Exception {
        Future<?> call = ApplicationManager.getApplication().executeOnPooledThread(
                () -> OpenSpecBaseAction.refreshToolWindow(getProject()));
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end && !call.isDone()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertTrue(call.isDone());
        call.get(); // rethrows anything the helper threw on the pooled thread
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    public void testRefreshOnTheUiThreadStillWorks() {
        OpenSpecBaseAction.refreshToolWindow(getProject());
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }
}
