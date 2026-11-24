package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliRunner;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The profile switch runs the CLI off the EDT and reports back on the EDT: {@code config profile} can take seconds to
 * start, or hang until the CLI timeout, and the status bar and Settings both start a switch from the EDT.
 */
public class WorkflowProfileSwitchThreadingTest extends BasePlatformTestCase {

    private String profileBefore;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        profileBefore = OpenSpecSettings.getInstance(getProject()).getProfile();
        CliDetectionTestSupport.fakeAvailable(getProject().getService(CliDetectionService.class), "1.17.0");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            service().setCliForTest(null);
            OpenSpecSettings.getInstance(getProject()).setProfile(profileBefore);
        } finally {
            super.tearDown();
        }
    }

    private WorkflowProfileSwitchService service() {
        return getProject().getService(WorkflowProfileSwitchService.class);
    }

    public void testTheCliRunsOffTheEdtAndTheResultComesBackOnIt() throws Exception {
        AtomicBoolean cliOnEdt = new AtomicBoolean(true);
        service().setCliForTest((project, args) -> {
            cliOnEdt.set(ApplicationManager.getApplication().isDispatchThread());
            return new CliRunner.CliResult(0, "", "");
        });
        AtomicReference<WorkflowProfileSwitchService.SwitchResult> result = new AtomicReference<>();
        AtomicBoolean callbackOnEdt = new AtomicBoolean(false);

        assertTrue(ApplicationManager.getApplication().isDispatchThread());
        service().switchProfileInBackground("custom", r -> {
            callbackOnEdt.set(ApplicationManager.getApplication().isDispatchThread());
            result.set(r);
        });
        assertNull("the call returns before the CLI finishes", result.get());

        long end = System.currentTimeMillis() + 20_000;
        while (result.get() == null && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        assertNotNull("the switch finished", result.get());
        assertFalse("config profile never runs on the EDT", cliOnEdt.get());
        assertTrue("the result is handed back on the EDT", callbackOnEdt.get());
        assertEquals(WorkflowProfileSwitchService.Outcome.SWITCHED, result.get().outcome());
        assertEquals("custom", OpenSpecSettings.getInstance(getProject()).getProfile());
    }

    /** A caller that runs the CLI-backed switch on the EDT is a bug, reported loudly in development and tests. */
    public void testTheBlockingSwitchOnTheEdtIsReported() {
        service().setCliForTest((project, args) -> new CliRunner.CliResult(0, "", ""));
        assertTrue(ApplicationManager.getApplication().isDispatchThread());
        Throwable reported = null;
        try {
            service().switchProfile("custom");
        } catch (Throwable t) {
            reported = t;
        }
        assertNotNull("LOG.error fails the test when switchProfile runs on the EDT", reported);
        assertTrue(String.valueOf(reported.getMessage()), String.valueOf(reported.getMessage()).contains("must not be called on the EDT"));
    }

    /** Apply then OK, or two quick picks, while a switch runs: the CLI runs once, and the update is offered once. */
    public void testASecondSwitchWhileOneRunsIsDropped() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger cliRuns = new java.util.concurrent.atomic.AtomicInteger();
        service().setCliForTest((project, args) -> {
            cliRuns.incrementAndGet();
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new CliRunner.CliResult(0, "", "");
        });
        java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();

        service().switchProfileInBackground("custom", r -> callbacks.incrementAndGet());
        assertEquals("Settings compares against the running switch", "custom", service().profileInFlight());
        service().switchProfileInBackground("custom", r -> callbacks.incrementAndGet());
        service().switchProfileInBackground("core", r -> callbacks.incrementAndGet());
        release.countDown();

        long end = System.currentTimeMillis() + 20_000;
        while (callbacks.get() == 0 && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals("the CLI ran once", 1, cliRuns.get());
        assertEquals("one result was handed back", 1, callbacks.get());
        assertNull("nothing is in flight afterwards", service().profileInFlight());
    }
}
