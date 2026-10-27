package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for Direct API error classification and request IDs, driven by the error bodies and headers under
 * {@code fixtures/ai/<provider>/errors/}: live captures, the providers' documented examples, and bodies derived from
 * a live envelope with only documented fields changed (provenance per file in {@code fixtures/ai/README.md}).
 */
public class DirectApiErrorsContractTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/ai");

    /** The kind each fixture must classify as. A new fixture without an entry here fails {@link Hygiene}. */
    private static final Map<String, ErrorKind> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("claude/errors/models-401-invalid-key", ErrorKind.AUTH);
        EXPECTED.put("claude/errors/messages-401-invalid-key", ErrorKind.AUTH);
        EXPECTED.put("claude/errors/messages-401-header-and-body-ids", ErrorKind.AUTH);
        EXPECTED.put("claude/errors/messages-429-spend-cap", ErrorKind.SPEND_LIMIT);
        EXPECTED.put("claude/errors/messages-402-billing-error", ErrorKind.BILLING);
        EXPECTED.put("claude/errors/messages-400-api-usage-limit", ErrorKind.SPEND_LIMIT);
        EXPECTED.put("claude/errors/messages-400-workspace-usage-limit", ErrorKind.SPEND_LIMIT);
        EXPECTED.put("claude/errors/messages-529-overloaded", ErrorKind.OVERLOADED);
        EXPECTED.put("claude/errors/messages-429-rate-limit", ErrorKind.RATE_LIMIT);
        EXPECTED.put("openai/errors/models-401-invalid-key", ErrorKind.AUTH);
        EXPECTED.put("openai/errors/chat-completions-401-invalid-key", ErrorKind.AUTH);
        EXPECTED.put("openai/errors/429-credit-balance-exhausted", ErrorKind.BILLING);
        EXPECTED.put("openai/errors/429-organization-spend-limit-exceeded", ErrorKind.SPEND_LIMIT);
        EXPECTED.put("openai/errors/429-project-spend-limit-exceeded", ErrorKind.SPEND_LIMIT);
        EXPECTED.put("openai/errors/429-organization-usage-limit-exceeded", ErrorKind.SPEND_LIMIT);
        EXPECTED.put("openai/errors/429-insufficient-quota", ErrorKind.SPEND_LIMIT);
        EXPECTED.put("openai/errors/429-slow-down", ErrorKind.RATE_LIMIT);
        EXPECTED.put("openai/errors/503-server-is-overloaded", ErrorKind.OVERLOADED);
        EXPECTED.put("openai/errors/400-service-tier-not-allowed", ErrorKind.SERVICE_TIER);
        EXPECTED.put("gemini/errors/models-400-api-key-invalid", ErrorKind.AUTH);
        EXPECTED.put("gemini/errors/generatecontent-400-api-key-invalid", ErrorKind.AUTH);
        EXPECTED.put("gemini/errors/generatecontent-400-invalid-argument", ErrorKind.UNKNOWN);
        EXPECTED.put("gemini/errors/models-403-no-key", ErrorKind.PERMISSION);
        EXPECTED.put("gemini/errors/generatecontent-403-no-key", ErrorKind.PERMISSION);
        EXPECTED.put("gemini/errors/generatecontent-402-payment-required", ErrorKind.BILLING);
        EXPECTED.put("gemini/errors/generatecontent-429-resource-exhausted", ErrorKind.RATE_LIMIT);
        EXPECTED.put("gemini/errors/generatecontent-503-unavailable", ErrorKind.OVERLOADED);
    }

    // ---- fixture loading ----

    static String body(String fixture) {
        return resource("/fixtures/ai/" + fixture + ".json");
    }

    /** The captured headers of a fixture: one {@code name: value} per line. */
    static HttpHeaders headers(String fixture) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String line : resource("/fixtures/ai/" + fixture + ".headers").split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                map.computeIfAbsent(line.substring(0, colon).trim(), k -> new ArrayList<>())
                        .add(line.substring(colon + 1).trim());
            }
        }
        return HttpHeaders.of(map, (k, v) -> true);
    }

    private static String resource(String path) {
        try (InputStream in = DirectApiErrorsContractTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "fixture not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The provider display name for a fixture's directory. */
    static String provider(String fixture) {
        return switch (fixture.substring(0, fixture.indexOf('/'))) {
            case "claude" -> AiProvider.CLAUDE.getDisplayName();
            case "openai" -> AiProvider.OPENAI.getDisplayName();
            case "gemini" -> AiProvider.GEMINI.getDisplayName();
            default -> throw new IllegalArgumentException(fixture);
        };
    }

    /** The HTTP status a fixture was returned with, from the three-digit token in its name. */
    static int status(String fixture) {
        Matcher m = Pattern.compile("(?:^|-)(\\d{3})-").matcher(fixture.substring(fixture.lastIndexOf('/') + 1));
        assertTrue(m.find(), "fixture name carries no status: " + fixture);
        return Integer.parseInt(m.group(1));
    }

    /** The exception the plugin builds for a fixture. Public so the surface tests use real errors, not hand-made ones. */
    public static AiApiException build(String fixture) {
        return DirectApiService.buildApiError(provider(fixture), status(fixture), body(fixture),
                ProviderRequestSettings.NONE, headers(fixture));
    }

    static Stream<String> fixtures() {
        return EXPECTED.keySet().stream();
    }

    private static boolean advisesRetry(String suggestion) {
        String s = suggestion.toLowerCase(Locale.ROOT);
        return s.contains("retry") || s.contains("try again") || s.contains("wait a moment");
    }

    // ---- classification ----

    @ParameterizedTest
    @MethodSource("fixtures")
    void classifiesEachFixture(String fixture) {
        AiApiException ex = build(fixture);
        assertEquals(EXPECTED.get(fixture), ex.getKind(), fixture);
        assertEquals(status(fixture), ex.getStatusCode(), fixture);
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void keepsTheProviderMessage(String fixture) {
        JsonObject error = JsonParser.parseString(body(fixture)).getAsJsonObject().getAsJsonObject("error");
        AiApiException ex = build(fixture);
        String mapped = ex.getMessage();
        assertTrue(mapped.startsWith(provider(fixture) + " API error: " + error.get("message").getAsString()),
                fixture + ": " + mapped);
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void billingQuotaAndSpendNeverAdviseRetrying(String fixture) {
        ErrorKind kind = EXPECTED.get(fixture);
        if (kind != ErrorKind.BILLING && kind != ErrorKind.SPEND_LIMIT) {
            return;
        }
        String suggestion = build(fixture).getSuggestion();
        assertNotNull(suggestion, fixture);
        assertFalse(advisesRetry(suggestion), fixture + ": " + suggestion);
        assertTrue(suggestion.contains("Billing") || suggestion.contains("Limits") || suggestion.contains("limits"),
                fixture + " must point at billing or limits: " + suggestion);
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void rateLimitAndOverloadAdviseWaiting(String fixture) {
        ErrorKind kind = EXPECTED.get(fixture);
        if (kind != ErrorKind.RATE_LIMIT && kind != ErrorKind.OVERLOADED) {
            return;
        }
        String suggestion = build(fixture).getSuggestion();
        assertNotNull(suggestion, fixture);
        assertTrue(advisesRetry(suggestion), fixture + ": " + suggestion);
    }

    @Test
    void claudeSpendCapIsNotARateLimitDespiteItsType() {
        // The documented spend cap carries type rate_limit_error; only details.error_code tells it apart.
        String fixture = "claude/errors/messages-429-spend-cap";
        assertEquals("rate_limit_error", JsonParser.parseString(body(fixture)).getAsJsonObject()
                .getAsJsonObject("error").get("type").getAsString());
        assertEquals(ErrorKind.SPEND_LIMIT, build(fixture).getKind());
    }

    @Test
    void openAiCodeDecidesBeforeTheBroaderType() {
        // credit_balance_exhausted carries type insufficient_quota (SPEND_LIMIT on its own) but is a billing error.
        String fixture = "openai/errors/429-credit-balance-exhausted";
        JsonObject error = JsonParser.parseString(body(fixture)).getAsJsonObject().getAsJsonObject("error");
        assertEquals("insufficient_quota", error.get("type").getAsString());
        assertEquals(ErrorKind.BILLING, build(fixture).getKind());
    }

    @Test
    void geminiInvalidKeyGetsTheKeySuggestionAndOtherGemini400sDoNot() {
        assertEquals(DirectApiErrors.KEY_SUGGESTION,
                build("gemini/errors/generatecontent-400-api-key-invalid").getSuggestion());
        assertNull(build("gemini/errors/generatecontent-400-invalid-argument").getSuggestion());
    }

    @Test
    void serviceTierNeedsTheServiceTierParam() {
        String fixture = "openai/errors/400-service-tier-not-allowed";
        assertEquals(DirectApiErrors.SERVICE_TIER_SUGGESTION, build(fixture).getSuggestion());

        JsonObject other = JsonParser.parseString(body(fixture)).getAsJsonObject();
        other.getAsJsonObject("error").addProperty("param", "messages");
        AiApiException ex = DirectApiService.buildApiError("OpenAI", 400, other.toString(),
                ProviderRequestSettings.NONE, headers(fixture));
        assertNotEquals(ErrorKind.SERVICE_TIER, ex.getKind());
        assertNull(ex.getSuggestion());
    }

    @Test
    void jsonSentAsPlainTextIsStillRead() {
        String live = "openai/errors/chat-completions-401-invalid-key";
        assertEquals("text/plain", headers(live).firstValue("content-type").orElseThrow());
        String message = JsonParser.parseString(body(live)).getAsJsonObject()
                .getAsJsonObject("error").get("message").getAsString();
        assertTrue(build(live).getMessage().startsWith("OpenAI API error: " + message));

        // A quota body under the same text/plain headers must still classify from its code, not fall back to 429.
        AiApiException quota = DirectApiService.buildApiError("OpenAI", 429,
                body("openai/errors/429-project-spend-limit-exceeded"), ProviderRequestSettings.NONE, headers(live));
        assertEquals(ErrorKind.SPEND_LIMIT, quota.getKind());
    }

    @Test
    void aBodyThatIsNotJsonFallsBackToTheStatus() {
        String html = "<html><head><title>413 Request Entity Too Large</title></head><body><center><h1>413 "
                + "Request Entity Too Large</h1></center><hr><center>cloudflare</center></body></html>"
                + " ".repeat(120);
        AiApiException ex = DirectApiService.buildApiError("Claude", 413, html);
        assertEquals("Claude API error: " + html.substring(0, 200) + "...", ex.getMessage());
        assertEquals(ErrorKind.UNKNOWN, ex.getKind());

        assertEquals(ErrorKind.RATE_LIMIT, DirectApiService.buildApiError("OpenAI", 429, "Too Many Requests").getKind());
        assertEquals(ErrorKind.OVERLOADED, DirectApiService.buildApiError("Claude", 529, "").getKind());
        assertEquals(ErrorKind.BILLING, DirectApiService.buildApiError("Gemini", 402, "Payment Required").getKind());
    }

    // ---- request IDs ----

    @Nested
    class RequestIds {

        @Test
        void claudeHeaderIdEndsTheMessage() {
            String fixture = "claude/errors/messages-401-invalid-key";
            String id = headers(fixture).firstValue("request-id").orElseThrow();
            AiApiException ex = build(fixture);
            assertEquals(id, ex.getRequestId());
            assertTrue(ex.getMessage().endsWith(" (request ID: " + id + ")"), ex.getMessage());
        }

        @Test
        void claudeHeaderWinsOverTheBody() {
            String fixture = "claude/errors/messages-401-header-and-body-ids";
            String header = headers(fixture).firstValue("request-id").orElseThrow();
            String inBody = JsonParser.parseString(body(fixture)).getAsJsonObject().get("request_id").getAsString();
            assertNotEquals(header, inBody, "the fixture must carry two different IDs");
            assertEquals(header, build(fixture).getRequestId());
        }

        @Test
        void claudeBodyIdWhenThereIsNoHeader() {
            String fixture = "claude/errors/models-401-invalid-key";
            String inBody = JsonParser.parseString(body(fixture)).getAsJsonObject().get("request_id").getAsString();
            AiApiException ex = DirectApiService.buildApiError("Claude", 401, body(fixture),
                    ProviderRequestSettings.NONE, HttpHeaders.of(Map.of(), (k, v) -> true));
            assertEquals(inBody, ex.getRequestId());
            assertTrue(ex.getMessage().endsWith(" (request ID: " + inBody + ")"));
        }

        @Test
        void openAiHeaderIdEndsTheMessage() {
            for (String fixture : List.of("openai/errors/models-401-invalid-key",
                    "openai/errors/chat-completions-401-invalid-key")) {
                String id = headers(fixture).firstValue("x-request-id").orElseThrow();
                AiApiException ex = build(fixture);
                assertEquals(id, ex.getRequestId(), fixture);
                assertTrue(ex.getMessage().endsWith(" (request ID: " + id + ")"), ex.getMessage());
            }
        }

        @Test
        void geminiAndMissingHeadersAddNothing() {
            AiApiException gemini = build("gemini/errors/generatecontent-400-api-key-invalid");
            assertNull(gemini.getRequestId());
            assertFalse(gemini.getMessage().contains("request ID"));

            AiApiException noHeaders = DirectApiService.buildApiError("OpenAI", 401,
                    body("openai/errors/models-401-invalid-key"));
            assertNull(noHeaders.getRequestId());
            assertFalse(noHeaders.getMessage().contains("request ID"));
        }

        @Test
        void aMappedWorkspaceMessageKeepsTheRequestId() {
            String unscoped = resource("/fixtures/ai/claude/models-400-unscoped-key.json");
            HttpHeaders headers = headers("claude/errors/models-401-invalid-key");
            String id = headers.firstValue("request-id").orElseThrow();
            AiApiException ex = DirectApiService.buildApiError("Claude", 400, unscoped,
                    ProviderRequestSettings.NONE, headers);
            assertEquals(DirectApiService.UNSCOPED_KEY_MESSAGE + " (request ID: " + id + ")", ex.getMessage());
            assertNull(ex.getSuggestion());
        }

        @Test
        void theLogLineCarriesTheRequestId() {
            assertEquals("Claude API error (HTTP 401, request ID req_1): {}",
                    DirectApiService.errorLogLine("Claude", 401, "req_1", "{}"));
            assertEquals("Gemini API error (HTTP 400): {}", DirectApiService.errorLogLine("Gemini", 400, null, "{}"));
        }
    }

    // ---- fixture hygiene ----

    @Nested
    class Hygiene {

        private List<Path> errorBodies() throws IOException {
            List<Path> bodies = new ArrayList<>();
            for (String provider : List.of("claude", "openai", "gemini")) {
                try (Stream<Path> files = Files.list(FIXTURES.resolve(provider).resolve("errors"))) {
                    files.filter(p -> p.toString().endsWith(".json")).sorted().forEach(bodies::add);
                }
            }
            return bodies;
        }

        private String name(Path body) {
            String rel = FIXTURES.relativize(body).toString().replace('\\', '/');
            return rel.substring(0, rel.length() - ".json".length());
        }

        @Test
        void everyFixtureHasHeadersAnExpectationAndAProvenanceRow() throws IOException {
            String readme = Files.readString(FIXTURES.resolve("README.md"));
            List<Path> bodies = errorBodies();
            assertEquals(EXPECTED.size(), bodies.size(), "every errors/ fixture needs an EXPECTED entry");
            for (Path body : bodies) {
                String name = name(body);
                assertTrue(EXPECTED.containsKey(name), "no expected kind for " + name);
                assertTrue(Files.exists(FIXTURES.resolve(name + ".headers")), "no .headers for " + name);
                Matcher row = Pattern.compile("^\\| `" + Pattern.quote(name) + "` \\| (LIVE|DOCS|DERIVED)\\b",
                        Pattern.MULTILINE).matcher(readme);
                assertTrue(row.find(), "no LIVE/DOCS/DERIVED README row for " + name);
            }
        }

        @Test
        void noFixtureCarriesAKey() throws IOException {
            Pattern key = Pattern.compile("sk-(ant-|proj-)?[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{20,}");
            for (Path body : errorBodies()) {
                for (Path file : List.of(body, FIXTURES.resolve(name(body) + ".headers"))) {
                    assertFalse(key.matcher(Files.readString(file)).find(), "key-like value in " + file);
                }
            }
        }
    }
}
