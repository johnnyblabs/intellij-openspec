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
    void archiveAndVerifyActionsAskTheSyncQuestionBeforeArchiving() throws IOException {
        for (String file : List.of("actions/OpenSpecArchiveAction.java", "actions/OpenSpecVerifyAction.java")) {
            String code = Files.readString(ModalityCallScanner.mainSource(file));
            assertTrue(ModalityCallScanner.codeContains(code, "ArchiveSyncPrompt.choose("),
                    file + " must ask the shared sync question before archiving");
            assertTrue(ModalityCallScanner.codeContains(code, "ArchiveDispatch.start(project, target, sync,"),
                    file + " must archive with the answer to that question");
        }
    }

    /**
     * Verify's two entry points show the report and archive only on its Archive… exit code, never on OK (upstream's
     * verify workflow doesn't archive). The Archive action keeps the pre-flight, whose OK archives.
     */
    @Test
    void verifyEntryPointsArchiveOnlyThroughTheReportsArchiveButton() throws IOException {
        for (String file : List.of("actions/OpenSpecVerifyAction.java", "toolwindow/WorkflowActionPanel.java")) {
            String code = stripComments(Files.readString(ModalityCallScanner.mainSource(file)));
            assertTrue(code.contains("VerifyDialog.report(project, result, archiveOffered)"), file + " must show the report");
            assertTrue(code.contains("VerifyDialog.archiveRequested(dialog.getExitCode())"),
                    file + " must archive only on the Archive… exit code");
            assertFalse(code.contains("new VerifyDialog(") || code.contains("VerifyDialog(project, result).showAndGet()"),
                    file + " must not use the pre-flight, whose OK archives");
        }
        String archive = stripComments(Files.readString(ModalityCallScanner.mainSource("actions/OpenSpecArchiveAction.java")));
        assertTrue(archive.contains("new VerifyDialog(project, result)"), "Archive keeps the pre-flight");
    }

    private static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
