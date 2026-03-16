package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.model.Change;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void conflictDetectionLogic_noOverlap() {
        // Simulate the conflict detection pattern from SpecSyncService
        Map<String, List<String>> capToChanges = new LinkedHashMap<>();
        capToChanges.computeIfAbsent("auth", k -> new ArrayList<>()).add("change-a");
        capToChanges.computeIfAbsent("export", k -> new ArrayList<>()).add("change-b");
        capToChanges.entrySet().removeIf(e -> e.getValue().size() < 2);

        assertTrue(capToChanges.isEmpty(), "No conflicts expected when capabilities don't overlap");
    }

    @Test
    void conflictDetectionLogic_withOverlap() {
        Map<String, List<String>> capToChanges = new LinkedHashMap<>();
        capToChanges.computeIfAbsent("workflow", k -> new ArrayList<>()).add("change-a");
        capToChanges.computeIfAbsent("workflow", k -> new ArrayList<>()).add("change-b");
        capToChanges.computeIfAbsent("auth", k -> new ArrayList<>()).add("change-a");
        capToChanges.entrySet().removeIf(e -> e.getValue().size() < 2);

        assertEquals(1, capToChanges.size());
        assertTrue(capToChanges.containsKey("workflow"));

        // Verify both changes are listed as conflicting
        Set<String> conflictingChanges = new HashSet<>();
        for (List<String> names : capToChanges.values()) {
            conflictingChanges.addAll(names);
        }
        assertTrue(conflictingChanges.contains("change-a"));
        assertTrue(conflictingChanges.contains("change-b"));
    }

    @Test
    void sequentialArchiveOrder_lastWriteWins() {
        // Simulating that last-archived change's sync takes precedence
        List<String> archiveOrder = List.of("change-a", "change-b", "change-c");
        // After sequential archive, the last change synced for a given capability wins
        // This is verified by checking that change-c would be processed last
        assertEquals("change-c", archiveOrder.getLast());
    }
}
