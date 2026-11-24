package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolType;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin's type for each tool CLI 2.1 added equals the evidence table ({@code tool-types-2.1.tsv}): one row per
 * tool, a source for every classified one, and no unclassified tool with a badge.
 */
class ToolTypes21Test {

    record Row(ToolType type, String source) {
    }

    static Map<String, Row> table() throws IOException {
        Map<String, Row> rows = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of("src/test/resources/tool-types-2.1.tsv"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            assertEquals(3, f.length, line);
            assertNull(rows.put(f[0], new Row(ToolType.valueOf(f[1]), f[2])), "duplicate row " + f[0]);
        }
        return rows;
    }

    @Test
    void everyToolCli21AddedHasTheTablesType() throws IOException {
        Map<String, Row> table = table();
        TreeSet<String> added = new TreeSet<>();
        for (AiTool tool : AiToolRegistry.all()) {
            if (!AiToolRegistry.addedIn21(tool)) continue;
            added.add(tool.id());
            Row row = table.get(tool.id());
            assertNotNull(row, tool.id() + " has no row");
            assertEquals(row.type(), tool.type(), tool.id());
            if (row.type() == ToolType.UNCLASSIFIED) {
                assertTrue(row.source().isEmpty(), tool.id() + ": an unclassified tool cites no source");
            } else {
                assertTrue(row.source().startsWith("https://"), tool.id() + ": a classified tool cites its vendor's page");
            }
        }
        assertEquals(38, added.size(), "sanity: CLI 2.1's 38 tools");
        assertEquals(added, table.keySet(), "a row for each of them, and no other");
    }

    @Test
    void anUnclassifiedToolHasNoBadgeAndTheGenericGuidance() {
        AiTool goose = AiToolRegistry.byId("goose");
        assertEquals(ToolType.UNCLASSIFIED, goose.type());
        assertNull(goose.type().badge());
        assertEquals("CLI", ToolType.CLI.badge());
        assertEquals("IDE", ToolType.IDE_PANEL.badge());
        assertFalse(AiToolDetectionService.isCliTool(goose.name()), "no save-path hints");
        assertEquals("Paste into your AI tool", AiToolDetectionService.getToolGuidance(goose.name()).pasteAction());
    }
}
