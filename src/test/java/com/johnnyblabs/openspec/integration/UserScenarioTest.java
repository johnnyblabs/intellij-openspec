package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolGuidance;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * User-scenario integration tests that simulate real configurations:
 * Corporate Copilot (skills disabled), CLI power user, API-only, zero config, multi-tool.
 * Each test verifies the full resolution chain: tool detection → delivery method → guidance.
 */
@ExtendWith(MockitoExtension.class)
class UserScenarioTest {

    @Mock Project project;
    @Mock OpenSpecSettings settings;
    @Mock AiToolDetectionService detection;

    private DeliveryMethodResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DeliveryMethodResolver(project);
    }

    @Nested
    @DisplayName("Corporate Copilot (skills disabled, no API)")
    class CorporateCopilotNoApi {

        @Test
        void delivery_resolvesToClipboard() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("NONE");
                when(project.getService(AiToolDetectionService.class)).thenReturn(detection);
                when(detection.hasDetectedTools()).thenReturn(true);
                when(detection.getPrimaryToolLabel()).thenReturn("GitHub Copilot");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.CLIPBOARD, result.mode());
                assertEquals("Copy for GitHub Copilot", result.label());
            }
        }

        @Test
        void guidance_providesCopilotPasteInstructions() {
            ToolGuidance guidance = AiToolDetectionService.getToolGuidance("GitHub Copilot");

            assertEquals("Copilot Chat", guidance.chatPanelName());
            assertEquals("Open Copilot Chat and paste the prompt", guidance.pasteAction());
            assertEquals("/opsx-", guidance.promptPrefix());
            assertFalse(guidance.canAutoSave());
        }

        @Test
        void generateAll_hiddenWithoutApi() {
            // Generate All requires Direct API — no API means hidden
            // The visibility check is: hasApi && remaining >= 2
            boolean hasApi = AiProvider.fromString("NONE") != AiProvider.NONE;
            assertFalse(hasApi);
        }
    }

    @Nested
    @DisplayName("Corporate Copilot + Direct API")
    class CorporateCopilotWithApi {

        @Test
        void delivery_resolvesToDirectApi() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("Claude");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.DIRECT_API, result.mode());
            }
        }

        @Test
        void generateAll_visibleWithApiConfigured() {
            // Simulate: API configured + 2+ remaining artifacts
            boolean hasApi = AiProvider.fromString("Claude") != AiProvider.NONE;
            int remaining = 3;
            boolean visible = hasApi && remaining >= 2;

            assertTrue(visible);
        }
    }

    @Nested
    @DisplayName("CLI power user (Claude Code, no API)")
    class CliPowerUser {

        @Test
        void delivery_resolvesToClipboardWithClaudeCodeLabel() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("NONE");
                when(project.getService(AiToolDetectionService.class)).thenReturn(detection);
                when(detection.hasDetectedTools()).thenReturn(true);
                when(detection.getPrimaryToolLabel()).thenReturn("Claude Code");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.CLIPBOARD, result.mode());
                assertEquals("Copy for Claude Code", result.label());
            }
        }

        @Test
        void guidance_enablesAutoSaveAndTerminal() {
            ToolGuidance guidance = AiToolDetectionService.getToolGuidance("Claude Code");

            assertTrue(guidance.canAutoSave());
            assertEquals("terminal", guidance.chatPanelName());
        }
    }

    @Nested
    @DisplayName("API-only user (no tools, OpenAI key)")
    class ApiOnlyUser {

        @Test
        void delivery_resolvesToDirectApi() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("OpenAI");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.DIRECT_API, result.mode());
                assertEquals("Generate via OpenAI", result.label());
            }
        }

        @Test
        void generateAll_visibleForApiOnlyUser() {
            boolean hasApi = AiProvider.fromString("OpenAI") != AiProvider.NONE;
            int remaining = 2;
            boolean visible = hasApi && remaining >= 2;

            assertTrue(visible);
        }
    }

    @Nested
    @DisplayName("Zero configuration (no tools, no API)")
    class ZeroConfig {

        @Test
        void delivery_resolvesToBareClipboardFallback() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("NONE");
                when(project.getService(AiToolDetectionService.class)).thenReturn(detection);
                when(detection.hasDetectedTools()).thenReturn(false);

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.CLIPBOARD, result.mode());
                assertEquals("Copy to Clipboard", result.label());
            }
        }

        @Test
        void generateAll_hiddenWithZeroConfig() {
            boolean hasApi = AiProvider.fromString("NONE") != AiProvider.NONE;
            assertFalse(hasApi);

            boolean visible = hasApi && 5 >= 2; // even with many remaining, no API means hidden
            assertFalse(visible);
        }
    }

    @Nested
    @DisplayName("Multi-tool (Claude Code + Copilot, Claude API key)")
    class MultiTool {

        @Test
        void delivery_prefersDirectApiWhenKeyExists() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("Claude");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.DIRECT_API, result.mode());
            }
        }

        @Test
        void savedClipboardPreference_overridesApiAvailability() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("CLIPBOARD");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.CLIPBOARD, result.mode());
            }
        }
    }
}
