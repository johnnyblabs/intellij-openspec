package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.util.UpdateOutputParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The cleanup dialog follows what the CLI actually said: it quotes the CLI's guidance line, and starts the files
 * checked only when the CLI certified they hold no user content. From 1.14 the CLI warns to back up custom content,
 * so nothing starts checked. Driven by real {@code openspec update} captures.
 */
public class LegacyCleanupDialogTest extends BasePlatformTestCase {

    private static UpdateOutputParser.LegacyCleanup capture(String version) {
        String path = "/fixtures/cli/" + version + "/update-legacy-pending.txt";
        try (InputStream is = LegacyCleanupDialogTest.class.getResourceAsStream(path)) {
            assertNotNull("missing fixture " + path, is);
            return UpdateOutputParser.parseLegacyCleanupBlock(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private LegacyCleanupDialog dialog(UpdateOutputParser.LegacyCleanup block) {
        LegacyCleanupDialog dialog = new LegacyCleanupDialog(getProject(), block);
        Disposer.register(getTestRootDisposable(), dialog.getDisposable());
        return dialog;
    }

    public void testOn1_14TheFilesStartUncheckedAndTheWarningIsQuoted() {
        UpdateOutputParser.LegacyCleanup block = capture("1.14.3");
        LegacyCleanupDialog dialog = dialog(block);

        assertEquals("1.14 warns to back up custom content, so nothing is preselected", List.of(),
                dialog.getSelectedFiles());
        assertTrue(dialog.introHtmlForTest(), dialog.introHtmlForTest().contains(block.guidance()));
        assertTrue("tells the user to choose", dialog.introHtmlForTest().contains("Nothing is selected"));
        assertFalse("never claims a certification the CLI didn't make",
                dialog.introHtmlForTest().contains("No user content to preserve"));
    }

    public void testBefore1_14TheCertifiedFilesStartChecked() {
        UpdateOutputParser.LegacyCleanup block = capture("1.13.2");
        LegacyCleanupDialog dialog = dialog(block);

        assertEquals(block.files(), dialog.getSelectedFiles());
        assertTrue(dialog.introHtmlForTest().contains("No user content to preserve"));
        assertFalse(dialog.introHtmlForTest().contains("Nothing is selected"));
    }

    public void testWithNoGuidanceLineNothingIsPreselectedOrQuoted() {
        LegacyCleanupDialog dialog = dialog(new UpdateOutputParser.LegacyCleanup(List.of(".cursor/commands/a.md"), null));
        assertEquals(List.of(), dialog.getSelectedFiles());
        assertFalse(dialog.introHtmlForTest().contains("The CLI says"));
    }

    public void testTheQuotedLineIsEscaped() {
        String html = LegacyCleanupDialog.introHtml("Back up <custom> & things:", false);
        assertTrue(html, html.contains("Back up &lt;custom&gt; &amp; things:"));
    }
}
