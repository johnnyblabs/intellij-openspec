package com.johnnyblabs.openspec.services;

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
        void forgecode_isCliTool() {
            assertTrue(AiToolDetectionService.isCliTool("ForgeCode"));
        }

        @Test
        void bobShell_isCliTool() {
            assertTrue(AiToolDetectionService.isCliTool("Bob Shell"));
        }

        @Test
        void junie_isIdePanelTool() {
            assertFalse(AiToolDetectionService.isCliTool("Junie"));
        }

        @Test
        void lingma_isIdePanelTool() {
            assertFalse(AiToolDetectionService.isCliTool("Lingma"));
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

    @Nested
    class ToolDirMapping {

        @Test
        void getToolDirName_knownTools() {
            assertEquals(".claude", AiToolDetectionService.getToolDirName("Claude Code"));
            assertEquals(".github", AiToolDetectionService.getToolDirName("GitHub Copilot"));
            assertEquals(".cursor", AiToolDetectionService.getToolDirName("Cursor"));
            assertEquals(".windsurf", AiToolDetectionService.getToolDirName("Windsurf"));
            assertEquals(".cline", AiToolDetectionService.getToolDirName("Cline"));
            assertEquals(".gemini", AiToolDetectionService.getToolDirName("Gemini"));
            assertEquals(".amazonq", AiToolDetectionService.getToolDirName("Amazon Q"));
            assertEquals(".kiro", AiToolDetectionService.getToolDirName("Kiro"));
            assertEquals(".roo", AiToolDetectionService.getToolDirName("Roo Code"));
            assertEquals(".trae", AiToolDetectionService.getToolDirName("Trae"));
        }

        @Test
        void getToolDirName_unknownReturnsNull() {
            assertNull(AiToolDetectionService.getToolDirName("Nonexistent Tool"));
        }

        @Test
        void getToolDirName_nullReturnsNull() {
            assertNull(AiToolDetectionService.getToolDirName(null));
        }
    }

    @Nested
    class CliToolIds {

        @Test
        void getCliToolId_knownTools() {
            assertEquals("claude", AiToolDetectionService.getCliToolId("Claude Code"));
            assertEquals("github-copilot", AiToolDetectionService.getCliToolId("GitHub Copilot"));
            assertEquals("cursor", AiToolDetectionService.getCliToolId("Cursor"));
            assertEquals("amazon-q", AiToolDetectionService.getCliToolId("Amazon Q"));
            assertEquals("roocode", AiToolDetectionService.getCliToolId("Roo Code"));
            assertEquals("auggie", AiToolDetectionService.getCliToolId("Augment"));
            assertEquals("junie", AiToolDetectionService.getCliToolId("Junie"));
            assertEquals("lingma", AiToolDetectionService.getCliToolId("Lingma"));
            assertEquals("bob", AiToolDetectionService.getCliToolId("Bob Shell"));
        }

        @Test
        void getCliToolId_forgeCodeDivergesFromDirectory() {
            // Upstream value is "forgecode" but the directory is ".forge" — verify the CLI ID
            // emitted matches upstream regardless of the directory key.
            assertEquals("forgecode", AiToolDetectionService.getCliToolId("ForgeCode"));
            assertEquals(".forge", AiToolDetectionService.getToolDirName("ForgeCode"));
        }

        @Test
        void getCliToolId_nullReturnsNull() {
            assertNull(AiToolDetectionService.getCliToolId(null));
        }

        @Test
        void getCliToolId_unknownReturnsNull() {
            assertNull(AiToolDetectionService.getCliToolId("Unknown"));
        }
    }

    @Nested
    class AllToolNames {

        @Test
        void getAllToolNames_returns28Tools() {
            var names = AiToolDetectionService.getAllToolNames();
            assertEquals(28, names.size());
        }

        @Test
        void getAllToolNames_containsExpectedTools() {
            var names = AiToolDetectionService.getAllToolNames();
            assertTrue(names.contains("Claude Code"));
            assertTrue(names.contains("GitHub Copilot"));
            assertTrue(names.contains("Cursor"));
            assertTrue(names.contains("Amazon Q"));
            assertTrue(names.contains("Kiro"));
            assertTrue(names.contains("Roo Code"));
            assertTrue(names.contains("Junie"));
            assertTrue(names.contains("Lingma"));
            assertTrue(names.contains("ForgeCode"));
            assertTrue(names.contains("Bob Shell"));
        }

        @Test
        void getAllToolNames_returnsImmutableList() {
            var names = AiToolDetectionService.getAllToolNames();
            assertThrows(UnsupportedOperationException.class, () -> names.add("Test"));
        }

        @Test
        void everyToolHasCliId() {
            for (String tool : AiToolDetectionService.getAllToolNames()) {
                assertNotNull(AiToolDetectionService.getCliToolId(tool),
                        tool + " should have a CLI ID");
            }
        }

        @Test
        void everyToolHasDirName() {
            for (String tool : AiToolDetectionService.getAllToolNames()) {
                assertNotNull(AiToolDetectionService.getToolDirName(tool),
                        tool + " should have a directory name");
            }
        }

        @Test
        void everyToolHasType() {
            for (String tool : AiToolDetectionService.getAllToolNames()) {
                assertNotNull(AiToolDetectionService.getToolType(tool),
                        tool + " should have a tool type");
            }
        }
    }

    @Nested
    class ToolStatusEnum {

        @Test
        void enumValues() {
            AiToolDetectionService.ToolStatus[] values = AiToolDetectionService.ToolStatus.values();
            assertEquals(3, values.length);
        }

        @Test
        void valueOf() {
            assertEquals(AiToolDetectionService.ToolStatus.CONFIGURED,
                    AiToolDetectionService.ToolStatus.valueOf("CONFIGURED"));
            assertEquals(AiToolDetectionService.ToolStatus.DETECTED,
                    AiToolDetectionService.ToolStatus.valueOf("DETECTED"));
            assertEquals(AiToolDetectionService.ToolStatus.AVAILABLE,
                    AiToolDetectionService.ToolStatus.valueOf("AVAILABLE"));
        }
    }

    @Nested
    class ToolInfoRecord {

        @Test
        void toolInfoHoldsAllFields() {
            var info = new AiToolDetectionService.ToolInfo(
                    "Claude Code",
                    AiToolDetectionService.ToolStatus.CONFIGURED,
                    AiToolDetectionService.ToolType.CLI,
                    "claude");
            assertEquals("Claude Code", info.name());
            assertEquals(AiToolDetectionService.ToolStatus.CONFIGURED, info.status());
            assertEquals(AiToolDetectionService.ToolType.CLI, info.type());
            assertEquals("claude", info.cliId());
        }
    }

    @Nested
    class MapConsistency {

        @Test
        void allMapsHaveSameSize() {
            // TOOL_DIRS, TOOL_TYPES, and CLI_TOOL_IDS should all have 28 entries (OpenSpec 1.3.x)
            var names = AiToolDetectionService.getAllToolNames();
            assertEquals(28, names.size(), "Should have 28 tools");

            for (String name : names) {
                assertNotNull(AiToolDetectionService.getToolType(name),
                        name + " missing from TOOL_TYPES");
                assertNotNull(AiToolDetectionService.getCliToolId(name),
                        name + " missing from CLI_TOOL_IDS");
                assertNotNull(AiToolDetectionService.getToolDirName(name),
                        name + " missing from TOOL_DIRS");
            }
        }
    }

    @Nested
    class ToolGuidanceLookups {

        @Test
        void forgeCode_hasExplicitTerminalGuidance() {
            var g = AiToolDetectionService.getToolGuidance("ForgeCode");
            assertEquals("terminal", g.chatPanelName());
            assertEquals("Paste into ForgeCode", g.pasteAction());
            assertNull(g.promptPrefix());
            assertTrue(g.canAutoSave());
        }

        @Test
        void bobShell_hasExplicitTerminalGuidance() {
            var g = AiToolDetectionService.getToolGuidance("Bob Shell");
            assertEquals("terminal", g.chatPanelName());
            assertEquals("Paste into Bob Shell", g.pasteAction());
            assertNull(g.promptPrefix());
            assertTrue(g.canAutoSave());
        }

        @Test
        void junie_hasExplicitGuidanceWithOpsxPrefix() {
            var g = AiToolDetectionService.getToolGuidance("Junie");
            assertEquals("Junie", g.chatPanelName());
            assertEquals("Open Junie and paste the prompt", g.pasteAction());
            assertEquals("/opsx-", g.promptPrefix());
            assertFalse(g.canAutoSave());
        }

        @Test
        void lingma_hasExplicitGuidanceWithoutPrefix() {
            var g = AiToolDetectionService.getToolGuidance("Lingma");
            assertEquals("Lingma chat", g.chatPanelName());
            assertEquals("Open Lingma chat and paste the prompt", g.pasteAction());
            assertNull(g.promptPrefix());
            assertFalse(g.canAutoSave());
        }

        @Test
        void unknownTool_fallsThroughToDefaultGuidance() {
            // Preserves the default-fallback regression coverage the prior
            // junieAndLingma_stillFallThroughToDefault test was providing.
            var g = AiToolDetectionService.getToolGuidance("Some Future Tool");
            assertEquals("your AI tool", g.chatPanelName());
            assertEquals("Paste into your AI tool", g.pasteAction());
        }
    }
}
