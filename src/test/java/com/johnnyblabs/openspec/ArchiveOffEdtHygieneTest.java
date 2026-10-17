package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The archive may run the OpenSpec CLI, so the UI entry points never call {@code ChangeService.archiveChange}
 * themselves (they ran it inside {@code invokeLater}, on the UI thread); they go through {@code ArchiveDispatch}.
 */
class ArchiveOffEdtHygieneTest {

    private static final Pattern DIRECT_CALL = Pattern.compile("\\.archiveChange\\(");

    @Test
    void uiEntryPointsArchiveThroughTheBackgroundDispatch() throws IOException {
        for (String file : List.of("toolwindow/WorkflowActionPanel.java", "actions/OpenSpecArchiveAction.java",
                "actions/OpenSpecVerifyAction.java")) {
            String code = Files.readString(ModalityCallScanner.mainSource(file));
            assertFalse(ModalityCallScanner.codeContains(code, "changeService.archiveChange(")
                            || DIRECT_CALL.matcher(stripComments(code)).find(),
                    file + " calls archiveChange directly; use ArchiveDispatch.start");
            assertTrue(ModalityCallScanner.codeContains(code, "ArchiveDispatch.start("), file + " should start the archive in the background");
        }
    }

    @Test
    void openDocumentsAreSavedBeforeEveryArchiveStarts() throws IOException {
        // The CLI archives the files on disk; unsaved editor text would be left out, then orphaned by the move.
        String dispatch = Files.readString(ModalityCallScanner.mainSource("services/ArchiveDispatch.java"));
        assertTrue(ModalityCallScanner.codeContains(dispatch, "saveAllDocuments()"), "ArchiveDispatch must save documents");
        int start = dispatch.indexOf("public static void start(");
        assertTrue(dispatch.indexOf("saveBeforeArchive();", start) > start, "start() must save before the task");
        String bulk = Files.readString(ModalityCallScanner.mainSource("dialogs/BulkArchiveDialog.java"));
        int run = bulk.indexOf("void runBulkArchive(Archiver archiver)");
        int save = bulk.indexOf("ArchiveDispatch.saveBeforeArchive();", run);
        assertTrue(save > run && save < bulk.indexOf("new Task.Backgroundable", run), "Bulk Archive must save before its task");
    }

    @Test
    void archiveAndVerifyActionsApplySpecUpdatesLikeOpenspecArchive() throws IOException {
        for (String file : List.of("actions/OpenSpecArchiveAction.java", "actions/OpenSpecVerifyAction.java")) {
            String code = Files.readString(ModalityCallScanner.mainSource(file));
            assertTrue(ModalityCallScanner.codeContains(code, "ArchiveDispatch.start(project, target, true,"),
                    file + " must archive with syncSpecs=true, as `openspec archive` applies the delta specs");
        }
    }

    private static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
