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
}
