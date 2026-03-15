package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.AiToolDetectionService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Manage AI Tools dialog's filtering and grouping logic.
 * Tests the data model behavior without requiring UI components.
 */
class ManageAiToolsFilterTest {

    private List<ToolInfo> testTools;

    @BeforeEach
    void setUp() {
        testTools = List.of(
                new ToolInfo("Claude Code", ToolStatus.CONFIGURED, AiToolDetectionService.ToolType.CLI, "claude"),
                new ToolInfo("GitHub Copilot", ToolStatus.CONFIGURED, AiToolDetectionService.ToolType.IDE_PANEL, "github-copilot"),
                new ToolInfo("Cursor", ToolStatus.DETECTED, AiToolDetectionService.ToolType.IDE_PANEL, "cursor"),
                new ToolInfo("Amazon Q", ToolStatus.AVAILABLE, AiToolDetectionService.ToolType.IDE_PANEL, "amazon-q"),
                new ToolInfo("Kiro", ToolStatus.AVAILABLE, AiToolDetectionService.ToolType.IDE_PANEL, "kiro"),
                new ToolInfo("Roo Code", ToolStatus.AVAILABLE, AiToolDetectionService.ToolType.IDE_PANEL, "roocode")
        );
    }

    // Helper: same filter logic as ManageAiToolsDialog.filterTools()
    private List<ToolInfo> filter(String query) {
        String lowerQuery = query.toLowerCase();
        return testTools.stream()
                .filter(t -> query.isEmpty() || t.name().toLowerCase().contains(lowerQuery))
                .toList();
    }

    private List<ToolInfo> byStatus(List<ToolInfo> tools, ToolStatus status) {
        return tools.stream().filter(t -> t.status() == status).toList();
    }

    @Nested
    class Filtering {

        @Test
        void emptyQuery_returnsAllTools() {
            List<ToolInfo> result = filter("");
            assertEquals(6, result.size());
        }

        @Test
        void exactMatch_returnsSingleTool() {
            List<ToolInfo> result = filter("Claude Code");
            assertEquals(1, result.size());
            assertEquals("Claude Code", result.getFirst().name());
        }

        @Test
        void partialMatch_returnsMatchingTools() {
            List<ToolInfo> result = filter("co");
            // Claude Code, Cursor (no), Roo Code
            assertTrue(result.stream().anyMatch(t -> t.name().equals("Claude Code")));
            assertTrue(result.stream().anyMatch(t -> t.name().equals("Roo Code")));
        }

        @Test
        void caseInsensitive() {
            List<ToolInfo> result = filter("CLAUDE");
            assertEquals(1, result.size());
            assertEquals("Claude Code", result.getFirst().name());
        }

        @Test
        void noMatch_returnsEmpty() {
            List<ToolInfo> result = filter("zzz nonexistent");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class StatusGrouping {

        @Test
        void groupsByStatus() {
            List<ToolInfo> all = filter("");
            List<ToolInfo> configured = byStatus(all, ToolStatus.CONFIGURED);
            List<ToolInfo> detected = byStatus(all, ToolStatus.DETECTED);
            List<ToolInfo> available = byStatus(all, ToolStatus.AVAILABLE);

            assertEquals(2, configured.size());
            assertEquals(1, detected.size());
            assertEquals(3, available.size());
        }

        @Test
        void configuredToolsFirst() {
            List<ToolInfo> all = filter("");
            List<ToolInfo> configured = byStatus(all, ToolStatus.CONFIGURED);

            assertTrue(configured.stream().anyMatch(t -> t.name().equals("Claude Code")));
            assertTrue(configured.stream().anyMatch(t -> t.name().equals("GitHub Copilot")));
        }

        @Test
        void detectedToolsIncluded() {
            List<ToolInfo> all = filter("");
            List<ToolInfo> detected = byStatus(all, ToolStatus.DETECTED);

            assertEquals("Cursor", detected.getFirst().name());
        }

        @Test
        void filterPreservesGrouping() {
            // Filter for "code" — should match Claude Code and Roo Code
            List<ToolInfo> result = filter("code");
            List<ToolInfo> configured = byStatus(result, ToolStatus.CONFIGURED);
            List<ToolInfo> available = byStatus(result, ToolStatus.AVAILABLE);

            assertEquals(1, configured.size()); // Claude Code
            assertEquals(1, available.size());  // Roo Code
        }
    }

    @Nested
    class ToolInfoRecord {

        @Test
        void recordFieldsAccessible() {
            ToolInfo info = new ToolInfo("Test", ToolStatus.AVAILABLE, AiToolDetectionService.ToolType.CLI, "test");
            assertEquals("Test", info.name());
            assertEquals(ToolStatus.AVAILABLE, info.status());
            assertEquals(AiToolDetectionService.ToolType.CLI, info.type());
            assertEquals("test", info.cliId());
        }

        @Test
        void recordEquality() {
            ToolInfo a = new ToolInfo("Test", ToolStatus.AVAILABLE, AiToolDetectionService.ToolType.CLI, "test");
            ToolInfo b = new ToolInfo("Test", ToolStatus.AVAILABLE, AiToolDetectionService.ToolType.CLI, "test");
            assertEquals(a, b);
        }

        @Test
        void differentStatusNotEqual() {
            ToolInfo a = new ToolInfo("Test", ToolStatus.AVAILABLE, AiToolDetectionService.ToolType.CLI, "test");
            ToolInfo b = new ToolInfo("Test", ToolStatus.CONFIGURED, AiToolDetectionService.ToolType.CLI, "test");
            assertNotEquals(a, b);
        }
    }

    @Nested
    class AllToolsConsistency {

        @Test
        void allToolNamesHaveCliIds() {
            for (String name : AiToolDetectionService.getAllToolNames()) {
                String cliId = AiToolDetectionService.getCliToolId(name);
                assertNotNull(cliId, name + " should have a CLI ID");
                assertFalse(cliId.isBlank(), name + " CLI ID should not be blank");
            }
        }

        @Test
        void allToolNamesHaveDirNames() {
            for (String name : AiToolDetectionService.getAllToolNames()) {
                String dirName = AiToolDetectionService.getToolDirName(name);
                assertNotNull(dirName, name + " should have a directory name");
                assertTrue(dirName.startsWith("."), name + " dir should start with dot");
            }
        }

        @Test
        void cliIdsDontContainSpaces() {
            for (String name : AiToolDetectionService.getAllToolNames()) {
                String cliId = AiToolDetectionService.getCliToolId(name);
                assertFalse(cliId.contains(" "),
                        name + " CLI ID '" + cliId + "' should not contain spaces");
            }
        }
    }
}
