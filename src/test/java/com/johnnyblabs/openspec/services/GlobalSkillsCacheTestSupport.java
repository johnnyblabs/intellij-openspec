package com.johnnyblabs.openspec.services;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Supplier;

/** Test access to {@link GlobalSkillsCache}'s seams from other packages. */
public final class GlobalSkillsCacheTestSupport {

    private GlobalSkillsCacheTestSupport() {
    }

    /** Points the cache at {@code home} (never the real one), or back at the real home with {@code null}. */
    public static void setHome(GlobalSkillsCache cache, @Nullable Supplier<@Nullable Path> home) {
        cache.setHomeForTest(home);
    }

    public static boolean isCached(GlobalSkillsCache cache) {
        return cache.isFresh();
    }

    /** Runs {@code work} on a pooled thread and returns its result, pumping the EDT while it waits. */
    public static <T> T offEdt(java.util.concurrent.Callable<T> work) throws Exception {
        java.util.concurrent.Future<T> future =
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(work);
        long end = System.currentTimeMillis() + 20_000;
        while (!future.isDone() && System.currentTimeMillis() < end) {
            com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(5);
        }
        return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
    }
}
