package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.ui.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The unsynced-delta-specs prompt's answers, as the archive reads them. */
class ArchiveSyncChoiceTest {

    @Test
    void archiveWithoutSyncingSkipsSpecs() {
        assertEquals(Boolean.FALSE, WorkflowActionPanel.archiveSyncSpecs(Messages.NO));
    }

    @Test
    void cancelOrClosingArchivesNothing() {
        assertNull(WorkflowActionPanel.archiveSyncSpecs(Messages.CANCEL));
        assertNull(WorkflowActionPanel.archiveSyncSpecs(-1));
    }
}
