package com.johnnyb.openspec.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiToolDetectionServiceTest {

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
        assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                AiToolDetectionService.getToolType("GitHub Copilot"));
        assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                AiToolDetectionService.getToolType("Unknown"));
    }
}
