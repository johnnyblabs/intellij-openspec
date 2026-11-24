package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The machine-wide skills cache: read in the background, never on the EDT, and cleared when the project's window
 * regains focus. Its home is injected, so the real one is never read.
 */
public class GlobalSkillsCacheTest extends BasePlatformTestCase {

    private Path home;
    private final AtomicInteger reads = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        home = Files.createTempDirectory("cache-home");
        Path skill = home.resolve(".agents/skills/openspec-explore/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "x\n");
        cache().setHomeForTest(() -> {
            reads.incrementAndGet();
            return home;
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            cache().setHomeForTest(null);
            com.intellij.openapi.util.io.FileUtil.delete(home.toFile());
        } finally {
            super.tearDown();
        }
    }

    private GlobalSkillsCache cache() {
        return GlobalSkillsCache.getInstance(getProject());
    }

    public void testOffTheEdtTheFirstSnapshotReadsHome() throws Exception {
        HomeSkills skills = GlobalSkillsCacheTestSupport.offEdt(() -> cache().snapshot());
        assertNotNull(skills.skill(".agents/skills", "openspec-explore"));
        assertTrue(cache().isFresh());
    }

    public void testOnTheEdtNothingIsReadAndALoadIsScheduled() throws Exception {
        assertTrue(ApplicationManager.getApplication().isDispatchThread());
        HomeSkills now = cache().snapshot();
        assertSame("the EDT gets the empty answer, not a disk read", HomeSkills.NONE, now);
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end && !cache().isFresh()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        assertTrue("the scheduled load filled the cache", cache().isFresh());
        assertNotNull(cache().snapshot().skill(".agents/skills", "openspec-explore"));
    }

    public void testASnapshotIsReadOnceUntilInvalidated() throws Exception {
        GlobalSkillsCacheTestSupport.offEdt(() -> cache().snapshot());
        GlobalSkillsCacheTestSupport.offEdt(() -> cache().snapshot());
        assertEquals(1, reads.get());
        cache().invalidate();
        waitUntilFresh();
        GlobalSkillsCacheTestSupport.offEdt(() -> cache().snapshot());
        assertEquals("an invalidate reads once more, in the background", 2, reads.get());
    }

    /** Returning to the IDE must not blank the answer: the EDT keeps the last snapshot while the reread runs. */
    public void testAfterAnInvalidateTheEdtStillSeesTheLastSnapshot() throws Exception {
        GlobalSkillsCacheTestSupport.offEdt(() -> cache().snapshot());
        cache().invalidate();
        assertNotNull(cache().snapshot().skill(".agents/skills", "openspec-explore"));
        waitUntilFresh();
    }

    /** A read that started before an invalidate must not store its older answer over the newer one. */
    public void testAReadOverlappingAnInvalidateDoesntStoreStaleSkills() throws Exception {
        GlobalSkillsCacheTestSupport.offEdt(() -> cache().snapshot());
        java.util.concurrent.CountDownLatch inScan = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Path slow = Files.createTempDirectory("slow-home");
        cache().setHomeForTest(() -> {
            if (inScan.getCount() > 0) {
                inScan.countDown();
                try {
                    release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return slow; // the old answer: no skills
            }
            return home;
        });
        java.util.concurrent.Future<HomeSkills> first = ApplicationManager.getApplication()
                .executeOnPooledThread(() -> cache().snapshot());
        assertTrue(inScan.await(10, java.util.concurrent.TimeUnit.SECONDS));
        cache().invalidate(); // a CLI run changed home while the first read was scanning
        release.countDown();
        first.get(10, java.util.concurrent.TimeUnit.SECONDS);
        waitUntilFresh();
        assertNotNull("the newer read wins", cache().snapshot().skill(".agents/skills", "openspec-explore"));
        com.intellij.openapi.util.io.FileUtil.delete(slow.toFile());
    }

    private void waitUntilFresh() throws InterruptedException {
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end && !cache().isFresh()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        assertTrue("the background read finished", cache().isFresh());
    }

    /** Published on the application bus, as the platform does when a window gains focus. */
    public void testTheProjectWindowRegainingFocusClearsTheCache() throws Exception {
        GlobalSkillsCacheTestSupport.offEdt(() -> cache().snapshot());
        assertTrue(cache().isFresh());

        IdeFrame other = mock(IdeFrame.class);
        ApplicationManager.getApplication().getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC)
                .applicationActivated(other);
        assertTrue("another project's window leaves it", cache().isFresh());

        IdeFrame mine = mock(IdeFrame.class);
        when(mine.getProject()).thenReturn(getProject());
        ApplicationManager.getApplication().getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC)
                .applicationActivated(mine);
        assertFalse("this project's window marks it out of date", cache().isFresh());
        waitUntilFresh();
    }
}
