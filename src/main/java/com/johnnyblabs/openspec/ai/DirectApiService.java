package com.johnnyblabs.openspec.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service(Service.Level.PROJECT)
public final class DirectApiService {
    private static final Logger LOG = Logger.getInstance(DirectApiService.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    // Non-streaming output cap. 16000 keeps large specs/designs from truncating on modern models
    // while staying comfortably under the 5-minute request timeout (non-streaming).
    private static final int MAX_TOKENS = 16000;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    // List-models endpoints (the free GET used to validate the key and discover the account's models).
    // limit=1000 (the maximum) returns every model in one page; the default page is 20.
    private static final String CLAUDE_MODELS_URL = "https://api.anthropic.com/v1/models?limit=1000";
    private static final String OPENAI_MODELS_URL = "https://api.openai.com/v1/models";
    // pageSize=1000 returns every model in one page (the default page is ~50). The key rides the
    // x-goog-api-key header, never the URL, matching buildGeminiRequest.
    private static final String GEMINI_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000";

    private final Project project;

    public DirectApiService(Project project) {
        this.project = project;
        // Warm the has-key cache off the EDT so isConfigured() (reached from ~8 UI paths) never does a
        // synchronous PasswordSafe read on the EDT. Guard on a live Application so plain unit tests
        // (no IntelliJ Application) can construct the service.
        var app = ApplicationManager.getApplication();
        if (app != null) {
            app.executeOnPooledThread(() -> {
                AiProvider p = getProvider();
                if (p != AiProvider.NONE) {
                    AiCredentialStore.hasApiKeyCached(p);
                }
            });
        }
    }

    // Package-private (not private) so a test can spy the service and stub the client, exercising the
    // list-models success path + session cache without real network. Returns a JDK type — no platform API.
    HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Checks if a direct API provider is configured with credentials.
     */
    public boolean isConfigured() {
        AiProvider provider = getProvider();
        // hasApiKeyCached is EDT-safe (a cached map read; warmed off-EDT in the constructor).
        return provider != AiProvider.NONE && AiCredentialStore.hasApiKeyCached(provider);
    }

    /**
     * Generates artifact content using the configured AI provider.
     */
    public String generate(ArtifactInstruction instruction) throws AiApiException {
        AiProvider provider = getProvider();
        if (provider == AiProvider.NONE) {
            throw new AiApiException("No AI provider configured");
        }

        String apiKey = AiCredentialStore.getApiKey(provider);
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiApiException("No API key configured for " + provider.getDisplayName());
        }

        String model = getModel(provider);
        String prompt = instruction.buildDirectApiPrompt();

        return switch (provider) {
            case CLAUDE -> callClaude(apiKey, model, prompt, requestSettings());
            case OPENAI -> callOpenAi(apiKey, model, prompt, requestSettings());
            case GEMINI -> callGemini(apiKey, model, prompt);
            case NONE -> throw new AiApiException("No AI provider configured");
        };
    }

    /**
     * Sends a raw prompt to the configured AI provider and returns the response.
     * Used for explore and other non-artifact interactions.
     */
    public String generateRaw(String prompt) throws AiApiException {
        AiProvider provider = getProvider();
        if (provider == AiProvider.NONE) {
            throw new AiApiException("No AI provider configured");
        }

        String apiKey = AiCredentialStore.getApiKey(provider);
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiApiException("No API key configured for " + provider.getDisplayName());
        }

        String model = getModel(provider);

        return switch (provider) {
            case CLAUDE -> callClaude(apiKey, model, prompt, requestSettings());
            case OPENAI -> callOpenAi(apiKey, model, prompt, requestSettings());
            case GEMINI -> callGemini(apiKey, model, prompt);
            case NONE -> throw new AiApiException("No AI provider configured");
        };
    }

    /**
     * Tests the API connection with a simple request.
     */
    public String testConnection() throws AiApiException {
        AiProvider provider = getProvider();
        String apiKey = AiCredentialStore.getApiKey(provider);
        String model = getModel(provider);
        return testConnection(provider, apiKey, model);
    }

    /**
     * Tests the API connection using explicit provider, key, and model values.
     * Used by the settings panel to test before Apply is clicked.
     */
    public String testConnection(AiProvider provider, String apiKey, String model) throws AiApiException {
        if (provider == AiProvider.NONE) {
            throw new AiApiException("No AI provider selected");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new AiApiException("No API key provided for " + provider.getDisplayName());
        }

        if (model == null || model.isBlank()) {
            model = provider.getDefaultModel();
        }

        String testPrompt = "Respond with exactly: OK";

        try {
            String response = switch (provider) {
                case CLAUDE -> callClaude(apiKey, model, testPrompt, requestSettings());
                case OPENAI -> callOpenAi(apiKey, model, testPrompt, requestSettings());
                case GEMINI -> callGemini(apiKey, model, testPrompt);
                case NONE -> throw new AiApiException("No provider");
            };
            return "Connected to " + provider.getDisplayName() + " (" + model + ")";
        } catch (AiApiException e) {
            throw new AiApiException("Connection test failed: " + e.getMessage(), e);
        }
    }

    /**
     * The Anthropic API version the request and response shapes here were written against. Newer
     * versions exist (2026-10-21), but no end date for this one is announced and the plugin uses
     * none of the fields they changed. Move to a newer version deliberately, not as a routine bump.
     */
    static final String ANTHROPIC_VERSION = "2023-06-01";

    /** Header that selects the workspace for a Claude key that isn't scoped to one. */
    static final String ANTHROPIC_WORKSPACE_HEADER = "anthropic-workspace-id";
    /** Headers that select the OpenAI organization and project for a legacy user key or a multi-org user. */
    static final String OPENAI_ORGANIZATION_HEADER = "OpenAI-Organization";
    static final String OPENAI_PROJECT_HEADER = "OpenAI-Project";

    /**
     * Builds a Claude Messages API request. Extracted (mirroring {@link #buildGeminiRequest})
     * so the auth, {@code anthropic-version} and workspace headers are assertable in a unit test.
     * The workspace header is sent only when the request settings name a workspace.
     */
    static HttpRequest buildClaudeRequest(String model, String apiKey, String prompt,
                                          ProviderRequestSettings requestSettings) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", MAX_TOKENS);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_API_URL))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)));
        addClaudeAuthHeaders(builder, apiKey, requestSettings);
        return builder.build();
    }

    /**
     * Claude auth headers shared by generation and list-models: {@code Authorization: Bearer} (the
     * legacy {@code x-api-key} is not sent), the API version, and the workspace when one is set.
     */
    private static void addClaudeAuthHeaders(HttpRequest.Builder builder, String apiKey,
                                             ProviderRequestSettings requestSettings) {
        builder.header("Authorization", "Bearer " + apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION);
        if (!requestSettings.claudeWorkspaceId().isEmpty()) {
            builder.header(ANTHROPIC_WORKSPACE_HEADER, requestSettings.claudeWorkspaceId());
        }
    }

    /**
     * OpenAI auth headers shared by generation and list-models: {@code Authorization: Bearer}, and the
     * organization and project only when set, so requests without them are unchanged.
     */
    private static void addOpenAiAuthHeaders(HttpRequest.Builder builder, String apiKey,
                                             ProviderRequestSettings requestSettings) {
        builder.header("Authorization", "Bearer " + apiKey);
        if (!requestSettings.openAiOrganization().isEmpty()) {
            builder.header(OPENAI_ORGANIZATION_HEADER, requestSettings.openAiOrganization());
        }
        if (!requestSettings.openAiProject().isEmpty()) {
            builder.header(OPENAI_PROJECT_HEADER, requestSettings.openAiProject());
        }
    }

    /**
     * Extracts the generated text from a Claude Messages API response body: the concatenated text of
     * every {@code text} content block. Every other block type ({@code thinking},
     * {@code redacted_thinking}, anything newer) is skipped, because models with thinking on by
     * default return a thinking block before the text. Extracted for contract-testing against
     * live-captured responses.
     */
    static String parseClaudeResponse(String body) throws AiApiException {
        JsonObject responseJson = JsonParser.parseString(body).getAsJsonObject();
        JsonArray content = responseJson.getAsJsonArray("content");
        StringBuilder text = new StringBuilder();
        boolean foundText = false;
        if (content != null) {
            for (var el : content) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject block = el.getAsJsonObject();
                if (block.has("type") && "text".equals(block.get("type").getAsString()) && block.has("text")) {
                    text.append(block.get("text").getAsString());
                    foundText = true;
                }
            }
        }
        if (foundText) {
            return text.toString();
        }
        String stopReason = responseJson.has("stop_reason") && !responseJson.get("stop_reason").isJsonNull()
                ? responseJson.get("stop_reason").getAsString()
                : "unknown";
        String message = "Claude returned no text (stop_reason: " + stopReason + ")";
        if ("max_tokens".equals(stopReason)) {
            message += " \u2014 the token limit was reached before any text was written";
        }
        throw new AiApiException(message);
    }

    private String callClaude(String apiKey, String model, String prompt, ProviderRequestSettings requestSettings)
            throws AiApiException {
        try {
            HttpRequest request = buildClaudeRequest(model, apiKey, prompt, requestSettings);
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw buildApiError("Claude", response.statusCode(), response.body(), requestSettings, response.headers());
            }
            return parseClaudeResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiApiException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the token-limit parameter name for the given OpenAI model. OpenAI deprecated
     * {@code max_tokens} in favor of {@code max_completion_tokens}, and reasoning models
     * ({@code o}-series, {@code gpt-5}, {@code gpt-6} and later) reject it. So only the legacy
     * non-reasoning chat families keep {@code max_tokens}; every other model, including families
     * released after this code, gets {@code max_completion_tokens}. Fine-tunes ({@code ft:<base>:…})
     * follow their base model.
     */
    static String openAiTokenParam(String model) {
        if (model != null) {
            String base = model.startsWith("ft:") ? model.substring(3) : model;
            for (String prefix : LEGACY_MAX_TOKENS_PREFIXES) {
                if (base.startsWith(prefix)) return "max_tokens";
            }
        }
        return "max_completion_tokens";
    }

    /** {@code gpt-4} covers gpt-4, gpt-4-turbo, gpt-4o and gpt-4.1. */
    private static final java.util.List<String> LEGACY_MAX_TOKENS_PREFIXES =
            java.util.List.of("gpt-3.5", "gpt-4", "chatgpt-4o");

    /**
     * Builds an OpenAI Chat Completions request. Extracted (mirroring {@link #buildGeminiRequest})
     * so the model-dependent token-limit parameter placement and the auth headers are assertable.
     */
    static HttpRequest buildOpenAiRequest(String model, String apiKey, String prompt,
                                          ProviderRequestSettings requestSettings) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty(openAiTokenParam(model), MAX_TOKENS);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)));
        addOpenAiAuthHeaders(builder, apiKey, requestSettings);
        return builder.build();
    }

    /**
     * Extracts the generated text from an OpenAI Chat Completions response body: the first choice's
     * {@code message.content}. When that is null or empty (a refusal, or a reasoning model that spent
     * the token limit before writing), it throws an error naming the {@code refusal} or
     * {@code finish_reason} instead of returning empty text. Extracted for contract-testing against
     * captured provider example output.
     */
    static String parseOpenAiResponse(String body) throws AiApiException {
        JsonObject responseJson = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = responseJson.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty() || !choices.get(0).isJsonObject()) {
            throw new AiApiException("Empty response from OpenAI API");
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = choice.has("message") && choice.get("message").isJsonObject()
                ? choice.getAsJsonObject("message")
                : new JsonObject();
        String content = stringOrNull(message, "content");
        if (content != null && !content.isEmpty()) {
            return content;
        }
        String refusal = stringOrNull(message, "refusal");
        if (refusal != null && !refusal.isBlank()) {
            throw new AiApiException("OpenAI declined the request: " + refusal);
        }
        String finishReason = stringOrNull(choice, "finish_reason");
        String error = "OpenAI returned no text (finish_reason: " + (finishReason != null ? finishReason : "unknown") + ")";
        if ("length".equals(finishReason)) {
            error += " \u2014 the token limit was reached before any text was written";
        }
        throw new AiApiException(error);
    }

    /** The string value of {@code key}, or {@code null} when it is absent, JSON null or not a string. */
    private static @Nullable String stringOrNull(JsonObject json, String key) {
        var el = json.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    private String callOpenAi(String apiKey, String model, String prompt, ProviderRequestSettings requestSettings)
            throws AiApiException {
        try {
            HttpRequest request = buildOpenAiRequest(model, apiKey, prompt, requestSettings);
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw buildApiError("OpenAI", response.statusCode(), response.body(), requestSettings, response.headers());
            }
            return parseOpenAiResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiApiException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a Gemini generateContent request. Extracted for testability — the API key
     * is sent via the x-goog-api-key header (Google's recommended pattern) rather than
     * embedded in the URL query string, so that keys do not leak into request logs.
     */
    static HttpRequest buildGeminiRequest(String model, String apiKey, String prompt) {
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject part = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);
        part.add("parts", parts);
        contents.add(part);
        body.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", MAX_TOKENS);
        body.add("generationConfig", generationConfig);

        String url = GEMINI_API_URL + model + ":generateContent";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();
    }

    /**
     * Extracts the generated text from a Gemini generateContent response body: the concatenated text
     * of every part of the first candidate that isn't marked {@code thought: true}. When there is no
     * such text it throws an error naming {@code promptFeedback.blockReason} (a blocked prompt has no
     * candidates) or the candidate's {@code finishReason}, instead of returning empty text. Extracted
     * for contract-testing against captured provider example output.
     */
    static String parseGeminiResponse(String body) throws AiApiException {
        JsonObject responseJson = JsonParser.parseString(body).getAsJsonObject();
        JsonArray candidates = responseJson.getAsJsonArray("candidates");
        JsonObject candidate = candidates != null && !candidates.isEmpty() && candidates.get(0).isJsonObject()
                ? candidates.get(0).getAsJsonObject()
                : null;
        if (candidate != null && candidate.has("content") && candidate.get("content").isJsonObject()) {
            JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
            StringBuilder text = new StringBuilder();
            if (parts != null) {
                for (var el : parts) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject part = el.getAsJsonObject();
                    boolean thought = part.has("thought") && part.get("thought").isJsonPrimitive()
                            && part.get("thought").getAsBoolean();
                    String partText = stringOrNull(part, "text");
                    if (!thought && partText != null) {
                        text.append(partText);
                    }
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }
        JsonObject feedback = responseJson.has("promptFeedback") && responseJson.get("promptFeedback").isJsonObject()
                ? responseJson.getAsJsonObject("promptFeedback")
                : null;
        String blockReason = feedback != null ? stringOrNull(feedback, "blockReason") : null;
        if (blockReason != null) {
            throw new AiApiException("Gemini blocked the prompt (blockReason: " + blockReason + ")");
        }
        String finishReason = candidate != null ? stringOrNull(candidate, "finishReason") : null;
        if (finishReason == null) {
            throw new AiApiException("Empty response from Gemini API");
        }
        String error = "Gemini returned no text (finishReason: " + finishReason + ")";
        if ("MAX_TOKENS".equals(finishReason)) {
            error += " \u2014 the token limit was reached before any text was written";
        }
        throw new AiApiException(error);
    }

    private String callGemini(String apiKey, String model, String prompt) throws AiApiException {
        try {
            HttpRequest request = buildGeminiRequest(model, apiKey, prompt);
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw buildApiError("Gemini", response.statusCode(), response.body(), ProviderRequestSettings.NONE,
                        response.headers());
            }
            return parseGeminiResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiApiException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    // --- Model discovery (list-models) ---

    /**
     * Builds the list-models {@code GET} for a provider, reusing the same auth headers as generation.
     * Extracted (mirroring {@link #buildGeminiRequest}) so the endpoint/headers are assertable and so
     * the key is verified this can never be embedded in the URL. Each provider reads only its own request settings.
     */
    static HttpRequest buildListModelsRequest(AiProvider provider, String apiKey,
                                              ProviderRequestSettings requestSettings) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().timeout(TIMEOUT).GET();
        switch (provider) {
            case OPENAI -> addOpenAiAuthHeaders(builder.uri(URI.create(OPENAI_MODELS_URL)), apiKey, requestSettings);
            case CLAUDE -> addClaudeAuthHeaders(builder.uri(URI.create(CLAUDE_MODELS_URL)), apiKey, requestSettings);
            case GEMINI -> builder.uri(URI.create(GEMINI_MODELS_URL))
                    .header("x-goog-api-key", apiKey);
            case NONE -> throw new IllegalArgumentException("No AI provider selected");
        }
        return builder.build();
    }

    /** OpenAI list-models shape: {@code {data:[{id}]}}. Extracted for contract-testing. */
    static java.util.List<String> parseOpenAiModels(String body) {
        return parseDataArrayIds(body);
    }

    /**
     * Anthropic list-models shape: {@code {data:[{id,display_name}]}} — all entries are
     * Messages-compatible, so the {@code id} is used directly (never {@code display_name}).
     */
    static java.util.List<String> parseAnthropicModels(String body) {
        return parseDataArrayIds(body);
    }

    private static java.util.List<String> parseDataArrayIds(String body) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        JsonArray data = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("data");
        if (data != null) {
            for (var el : data) {
                JsonObject m = el.getAsJsonObject();
                if (m.has("id")) {
                    ids.add(m.get("id").getAsString());
                }
            }
        }
        return ids;
    }

    /**
     * Gemini list-models shape: {@code {models:[{name:"models/…",supportedGenerationMethods:[…]}]}}.
     * Keeps only models whose {@code supportedGenerationMethods} contains {@code generateContent}
     * (drops embedding/answer-only models) and strips the {@code models/} name prefix. Extracted for
     * contract-testing — the filter's negative branch is exercised by a real embedding entry in the fixture.
     */
    static java.util.List<String> parseGeminiModels(String body) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        JsonArray models = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("models");
        if (models != null) {
            for (var el : models) {
                JsonObject m = el.getAsJsonObject();
                if (!m.has("name")) {
                    continue;
                }
                JsonArray methods = m.getAsJsonArray("supportedGenerationMethods");
                boolean supportsGenerate = false;
                if (methods != null) {
                    for (var method : methods) {
                        if ("generateContent".equals(method.getAsString())) {
                            supportsGenerate = true;
                            break;
                        }
                    }
                }
                if (supportsGenerate) {
                    String name = m.get("name").getAsString();
                    ids.add(name.startsWith("models/") ? name.substring("models/".length()) : name);
                }
            }
        }
        return ids;
    }

    /**
     * Fetches the account's available models for the given provider using the configured key — a free
     * {@code GET}, no token cost, which doubles as key validation. Blocking (network I/O) — call off
     * the EDT. Non-200 throws {@link AiApiException} via {@link #buildApiError}. Callers treat any
     * failure (or an empty list) as a signal to fall back to the static model list. {@code requestSettings}
     * are the provider-specific settings to send (Settings passes the unsaved form values).
     */
    public java.util.List<String> listModels(AiProvider provider, String apiKey, ProviderRequestSettings requestSettings)
            throws AiApiException {
        if (provider == null || provider == AiProvider.NONE) {
            throw new AiApiException("No AI provider selected");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiApiException("No API key provided for " + provider.getDisplayName());
        }
        try {
            HttpRequest request = buildListModelsRequest(provider, apiKey, requestSettings);
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw buildApiError(provider.getDisplayName(), response.statusCode(), response.body(), requestSettings,
                        response.headers());
            }
            java.util.List<String> models = switch (provider) {
                case OPENAI -> parseOpenAiModels(response.body());
                case CLAUDE -> parseAnthropicModels(response.body());
                case GEMINI -> parseGeminiModels(response.body());
                case NONE -> java.util.List.of();
            };
            // Warm the per-session cache so the tool-window model selector and the Settings "Test"
            // share one fetch per provider per session (in-memory only — never persisted across sessions).
            discoveredModels.put(provider, java.util.List.copyOf(models));
            return models;
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiApiException(provider.getDisplayName() + " model list failed: " + e.getMessage(), e);
        }
    }

    /**
     * Per-session, in-memory cache of the last successfully fetched model list per provider (warmed by
     * {@link #listModels}). NOT persisted — honors the {@code fetch-provider-models-dynamically} non-goal
     * of no cross-session model-list caching.
     */
    private final java.util.Map<AiProvider, java.util.List<String>> discoveredModels =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** The cached model list for a provider from this session, or {@code null} if none has been fetched. */
    public java.util.List<String> getDiscoveredModels(AiProvider provider) {
        return provider == null ? null : discoveredModels.get(provider);
    }

    // Package-private so the mapping is testable against the live-captured error bodies.
    static AiApiException buildApiError(String providerName, int statusCode, String responseBody) {
        return buildApiError(providerName, statusCode, responseBody, ProviderRequestSettings.NONE, null);
    }

    static AiApiException buildApiError(String providerName, int statusCode, String responseBody,
                                        ProviderRequestSettings requestSettings) {
        return buildApiError(providerName, statusCode, responseBody, requestSettings, null);
    }

    public static final String OPENAI_SCOPE_SUGGESTION = "Check your API key, and that the Organization ID and Project ID "
            + "in Settings \u2192 Tools \u2192 OpenSpec \u2192 Direct API belong to it";

    /**
     * Added to a Gemini permission error. Google rejects older standard keys that aren't restricted to the Gemini API
     * but documents no distinct error for them, so this is worded as a possible cause, not a diagnosis.
     */
    public static final String GEMINI_KEY_RESTRICTION_NOTE = "Google rejects older keys that aren't restricted to the "
            + "Gemini API, and blocks them when unused. If yours is one, create a new key in Google AI Studio";

    /**
     * The error for a failed request. The body's documented error field picks the suggestion, the status only when it
     * has none ({@link DirectApiErrors}). With an OpenAI organization or project set, a 401 also points at those
     * settings; that hint is chosen on the status alone, because OpenAI doesn't document the body for a mismatch.
     * The provider's request ID, when the response carries one, ends the message. {@code headers} may be null.
     */
    static AiApiException buildApiError(String providerName, int statusCode, String responseBody,
                                        ProviderRequestSettings requestSettings, @Nullable HttpHeaders headers) {
        String requestId = DirectApiErrors.requestId(providerName, headers, responseBody);
        LOG.warn(errorLogLine(providerName, statusCode, requestId, responseBody));
        ErrorKind kind = DirectApiErrors.classify(providerName, statusCode, DirectApiErrors.errorObject(responseBody));
        String suggestion = DirectApiErrors.suggestion(providerName, kind);
        if (statusCode == 401 && AiProvider.OPENAI.getDisplayName().equals(providerName)
                && requestSettings.hasOpenAiScope()) {
            suggestion = OPENAI_SCOPE_SUGGESTION;
        }
        String userMessage = providerName + " API error: " + extractErrorMessage(responseBody);
        if (AiProvider.CLAUDE.getDisplayName().equals(providerName)) {
            String mapped = claudeWorkspaceMessage(statusCode, responseBody);
            if (mapped != null) {
                userMessage = mapped;
                suggestion = null;
            }
        }
        if (requestId != null) {
            userMessage += " (request ID: " + requestId + ")";
        }
        return new AiApiException(userMessage, statusCode, providerName, suggestion, kind, requestId, null);
    }

    static String errorLogLine(String providerName, int statusCode, @Nullable String requestId, String responseBody) {
        return providerName + " API error (HTTP " + statusCode
                + (requestId != null ? ", request ID " + requestId : "") + "): " + responseBody;
    }

    static final String UNSCOPED_KEY_MESSAGE = "This Claude API key isn't scoped to a workspace. Enter a "
            + "Workspace ID in Settings \u2192 Tools \u2192 OpenSpec \u2192 Direct API, or use a key scoped to one workspace.";
    static final String WORKSPACE_NOT_FOUND_MESSAGE = "Workspace ID not found, or this key can't access it.";
    static final String INVALID_WORKSPACE_MESSAGE = "Claude doesn't recognize this Workspace ID. Copy it from "
            + "Console \u2192 Settings \u2192 Workspaces in Settings \u2192 Tools \u2192 OpenSpec \u2192 Direct API.";

    /**
     * Maps Claude's workspace-selection errors to an actionable message, or returns {@code null} to keep
     * the provider's own message. Matched on error type plus wording, pinned by a live-captured 400.
     */
    private static @Nullable String claudeWorkspaceMessage(int statusCode, String responseBody) {
        try {
            JsonObject error = JsonParser.parseString(responseBody).getAsJsonObject().getAsJsonObject("error");
            if (error == null || !error.has("type") || !error.has("message")) {
                return null;
            }
            String type = error.get("type").getAsString();
            String message = error.get("message").getAsString();
            if (statusCode == 400 && "invalid_request_error".equals(type) && message.contains(ANTHROPIC_WORKSPACE_HEADER)) {
                // The same header name appears in two live 400s: a malformed ID, and a key with no workspace at all.
                return message.contains("valid workspace ID") ? INVALID_WORKSPACE_MESSAGE : UNSCOPED_KEY_MESSAGE;
            }
            if (statusCode == 404 && "not_found_error".equals(type) && message.startsWith("Workspace")) {
                return WORKSPACE_NOT_FOUND_MESSAGE;
            }
        } catch (Exception ignored) {
            // Not JSON or an unexpected shape — keep the provider's message.
        }
        return null;
    }

    private static String extractErrorMessage(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) {
                JsonObject error = json.getAsJsonObject("error");
                if (error != null && error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
            // Not valid JSON or unexpected structure — fall through to truncation
        }
        if (responseBody.length() > 200) {
            return responseBody.substring(0, 200) + "...";
        }
        return responseBody;
    }

    private AiProvider getProvider() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        return AiProvider.fromString(settings.getAiProvider());
    }

    private ProviderRequestSettings requestSettings() {
        return ProviderRequestSettings.from(OpenSpecSettings.getInstance(project));
    }

    private String getModel(AiProvider provider) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String model = settings.getAiModel();
        if (model == null || model.isBlank()) {
            return provider.getDefaultModel();
        }
        return model;
    }
}
