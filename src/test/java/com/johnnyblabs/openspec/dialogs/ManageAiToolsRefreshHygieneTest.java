package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.ModalityCallScanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manage AI Tools runs the CLI in a background task. A synchronous VFS refresh there throws "Access is allowed from
 * write thread only", which skipped the list reload and left the button stuck. The task must refresh asynchronously
 * through the modality-aware helper, post every EDT hop with the dialog's modality (the dialog is modal), and restore
 * the button on any failure, not only a CLI error.
 */
class ManageAiToolsRefreshHygieneTest {

    private static final Path SOURCE = ModalityCallScanner.mainSource("dialogs/ManageAiToolsDialog.java");

    private static String methodBody(String content, String signaturePrefix) {
        int sig = content.indexOf(signaturePrefix);
        assertTrue(sig >= 0, "expected to find method: " + signaturePrefix);
        int bodyStart = content.indexOf('{', sig);
        int depth = 0;
        for (int i = bodyStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return content.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces for: " + signaturePrefix);
    }

    @Test
    void backgroundCliRunRefreshesAsynchronously() throws IOException {
        String body = methodBody(Files.readString(SOURCE), "private void runCliAction(");
        assertTrue(ModalityCallScanner.codeContains(body, "CliRunner.run("), "anti-vacuity: this is the background CLI run");
        assertFalse(ModalityCallScanner.codeContains(body, "syncRefresh("), "no synchronous VFS refresh inside the background task");
        assertTrue(ModalityCallScanner.codeContains(body, "refreshThenPost("), "the reload must chain off the modality-aware async refresh");
        assertTrue(ModalityCallScanner.codeContains(body, "catch (Exception"), "any failure must restore the button, not only a CliException");
        assertFalse(ModalityCallScanner.codeContains(body, "SwingUtilities.invokeLater("), "use Application.invokeLater with the dialog's modality");
    }

    @Test
    void refreshHelperPassesTheModalityToTheRefresh() throws IOException {
        String body = methodBody(Files.readString(SOURCE), "static void refreshThenPost(");
        // LocalFileSystem.refreshFiles uses the caller's default modality: non-modal on a background task, so it
        // would be held until the dialog closes.
        assertFalse(ModalityCallScanner.codeContains(body, "refreshFiles("), "refreshFiles takes no modality; use RefreshQueue.refresh(..., modality, ...)");
        assertTrue(ModalityCallScanner.codeContains(body, "RefreshQueue.getInstance().refresh(") && ModalityCallScanner.codeContains(body, "modality"),
                "the refresh itself must carry the dialog's modality");
    }

    @Test
    void everyEdtHopCarriesAModality() throws IOException {
        List<ModalityCallScanner.Call> calls = ModalityCallScanner.calls(SOURCE);
        assertTrue(calls.size() >= 4, "anti-vacuity: expected the dialog's EDT hops, found " + calls);
        List<String> missing = calls.stream().filter(c -> !c.hasSecondArgument())
                .map(c -> c.method() + " at line " + c.line()).toList();
        assertTrue(missing.isEmpty(), "pass the modality explicitly: " + missing);
    }
}
