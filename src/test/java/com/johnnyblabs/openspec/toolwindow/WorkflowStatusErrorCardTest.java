package com.johnnyblabs.openspec.toolwindow;

import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.CliFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The workflow panel's "can't read change" card text, from real CLI 1.14.3 rejections. */
class WorkflowStatusErrorCardTest {

    private static String capturedMessage(String fixture) {
        return JsonParser.parseString(CliFixtures.text("1.14.3/errors/" + fixture + ".stdout")).getAsJsonObject()
                .getAsJsonArray("status").get(0).getAsJsonObject().get("message").getAsString();
    }

    @Test
    void summaryNamesTheChangeAndTheCliReason() {
        String reason = capturedMessage("status-unknown");
        String summary = WorkflowActionPanel.statusErrorSummary("nosuch", reason);
        assertTrue(com.intellij.openapi.util.text.StringUtil.unescapeXmlEntities(summary)
                .contains("Can't read change 'nosuch'"));
        String shown = com.intellij.openapi.util.text.StringUtil.unescapeXmlEntities(summary);
        assertTrue(shown.contains("Change 'nosuch' not found. Available changes</html>"),
                "the CLI's first line, its trailing colon dropped: " + shown);
        assertFalse(summary.contains("No changes yet"));
    }

    @Test
    void multiLineYamlErrorShowsItsFirstLineAndKeepsTheRestForTheTooltip() {
        String reason = capturedMessage("status-badyaml");
        assertTrue(reason.contains("\n"), "capture precondition: the YAML error spans lines");
        String summary = WorkflowActionPanel.statusErrorSummary("badyaml", reason);
        assertFalse(summary.contains("schema: [spec-driven"), "only the first line is in the label");
        assertFalse(summary.endsWith(":</html>"), "a trailing colon is dropped");

        String tooltip = WorkflowActionPanel.statusErrorTooltip(reason);
        assertTrue(tooltip.startsWith("<html><pre>"));
        assertTrue(tooltip.contains("schema: [spec-driven"), "the full message is in the tooltip");
    }

    @Test
    void markupInTheReasonIsEscaped() {
        String summary = WorkflowActionPanel.statusErrorSummary("c<x>", "bad <b>thing</b>");
        assertFalse(summary.contains("<b>"));
        assertTrue(summary.contains("&lt;b&gt;"));
    }
}
