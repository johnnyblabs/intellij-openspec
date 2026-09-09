package com.johnnyblabs.openspec.ai;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
                credsMock.when(() -> AiCredentialStore.hasApiKeyCached(AiProvider.CLAUDE)).thenReturn(false);

                assertFalse(service.isConfigured());
                // isConfigured must use the EDT-safe cached check, never the blocking PasswordSafe read.
                credsMock.verify(() -> AiCredentialStore.hasApiKey(any()), never());
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
                credsMock.when(() -> AiCredentialStore.hasApiKeyCached(AiProvider.CLAUDE)).thenReturn(true);

                assertTrue(service.isConfigured());
                credsMock.verify(() -> AiCredentialStore.hasApiKey(any()), never());
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
    class GenerateRaw {

        @Test
        void throwsAiApiException_whenNoProviderConfigured() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("NONE");

                AiApiException ex = assertThrows(AiApiException.class,
                        () -> service.generateRaw("test prompt"));
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
                        () -> service.generateRaw("test prompt"));
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
                        () -> service.generateRaw("test prompt"));
                assertTrue(ex.getMessage().contains("No API key configured for OpenAI"));
            }
        }
    }

    @Nested
    class OpenAiTokenParam {

        // Reasoning family reject max_tokens and require max_completion_tokens.
        @ParameterizedTest
        @ValueSource(strings = {"o1", "o1-mini", "o1-preview", "o3", "o3-mini", "o4-mini", "gpt-5", "gpt-5-mini"})
        void reasoningModels_useMaxCompletionTokens(String model) {
            assertEquals("max_completion_tokens", DirectApiService.openAiTokenParam(model),
                    model + " is a reasoning model and must use max_completion_tokens");
        }

        // Negative anchors: chat models must NOT be swept into the reasoning path. These are
        // load-bearing — without them an over-broad predicate would still pass.
        @ParameterizedTest
        @ValueSource(strings = {"gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"})
        void chatModels_useMaxTokens(String model) {
            assertEquals("max_tokens", DirectApiService.openAiTokenParam(model),
                    model + " is a chat model and must use max_tokens");
        }

        @Test
        void nullModel_defaultsToMaxTokens() {
            assertEquals("max_tokens", DirectApiService.openAiTokenParam(null));
        }
    }

    @Nested
    class BuildClaudeRequest {

        @Test
        void setsCurrentAnthropicVersionHeader() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p");
            assertEquals("2023-06-01",
                    request.headers().firstValue("anthropic-version").orElse(null),
                    "anthropic-version must be the current required value 2023-06-01");
        }

        @Test
        void setsApiKeyHeaderNotInUrl() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "test-secret-key", "p");
            assertEquals("test-secret-key", request.headers().firstValue("x-api-key").orElse(null));
            assertFalse(request.uri().toString().contains("test-secret-key"),
                    "URL must not contain the API key: " + request.uri());
        }

        @Test
        void usesPostToMessagesEndpoint() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p");
            assertEquals("POST", request.method());
            assertTrue(request.uri().toString().endsWith("/v1/messages"),
                    "URI must be the Messages endpoint: " + request.uri());
            assertEquals("application/json",
                    request.headers().firstValue("Content-Type").orElse(null));
        }
    }

    @Nested
    class BuildGeminiRequest {

        @Test
        void setsXGoogApiKeyHeader() {
            var request = DirectApiService.buildGeminiRequest("gemini-2.5-pro", "test-secret-key", "hello");
            assertEquals("test-secret-key",
                    request.headers().firstValue("x-goog-api-key").orElse(null),
                    "x-goog-api-key header must carry the API key");
        }

        @Test
        void doesNotEmbedKeyInUrl() {
            var request = DirectApiService.buildGeminiRequest("gemini-2.5-pro", "test-secret-key", "hello");
            String url = request.uri().toString();
            assertFalse(url.contains("?key="), "URL must not have ?key= query param: " + url);
            assertFalse(url.contains("test-secret-key"), "URL must not contain the API key value: " + url);
        }

        @Test
        void preservesEndpointPath() {
            var request = DirectApiService.buildGeminiRequest("gemini-2.5-pro", "test-secret-key", "hello");
            assertTrue(request.uri().toString().endsWith("/gemini-2.5-pro:generateContent"),
                    "URI must end with /<model>:generateContent: " + request.uri());
        }

        @Test
        void setsContentTypeJson() {
            var request = DirectApiService.buildGeminiRequest("gemini-2.5-pro", "k", "p");
            assertEquals("application/json",
                    request.headers().firstValue("Content-Type").orElse(null));
        }

        @Test
        void usesPostMethod() {
            var request = DirectApiService.buildGeminiRequest("gemini-2.5-pro", "k", "p");
            assertEquals("POST", request.method());
        }
    }

    @Nested
    class AiProviderModels {

        @Test
        void claudeModelsAreDatelessAliases() {
            // Strengthened from a prefix-only check (which passed for the fabricated dated ID that
            // shipped the C1 404). Every Claude entry must be a dateless family alias with no
            // 8-digit snapshot suffix. The primary C1 guard lives in AiProviderTest.
            var models = AiProvider.CLAUDE.getModels();
            assertFalse(models.isEmpty());
            assertTrue(models.stream().allMatch(m -> m.matches("^claude-(sonnet|opus|haiku)-\\d+(-\\d+)?$")),
                    "Claude models must be dateless aliases claude-<family>-<major>[-<minor>]: " + models);
            assertTrue(models.stream().noneMatch(m -> m.matches(".*-\\d{8}$")),
                    "Claude models must not carry an 8-digit snapshot date: " + models);
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

    @Nested
    class BuildListModelsRequest {

        @Test
        void openAiListRequestHitsModelsEndpointWithBearerAuth() {
            var request = DirectApiService.buildListModelsRequest(AiProvider.OPENAI, "test-secret-key");
            assertEquals("GET", request.method());
            assertTrue(request.uri().toString().endsWith("/v1/models"),
                    "OpenAI list endpoint must be /v1/models: " + request.uri());
            assertEquals("Bearer test-secret-key",
                    request.headers().firstValue("Authorization").orElse(null));
            assertFalse(request.uri().toString().contains("test-secret-key"),
                    "URL must not contain the API key: " + request.uri());
        }

        @Test
        void anthropicListRequestSendsKeyAndVersionHeaders() {
            var request = DirectApiService.buildListModelsRequest(AiProvider.CLAUDE, "test-secret-key");
            assertEquals("GET", request.method());
            assertTrue(request.uri().toString().endsWith("/v1/models"),
                    "Anthropic list endpoint must be /v1/models: " + request.uri());
            assertEquals("test-secret-key", request.headers().firstValue("x-api-key").orElse(null));
            assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElse(null));
            assertFalse(request.uri().toString().contains("test-secret-key"),
                    "URL must not contain the API key: " + request.uri());
        }

        @Test
        void geminiListRequestSendsKeyHeaderNotInUrl() {
            var request = DirectApiService.buildListModelsRequest(AiProvider.GEMINI, "test-secret-key");
            assertEquals("GET", request.method());
            String url = request.uri().toString();
            assertTrue(url.startsWith("https://generativelanguage.googleapis.com/v1beta/models"),
                    "Gemini list endpoint must be the v1beta models collection: " + url);
            assertEquals("test-secret-key", request.headers().firstValue("x-goog-api-key").orElse(null));
            assertFalse(url.contains("test-secret-key"), "URL must not contain the API key: " + url);
        }

        @Test
        void noneProviderIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> DirectApiService.buildListModelsRequest(AiProvider.NONE, "k"));
        }
    }

    @Nested
    class ListModels {
        // The guard clauses throw before any network call, so they are cheaply testable off-network.

        @Test
        void noneProviderThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> service.listModels(AiProvider.NONE, "k"));
            assertTrue(ex.getMessage().contains("No AI provider"));
        }

        @Test
        void nullKeyThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> service.listModels(AiProvider.CLAUDE, null));
            assertTrue(ex.getMessage().contains("No API key"));
        }

        @Test
        void blankKeyThrows() {
            assertThrows(AiApiException.class, () -> service.listModels(AiProvider.CLAUDE, "   "));
        }
    }
}
