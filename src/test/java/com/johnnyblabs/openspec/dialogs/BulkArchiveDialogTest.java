package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.model.Change;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Row defaults. The archive loop, status load, blocking gate and order are tested against the real code in
 * {@code BulkArchiveDialogModalityTest} (and conflict detection in {@code SpecSyncServiceTest.DetectConflicts}); the
 * earlier tests here only re-implemented that logic on local maps and lists, so they were removed.
 */
class BulkArchiveDialogTest {

    @Test
    void changeRow_defaultsToSelected() {
        Change change = new Change("test-change", "/tmp/test");
        BulkArchiveDialog.ChangeRow row = new BulkArchiveDialog.ChangeRow(change);

        assertTrue(row.selected);
        assertEquals("loading...", row.artifactStatus);
        assertFalse(row.hasConflict);
        assertEquals("", row.archiveStatus);
    }
}
