package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.util.Condition;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * The Bulk Archive dialog is modal, so its background results must be posted with the dialog's modality or the
 * platform holds them until the dialog closes. Each test runs the real loading / archiving code from a pooled thread
 * inside a simulated modal state and checks the result arrives <em>before</em> the modal is left, with a negative
 * control showing the same code without the modality is held. (In unit-test mode a {@code Task.Backgroundable} runs on
 * the EDT, so the background thread is supplied explicitly here.)
 */
public class BulkArchiveDialogModalityTest extends BasePlatformTestCase {

    /** Captured real {@code status --change --json} (CLI 1.14.3): all four artifacts done. */
    private static ChangeArtifactDag capturedStatus() {
        String path = "/fixtures/cli/1.14.3/additive-shapes/status.json";
        try (InputStream is = BulkArchiveDialogModalityTest.class.getResourceAsStream(path)) {
            assertNotNull("missing fixture " + path, is);
            return CliOutputParser.parseChangeStatus(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<BulkArchiveDialog.ChangeRow> rows(String... names) {
        List<BulkArchiveDialog.ChangeRow> rows = new ArrayList<>();
        for (String name : names) {
            rows.add(new BulkArchiveDialog.ChangeRow(new Change(name, "/fixture/openspec/changes/" + name)));
        }
        return rows;
    }

    /** Pumps EDT events until {@code done} holds or 5s pass; true if it held. */
    private static boolean within(BooleanSupplier done) throws InterruptedException {
        long end = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            if (done.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static final Condition<Object> NEVER_EXPIRED = ignored -> false;

    /**
     * Runs {@code work} on a pooled thread and waits for it while pumping EDT events. The test thread is the EDT, and
     * the archive loop hops to the EDT with invokeAndWait, so a plain {@code Future.get()} here would deadlock.
     */
    private static void runOffTheEdt(Runnable work) throws Exception {
        Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(work);
        assertTrue("background work did not finish", within(future::isDone));
        future.get(); // surfaces any assertion thrown inside
    }

    public void testStatusesReachTheOpenDialog() throws Exception {
        ChangeArtifactDag dag = capturedStatus();
        String expected = dag.getArtifacts().size() + "/" + dag.getArtifacts().size() + " complete";
        List<BulkArchiveDialog.ChangeRow> rows = rows("alpha", "beta");
        AtomicBoolean refreshed = new AtomicBoolean();

        Object dialog = new Object();
        boolean arrivedWhileOpen;
        LaterInvocator.enterModal(dialog);
        try {
            ModalityState modality = ModalityState.current();
            BulkArchiveDialog.loadStatuses(rows, name -> dag, modality, NEVER_EXPIRED, () -> refreshed.set(true));
            arrivedWhileOpen = within(refreshed::get);
        } finally {
            LaterInvocator.leaveModal(dialog);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }

        assertTrue("the table refresh must run while the dialog is open", arrivedWhileOpen);
        for (BulkArchiveDialog.ChangeRow row : rows) {
            assertEquals(expected, row.artifactStatus);
            assertTrue(row.allComplete);
        }
    }

    public void testWithoutTheDialogsModalityTheStatusRefreshIsHeld() throws Exception {
        // Negative control: the same code posted NON_MODAL waits for the modal to close — the original bug.
        List<BulkArchiveDialog.ChangeRow> rows = rows("alpha");
        AtomicBoolean refreshed = new AtomicBoolean();
        Object dialog = new Object();
        boolean arrivedWhileOpen;
        LaterInvocator.enterModal(dialog);
        try {
            BulkArchiveDialog.loadStatuses(rows, name -> capturedStatus(), ModalityState.nonModal(), NEVER_EXPIRED,
                    () -> refreshed.set(true));
            arrivedWhileOpen = within(refreshed::get);
        } finally {
            LaterInvocator.leaveModal(dialog);
        }
        assertFalse("NON_MODAL must be held while the dialog is open", arrivedWhileOpen);
        assertTrue("…and delivered once it closes", within(refreshed::get));
    }

    public void testAnExpiredDialogDropsTheStatusRefresh() throws Exception {
        AtomicBoolean refreshed = new AtomicBoolean();
        BulkArchiveDialog.loadStatuses(rows("alpha"), name -> null, ModalityState.nonModal(), ignored -> true,
                () -> refreshed.set(true));
        within(refreshed::get);
        assertFalse("a closed dialog must not be updated", refreshed.get());
    }

    public void testArchiveLoopCompletesInsideTheOpenDialog() throws Exception {
        List<BulkArchiveDialog.ChangeRow> rows = rows("alpha", "broken", "gamma");
        List<String> archivedOnEdt = new ArrayList<>();
        List<String> synced = new ArrayList<>();
        BulkArchiveDialog.Archiver archiver = new BulkArchiveDialog.Archiver() {
            @Override
            public List<String> check(String changeName) {
                assertFalse("the pre-check runs off the EDT", ApplicationManager.getApplication().isDispatchThread());
                return List.of();
            }

            @Override
            public void sync(String changeName) {
                assertFalse("spec sync runs off the EDT", ApplicationManager.getApplication().isDispatchThread());
                synced.add(changeName);
            }

            @Override
            public void archive(Change change) throws Exception {
                assertTrue("the archive's write action runs on the EDT",
                        ApplicationManager.getApplication().isDispatchThread());
                if (change.getName().equals("broken")) {
                    throw new IllegalStateException("cannot move broken");
                }
                archivedOnEdt.add(change.getName());
            }
        };

        Object dialog = new Object();
        Future<?> loop;
        boolean finishedWhileOpen;
        LaterInvocator.enterModal(dialog);
        try {
            ModalityState modality = ModalityState.current();
            loop = ApplicationManager.getApplication().executeOnPooledThread(
                    () -> BulkArchiveDialog.archiveAll(rows, modality, archiver, () -> { }, (i, name) -> { }));
            finishedWhileOpen = within(loop::isDone);
        } finally {
            LaterInvocator.leaveModal(dialog);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }
        loop.get(5, TimeUnit.SECONDS); // surfaces any assertion thrown inside the loop or the archiver

        assertTrue("every archive must complete while the dialog is open", finishedWhileOpen);
        assertEquals(List.of("alpha", "broken", "gamma"), synced);
        assertEquals("a failure doesn't stop the remaining changes", List.of("alpha", "gamma"), archivedOnEdt);
        assertEquals("Done", rows.get(0).archiveStatus);
        assertEquals("Failed: cannot move broken", rows.get(1).archiveStatus);
        assertEquals("Done", rows.get(2).archiveStatus);
    }

    public void testASyncFailureMarksTheRowAndSkipsItsArchive() throws Exception {
        List<BulkArchiveDialog.ChangeRow> rows = rows("alpha");
        AtomicBoolean archived = new AtomicBoolean();
        BulkArchiveDialog.Archiver archiver = new BulkArchiveDialog.Archiver() {
            @Override
            public List<String> check(String changeName) {
                return List.of();
            }

            @Override
            public void sync(String changeName) throws Exception {
                throw new java.io.IOException("spec write failed");
            }

            @Override
            public void archive(Change change) {
                archived.set(true);
            }
        };
        runOffTheEdt(() -> BulkArchiveDialog.archiveAll(rows, ModalityState.nonModal(), archiver, () -> { }, (i, n) -> { }));
        assertFalse("a change whose specs failed to sync is not archived", archived.get());
        assertEquals("Failed: spec write failed", rows.get(0).archiveStatus);
    }

    public void testAStatusFailureForOneChangeDoesNotStrandTheRest() throws Exception {
        ChangeArtifactDag dag = capturedStatus();
        List<BulkArchiveDialog.ChangeRow> rows = rows("broken", "beta");
        AtomicBoolean refreshed = new AtomicBoolean();
        BulkArchiveDialog.loadStatuses(rows, name -> {
            if (name.equals("broken")) {
                throw new IllegalStateException("status failed");
            }
            return dag;
        }, ModalityState.nonModal(), NEVER_EXPIRED, () -> refreshed.set(true));
        assertTrue("the refresh still runs", within(refreshed::get));
        assertEquals("unknown", rows.get(0).artifactStatus);
        assertEquals(dag.getArtifacts().size() + "/" + dag.getArtifacts().size() + " complete",
                rows.get(1).artifactStatus);
    }

    public void testWithoutTheDialogsModalityTheArchiveStepWaitsForTheDialogToClose() throws Exception {
        // Negative control for the archive hop: invokeAndWait posted NON_MODAL blocks the worker while the dialog is
        // open (the stall the explicit modality prevents) and completes once it closes.
        List<BulkArchiveDialog.ChangeRow> rows = rows("alpha");
        BulkArchiveDialog.Archiver archiver = new BulkArchiveDialog.Archiver() {
            @Override
            public List<String> check(String changeName) {
                return List.of();
            }

            @Override
            public void sync(String changeName) {
            }

            @Override
            public void archive(Change change) {
            }
        };
        Object dialog = new Object();
        Future<?> loop;
        boolean finishedWhileOpen;
        LaterInvocator.enterModal(dialog);
        try {
            loop = ApplicationManager.getApplication().executeOnPooledThread(() -> BulkArchiveDialog.archiveAll(
                    rows, ModalityState.nonModal(), archiver, () -> { }, (i, n) -> { }));
            finishedWhileOpen = within(loop::isDone);
        } finally {
            LaterInvocator.leaveModal(dialog); // releases the held worker
        }
        assertFalse("NON_MODAL archive hop must be held while the dialog is open", finishedWhileOpen);
        assertTrue("…and run once it closes", within(loop::isDone));
        assertEquals("Done", rows.get(0).archiveStatus);
    }

    public void testBlockedChangesAreNeitherSyncedNorArchivedAndEveryCheckRunsFirst() throws Exception {
        List<BulkArchiveDialog.ChangeRow> rows = rows("alpha", "orphaned", "gamma");
        List<String> calls = new ArrayList<>();
        BulkArchiveDialog.Archiver archiver = new BulkArchiveDialog.Archiver() {
            @Override
            public List<String> check(String changeName) {
                calls.add("check " + changeName);
                return changeName.equals("orphaned")
                        ? List.of("MODIFIED requirement 'Gone' not found in demo [sync-modified-target-missing]")
                        : List.of();
            }

            @Override
            public void sync(String changeName) {
                calls.add("sync " + changeName);
            }

            @Override
            public void archive(Change change) {
                calls.add("archive " + change.getName());
            }
        };
        runOffTheEdt(() -> BulkArchiveDialog.archiveAll(rows, ModalityState.nonModal(), archiver, () -> { }, (i, n) -> { }));

        assertEquals(List.of("check alpha", "check orphaned", "check gamma",
                "sync alpha", "archive alpha", "sync gamma", "archive gamma"), calls);
        assertEquals("Done", rows.get(0).archiveStatus);
        assertTrue(rows.get(1).archiveStatus, rows.get(1).archiveStatus.startsWith("Blocked: MODIFIED requirement 'Gone'"));
        assertEquals("Done", rows.get(2).archiveStatus);
    }

    public void testAProblemAppearingAtWriteTimeBlocksThatChange() throws Exception {
        // sync() re-checks: an earlier archive in the batch can leave a later change with a missing target.
        List<BulkArchiveDialog.ChangeRow> rows = rows("alpha");
        AtomicBoolean archived = new AtomicBoolean();
        BulkArchiveDialog.Archiver archiver = new BulkArchiveDialog.Archiver() {
            @Override
            public List<String> check(String changeName) {
                return List.of();
            }

            @Override
            public void sync(String changeName) throws Exception {
                throw new BulkArchiveDialog.Blocked("REMOVED: requirement 'Old' not found in demo");
            }

            @Override
            public void archive(Change change) {
                archived.set(true);
            }
        };
        runOffTheEdt(() -> BulkArchiveDialog.archiveAll(rows, ModalityState.nonModal(), archiver, () -> { }, (i, n) -> { }));
        assertFalse(archived.get());
        assertEquals("Blocked: REMOVED: requirement 'Old' not found in demo", rows.get(0).archiveStatus);
    }

    public void testOkComesBackEvenWhenTheArchiveRunDiesUnexpectedly() throws Exception {
        // The real task (synchronous in test mode). An Error escapes run() — the per-row handling only catches
        // Exception — and onFinished() must still re-enable OK, or the dialog can't be closed at all.
        com.intellij.testFramework.ServiceContainerUtil.replaceService(getProject(),
                com.johnnyblabs.openspec.services.ArtifactOrchestrationService.class,
                org.mockito.Mockito.mock(com.johnnyblabs.openspec.services.ArtifactOrchestrationService.class),
                getTestRootDisposable());
        BulkArchiveDialog dialog = new BulkArchiveDialog(getProject(),
                List.of(new Change("alpha", "/fixture/openspec/changes/alpha")));
        com.intellij.openapi.util.Disposer.register(getTestRootDisposable(), dialog.getDisposable());
        BulkArchiveDialog.Archiver dying = new BulkArchiveDialog.Archiver() {
            @Override
            public List<String> check(String changeName) {
                throw new AssertionError("unexpected failure outside the per-change handling");
            }

            @Override
            public void sync(String changeName) {
            }

            @Override
            public void archive(Change change) {
            }
        };

        com.intellij.testFramework.LoggedErrorProcessor.executeWith(new com.intellij.testFramework.LoggedErrorProcessor() {
            @Override
            public java.util.Set<Action> processError(String category, String message, String[] details, Throwable t) {
                return java.util.EnumSet.noneOf(Action.class); // the task's own error logging is expected here
            }
        }, () -> dialog.runBulkArchive(dying));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertTrue("OK must be re-enabled so the dialog can be closed", dialog.isOKActionEnabled());
    }
}
