package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.coordination.WorksetEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the pure validation rules that {@code NewStoreDialog.doValidate()} and
 * {@code NewWorksetDialog.doValidate()} delegate to. These block OK on invalid input without a
 * running IDE.
 */
class CoordinationDialogValidationTest {

    // ---- T.3: New Store validation ------------------------------------------

    @Test
    void storeValidationBlocksBlankId(@TempDir Path existing) {
        assertNotNull(NewStoreDialog.validateStore("", existing.toString()), "blank id blocks OK");
        assertNotNull(NewStoreDialog.validateStore("   ", existing.toString()));
        assertNotNull(NewStoreDialog.validateStore(null, existing.toString()));
    }

    @Test
    void storeValidationBlocksBlankOrMissingPath() {
        assertNotNull(NewStoreDialog.validateStore("my-store", ""), "blank path blocks OK (path is required)");
        assertNotNull(NewStoreDialog.validateStore("my-store", null));
        assertNotNull(NewStoreDialog.validateStore("my-store", "/no/such/folder/anywhere"),
                "a non-existent folder blocks OK");
    }

    @Test
    void storeValidationAcceptsIdWithExistingFolder(@TempDir Path existing) {
        assertNull(NewStoreDialog.validateStore("my-store", existing.toString()));
    }

    // ---- T.3: New Workset validation ----------------------------------------

    @Test
    void worksetValidationBlocksBlankName() {
        List<WorksetEntry.Member> members = List.of(new WorksetEntry.Member("m", "/fixture/a"));
        assertNotNull(NewWorksetDialog.validateWorkset("", members));
        assertNotNull(NewWorksetDialog.validateWorkset(null, members));
    }

    @Test
    void worksetValidationBlocksEmptyOrIncompleteMembers() {
        assertNotNull(NewWorksetDialog.validateWorkset("view", List.of()), "no members blocks OK");
        assertNotNull(NewWorksetDialog.validateWorkset("view",
                List.of(new WorksetEntry.Member("", "/fixture/a"))), "blank member name blocks OK");
        assertNotNull(NewWorksetDialog.validateWorkset("view",
                List.of(new WorksetEntry.Member("m", ""))), "blank member folder blocks OK");
    }

    @Test
    void worksetValidationAcceptsNameWithCompleteMembers() {
        assertNull(NewWorksetDialog.validateWorkset("view", List.of(
                new WorksetEntry.Member("primary", "/fixture/a"),
                new WorksetEntry.Member("secondary", "/fixture/b"))));
    }
}
