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
}
