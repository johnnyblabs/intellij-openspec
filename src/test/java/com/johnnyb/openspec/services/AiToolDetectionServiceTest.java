package com.johnnyb.openspec.services;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiToolDetectionServiceTest {

    @Nested
    class ToolClassification {

        @Test
        void claudeCode_isCliTool() {
            assertTrue(AiToolDetectionService.isCliTool("Claude Code"));
        }

        @Test
        void gemini_isCliTool() {
            assertTrue(AiToolDetectionService.isCliTool("Gemini"));
        }

        @Test
        void copilot_isIdePanelTool() {
            assertFalse(AiToolDetectionService.isCliTool("GitHub Copilot"));
        }

        @Test
        void cursor_isIdePanelTool() {
            assertFalse(AiToolDetectionService.isCliTool("Cursor"));
        }

        @Test
        void windsurf_isIdePanelTool() {
            assertFalse(AiToolDetectionService.isCliTool("Windsurf"));
        }

        @Test
        void cline_isIdePanelTool() {
            assertFalse(AiToolDetectionService.isCliTool("Cline"));
        }

        @Test
        void unknownTool_defaultsToIdePanel() {
            assertFalse(AiToolDetectionService.isCliTool("Some New Tool"));
        }

        @Test
        void getToolType_returnsCorrectTypes() {
            assertEquals(AiToolDetectionService.ToolType.CLI,
                    AiToolDetectionService.getToolType("Claude Code"));
            assertEquals(AiToolDetectionService.ToolType.CLI,
                    AiToolDetectionService.getToolType("Gemini"));
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                    AiToolDetectionService.getToolType("GitHub Copilot"));
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                    AiToolDetectionService.getToolType("Cursor"));
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                    AiToolDetectionService.getToolType("Windsurf"));
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                    AiToolDetectionService.getToolType("Cline"));
        }

        @Test
        void getToolType_unknownDefaultsToIdePanel() {
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                    AiToolDetectionService.getToolType("Unknown"));
        }

        @Test
        void nullToolName_defaultsToIdePanel() {
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                    AiToolDetectionService.getToolType(null));
            assertFalse(AiToolDetectionService.isCliTool(null));
        }

        @Test
        void emptyToolName_defaultsToIdePanel() {
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                    AiToolDetectionService.getToolType(""));
            assertFalse(AiToolDetectionService.isCliTool(""));
        }

        @Test
        void allKnownToolsCovered() {
            // Verify all 6 known tools have explicit type mappings
            String[] cliTools = {"Claude Code", "Gemini"};
            String[] idePanelTools = {"GitHub Copilot", "Cursor", "Windsurf", "Cline"};

            for (String tool : cliTools) {
                assertEquals(AiToolDetectionService.ToolType.CLI,
                        AiToolDetectionService.getToolType(tool),
                        tool + " should be CLI");
            }
            for (String tool : idePanelTools) {
                assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                        AiToolDetectionService.getToolType(tool),
                        tool + " should be IDE_PANEL");
            }
        }
    }

    @Nested
    class ToolTypeEnum {

        @Test
        void enumValues() {
            AiToolDetectionService.ToolType[] values = AiToolDetectionService.ToolType.values();
            assertEquals(2, values.length);
            assertEquals(AiToolDetectionService.ToolType.CLI, values[0]);
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL, values[1]);
        }

        @Test
        void valueOf() {
            assertEquals(AiToolDetectionService.ToolType.CLI,
                    AiToolDetectionService.ToolType.valueOf("CLI"));
            assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                    AiToolDetectionService.ToolType.valueOf("IDE_PANEL"));
        }
    }
}
