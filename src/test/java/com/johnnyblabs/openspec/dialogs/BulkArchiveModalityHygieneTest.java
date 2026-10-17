package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.ModalityCallScanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Bulk Archive dialog is modal and the spec sync it drives hops to the EDT from a background task: every
 * invokeLater/invokeAndWait in both must pass an explicit modality, and the old invokeLater + latch hand-off (which only
 * worked by inheriting the dialog's modality, and deadlocked in tests) must not return.
 */
class BulkArchiveModalityHygieneTest {

    private static void assertEveryCallCarriesAModality(Path source, int minimumCalls) throws IOException {
        List<ModalityCallScanner.Call> calls = ModalityCallScanner.calls(source);
        assertTrue(calls.size() >= minimumCalls,
                "anti-vacuity: expected at least " + minimumCalls + " calls in " + source.getFileName() + ", found " + calls);
        List<String> missing = calls.stream().filter(c -> !c.hasSecondArgument())
                .map(c -> c.method() + " at line " + c.line()).toList();
        assertTrue(missing.isEmpty(), source.getFileName() + ": pass the modality explicitly — " + missing);
        assertFalse(ModalityCallScanner.constructsLatch(Files.readString(source)),
                source.getFileName() + ": use invokeAndWait with a modality instead of an invokeLater + latch hand-off");
    }

    @Test
    void bulkArchiveDialogPassesAModalityEverywhere() throws IOException {
        assertEveryCallCarriesAModality(ModalityCallScanner.mainSource("dialogs/BulkArchiveDialog.java"), 4);
    }

    @Test
    void specSyncHandOffPassesAModality() throws IOException {
        assertEveryCallCarriesAModality(ModalityCallScanner.mainSource("services/SpecSyncService.java"), 1);
    }
}
