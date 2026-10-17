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

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Pin "no CLI" (the plugin's own archive) unless a test fakes one: never the CLI installed on this machine.
        com.johnnyblabs.openspec.services.CliDetectionTestSupport.fakeUnavailable(
                getProject().getService(com.johnnyblabs.openspec.services.CliDetectionService.class));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ChangeService reset = getProject().getService(ChangeService.class);
            reset.setArchiveCliForTest(null);
            reset.setClockForTest(null);
        } finally {
            super.tearDown();
        }
    }

    private String archiveInsideOpenDialog(Change change) throws Exception {
        Object dialog = new Object();
        Future<String> loop;
        LaterInvocator.enterModal(dialog);
        try {
            ModalityState modality = ModalityState.current();
            loop = ApplicationManager.getApplication().executeOnPooledThread(
                    () -> BulkArchiveDialogTestAccess.archiveWithProductionArchiver(getProject(), change, modality));
            long end = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < end && !loop.isDone()) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(20);
            }
            assertTrue("the archive must finish while the dialog is open", loop.isDone());
        } finally {
            LaterInvocator.leaveModal(dialog);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }
        return loop.get();
    }

    public void testWithTheCliEachChangeIsOneArchiveCallOffTheUiThread() throws Exception {
        ChangeService changes = getProject().getService(ChangeService.class);
        com.johnnyblabs.openspec.services.CliDetectionTestSupport.fakeAvailable(
                getProject().getService(com.johnnyblabs.openspec.services.CliDetectionService.class), "1.14.3");
        String captured = java.nio.file.Files.readString(java.nio.file.Paths.get(
                "src/test/resources/fixtures/cli/archive-1.14.3/modified-ok/archive.json"));
        List<List<String>> calls = new java.util.ArrayList<>();
        List<Boolean> onUi = new java.util.ArrayList<>();
        changes.setArchiveCliForTest((project, args) -> {
            calls.add(List.of(args));
            onUi.add(ApplicationManager.getApplication().isDispatchThread());
            return new com.johnnyblabs.openspec.util.CliRunner.CliResult(0, captured, "");
        });
        myFixture.addFileToProject("openspec/changes/alpha/proposal.md", "## Why\n\nx\n");

        assertEquals("Done", archiveInsideOpenDialog(change("alpha")));
        assertEquals(List.of(List.of("archive", "alpha", "--yes", "--json")), calls);
        assertEquals(List.of(false), onUi);
    }

    public void testWithTheCliARefusalMarksTheRowFailedWithTheCliMessage() throws Exception {
        ChangeService changes = getProject().getService(ChangeService.class);
        com.johnnyblabs.openspec.services.CliDetectionTestSupport.fakeAvailable(
                getProject().getService(com.johnnyblabs.openspec.services.CliDetectionService.class), "1.14.3");
        String captured = java.nio.file.Files.readString(java.nio.file.Paths.get(
                "src/test/resources/fixtures/cli/archive-1.14.3/two-caps-one-refused/archive.json"));
        changes.setArchiveCliForTest((project, args) -> new com.johnnyblabs.openspec.util.CliRunner.CliResult(1, captured, ""));
        myFixture.addFileToProject("openspec/changes/alpha/proposal.md", "## Why\n\nx\n");

        assertEquals("Failed: other MODIFIED failed for header \"### Requirement: Missing\" - not found "
                + "Fix the change delta specs and rerun. No files were changed.", archiveInsideOpenDialog(change("alpha")));
        assertTrue("still active", changes.getActiveChanges().stream().anyMatch(c -> c.getName().equals("alpha")));
    }

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
