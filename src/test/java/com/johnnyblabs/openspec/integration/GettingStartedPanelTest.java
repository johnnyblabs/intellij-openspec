package com.johnnyblabs.openspec.integration;

import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.toolwindow.GettingStartedPanel;

/**
 * Integration test for GettingStartedPanel state detection.
 * Uses the real IntelliJ project fixture with openspec/ directory.
 */
public class GettingStartedPanelTest extends OpenSpecIntegrationTestBase {

    public void testDetectsReadyWhenChangesExist() {
        // The test fixture has an initialized openspec/ project with changes
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredDeliveryMethod("clipboard");

        GettingStartedPanel panel = new GettingStartedPanel(getProject());
        assertEquals(GettingStartedPanel.State.READY, panel.detectState());
    }

    public void testDetectsNoAiConfiguredWhenDeliveryMethodEmpty() {
        // Remove all changes and archives so the state check reaches NO_AI_CONFIGURED
        com.intellij.openapi.vfs.VirtualFile changesDir = myFixture.findFileInTempDir("openspec/changes");
        if (changesDir != null) {
            try {
                com.intellij.openapi.application.WriteAction.run(() -> {
                    for (com.intellij.openapi.vfs.VirtualFile child : changesDir.getChildren()) {
                        if (child.isDirectory()) {
                            child.delete(this);
                        }
                    }
                });
            } catch (Exception e) {
                fail("Failed to clean changes dir: " + e.getMessage());
            }
        }

        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredDeliveryMethod("");

        GettingStartedPanel panel = new GettingStartedPanel(getProject());
        assertEquals(GettingStartedPanel.State.NO_AI_CONFIGURED, panel.detectState());
    }

    public void testDetectsNoChangesWhenDeliveryConfiguredButNoChanges() {
        // Remove all changes from the fixture
        com.intellij.openapi.vfs.VirtualFile changesDir = myFixture.findFileInTempDir("openspec/changes");
        if (changesDir != null) {
            for (com.intellij.openapi.vfs.VirtualFile child : changesDir.getChildren()) {
                if (child.isDirectory() && !"archive".equals(child.getName())) {
                    try {
                        com.intellij.openapi.application.WriteAction.run(() -> child.delete(this));
                    } catch (Exception e) {
                        fail("Failed to delete change dir: " + e.getMessage());
                    }
                }
            }
        }

        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredDeliveryMethod("clipboard");

        GettingStartedPanel panel = new GettingStartedPanel(getProject());
        assertEquals(GettingStartedPanel.State.NO_CHANGES, panel.detectState());
    }

    public void testDetectsReadyWhenOnlyArchivedChangesExist() {
        // Remove active changes but keep archive with content
        com.intellij.openapi.vfs.VirtualFile changesDir = myFixture.findFileInTempDir("openspec/changes");
        if (changesDir != null) {
            try {
                com.intellij.openapi.application.WriteAction.run(() -> {
                    // Remove active changes
                    for (com.intellij.openapi.vfs.VirtualFile child : changesDir.getChildren()) {
                        if (child.isDirectory() && !"archive".equals(child.getName())) {
                            child.delete(this);
                        }
                    }
                    // Ensure archive exists with content
                    com.intellij.openapi.vfs.VirtualFile archive = changesDir.findChild("archive");
                    if (archive == null) {
                        archive = changesDir.createChildDirectory(this, "archive");
                    }
                    if (archive.getChildren().length == 0) {
                        archive.createChildDirectory(this, "2026-01-01-old-change");
                    }
                });
            } catch (Exception e) {
                fail("Failed to setup archive: " + e.getMessage());
            }
        }

        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredDeliveryMethod("clipboard");

        GettingStartedPanel panel = new GettingStartedPanel(getProject());
        assertEquals(GettingStartedPanel.State.READY, panel.detectState());
    }

    public void testDetectsReadyWithArchivesEvenWithoutDeliveryMethod() {
        // Archive exists but no delivery method — should still be READY
        // because the project has been actively used
        com.intellij.openapi.vfs.VirtualFile changesDir = myFixture.findFileInTempDir("openspec/changes");
        if (changesDir != null) {
            try {
                com.intellij.openapi.application.WriteAction.run(() -> {
                    for (com.intellij.openapi.vfs.VirtualFile child : changesDir.getChildren()) {
                        if (child.isDirectory() && !"archive".equals(child.getName())) {
                            child.delete(this);
                        }
                    }
                    com.intellij.openapi.vfs.VirtualFile archive = changesDir.findChild("archive");
                    if (archive == null) {
                        archive = changesDir.createChildDirectory(this, "archive");
                    }
                    if (archive.getChildren().length == 0) {
                        archive.createChildDirectory(this, "2026-01-01-old-change");
                    }
                });
            } catch (Exception e) {
                fail("Failed to setup archive: " + e.getMessage());
            }
        }

        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredDeliveryMethod("");

        GettingStartedPanel panel = new GettingStartedPanel(getProject());
        assertEquals(GettingStartedPanel.State.READY, panel.detectState());
    }

    public void testEmptyArchiveDoesNotCountAsReady() {
        // Archive directory exists but is empty — should NOT be READY
        com.intellij.openapi.vfs.VirtualFile changesDir = myFixture.findFileInTempDir("openspec/changes");
        if (changesDir != null) {
            try {
                com.intellij.openapi.application.WriteAction.run(() -> {
                    for (com.intellij.openapi.vfs.VirtualFile child : changesDir.getChildren()) {
                        if (child.isDirectory()) {
                            child.delete(this);
                        }
                    }
                    // Create empty archive
                    changesDir.createChildDirectory(this, "archive");
                });
            } catch (Exception e) {
                fail("Failed to setup empty archive: " + e.getMessage());
            }
        }

        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredDeliveryMethod("clipboard");

        GettingStartedPanel panel = new GettingStartedPanel(getProject());
        assertEquals(GettingStartedPanel.State.NO_CHANGES, panel.detectState());
    }
}
