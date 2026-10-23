package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

        // Reasoning family reject max_tokens and require max_completion_tokens. The gpt-6 entries are
        // the current flagships (and the OpenAI fallback list); the old o1/o3/o4/gpt-5 prefix rule sent
        // them max_tokens. The last entry stands in for a family released after this code.
        @ParameterizedTest
        @ValueSource(strings = {"o1", "o1-mini", "o1-preview", "o3", "o3-mini", "o4-mini", "gpt-5", "gpt-5-mini",
                "gpt-5.6-terra", "gpt-6-luna", "gpt-6-astra", "gpt-6.1-sol", "gpt-7-future"})
        void reasoningModels_useMaxCompletionTokens(String model) {
            assertEquals("max_completion_tokens", DirectApiService.openAiTokenParam(model),
                    model + " is a reasoning model and must use max_completion_tokens");
        }

        // Negative anchors: chat models must NOT be swept into the reasoning path. These are
        // load-bearing — without them an over-broad predicate would still pass.
        @ParameterizedTest
        @ValueSource(strings = {"gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4.1", "gpt-3.5-turbo", "chatgpt-4o-latest",
                "ft:gpt-4o-mini-2024-07-18:acme::abc123"})
        void chatModels_useMaxTokens(String model) {
            assertEquals("max_tokens", DirectApiService.openAiTokenParam(model),
                    model + " is a chat model and must use max_tokens");
        }

        @Test
        void reasoningFineTune_followsItsBaseModel() {
            assertEquals("max_completion_tokens", DirectApiService.openAiTokenParam("ft:o4-mini-2025-04-16:acme::abc123"));
        }

        @Test
        void nullModel_defaultsToMaxCompletionTokens() {
            // Never reached in practice (getModel falls back to the default), but the non-deprecated parameter is the
            // safe answer for an unknown model.
            assertEquals("max_completion_tokens", DirectApiService.openAiTokenParam(null));
        }

        @Test
        void everyOpenAiFallbackModel_usesMaxCompletionTokens() {
            // The fallback list and the token rule must change together: a gpt-6 default sent max_tokens is a 400.
            for (String model : AiProvider.OPENAI.getModels()) {
                assertEquals("max_completion_tokens", DirectApiService.openAiTokenParam(model), model);
            }
        }
    }

    @Nested
    class BuildClaudeRequest {

        @Test
        void setsCurrentAnthropicVersionHeader() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", null);
            assertEquals("2023-06-01",
                    request.headers().firstValue("anthropic-version").orElse(null),
                    "anthropic-version must be the current required value 2023-06-01");
        }

        @Test
        void sendsBearerAuth_noLegacyKeyHeader_keyNotInUrl() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "test-secret-key", "p", null);
            assertEquals("Bearer test-secret-key", request.headers().firstValue("Authorization").orElse(null));
            assertTrue(request.headers().firstValue("x-api-key").isEmpty(), "the legacy x-api-key must not be sent");
            assertFalse(request.uri().toString().contains("test-secret-key"),
                    "URL must not contain the API key: " + request.uri());
        }

        @Test
        void sendsWorkspaceHeaderWhenSet() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", "wrkspc_example");
            assertEquals("wrkspc_example", request.headers().firstValue("anthropic-workspace-id").orElse(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void omitsWorkspaceHeaderWhenBlank(String blank) {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", blank);
            assertTrue(request.headers().firstValue("anthropic-workspace-id").isEmpty());
        }

        @Test
        void omitsWorkspaceHeaderWhenNull() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", null);
            assertTrue(request.headers().firstValue("anthropic-workspace-id").isEmpty());
        }

        @Test
        void usesPostToMessagesEndpoint() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", null);
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
            // gpt-4o and gpt-4o-mini shut down 2027-01-14 and o1-mini retired 2025-10-27 (OpenAI deprecations page).
            assertTrue(models.stream().noneMatch(m -> m.startsWith("gpt-4") || m.equals("o1-mini")),
                    "OpenAI fallback list must not carry deprecated or retired models: " + models);
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
            var request = DirectApiService.buildListModelsRequest(AiProvider.OPENAI, "test-secret-key", null);
            assertEquals("GET", request.method());
            assertTrue(request.uri().toString().endsWith("/v1/models"),
                    "OpenAI list endpoint must be /v1/models: " + request.uri());
            assertEquals("Bearer test-secret-key",
                    request.headers().firstValue("Authorization").orElse(null));
            assertFalse(request.uri().toString().contains("test-secret-key"),
                    "URL must not contain the API key: " + request.uri());
        }

        @Test
        void anthropicListRequestSendsBearerAndVersionHeaders() {
            var request = DirectApiService.buildListModelsRequest(AiProvider.CLAUDE, "test-secret-key", null);
            assertEquals("GET", request.method());
            assertTrue(request.uri().toString().endsWith("/v1/models"),
                    "Anthropic list endpoint must be /v1/models: " + request.uri());
            assertEquals("Bearer test-secret-key", request.headers().firstValue("Authorization").orElse(null));
            assertTrue(request.headers().firstValue("x-api-key").isEmpty(), "the legacy x-api-key must not be sent");
            assertTrue(request.headers().firstValue("anthropic-workspace-id").isEmpty(),
                    "no workspace header when none is configured");
            assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElse(null));
            assertFalse(request.uri().toString().contains("test-secret-key"),
                    "URL must not contain the API key: " + request.uri());
        }

        @Test
        void anthropicListRequestSendsWorkspaceHeaderWhenSet() {
            var request = DirectApiService.buildListModelsRequest(AiProvider.CLAUDE, "k", "wrkspc_example");
            assertEquals("wrkspc_example", request.headers().firstValue("anthropic-workspace-id").orElse(null));
        }

        @Test
        void workspaceHeaderIsClaudeOnly() {
            for (AiProvider p : List.of(AiProvider.OPENAI, AiProvider.GEMINI)) {
                var request = DirectApiService.buildListModelsRequest(p, "k", "wrkspc_example");
                assertTrue(request.headers().firstValue("anthropic-workspace-id").isEmpty(),
                        p + " must never carry the Claude workspace header");
            }
        }

        @Test
        void geminiListRequestSendsKeyHeaderNotInUrl() {
            var request = DirectApiService.buildListModelsRequest(AiProvider.GEMINI, "test-secret-key", null);
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
                    () -> DirectApiService.buildListModelsRequest(AiProvider.NONE, "k", null));
        }
    }

    @Nested
    class ListModels {
        // The guard clauses throw before any network call, so they are cheaply testable off-network.

        @Test
        void noneProviderThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> service.listModels(AiProvider.NONE, "k", null));
            assertTrue(ex.getMessage().contains("No AI provider"));
        }

        @Test
        void nullKeyThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> service.listModels(AiProvider.CLAUDE, null, null));
            assertTrue(ex.getMessage().contains("No API key"));
        }

        @Test
        void blankKeyThrows() {
            assertThrows(AiApiException.class, () -> service.listModels(AiProvider.CLAUDE, "   ", null));
        }
    }

    @Nested
    class SessionModelCache {
        private String loadFixture(String path) {
            try (InputStream in = getClass().getResourceAsStream("/fixtures/ai/" + path)) {
                assertNotNull(in, "fixture not found: " + path);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @SuppressWarnings("unchecked")
        void listModelsWarmsCache_andAccessorDoesNotRefetch() throws Exception {
            DirectApiService spy = spy(new DirectApiService(project));
            HttpClient mockClient = mock(HttpClient.class);
            doReturn(mockClient).when(spy).createHttpClient();

            HttpResponse<String> resp = mock(HttpResponse.class);
            when(resp.statusCode()).thenReturn(200);
            // Body from the committed fixture — never a hand-written shape (parser is a contract boundary).
            when(resp.body()).thenReturn(loadFixture("openai/models-list.json"));
            // doReturn (untyped) avoids the HttpResponse<Object> vs <String> generic-inference mismatch.
            doReturn(resp).when(mockClient).send(any(), any());

            // Cold: nothing cached before the first fetch.
            assertNull(spy.getDiscoveredModels(AiProvider.OPENAI));

            List<String> models = spy.listModels(AiProvider.OPENAI, "k", null);
            assertEquals(List.of("model-id-0", "model-id-1", "model-id-2"), models);

            // Warm: the accessor returns the fetched list (write-on-success)...
            assertEquals(models, spy.getDiscoveredModels(AiProvider.OPENAI));
            // ...and does NOT trigger a second network call (anti-tautology: reverting the write fails the
            // line above; making the accessor re-fetch makes this times(1) become 2).
            verify(mockClient, times(1)).send(any(), any());
        }
    }

    @Nested
    class ClaudeWorkspaceErrors {
        private String loadFixture(String path) {
            try (InputStream in = getClass().getResourceAsStream("/fixtures/ai/" + path)) {
                assertNotNull(in, "fixture not found: " + path);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void liveUnscopedKey400_mapsToActionableMessage() {
            // LIVE capture: GET /v1/models with a key not scoped to a workspace and no workspace header.
            AiApiException ex = DirectApiService.buildApiError("Claude", 400,
                    loadFixture("claude/models-400-unscoped-key.json"));
            assertEquals(DirectApiService.UNSCOPED_KEY_MESSAGE, ex.getMessage());
            assertTrue(ex.getMessage().contains("Workspace ID"));
            assertEquals(400, ex.getStatusCode());
        }

        @Test
        void liveInvalidWorkspaceId400_isNotReportedAsAnUnscopedKey() {
            // LIVE capture: GET /v1/models with a well-formed but unknown wrkspc_ ID. Its message also names the
            // header, so a header-name-only match would wrongly tell the user their key has no workspace.
            AiApiException ex = DirectApiService.buildApiError("Claude", 400,
                    loadFixture("claude/models-400-invalid-workspace-id.json"));
            assertEquals(DirectApiService.INVALID_WORKSPACE_MESSAGE, ex.getMessage());
        }

        @Test
        void workspaceNotFound404_mapsToActionableMessage() {
            // Derived from the live 400 envelope (only status, error.type and error.message change): a live 404 needs a
            // well-formed ID for a workspace the key can't see, which wasn't capturable.
            JsonObject json = JsonParser.parseString(loadFixture("claude/models-400-unscoped-key.json")).getAsJsonObject();
            JsonObject error = json.getAsJsonObject("error");
            error.addProperty("type", "not_found_error");
            error.addProperty("message", "Workspace not found");
            AiApiException ex = DirectApiService.buildApiError("Claude", 404, json.toString());
            assertEquals(DirectApiService.WORKSPACE_NOT_FOUND_MESSAGE, ex.getMessage());
        }

        @Test
        void liveUnrelated400_keepsProviderMessage() {
            // LIVE capture: POST /v1/messages without max_tokens — the same error type, no workspace involved.
            AiApiException ex = DirectApiService.buildApiError("Claude", 400,
                    loadFixture("claude/messages-400-missing-max-tokens.json"));
            assertEquals("Claude API error: max_tokens: Field required", ex.getMessage());
        }

        @Test
        void otherProviders_areNotWorkspaceMapped() {
            AiApiException ex = DirectApiService.buildApiError("OpenAI", 400,
                    loadFixture("claude/models-400-unscoped-key.json"));
            assertTrue(ex.getMessage().startsWith("OpenAI API error: This API key is not scoped"), ex.getMessage());
        }
    }

    @Nested
    class WorkspacePlumbing {
        private String loadFixture(String path) {
            try (InputStream in = getClass().getResourceAsStream("/fixtures/ai/" + path)) {
                assertNotNull(in, "fixture not found: " + path);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        private HttpClient stubClient(DirectApiService spy, String body) throws Exception {
            HttpClient client = mock(HttpClient.class);
            doReturn(client).when(spy).createHttpClient();
            HttpResponse<String> resp = mock(HttpResponse.class);
            when(resp.statusCode()).thenReturn(200);
            when(resp.body()).thenReturn(body);
            doReturn(resp).when(client).send(any(), any());
            return client;
        }

        private java.net.http.HttpRequest sent(HttpClient client) throws Exception {
            var captor = org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
            verify(client).send(captor.capture(), any());
            return captor.getValue();
        }

        @Test
        void listModels_sendsThePassedWorkspace_notTheSavedOne() throws Exception {
            DirectApiService spy = spy(new DirectApiService(project));
            HttpClient client = stubClient(spy, loadFixture("claude/models-list-live.json"));
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                lenient().when(settings.getAiWorkspaceId()).thenReturn("wrkspc_saved");

                spy.listModels(AiProvider.CLAUDE, "k", "wrkspc_form");
            }
            assertEquals("wrkspc_form", sent(client).headers().firstValue("anthropic-workspace-id").orElse(null),
                    "Settings \u2192 Test must send the unsaved form value");
        }

        @Test
        void generateRaw_sendsTheSavedWorkspace() throws Exception {
            DirectApiService spy = spy(new DirectApiService(project));
            HttpClient client = stubClient(spy, loadFixture("claude/messages-thinking-first.json"));
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class);
                 MockedStatic<AiCredentialStore> credsMock = mockStatic(AiCredentialStore.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("CLAUDE");
                when(settings.getAiModel()).thenReturn("claude-sonnet-5-5");
                when(settings.getAiWorkspaceId()).thenReturn("wrkspc_saved");
                credsMock.when(() -> AiCredentialStore.getApiKey(AiProvider.CLAUDE)).thenReturn("k");

                String text = spy.generateRaw("p");
                assertTrue(text.startsWith("# design.md"), "the thinking-first live response parses end to end");
            }
            var request = sent(client);
            assertEquals("wrkspc_saved", request.headers().firstValue("anthropic-workspace-id").orElse(null));
            assertEquals("Bearer k", request.headers().firstValue("Authorization").orElse(null));
        }
    }
}
