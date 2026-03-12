package com.johnnyb.openspec.integration;

import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.toolwindow.GettingStartedPanel;

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
}
