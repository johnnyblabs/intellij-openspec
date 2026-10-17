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

    private static final String GHOST_MAIN = """
            # ghost Specification

            ## Purpose
            A capability whose spec exists but lacks the requirement the delta modifies.

            ## Requirements

            ### Requirement: Present
            The system SHALL be present.

            #### Scenario: Here
            - **WHEN** checked
            - **THEN** present
            """;

    public void testCheckReportsAMissingModifiedTarget() throws Exception {
        myFixture.addFileToProject("openspec/specs/ghost/spec.md", GHOST_MAIN);
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

        assertEquals(List.of("ghost MODIFIED failed for header \"### Requirement: Gone\" - not found"), problems);
    }

    public void testCheckDoesNotBlockOnARemovedTargetThatIsAlreadyGone() throws Exception {
        // OpenSpec treats it as already removed (a warning), so Bulk Archive doesn't block on it either.
        myFixture.addFileToProject("openspec/specs/ghost/spec.md", GHOST_MAIN);
        myFixture.addFileToProject("openspec/changes/tidy/proposal.md", "## Why\n\nx\n");
        myFixture.addFileToProject("openspec/changes/tidy/specs/ghost/spec.md",
                "## REMOVED Requirements\n\n- `### Requirement: Long gone`\n");

        assertEquals(List.of(), BulkArchiveDialogTestAccess.productionCheck(getProject(), "tidy"));
    }

    public void testCheckBlocksWhenTheArchiveFolderExists() throws Exception {
        ChangeService changes = getProject().getService(ChangeService.class);
        changes.setClockForTest(java.time.Clock.fixed(java.time.Instant.parse("2026-10-17T12:00:00Z"), java.time.ZoneOffset.UTC));
        myFixture.addFileToProject("openspec/changes/plain/proposal.md", "## Why\n\nx\n");
        myFixture.addFileToProject("openspec/changes/archive/2026-10-17-plain/proposal.md", "## Why\n\nold\n");

        assertEquals(List.of("Archive '2026-10-17-plain' already exists."),
                BulkArchiveDialogTestAccess.productionCheck(getProject(), "plain"));
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
        String archived = changes.archiveTargetName("plain");
        assertTrue("archived under its dated name " + archived,
                changes.getArchivedChanges().stream().anyMatch(c -> c.getName().equals(archived)));
    }
}
