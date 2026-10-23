package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Manage AI Tools is modal and reloads its list after a background CLI run. The reload is chained off an async VFS
 * refresh, so the refresh itself must carry the dialog's modality or the platform holds it (and the reload) until the
 * dialog closes. Each test runs the real helper from a pooled thread inside a simulated modal state. A negative control
 * shows the earlier {@code refreshFiles} shape is held. (In unit-test mode a {@code Task.Backgroundable} runs on the
 * EDT, so the background thread is supplied explicitly here.)
 */
public class ManageAiToolsDialogModalityTest extends BasePlatformTestCase {

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

    /** Runs {@code work} on a pooled thread and waits while pumping EDT events (never a bare get() on the EDT). */
    private static void runOffTheEdt(Runnable work) throws Exception {
        Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(work);
        assertTrue("background work did not finish", within(future::isDone));
        future.get(); // surfaces any exception thrown inside, such as a write-thread assertion
    }

    private VirtualFile dir() {
        VirtualFile dir = myFixture.getTempDirFixture().getFile("");
        assertNotNull(dir);
        return dir;
    }

    public void testReloadReachesTheOpenDialog() throws Exception {
        VirtualFile dir = dir();
        AtomicBoolean reloaded = new AtomicBoolean();
        Object modal = new Object();
        LaterInvocator.enterModal(modal);
        try {
            ModalityState modality = ModalityState.current();
            runOffTheEdt(() -> ManageAiToolsDialog.refreshThenPost(dir, modality, NEVER_EXPIRED, () -> reloaded.set(true)));
            assertTrue("the list reload must run while the dialog is still open", within(reloaded::get));
        } finally {
            LaterInvocator.leaveModal(modal);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }
    }

    public void testWithNoDirectoryTheReloadIsPostedDirectly() throws Exception {
        AtomicBoolean reloaded = new AtomicBoolean();
        Object modal = new Object();
        LaterInvocator.enterModal(modal);
        try {
            ModalityState modality = ModalityState.current();
            runOffTheEdt(() -> ManageAiToolsDialog.refreshThenPost(null, modality, NEVER_EXPIRED, () -> reloaded.set(true)));
            assertTrue(within(reloaded::get));
        } finally {
            LaterInvocator.leaveModal(modal);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }
    }

    public void testAnExpiredDialogIsNotReloaded() throws Exception {
        AtomicBoolean reloaded = new AtomicBoolean();
        ModalityState modality = ModalityState.defaultModalityState();
        runOffTheEdt(() -> ManageAiToolsDialog.refreshThenPost(dir(), modality, ignored -> true, () -> reloaded.set(true)));
        // Give a would-be reload every chance to run.
        within(() -> false);
        assertFalse("a disposed dialog must not be reloaded", reloaded.get());
    }

    /** Negative control: the earlier shape inherits the background thread's non-modal state, so it is held. */
    public void testControl_refreshFilesWithoutAModalityIsHeldUntilTheDialogCloses() throws Exception {
        VirtualFile dir = dir();
        AtomicBoolean reloaded = new AtomicBoolean();
        Object modal = new Object();
        LaterInvocator.enterModal(modal);
        try {
            ModalityState modality = ModalityState.current();
            runOffTheEdt(() -> LocalFileSystem.getInstance().refreshFiles(List.of(dir), true, true,
                    () -> ApplicationManager.getApplication().invokeLater(() -> reloaded.set(true), modality)));
            within(() -> false);
            assertFalse("control: without a modality the refresh is held while the dialog is open", reloaded.get());
        } finally {
            LaterInvocator.leaveModal(modal);
        }
        assertTrue("control: it runs once the dialog closes", within(reloaded::get));
    }
}
