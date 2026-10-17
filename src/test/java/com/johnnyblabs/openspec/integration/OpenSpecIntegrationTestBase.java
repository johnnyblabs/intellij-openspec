package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Base class for OpenSpec integration tests.
 * Sets up a project with an openspec/ directory structure from test fixtures.
 */
public abstract class OpenSpecIntegrationTestBase extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/testProject";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        copyOpenSpecFixtures();
    }

    /**
     * Copies the openspec/ fixture directory into the test project.
     */
    protected void copyOpenSpecFixtures() {
        myFixture.copyDirectoryToProject("openspec", "openspec");
        // Ensure VFS sees all nested directories (needed for delta spec discovery)
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        if (root != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, root);
        }
    }

    /**
     * Returns the openspec root VirtualFile in the test project.
     */
    protected VirtualFile getOpenSpecRoot() {
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        assertNotNull("openspec directory should exist in test project", root);
        return root;
    }

    /**
     * Returns the config.yaml VirtualFile.
     */
    protected VirtualFile getConfigFile() {
        VirtualFile config = myFixture.findFileInTempDir("openspec/config.yaml");
        assertNotNull("openspec/config.yaml should exist", config);
        return config;
    }

    /**
     * Returns the changes directory VirtualFile.
     */
    protected VirtualFile getChangesDir() {
        return myFixture.findFileInTempDir("openspec/changes");
    }

    /**
     * Returns a specific change directory by name.
     */
    protected VirtualFile getChangeDir(String changeName) {
        return myFixture.findFileInTempDir("openspec/changes/" + changeName);
    }

    /**
     * Runs {@code work} on a pooled thread while pumping the UI queue, returning its result or rethrowing its failure.
     * Archives must run off the UI thread, and light tests themselves run on it.
     */
    protected <T> T offEdt(java.util.concurrent.Callable<T> work) throws Exception {
        // Catch inside the pooled task: an exception escaping it is logged as an error, which fails a test that expects it.
        Object[] box = new Object[2];
        java.util.concurrent.Future<?> future = com.intellij.openapi.application.ApplicationManager.getApplication()
                .executeOnPooledThread(() -> {
                    try {
                        box[0] = work.call();
                    } catch (Throwable t) {
                        box[1] = t;
                    }
                });
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end && !future.isDone()) {
            com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        assertTrue("timed out waiting for background work", future.isDone());
        com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        future.get();
        if (box[1] instanceof Exception ex) throw ex;
        if (box[1] instanceof Error err) throw err;
        @SuppressWarnings("unchecked") T result = (T) box[0];
        return result;
    }
}
