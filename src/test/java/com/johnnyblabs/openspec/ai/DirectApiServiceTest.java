package com.johnnyblabs.openspec.ai;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectApiServiceTest {

    @Mock Project project;
    @Mock OpenSpecSettings settings;

    private DirectApiService service;

    @BeforeEach
    void setUp() {
        service = new DirectApiService(project);
    }

    @Nested
    class IsConfigured {

        @Test
        void returnsFalse_whenNoApiKey() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class);
                 MockedStatic<AiCredentialStore> credsMock = mockStatic(AiCredentialStore.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("Claude");
                credsMock.when(() -> AiCredentialStore.hasApiKey(AiProvider.CLAUDE)).thenReturn(false);

                assertFalse(service.isConfigured());
            }
        }

        @Test
        void returnsFalse_whenProviderIsNone() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("NONE");

                assertFalse(service.isConfigured());
            }
        }

        @Test
        void returnsTrue_whenClaudeApiKeyExists() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class);
                 MockedStatic<AiCredentialStore> credsMock = mockStatic(AiCredentialStore.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("Claude");
                credsMock.when(() -> AiCredentialStore.hasApiKey(AiProvider.CLAUDE)).thenReturn(true);

                assertTrue(service.isConfigured());
            }
        }
    }

    @Nested
    class Generate {

        @Test
        void throwsAiApiException_whenNoProviderConfigured() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("NONE");

                AiApiException ex = assertThrows(AiApiException.class,
                        () -> service.generate(null));
                assertTrue(ex.getMessage().contains("No AI provider configured"));
            }
        }

        @Test
        void throwsAiApiException_whenNoApiKeyStored() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class);
                 MockedStatic<AiCredentialStore> credsMock = mockStatic(AiCredentialStore.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("Claude");
                credsMock.when(() -> AiCredentialStore.getApiKey(AiProvider.CLAUDE)).thenReturn(null);

                AiApiException ex = assertThrows(AiApiException.class,
                        () -> service.generate(null));
                assertTrue(ex.getMessage().contains("No API key configured for Claude"));
            }
        }

        @Test
        void throwsAiApiException_whenApiKeyIsBlank() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class);
                 MockedStatic<AiCredentialStore> credsMock = mockStatic(AiCredentialStore.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("OpenAI");
                credsMock.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENAI)).thenReturn("   ");

                AiApiException ex = assertThrows(AiApiException.class,
                        () -> service.generate(null));
                assertTrue(ex.getMessage().contains("No API key configured for OpenAI"));
            }
        }
    }

    @Nested
    class OpenAiTokenParam {

        @Test
        void gpt4o_usesMaxTokens() {
            assertEquals("max_tokens", DirectApiService.openAiTokenParam("gpt-4o"));
        }

        @Test
        void gpt4oMini_usesMaxTokens() {
            assertEquals("max_tokens", DirectApiService.openAiTokenParam("gpt-4o-mini"));
        }

        @Test
        void o1Mini_usesMaxCompletionTokens() {
            assertEquals("max_completion_tokens", DirectApiService.openAiTokenParam("o1-mini"));
        }

        @Test
        void o1Preview_usesMaxCompletionTokens() {
            assertEquals("max_completion_tokens", DirectApiService.openAiTokenParam("o1-preview"));
        }

        @Test
        void o1_usesMaxCompletionTokens() {
            assertEquals("max_completion_tokens", DirectApiService.openAiTokenParam("o1"));
        }

        @Test
        void gpt35_usesMaxTokens() {
            assertEquals("max_tokens", DirectApiService.openAiTokenParam("gpt-3.5-turbo"));
        }
    }

    @Nested
    class AiProviderModels {

        @Test
        void claudeModelsAreCurrent() {
            var models = AiProvider.CLAUDE.getModels();
            assertFalse(models.isEmpty());
            assertTrue(models.stream().allMatch(m -> m.startsWith("claude-")),
                    "All Claude models should start with claude-");
        }

        @Test
        void openAiModelsAreCurrent() {
            var models = AiProvider.OPENAI.getModels();
            assertFalse(models.isEmpty());
            assertTrue(models.contains("gpt-4o"), "Should include gpt-4o");
        }

        @Test
        void geminiModelsAreCurrent() {
            var models = AiProvider.GEMINI.getModels();
            assertFalse(models.isEmpty());
            assertTrue(models.stream().allMatch(m -> m.startsWith("gemini-")),
                    "All Gemini models should start with gemini-");
        }

        @Test
        void noneHasNoModels() {
            assertTrue(AiProvider.NONE.getModels().isEmpty());
        }
    }
}
