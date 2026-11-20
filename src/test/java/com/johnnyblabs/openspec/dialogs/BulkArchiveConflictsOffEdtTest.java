package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Conflict detection reads each selected change's delta specs from disk, so it must run on a pooled thread, not the
 * EDT (issue 586). The result is posted back to the EDT for the dialog to show.
 */
public class BulkArchiveConflictsOffEdtTest extends BasePlatformTestCase {

    private static final Condition<Object> NEVER_EXPIRED = ignored -> false;

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

    public void testConflictDetectionRunsOffTheEdt() throws Exception {
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        AtomicBoolean delivered = new AtomicBoolean();
        BulkArchiveDialog.loadConflicts(List.of("alpha", "beta"), names -> {
            onEdt.set(ApplicationManager.getApplication().isDispatchThread());
            return Map.of();
        }, ModalityState.nonModal(), NEVER_EXPIRED, conflicts -> delivered.set(true));

        assertTrue("the result must arrive back on the EDT", within(delivered::get));
        assertNotNull("detection must have run", onEdt.get());
        assertFalse("spec files must not be read on the EDT", onEdt.get());
    }

    public void testTheDetectedConflictsReachTheDialog() throws Exception {
        Map<String, List<String>> expected = Map.of("validation", List.of("alpha", "beta"));
        AtomicReference<Map<String, List<String>>> received = new AtomicReference<>();
        BulkArchiveDialog.loadConflicts(List.of("alpha", "beta"), names -> expected, ModalityState.nonModal(),
                NEVER_EXPIRED, received::set);

        assertTrue("the conflicts must be delivered", within(() -> received.get() != null));
        assertEquals(expected, received.get());
    }

    public void testAnExpiredDialogDropsTheConflicts() throws Exception {
        AtomicBoolean delivered = new AtomicBoolean();
        BulkArchiveDialog.loadConflicts(List.of("alpha", "beta"), names -> Map.of(), ModalityState.nonModal(),
                ignored -> true, conflicts -> delivered.set(true));

        within(() -> false);
        assertFalse("a closed dialog must not be updated", delivered.get());
    }
}
