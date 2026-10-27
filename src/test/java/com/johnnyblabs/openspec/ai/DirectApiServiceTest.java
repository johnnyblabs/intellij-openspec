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
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", ProviderRequestSettings.NONE);
            assertEquals("2023-06-01",
                    request.headers().firstValue("anthropic-version").orElse(null),
                    "anthropic-version must be the current required value 2023-06-01");
        }

        @Test
        void sendsBearerAuth_noLegacyKeyHeader_keyNotInUrl() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "test-secret-key", "p", ProviderRequestSettings.NONE);
            assertEquals("Bearer test-secret-key", request.headers().firstValue("Authorization").orElse(null));
            assertTrue(request.headers().firstValue("x-api-key").isEmpty(), "the legacy x-api-key must not be sent");
            assertFalse(request.uri().toString().contains("test-secret-key"),
                    "URL must not contain the API key: " + request.uri());
        }

        @Test
        void sendsWorkspaceHeaderWhenSet() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", ProviderRequestSettings.claudeWorkspace("wrkspc_example"));
            assertEquals("wrkspc_example", request.headers().firstValue("anthropic-workspace-id").orElse(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void omitsWorkspaceHeaderWhenBlank(String blank) {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", ProviderRequestSettings.claudeWorkspace(blank));
            assertTrue(request.headers().firstValue("anthropic-workspace-id").isEmpty());
        }

        @Test
        void omitsWorkspaceHeaderWhenNull() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", ProviderRequestSettings.NONE);
            assertTrue(request.headers().firstValue("anthropic-workspace-id").isEmpty());
        }

        @Test
        void usesPostToMessagesEndpoint() {
            var request = DirectApiService.buildClaudeRequest("claude-sonnet-4-5", "k", "p", ProviderRequestSettings.NONE);
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

        @Test
        void asksGoogleNotToStoreTheRequestAtTheTopLevel() {
            JsonObject body = bodyJson(DirectApiService.buildGeminiRequest("gemini-3.8-flash", "k", "p"));
            var store = body.get("store");
            assertNotNull(store, "the body carries store");
            assertTrue(store.isJsonPrimitive() && store.getAsJsonPrimitive().isBoolean(), "store is a JSON boolean: " + store);
            assertFalse(store.getAsBoolean(), "store is false");
            assertFalse(body.getAsJsonObject("generationConfig").has("store"),
                    "store inside generationConfig is rejected by Google with 400");
            assertEquals(java.util.Set.of("contents", "generationConfig", "store"), body.keySet());
        }
    }

    @Test
    void claudeAndOpenAiBodiesCarryNoStoreField() {
        assertFalse(bodyJson(DirectApiService.buildClaudeRequest("claude-sonnet-5-5", "k", "p",
                ProviderRequestSettings.NONE)).has("store"));
        assertFalse(bodyJson(DirectApiService.buildOpenAiRequest("gpt-6-luna", "k", "p",
                ProviderRequestSettings.NONE)).has("store"));
    }

    /** The JSON body an {@link java.net.http.HttpRequest} would send, read from its body publisher. */
    static JsonObject bodyJson(java.net.http.HttpRequest request) {
        var publisher = request.bodyPublisher().orElseThrow();
        var out = new java.io.ByteArrayOutputStream();
        var done = new java.util.concurrent.CompletableFuture<Void>();
        publisher.subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(java.nio.ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.join();
        return JsonParser.parseString(out.toString(StandardCharsets.UTF_8)).getAsJsonObject();
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
            var request = DirectApiService.buildListModelsRequest(AiProvider.OPENAI, "test-secret-key", ProviderRequestSettings.NONE);
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
            var request = DirectApiService.buildListModelsRequest(AiProvider.CLAUDE, "test-secret-key", ProviderRequestSettings.NONE);
            assertEquals("GET", request.method());
            // limit=1000 asks for every model in one page (the default page is 20).
            assertEquals(java.net.URI.create("https://api.anthropic.com/v1/models?limit=1000"), request.uri());
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
            var request = DirectApiService.buildListModelsRequest(AiProvider.CLAUDE, "k", ProviderRequestSettings.claudeWorkspace("wrkspc_example"));
            assertEquals("wrkspc_example", request.headers().firstValue("anthropic-workspace-id").orElse(null));
        }

        @Test
        void workspaceHeaderIsClaudeOnly() {
            for (AiProvider p : List.of(AiProvider.OPENAI, AiProvider.GEMINI)) {
                var request = DirectApiService.buildListModelsRequest(p, "k", ProviderRequestSettings.claudeWorkspace("wrkspc_example"));
                assertTrue(request.headers().firstValue("anthropic-workspace-id").isEmpty(),
                        p + " must never carry the Claude workspace header");
            }
        }

        @Test
        void geminiListRequestSendsKeyHeaderNotInUrl() {
            var request = DirectApiService.buildListModelsRequest(AiProvider.GEMINI, "test-secret-key", ProviderRequestSettings.NONE);
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
                    () -> DirectApiService.buildListModelsRequest(AiProvider.NONE, "k", ProviderRequestSettings.NONE));
        }
    }

    @Nested
    class ListModels {
        // The guard clauses throw before any network call, so they are cheaply testable off-network.

        @Test
        void noneProviderThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> service.listModels(AiProvider.NONE, "k", ProviderRequestSettings.NONE));
            assertTrue(ex.getMessage().contains("No AI provider"));
        }

        @Test
        void nullKeyThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> service.listModels(AiProvider.CLAUDE, null, ProviderRequestSettings.NONE));
            assertTrue(ex.getMessage().contains("No API key"));
        }

        @Test
        void blankKeyThrows() {
            assertThrows(AiApiException.class, () -> service.listModels(AiProvider.CLAUDE, "   ", ProviderRequestSettings.NONE));
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

            List<String> models = spy.listModels(AiProvider.OPENAI, "k", ProviderRequestSettings.NONE);
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

        // The live captures' body request_id (sanitized) ends every Claude error message.
        private static final String ID = " (request ID: req_SANITIZED)";

        @Test
        void liveUnscopedKey400_mapsToActionableMessage() {
            // LIVE capture: GET /v1/models with a key not scoped to a workspace and no workspace header.
            AiApiException ex = DirectApiService.buildApiError("Claude", 400,
                    loadFixture("claude/models-400-unscoped-key.json"));
            assertEquals(DirectApiService.UNSCOPED_KEY_MESSAGE + ID, ex.getMessage());
            assertTrue(ex.getMessage().contains("Workspace ID"));
            assertEquals(400, ex.getStatusCode());
        }

        @Test
        void liveInvalidWorkspaceId400_isNotReportedAsAnUnscopedKey() {
            // LIVE capture: GET /v1/models with a well-formed but unknown wrkspc_ ID. Its message also names the
            // header, so a header-name-only match would wrongly tell the user their key has no workspace.
            AiApiException ex = DirectApiService.buildApiError("Claude", 400,
                    loadFixture("claude/models-400-invalid-workspace-id.json"));
            assertEquals(DirectApiService.INVALID_WORKSPACE_MESSAGE + ID, ex.getMessage());
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
            assertEquals(DirectApiService.WORKSPACE_NOT_FOUND_MESSAGE + ID, ex.getMessage());
        }

        @Test
        void liveUnrelated400_keepsProviderMessage() {
            // LIVE capture: POST /v1/messages without max_tokens — the same error type, no workspace involved.
            AiApiException ex = DirectApiService.buildApiError("Claude", 400,
                    loadFixture("claude/messages-400-missing-max-tokens.json"));
            assertEquals("Claude API error: max_tokens: Field required" + ID, ex.getMessage());
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

                spy.listModels(AiProvider.CLAUDE, "k", ProviderRequestSettings.claudeWorkspace("wrkspc_form"));
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

    /**
     * OpenAI organization and project headers, on both OpenAI request kinds. Header names are written as literals,
     * never the production constants, so a misspelt constant fails here.
     */
    @Nested
    class OpenAiScopeHeaders {

        static java.util.stream.Stream<org.junit.jupiter.api.Named<java.util.function.Function<ProviderRequestSettings, java.net.http.HttpRequest>>> openAiRequests() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.api.Named.of("generation", s -> DirectApiService.buildOpenAiRequest("gpt-6-luna", "k", "p", s)),
                    org.junit.jupiter.api.Named.of("list-models", s -> DirectApiService.buildListModelsRequest(AiProvider.OPENAI, "k", s)));
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("openAiRequests")
        void bothHeadersWhenBothAreSet(java.util.function.Function<ProviderRequestSettings, java.net.http.HttpRequest> build) {
            var request = build.apply(ProviderRequestSettings.openAi("org-abc", "proj_xyz"));
            assertEquals("org-abc", request.headers().firstValue("OpenAI-Organization").orElse(null));
            assertEquals("proj_xyz", request.headers().firstValue("OpenAI-Project").orElse(null));
            assertEquals("Bearer k", request.headers().firstValue("Authorization").orElse(null));
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("openAiRequests")
        void noOptionalHeaderWhenBothAreBlank(java.util.function.Function<ProviderRequestSettings, java.net.http.HttpRequest> build) {
            var blank = build.apply(ProviderRequestSettings.openAi("  ", null));
            var none = build.apply(ProviderRequestSettings.NONE);
            assertTrue(blank.headers().firstValue("OpenAI-Organization").isEmpty());
            assertTrue(blank.headers().firstValue("OpenAI-Project").isEmpty());
            // The same header set as a request with no settings at all, so nothing else can drift in.
            assertEquals(none.headers().map(), blank.headers().map());
        }

        @Test
        void withNoSettingsTheRequestsCarryOnlyTodaysHeaders() {
            var generation = DirectApiService.buildOpenAiRequest("gpt-6-luna", "k", "p", ProviderRequestSettings.NONE);
            var listModels = DirectApiService.buildListModelsRequest(AiProvider.OPENAI, "k", ProviderRequestSettings.NONE);
            assertEquals(java.util.Set.of("authorization", "content-type"), headerNames(generation));
            assertEquals(java.util.Set.of("authorization"), headerNames(listModels));
        }

        private static java.util.Set<String> headerNames(java.net.http.HttpRequest request) {
            return request.headers().map().keySet().stream().map(n -> n.toLowerCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("openAiRequests")
        void onlyTheProjectWhenOnlyTheProjectIsSet(java.util.function.Function<ProviderRequestSettings, java.net.http.HttpRequest> build) {
            var request = build.apply(ProviderRequestSettings.openAi("", " proj_xyz "));
            assertTrue(request.headers().firstValue("OpenAI-Organization").isEmpty());
            assertEquals("proj_xyz", request.headers().firstValue("OpenAI-Project").orElse(null), "sent trimmed");
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("openAiRequests")
        void onlyTheOrganizationWhenOnlyTheOrganizationIsSet(java.util.function.Function<ProviderRequestSettings, java.net.http.HttpRequest> build) {
            var request = build.apply(ProviderRequestSettings.openAi(" org-abc", ""));
            assertEquals("org-abc", request.headers().firstValue("OpenAI-Organization").orElse(null));
            assertTrue(request.headers().firstValue("OpenAI-Project").isEmpty());
        }

        @Test
        void anUnexpectedPrefixIsStillSent() {
            var request = DirectApiService.buildOpenAiRequest("gpt-6-luna", "k", "p",
                    ProviderRequestSettings.openAi("acme", "project-1"));
            assertEquals("acme", request.headers().firstValue("OpenAI-Organization").orElse(null));
            assertEquals("project-1", request.headers().firstValue("OpenAI-Project").orElse(null));
        }

        @Test
        void otherProvidersNeverCarryTheOpenAiHeaders() {
            var all = new ProviderRequestSettings("wrkspc_example", "org-abc", "proj_xyz");
            var requests = List.of(
                    DirectApiService.buildClaudeRequest("claude-sonnet-5-5", "k", "p", all),
                    DirectApiService.buildListModelsRequest(AiProvider.CLAUDE, "k", all),
                    DirectApiService.buildListModelsRequest(AiProvider.GEMINI, "k", all));
            for (var request : requests) {
                assertTrue(request.headers().firstValue("OpenAI-Organization").isEmpty(), request.uri().toString());
                assertTrue(request.headers().firstValue("OpenAI-Project").isEmpty(), request.uri().toString());
            }
            assertEquals("wrkspc_example", requests.get(0).headers().firstValue("anthropic-workspace-id").orElse(null),
                    "Claude still reads its own setting");
        }

        @Test
        void openAiNeverCarriesTheClaudeWorkspace() {
            var request = DirectApiService.buildOpenAiRequest("gpt-6-luna", "k", "p",
                    new ProviderRequestSettings("wrkspc_example", "", ""));
            assertTrue(request.headers().firstValue("anthropic-workspace-id").isEmpty());
        }
    }

    /** The 401 hint, against live-captured invalid-key bodies from both OpenAI endpoints. */
    @Nested
    class OpenAiAuthErrors {
        private String loadFixture(String path) {
            try (InputStream in = getClass().getResourceAsStream("/fixtures/ai/" + path)) {
                assertNotNull(in, "fixture not found: " + path);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private String providerMessage(String body) {
            return JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error").get("message").getAsString();
        }

        @ParameterizedTest
        @ValueSource(strings = {"openai/models-401-invalid-key.json", "openai/chat-completions-401-invalid-key.json"})
        void a401WithAnIdSetKeepsOpenAisMessageAndPointsAtTheIds(String fixture) {
            String body = loadFixture(fixture);
            for (var scope : List.of(ProviderRequestSettings.openAi("org-abc", ""), ProviderRequestSettings.openAi("", "proj_xyz"))) {
                AiApiException ex = DirectApiService.buildApiError("OpenAI", 401, body, scope);
                assertEquals("OpenAI API error: " + providerMessage(body), ex.getMessage());
                // Exact: Settings \u2192 Test recognizes the hint by this string.
                assertEquals(DirectApiService.OPENAI_SCOPE_SUGGESTION, ex.getSuggestion());
                assertTrue(ex.getSuggestion().contains("Organization ID") && ex.getSuggestion().contains("Project ID"),
                        ex.getSuggestion());
                assertTrue(ex.getSuggestion().contains("API key"), "still asks to check the key");
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"openai/models-401-invalid-key.json", "openai/chat-completions-401-invalid-key.json"})
        void a401WithNoIdSetIsUnchanged(String fixture) {
            String body = loadFixture(fixture);
            AiApiException withNone = DirectApiService.buildApiError("OpenAI", 401, body, ProviderRequestSettings.NONE);
            AiApiException legacy = DirectApiService.buildApiError("OpenAI", 401, body);
            assertEquals("Check your API key in Settings \u2192 Tools \u2192 OpenSpec", withNone.getSuggestion());
            assertEquals(legacy.getSuggestion(), withNone.getSuggestion());
            assertEquals(legacy.getMessage(), withNone.getMessage());
        }

        @Test
        void onlyA401GetsTheHint() {
            String body = loadFixture("openai/models-401-invalid-key.json");
            AiApiException forbidden = DirectApiService.buildApiError("OpenAI", 403, body,
                    ProviderRequestSettings.openAi("org-abc", "proj_xyz"));
            assertEquals("Check your API key in Settings \u2192 Tools \u2192 OpenSpec", forbidden.getSuggestion());
        }

        @Test
        void otherProvidersNeverGetTheHint() {
            String body = loadFixture("claude/models-400-unscoped-key.json");
            AiApiException claude = DirectApiService.buildApiError("Claude", 401, body,
                    new ProviderRequestSettings("", "org-abc", "proj_xyz"));
            assertEquals("Check your API key in Settings \u2192 Tools \u2192 OpenSpec", claude.getSuggestion());
        }
    }

    /**
     * Every request path hands the response headers and body to the error builder: the request ID and the
     * body-first kind reach the exception from generation and from the model list, for each provider.
     */
    @Nested
    class ErrorResponsePlumbing {

        @SuppressWarnings("unchecked")
        private void stubError(DirectApiService spy, String fixture) throws Exception {
            HttpClient client = mock(HttpClient.class);
            doReturn(client).when(spy).createHttpClient();
            HttpResponse<String> resp = mock(HttpResponse.class);
            when(resp.statusCode()).thenReturn(DirectApiErrorsContractTest.status(fixture));
            when(resp.body()).thenReturn(DirectApiErrorsContractTest.body(fixture));
            when(resp.headers()).thenReturn(DirectApiErrorsContractTest.headers(fixture));
            doReturn(resp).when(client).send(any(), any());
        }

        private AiApiException generateRawFails(String providerSetting, AiProvider provider, String fixture)
                throws Throwable {
            DirectApiService spy = spy(new DirectApiService(project));
            stubError(spy, fixture);
            AiApiException[] thrown = new AiApiException[1];
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class);
                 MockedStatic<AiCredentialStore> credsMock = mockStatic(AiCredentialStore.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                lenient().when(settings.getAiProvider()).thenReturn(providerSetting);
                lenient().when(settings.getAiWorkspaceId()).thenReturn("");
                lenient().when(settings.getAiOpenAiOrganization()).thenReturn("");
                lenient().when(settings.getAiOpenAiProject()).thenReturn("");
                credsMock.when(() -> AiCredentialStore.getApiKey(provider)).thenReturn("k");
                thrown[0] = assertThrows(AiApiException.class, () -> spy.generateRaw("p"));
            }
            return thrown[0];
        }

        @Test
        void claudeGenerationCarriesTheHeaderRequestId() throws Throwable {
            String fixture = "claude/errors/messages-401-header-and-body-ids";
            AiApiException ex = generateRawFails("CLAUDE", AiProvider.CLAUDE, fixture);
            assertEquals(DirectApiErrorsContractTest.headers(fixture).firstValue("request-id").orElseThrow(),
                    ex.getRequestId());
        }

        @Test
        void openAiGenerationCarriesTheRequestIdAndTheBodyKind() throws Throwable {
            String fixture = "openai/errors/429-credit-balance-exhausted";
            AiApiException ex = generateRawFails("OPENAI", AiProvider.OPENAI, fixture);
            assertEquals(DirectApiErrorsContractTest.headers(fixture).firstValue("x-request-id").orElseThrow(),
                    ex.getRequestId());
            assertEquals(ErrorKind.BILLING, ex.getKind());
        }

        @Test
        void geminiGenerationClassifiesTheBadKey400() throws Throwable {
            AiApiException ex = generateRawFails("GEMINI", AiProvider.GEMINI,
                    "gemini/errors/generatecontent-400-api-key-invalid");
            assertEquals(ErrorKind.AUTH, ex.getKind());
            assertNull(ex.getRequestId());
        }

        @Test
        void theModelListCarriesTheRequestId() throws Exception {
            for (String fixture : List.of("claude/errors/models-401-invalid-key", "openai/errors/models-401-invalid-key")) {
                DirectApiService spy = spy(new DirectApiService(project));
                stubError(spy, fixture);
                AiProvider provider = fixture.startsWith("claude") ? AiProvider.CLAUDE : AiProvider.OPENAI;
                AiApiException ex = assertThrows(AiApiException.class,
                        () -> spy.listModels(provider, "k", ProviderRequestSettings.NONE));
                assertNotNull(ex.getRequestId(), fixture);
                assertTrue(ex.getMessage().endsWith(" (request ID: " + ex.getRequestId() + ")"), ex.getMessage());
            }
        }
    }

    /** Generation sends the saved IDs; Test (list-models) sends the ones passed in. */
    @Nested
    class OpenAiPlumbing {
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
            return stubClient(spy, 200, body);
        }

        @SuppressWarnings("unchecked")
        private HttpClient stubClient(DirectApiService spy, int status, String body) throws Exception {
            HttpClient client = mock(HttpClient.class);
            doReturn(client).when(spy).createHttpClient();
            HttpResponse<String> resp = mock(HttpResponse.class);
            when(resp.statusCode()).thenReturn(status);
            when(resp.body()).thenReturn(body);
            doReturn(resp).when(client).send(any(), any());
            return client;
        }

        private java.net.http.HttpRequest sent(HttpClient client) throws Exception {
            var captor = org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
            verify(client).send(captor.capture(), any());
            return captor.getValue();
        }

        /** Runs {@code call} against saved OpenAI settings with org-saved / proj_saved and the key "k". */
        private void withSavedOpenAiIds(org.junit.jupiter.api.function.Executable call) throws Throwable {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class);
                 MockedStatic<AiCredentialStore> credsMock = mockStatic(AiCredentialStore.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                lenient().when(settings.getAiProvider()).thenReturn("OPENAI");
                lenient().when(settings.getAiModel()).thenReturn("gpt-6-luna");
                lenient().when(settings.getAiOpenAiOrganization()).thenReturn("org-saved");
                lenient().when(settings.getAiOpenAiProject()).thenReturn("proj_saved");
                credsMock.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENAI)).thenReturn("k");
                call.execute();
            }
        }

        private static final com.johnnyblabs.openspec.model.ArtifactInstruction DESIGN =
                new com.johnnyblabs.openspec.model.ArtifactInstruction("add-auth", "design", null, "design.md",
                        "Write the design.", null, List.of(), List.of());

        @Test
        void generate_sendsTheSavedIds() throws Throwable {
            DirectApiService spy = spy(new DirectApiService(project));
            HttpClient client = stubClient(spy, loadFixture("openai/chat-completion-response.json"));
            withSavedOpenAiIds(() -> spy.generate(DESIGN));
            var request = sent(client);
            assertEquals("org-saved", request.headers().firstValue("OpenAI-Organization").orElse(null));
            assertEquals("proj_saved", request.headers().firstValue("OpenAI-Project").orElse(null));
        }

        @Test
        void testConnection_sendsTheSavedIds() throws Throwable {
            DirectApiService spy = spy(new DirectApiService(project));
            HttpClient client = stubClient(spy, loadFixture("openai/chat-completion-response.json"));
            withSavedOpenAiIds(() -> spy.testConnection());
            var request = sent(client);
            assertEquals("org-saved", request.headers().firstValue("OpenAI-Organization").orElse(null));
            assertEquals("proj_saved", request.headers().firstValue("OpenAI-Project").orElse(null));
        }

        @Test
        void a401DuringGenerationCarriesTheIdHint() throws Throwable {
            DirectApiService spy = spy(new DirectApiService(project));
            stubClient(spy, 401, loadFixture("openai/chat-completions-401-invalid-key.json"));
            AiApiException[] thrown = new AiApiException[1];
            withSavedOpenAiIds(() -> thrown[0] = assertThrows(AiApiException.class, () -> spy.generateRaw("p")));
            assertEquals(401, thrown[0].getStatusCode());
            assertEquals(DirectApiService.OPENAI_SCOPE_SUGGESTION, thrown[0].getSuggestion());
        }

        @Test
        void a401FromTheModelListCarriesTheIdHint() throws Exception {
            DirectApiService spy = spy(new DirectApiService(project));
            stubClient(spy, 401, loadFixture("openai/models-401-invalid-key.json"));
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> spy.listModels(AiProvider.OPENAI, "k", ProviderRequestSettings.openAi("org-form", "")));
            assertEquals(DirectApiService.OPENAI_SCOPE_SUGGESTION, ex.getSuggestion(),
                    "Settings \u2192 Test shows its ID hint only for this exact suggestion");
        }

        @Test
        void generateRaw_sendsTheSavedIds() throws Exception {
            DirectApiService spy = spy(new DirectApiService(project));
            HttpClient client = stubClient(spy, loadFixture("openai/chat-completion-response.json"));
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class);
                 MockedStatic<AiCredentialStore> credsMock = mockStatic(AiCredentialStore.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getAiProvider()).thenReturn("OPENAI");
                when(settings.getAiModel()).thenReturn("gpt-6-luna");
                when(settings.getAiOpenAiOrganization()).thenReturn("org-saved");
                when(settings.getAiOpenAiProject()).thenReturn("proj_saved");
                credsMock.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENAI)).thenReturn("k");

                spy.generateRaw("p");
            }
            var request = sent(client);
            assertEquals("org-saved", request.headers().firstValue("OpenAI-Organization").orElse(null));
            assertEquals("proj_saved", request.headers().firstValue("OpenAI-Project").orElse(null));
        }

        @Test
        void listModels_sendsThePassedIds_notTheSavedOnes() throws Exception {
            DirectApiService spy = spy(new DirectApiService(project));
            HttpClient client = stubClient(spy, loadFixture("openai/models-list.json"));
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                lenient().when(settings.getAiOpenAiOrganization()).thenReturn("org-saved");
                lenient().when(settings.getAiOpenAiProject()).thenReturn("proj_saved");

                spy.listModels(AiProvider.OPENAI, "k", ProviderRequestSettings.openAi("org-form", "proj_form"));
            }
            var request = sent(client);
            assertEquals("org-form", request.headers().firstValue("OpenAI-Organization").orElse(null),
                    "Settings \u2192 Test must send the unsaved form values");
            assertEquals("proj_form", request.headers().firstValue("OpenAI-Project").orElse(null));
        }
    }
}
