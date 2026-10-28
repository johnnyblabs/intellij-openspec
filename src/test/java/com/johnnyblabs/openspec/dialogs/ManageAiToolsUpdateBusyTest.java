package com.johnnyblabs.openspec.dialogs;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.UpdatePreviewService;
import com.johnnyblabs.openspec.services.UpdatePreviewTestSupport;
import com.johnnyblabs.openspec.util.CliRunner;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Manage AI Tools' Update button on the preview path: busy while the preview runs, so a second click can't start a
 * second preview, and usable again on every outcome. Driven by captured CLI 1.15.0 dry-run output.
 */
public class ManageAiToolsUpdateBusyTest extends BasePlatformTestCase {

    private static CliRunner.CliResult captured(String name) {
        String base = "/fixtures/cli/1.15.0/update-dry-run/" + name;
        String stderr = ManageAiToolsUpdateBusyTest.class.getResource(base + ".stderr") != null ? read(base + ".stderr") : "";
        return new CliRunner.CliResult(Integer.parseInt(read(base + ".exit").strip()), read(base + ".json"), stderr);
    }

    private static String read(String path) {
        try (InputStream is = ManageAiToolsUpdateBusyTest.class.getResourceAsStream(path)) {
            assertNotNull("missing fixture " + path, is);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final CliRunner.CliResult OK = new CliRunner.CliResult(0, "Updated.\n", "");
    private static final CliRunner.CliResult FAILED = new CliRunner.CliResult(1, "", "boom");

    private final List<String> stateWhilePreviewing = new ArrayList<>();

    private JButton run(CliRunner.CliResult dryRun, CliRunner.CliResult apply, boolean accept) {
        UpdatePreviewService service = new UpdatePreviewService(getProject());
        JButton button = new JButton("Update");
        UpdatePreviewTestSupport.answer(service, dryRun, apply, summary -> {
            stateWhilePreviewing.add(button.isEnabled() + " " + button.getText());
            return accept;
        });
        ManageAiToolsDialog.previewWithBusyButton(service, "claude", button, () -> false, result -> { });
        return button;
    }

    public void testTheButtonIsBusyWhileThePreviewIsShown() {
        run(captured("windsurf-move"), OK, false);
        assertEquals(List.of("false ..."), stateWhilePreviewing);
    }

    public void testCancelMakesTheButtonUsableAgain() {
        JButton button = run(captured("windsurf-move"), OK, false);
        assertTrue(button.isEnabled());
        assertEquals("Update", button.getText());
    }

    public void testAFailedDryRunOrNothingToDoMakesTheButtonUsableAgain() {
        for (String name : List.of("no-openspec-dir", "up-to-date")) {
            JButton button = run(captured(name), OK, true);
            assertTrue(name, button.isEnabled());
            assertEquals(name, "Update", button.getText());
        }
        assertTrue("no preview was shown", stateWhilePreviewing.isEmpty());
    }

    public void testAFailedApplyOffersRetry() {
        JButton button = run(captured("windsurf-move"), FAILED, true);
        assertTrue(button.isEnabled());
        assertEquals("Retry", button.getText());
    }
}
