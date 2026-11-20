package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.ui.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The archive sync question, shared by the workflow panel, the Archive menu and Verify. The answers map to what the
 * archive does, and the wording says what each choice does.
 */
class ArchiveSyncPromptTest {

    @Test
    void syncAndArchiveSyncs() {
        assertEquals(Boolean.TRUE, ArchiveSyncPrompt.fromChoice(Messages.YES));
    }

    @Test
    void archiveWithoutSyncingSkipsSpecs() {
        assertEquals(Boolean.FALSE, ArchiveSyncPrompt.fromChoice(Messages.NO));
    }

    @Test
    void cancelOrClosingArchivesNothing() {
        assertNull(ArchiveSyncPrompt.fromChoice(Messages.CANCEL));
        assertNull(ArchiveSyncPrompt.fromChoice(-1));
    }

    @Test
    void messageNamesBothChoicesAndWhatEachDoes() {
        String message = ArchiveSyncPrompt.message("alpha");
        assertTrue(message.contains("\"alpha\""), message);
        assertTrue(message.contains("Sync and Archive merges them into the main specs"), message);
        assertTrue(message.contains("Archive Without Syncing archives the change and leaves its delta specs out of the main specs"),
                message);
    }

    @Test
    void aChangeWithoutDeltaSpecsIsArchivedWithoutAsking() {
        // No dialog is shown on this path, so no project is needed.
        assertEquals(Boolean.TRUE, ArchiveSyncPrompt.choose(null, "alpha", false));
    }
}
