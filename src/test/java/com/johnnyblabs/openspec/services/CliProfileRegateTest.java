package com.johnnyblabs.openspec.services;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.util.CliRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Action gating follows a CLI change during a session: a detection that reports a different CLI, announced on the
 * project bus, reaches the {@code plugin.xml} listener, which re-resolves the active workflows off the EDT and then
 * updates the gating UI. The config answers are the real 1.15.0 {@code config list --json} captures.
 */
public class CliProfileRegateTest extends BasePlatformTestCase {

    private CliDetectionService detection;
    private WorkflowProfileService profiles;
    private final AtomicInteger configReads = new AtomicInteger();
    private final AtomicInteger uiUpdates = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        detection = new CliDetectionService(getProject());
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        profiles = new WorkflowProfileService(getProject());
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profiles, getTestRootDisposable());
        String fresh = fixture("1.15.0/config-list/fresh.json");
        profiles.setConfigListForTest(() -> {
            configReads.incrementAndGet();
            return new CliRunner.CliResult(0, fresh, "");
        });
        profiles.setAfterCliRefreshForTest(p -> uiUpdates.incrementAndGet());
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = CliProfileRegateTest.class.getResourceAsStream("/fixtures/cli/" + name)) {
            assertNotNull(name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void pumpUntil(BooleanSupplier done) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            if (done.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("timed out");
    }

    private static void pumpFor(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
    }

    public void testAnUpgradeDuringTheSessionReGatesActions() throws Exception {
        CliDetectionTestSupport.fakeAvailable(detection, "1.5.0");
        assertFalse("sanity: 1.5's core set has no update", profiles.isWorkflowEnabled("update"));

        // Detection publishes on the project bus; the plugin.xml listener picks it up.
        CliDetectionTestSupport.fakeAvailable(detection, "1.6.0");
        pumpUntil(() -> profiles.getActiveWorkflows().contains("update") && uiUpdates.get() == 1);
        assertTrue(profiles.isWorkflowEnabled("update"));
    }

    public void testTheSameCliAgainDoesNotReResolve() throws Exception {
        CliDetectionTestSupport.fakeAvailable(detection, "1.6.0");
        profiles.getActiveWorkflows();
        int reads = configReads.get();
        assertEquals("sanity: resolved once", 1, reads);

        CliDetectionTestSupport.fakeAvailable(detection, "1.6.0");
        pumpFor(300);
        assertEquals(reads, configReads.get());
        assertEquals(0, uiUpdates.get());
    }

    public void testNoReResolveBeforeAnythingAskedForTheWorkflows() throws Exception {
        CliDetectionTestSupport.fakeAvailable(detection, "1.6.0");
        pumpFor(300);
        assertEquals("the first ask resolves for the CLI it finds", 0, configReads.get());
    }
}
