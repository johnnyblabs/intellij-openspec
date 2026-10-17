package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.dialogs.BulkArchiveDialogTestAccess;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;

import java.util.List;
import java.util.concurrent.Future;

/**
 * The Bulk Archive dialog's production archiver against a real (light) project: the check reports the sync service's
 * real blocking warning, and the archive step really moves the change directory — run from a background thread inside
 * a simulated modal dialog, as the dialog does.
 */
public class BulkArchiveProductionArchiverTest extends OpenSpecIntegrationTestBase {

    private Change change(String name) {
        return getProject().getService(ChangeService.class).getActiveChanges().stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    public void testCheckReportsAMissingModifiedTarget() throws Exception {
        myFixture.addFileToProject("openspec/changes/orphaned/proposal.md", "## Why\n\nx\n");
        myFixture.addFileToProject("openspec/changes/orphaned/specs/ghost/spec.md", """
                ## MODIFIED Requirements

                ### Requirement: Gone
                The system SHALL do something that no main spec defines.

                #### Scenario: Never
                - **WHEN** archived
                - **THEN** nothing to modify
                """);

        List<String> problems = BulkArchiveDialogTestAccess.productionCheck(getProject(), "orphaned");

        assertEquals(1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("[sync-modified-target-missing]"));
        assertTrue(problems.get(0), problems.get(0).contains("Gone"));
    }

    public void testCheckIsCleanForAChangeWithoutDeltaSpecs() throws Exception {
        myFixture.addFileToProject("openspec/changes/plain/proposal.md", "## Why\n\nx\n");
        assertEquals(List.of(), BulkArchiveDialogTestAccess.productionCheck(getProject(), "plain"));
    }

    public void testArchiveAllMovesTheChangeInsideTheOpenDialog() throws Exception {
        myFixture.addFileToProject("openspec/changes/plain/proposal.md", "## Why\n\nx\n");
        Change plain = change("plain");

        Object dialog = new Object();
        Future<?> loop;
        boolean doneWhileOpen = false;
        LaterInvocator.enterModal(dialog);
        try {
            ModalityState modality = ModalityState.current();
            loop = ApplicationManager.getApplication().executeOnPooledThread(
                    () -> BulkArchiveDialogTestAccess.archiveWithProductionArchiver(getProject(), plain, modality));
            long end = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < end && !loop.isDone()) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(20);
            }
            doneWhileOpen = loop.isDone();
        } finally {
            LaterInvocator.leaveModal(dialog);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }
        assertTrue("the archive must finish while the dialog is open", doneWhileOpen);
        assertEquals("Done", loop.get());

        ChangeService changes = getProject().getService(ChangeService.class);
        assertTrue("no longer active", changes.getActiveChanges().stream().noneMatch(c -> c.getName().equals("plain")));
        assertTrue("now archived", changes.getArchivedChanges().stream().anyMatch(c -> c.getName().equals("plain")));
    }
}
