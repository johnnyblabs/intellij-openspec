package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.model.SpecSyncResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A retirement is shown before it happens: the preview's tab, diff title and banner, and the archive prompt. */
class RetirementShownTest {

    private static SpecSyncResult result(String id, boolean retired) {
        return new SpecSyncResult(id, "/p/openspec/specs/" + id + "/spec.md", "# " + id + "\n", retired ? "" : "# x\n",
                List.of(), List.of(), List.of(), retired);
    }

    @Test
    void thePreviewNamesTheDeletion() {
        SpecSyncResult retired = result("foo", true);
        SpecSyncResult kept = result("bar", false);
        assertEquals("foo (retire)", SyncPreviewDialog.tabTitle(retired));
        assertEquals("bar", SyncPreviewDialog.tabTitle(kept));
        assertEquals("After sync: file deleted", SyncPreviewDialog.afterTitle(retired));
        assertEquals("After sync", SyncPreviewDialog.afterTitle(kept));
        assertEquals(List.of("Retires capability foo: specs/foo/spec.md will be deleted."),
                SyncPreviewDialog.retirementLines(List.of(retired, kept)));
        assertTrue(retired.hasChanges(), "a retirement is a change to apply");
    }

    @Test
    void theArchivePromptNamesTheSpecsItDeletes() {
        String message = ArchiveSyncPrompt.message("c", List.of("foo", "group/bar"));
        assertTrue(message.contains("also deletes spec file(s): specs/foo/spec.md, specs/group/bar/spec.md"), message);
        assertFalse(ArchiveSyncPrompt.message("c", List.of()).contains("deletes"), "nothing retired, nothing said");
    }
}
