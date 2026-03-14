package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolGuidance;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ToolGuidanceTest {

    static Stream<String> allKnownTools() {
        return Stream.of("Claude Code", "Gemini", "GitHub Copilot", "Cursor", "Windsurf", "Cline");
    }

    @Nested
    class AllToolsHaveGuidance {

        @ParameterizedTest
        @MethodSource("com.johnnyblabs.openspec.services.ToolGuidanceTest#allKnownTools")
        void returnsNonNullGuidance(String toolName) {
            ToolGuidance guidance = AiToolDetectionService.getToolGuidance(toolName);
            assertNotNull(guidance, toolName + " should have guidance");
        }

        @ParameterizedTest
        @MethodSource("com.johnnyblabs.openspec.services.ToolGuidanceTest#allKnownTools")
        void hasNonEmptyChatPanelName(String toolName) {
            ToolGuidance guidance = AiToolDetectionService.getToolGuidance(toolName);
            assertNotNull(guidance.chatPanelName());
            assertFalse(guidance.chatPanelName().isEmpty(), toolName + " chatPanelName should not be empty");
        }

        @ParameterizedTest
        @MethodSource("com.johnnyblabs.openspec.services.ToolGuidanceTest#allKnownTools")
        void hasNonEmptyPasteAction(String toolName) {
            ToolGuidance guidance = AiToolDetectionService.getToolGuidance(toolName);
            assertNotNull(guidance.pasteAction());
            assertFalse(guidance.pasteAction().isEmpty(), toolName + " pasteAction should not be empty");
        }
    }

    @Nested
    class CliToolsCanAutoSave {

        @ParameterizedTest
        @ValueSource(strings = {"Claude Code", "Gemini"})
        void cliToolsReportCanAutoSaveTrue(String toolName) {
            assertTrue(AiToolDetectionService.getToolGuidance(toolName).canAutoSave(),
                    toolName + " should have canAutoSave true");
        }
    }

    @Nested
    class IdePanelToolsCannotAutoSave {

        @ParameterizedTest
        @ValueSource(strings = {"GitHub Copilot", "Cursor", "Windsurf", "Cline"})
        void idePanelToolsReportCanAutoSaveFalse(String toolName) {
            assertFalse(AiToolDetectionService.getToolGuidance(toolName).canAutoSave(),
                    toolName + " should have canAutoSave false");
        }
    }

    @Nested
    class PromptPrefixCorrectness {

        @Test
        void claudeCode_hasOpsxColonPrefix() {
            assertEquals("/opsx:", AiToolDetectionService.getToolGuidance("Claude Code").promptPrefix());
        }

        @Test
        void copilot_hasOpsxDashPrefix() {
            assertEquals("/opsx-", AiToolDetectionService.getToolGuidance("GitHub Copilot").promptPrefix());
        }

        @ParameterizedTest
        @ValueSource(strings = {"Cursor", "Windsurf", "Cline"})
        void otherIdePanelTools_haveNullPrefix(String toolName) {
            assertNull(AiToolDetectionService.getToolGuidance(toolName).promptPrefix(),
                    toolName + " should have null promptPrefix");
        }

        @Test
        void gemini_hasNullPrefix() {
            assertNull(AiToolDetectionService.getToolGuidance("Gemini").promptPrefix());
        }
    }

    @Nested
    class DefaultGuidanceFallback {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"Unknown Tool", "VS Code", "Neovim"})
        void unknownToolReturnsDefaultGuidance(String toolName) {
            ToolGuidance guidance = AiToolDetectionService.getToolGuidance(toolName);
            assertNotNull(guidance);
            assertEquals("your AI tool", guidance.chatPanelName());
            assertNull(guidance.promptPrefix());
            assertFalse(guidance.canAutoSave());
        }
    }

    @Nested
    class SpecificToolMetadata {

        @Test
        void copilot_chatPanelName() {
            assertEquals("Copilot Chat", AiToolDetectionService.getToolGuidance("GitHub Copilot").chatPanelName());
        }

        @Test
        void copilot_pasteAction() {
            assertEquals("Open Copilot Chat and paste the prompt",
                    AiToolDetectionService.getToolGuidance("GitHub Copilot").pasteAction());
        }

        @Test
        void claudeCode_chatPanelName() {
            assertEquals("terminal", AiToolDetectionService.getToolGuidance("Claude Code").chatPanelName());
        }

        @Test
        void cursor_chatPanelName() {
            assertEquals("Composer", AiToolDetectionService.getToolGuidance("Cursor").chatPanelName());
        }

        @Test
        void windsurf_chatPanelName() {
            assertEquals("Cascade", AiToolDetectionService.getToolGuidance("Windsurf").chatPanelName());
        }

        @Test
        void cline_chatPanelName() {
            assertEquals("Cline chat", AiToolDetectionService.getToolGuidance("Cline").chatPanelName());
        }
    }
}
