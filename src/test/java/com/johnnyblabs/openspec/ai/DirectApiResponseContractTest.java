package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.model.MultiFileArtifact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the three Direct-API response parsers. Claude, and Gemini's text and token-limit cases, are
 * driven by live-captured responses; OpenAI and the other Gemini cases by each provider's published example response
 * shape (see
 * {@code src/test/resources/fixtures/ai/README.md} for provenance). The asserted text is read from the committed
 * fixture, so these are real contract checks against the provider's stated shape — not the
 * hand-authored inline literals the repo's contract-test discipline forbids.
 */
class DirectApiResponseContractTest {

    private static String loadFixture(String path) {
        try (InputStream in = DirectApiResponseContractTest.class.getResourceAsStream("/fixtures/ai/" + path)) {
            assertNotNull(in, "fixture not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to load fixture " + path, e);
        }
    }

    @Nested
    class ClaudeParserContract {
        // Both Claude fixtures are LIVE captures (see the fixtures README). The expected text is read
        // straight out of the captured text block, so the assertion is the provider's real output.
        private String capturedTextBlock(String fixture, int index) {
            JsonObject block = JsonParser.parseString(loadFixture(fixture)).getAsJsonObject()
                    .getAsJsonArray("content").get(index).getAsJsonObject();
            assertEquals("text", block.get("type").getAsString(), "fixture block " + index + " must be text");
            return block.get("text").getAsString();
        }

        @Test
        void extractsTextFromLiveTextOnlyResponse() throws Exception {
            String text = DirectApiService.parseClaudeResponse(loadFixture("claude/messages-text-only.json"));
            assertEquals(capturedTextBlock("claude/messages-text-only.json", 0), text);
            assertTrue(text.startsWith("# Design: Dark-Mode Toggle"));
        }

        @Test
        void skipsLeadingThinkingBlockInLiveResponse() throws Exception {
            String body = loadFixture("claude/messages-thinking-first.json");
            // Guard the fixture's load-bearing shape: a thinking block first, then the text.
            JsonArray content = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("content");
            assertEquals("thinking", content.get(0).getAsJsonObject().get("type").getAsString());

            String text = DirectApiService.parseClaudeResponse(body);
            assertEquals(capturedTextBlock("claude/messages-thinking-first.json", 1), text);
            assertTrue(text.startsWith("# design.md: Dark-Mode Toggle"));
        }

        @Test
        void concatenatesEveryTextBlock() throws Exception {
            // Derived from the live capture: the text block duplicated after itself.
            JsonObject json = JsonParser.parseString(loadFixture("claude/messages-thinking-first.json")).getAsJsonObject();
            JsonArray content = json.getAsJsonArray("content");
            content.add(content.get(1).deepCopy());
            String expected = capturedTextBlock("claude/messages-thinking-first.json", 1);

            assertEquals(expected + expected, DirectApiService.parseClaudeResponse(json.toString()));
        }

        @Test
        void noTextBlockNamesStopReason() {
            // Derived from the live capture: drop the text block, as when the token limit is hit while
            // thinking. Only content and stop_reason differ from the real response.
            JsonObject json = JsonParser.parseString(loadFixture("claude/messages-thinking-first.json")).getAsJsonObject();
            json.getAsJsonArray("content").remove(1);
            json.addProperty("stop_reason", "max_tokens");

            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseClaudeResponse(json.toString()));
            assertTrue(ex.getMessage().contains("stop_reason: max_tokens"), ex.getMessage());
            assertTrue(ex.getMessage().contains("token limit"), ex.getMessage());
        }

        @Test
        void liveMultiFileSpecsResponseParsesIntoFiles() throws Exception {
            // LIVE capture: claude-sonnet-5-5's answer to the plugin's real Direct API specs prompt (committed next to
            // it as messages-specs-multi-file.prompt.md). Pins that a real model follows the marker format.
            String text = DirectApiService.parseClaudeResponse(loadFixture("claude/messages-specs-multi-file.json"));
            List<MultiFileArtifact.FileEntry> files = MultiFileArtifact.parse(text, "specs/**/*.md");

            assertEquals(1, files.size(), "the demo proposal names one capability");
            assertEquals("specs/greeting/spec.md", files.getFirst().path());
            String spec = files.getFirst().content();
            assertTrue(spec.startsWith("# Spec Delta\n"), spec);
            assertFalse(spec.contains("<!-- file:"), "the marker line is not part of the file");
            assertTrue(spec.contains("### Requirement: Farewell message") && spec.contains("#### Scenario:"), spec);
        }

        @Test
        void emptyContentThrowsWithStopReason() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseClaudeResponse("{\"content\":[],\"stop_reason\":\"end_turn\"}"));
            assertTrue(ex.getMessage().contains("no text (stop_reason: end_turn)"), ex.getMessage());
        }
    }

    @Nested
    class OpenAiParserContract {
        @Test
        void extractsContentFromDocumentedShape() throws Exception {
            String text = DirectApiService.parseOpenAiResponse(loadFixture("openai/chat-completion-response.json"));
            assertEquals("\n\nHello there, how may I assist you today?", text);
        }

        @Test
        void extractsContentFromOpenApiExampleWithNullRefusal() throws Exception {
            // The newer documented shape carries "refusal": null beside the content.
            String body = loadFixture("openai/chat-completion-openapi-example.json");
            JsonObject message = firstChoice(JsonParser.parseString(body).getAsJsonObject()).getAsJsonObject("message");
            assertTrue(message.get("refusal").isJsonNull(), "fixture must keep the documented null refusal");

            assertEquals(message.get("content").getAsString(), DirectApiService.parseOpenAiResponse(body));
        }

        @Test
        void nullContentAtTokenLimitNamesFinishReason() {
            // Derived from the documented example: content null and finish_reason "length", as when a reasoning
            // model spends the limit before writing. Both fields are typed nullable/enum in the published schema.
            JsonObject json = JsonParser.parseString(loadFixture("openai/chat-completion-openapi-example.json")).getAsJsonObject();
            JsonObject choice = firstChoice(json);
            choice.getAsJsonObject("message").add("content", com.google.gson.JsonNull.INSTANCE);
            choice.addProperty("finish_reason", "length");

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseOpenAiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("finish_reason: length"), ex.getMessage());
            assertTrue(ex.getMessage().contains("token limit"), ex.getMessage());
        }

        @Test
        void refusalIsReportedInsteadOfEmptyText() {
            // Derived from the documented example: the schema's refusal string set, content null.
            JsonObject json = JsonParser.parseString(loadFixture("openai/chat-completion-openapi-example.json")).getAsJsonObject();
            JsonObject message = firstChoice(json).getAsJsonObject("message");
            message.add("content", com.google.gson.JsonNull.INSTANCE);
            message.addProperty("refusal", "I can't help with that.");

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseOpenAiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("declined") && ex.getMessage().contains("I can't help with that."),
                    ex.getMessage());
        }

        @Test
        void blankRefusalFallsBackToFinishReason() {
            JsonObject json = JsonParser.parseString(loadFixture("openai/chat-completion-openapi-example.json")).getAsJsonObject();
            JsonObject message = firstChoice(json).getAsJsonObject("message");
            message.add("content", com.google.gson.JsonNull.INSTANCE);
            message.addProperty("refusal", "   ");

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseOpenAiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("finish_reason: stop"), ex.getMessage());
        }

        @Test
        void missingMessageAndFinishReasonIsAnApiError() {
            // Crash hardening: the error is an AiApiException naming "unknown", never a raw NPE/ClassCastException.
            JsonObject json = JsonParser.parseString(loadFixture("openai/chat-completion-openapi-example.json")).getAsJsonObject();
            JsonObject choice = firstChoice(json);
            choice.remove("message");
            choice.remove("finish_reason");

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseOpenAiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("finish_reason: unknown"), ex.getMessage());
        }

        @Test
        void nonObjectChoiceIsAnApiError() {
            JsonObject json = JsonParser.parseString(loadFixture("openai/chat-completion-openapi-example.json")).getAsJsonObject();
            json.getAsJsonArray("choices").set(0, new com.google.gson.JsonPrimitive("x"));

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseOpenAiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("Empty response"), ex.getMessage());
        }

        @Test
        void emptyStringContentIsAnError() {
            JsonObject json = JsonParser.parseString(loadFixture("openai/chat-completion-openapi-example.json")).getAsJsonObject();
            firstChoice(json).getAsJsonObject("message").addProperty("content", "");

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseOpenAiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("finish_reason: stop"), ex.getMessage());
        }

        @Test
        void emptyChoicesThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseOpenAiResponse("{\"choices\":[]}"));
            assertTrue(ex.getMessage().contains("Empty response"));
        }

        private JsonObject firstChoice(JsonObject json) {
            return json.getAsJsonArray("choices").get(0).getAsJsonObject();
        }
    }

    @Nested
    class GeminiStoreContract {
        // LIVE probes of where Google accepts the request-level store field (gemini/store/, see the fixtures README),
        // and the request bodies the capture script recorded next to the gemini/live/ responses.
        private JsonObject json(String path) {
            return JsonParser.parseString(loadFixture(path)).getAsJsonObject();
        }

        private int status(String path) {
            return Integer.parseInt(loadFixture(path).strip());
        }

        /** Every dotted path in {@code object} that ends in a field named {@code name}. */
        private java.util.Set<String> pathsOf(JsonObject object, String name, String prefix) {
            java.util.Set<String> paths = new java.util.TreeSet<>();
            for (var entry : object.entrySet()) {
                String path = prefix + entry.getKey();
                if (entry.getKey().equals(name)) paths.add(path);
                if (entry.getValue().isJsonObject()) paths.addAll(pathsOf(entry.getValue().getAsJsonObject(), name, path + "."));
            }
            return paths;
        }

        @Test
        void thePluginPutsStoreWhereGoogleAcceptedItAndNotWhereGoogleRejectedIt() throws Exception {
            JsonObject plugin = DirectApiServiceTest.bodyJson(DirectApiService.buildGeminiRequest("gemini-3.8-flash", "k", "p"));
            JsonObject accepted = json("gemini/store/accepted-top-level-store.request.json");
            JsonObject rejected = json("gemini/store/rejected-generationconfig-store.request.json");

            assertEquals(200, status("gemini/store/accepted-top-level-store.status"));
            assertEquals("ok", DirectApiService.parseGeminiResponse(loadFixture("gemini/store/accepted-top-level-store.response.json")));
            assertEquals(400, status("gemini/store/rejected-generationconfig-store.status"));
            JsonObject violation = json("gemini/store/rejected-generationconfig-store.response.json").getAsJsonObject("error")
                    .getAsJsonArray("details").get(0).getAsJsonObject().getAsJsonArray("fieldViolations").get(0).getAsJsonObject();
            assertEquals("generation_config", violation.get("field").getAsString());
            assertTrue(violation.get("description").getAsString().contains("Unknown name \"store\""), violation.toString());

            assertEquals(pathsOf(accepted, "store", ""), pathsOf(plugin, "store", ""));
            assertEquals(accepted.get("store"), plugin.get("store"));
            java.util.Set<String> rejectedPaths = pathsOf(rejected, "store", "");
            assertEquals(java.util.Set.of("generationConfig.store"), rejectedPaths);
            assertTrue(java.util.Collections.disjoint(rejectedPaths, pathsOf(plugin, "store", "")));
        }

        @Test
        void theLiveCapturesSentExactlyThePluginsBodyAndGotAReply() {
            for (String name : List.of("generatecontent-text", "generatecontent-max-tokens")) {
                JsonObject recorded = json("gemini/live/" + name + ".request.json");
                assertEquals(200, status("gemini/live/" + name + ".status"), name);
                String prompt = recorded.getAsJsonArray("contents").get(0).getAsJsonObject().getAsJsonArray("parts")
                        .get(0).getAsJsonObject().get("text").getAsString();
                JsonObject plugin = DirectApiServiceTest.bodyJson(DirectApiService.buildGeminiRequest("m", "k", prompt));
                // The token-limit case sends a tiny budget on purpose; everything else is the plugin's body.
                JsonObject expected = recorded.deepCopy();
                expected.getAsJsonObject("generationConfig").add("maxOutputTokens",
                        plugin.getAsJsonObject("generationConfig").get("maxOutputTokens"));
                assertEquals(expected, plugin, name + ": the capture script must send the plugin's body");
            }
        }
    }

    @Nested
    class GeminiParserContract {
        // LIVE captures (scripts/capture-gemini-generatecontent.py; see the fixtures README). Gemini 3.x thinks by
        // default: the reply's only part carries the text AND a thoughtSignature, and no thought part is returned
        // because the plugin doesn't ask for one.
        private JsonObject live(String fixture) {
            return JsonParser.parseString(loadFixture("gemini/live/" + fixture)).getAsJsonObject();
        }

        @Test
        void extractsTextFromLiveThinkingResponse() throws Exception {
            JsonObject json = live("generatecontent-text.json");
            JsonObject part = parts(json).get(0).getAsJsonObject();
            // Guard the capture's load-bearing shape: the text part also carries a thought signature.
            assertTrue(part.has("thoughtSignature"), "the live reply's text part carries a thoughtSignature");
            assertTrue(json.getAsJsonObject("usageMetadata").get("thoughtsTokenCount").getAsInt() > 0, "the model thought");

            String text = DirectApiService.parseGeminiResponse(json.toString());

            assertEquals(part.get("text").getAsString(), text);
            assertFalse(text.contains(part.get("thoughtSignature").getAsString()), "the signature is never returned as text");
        }

        @Test
        void liveTokenLimitResponseWithEmptyContentNamesMaxTokens() {
            JsonObject json = live("generatecontent-max-tokens.json");
            JsonObject candidate = json.getAsJsonArray("candidates").get(0).getAsJsonObject();
            // Guard the capture's load-bearing shape: thinking used the budget, so content is {} with no parts.
            assertEquals("MAX_TOKENS", candidate.get("finishReason").getAsString());
            assertFalse(candidate.getAsJsonObject("content").has("parts"), "the live content object has no parts");

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseGeminiResponse(json.toString()));

            assertTrue(ex.getMessage().contains("MAX_TOKENS"), ex.getMessage());
            assertTrue(ex.getMessage().contains("token limit was reached"), ex.getMessage());
        }

        @Test
        void extractsTextFromDocumentedShape() throws Exception {
            String text = DirectApiService.parseGeminiResponse(loadFixture("gemini/generatecontent-response.json"));
            assertEquals(
                    "AI works by learning patterns from large amounts of data, then using those patterns to make predictions or generate new content.",
                    text);
        }

        // Gemini's reference page has no example for these cases, so each one is derived from the documented
        // example above, changing only the fields the reference defines (Part.thought, FinishReason,
        // promptFeedback.blockReason).
        private JsonObject documented() {
            return JsonParser.parseString(loadFixture("gemini/generatecontent-response.json")).getAsJsonObject();
        }

        private JsonArray parts(JsonObject json) {
            return json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts");
        }

        @Test
        void concatenatesTextPartsAndSkipsThoughts() throws Exception {
            JsonObject json = documented();
            JsonArray parts = parts(json);
            String documentedText = parts.get(0).getAsJsonObject().get("text").getAsString();
            JsonObject thought = new JsonObject();
            thought.addProperty("text", "Planning the answer.");
            thought.addProperty("thought", true);
            JsonObject text = parts.get(0).getAsJsonObject();
            parts.set(0, thought);
            parts.add(new com.google.gson.JsonPrimitive("not a part"));
            parts.add(text);
            JsonObject notThought = text.deepCopy();
            notThought.addProperty("thought", false);
            parts.add(notThought);

            assertEquals(documentedText + documentedText, DirectApiService.parseGeminiResponse(json.toString()));
        }

        @Test
        void candidateWithoutContentNamesFinishReason() {
            JsonObject json = documented();
            JsonObject candidate = json.getAsJsonArray("candidates").get(0).getAsJsonObject();
            candidate.remove("content");
            candidate.addProperty("finishReason", "MAX_TOKENS");

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseGeminiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("finishReason: MAX_TOKENS"), ex.getMessage());
            assertTrue(ex.getMessage().contains("token limit"), ex.getMessage());
        }

        @Test
        void nonObjectContentNamesFinishReason() {
            JsonObject json = documented();
            JsonObject candidate = json.getAsJsonArray("candidates").get(0).getAsJsonObject();
            candidate.add("content", new JsonArray());

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseGeminiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("finishReason: STOP"), ex.getMessage());
        }

        @Test
        void onlyThoughtPartsIsAnError() {
            JsonObject json = documented();
            parts(json).get(0).getAsJsonObject().addProperty("thought", true);
            json.getAsJsonArray("candidates").get(0).getAsJsonObject().addProperty("finishReason", "SAFETY");

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseGeminiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("finishReason: SAFETY"), ex.getMessage());
        }

        @Test
        void blockedPromptNamesBlockReason() {
            // A blocked prompt returns no candidates and sets promptFeedback.blockReason.
            JsonObject json = documented();
            json.remove("candidates");
            JsonObject feedback = new JsonObject();
            feedback.addProperty("blockReason", "SAFETY");
            json.add("promptFeedback", feedback);

            AiApiException ex = assertThrows(AiApiException.class, () -> DirectApiService.parseGeminiResponse(json.toString()));
            assertTrue(ex.getMessage().contains("blockReason: SAFETY"), ex.getMessage());
        }

        @Test
        void emptyCandidatesThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseGeminiResponse("{\"candidates\":[]}"));
            assertTrue(ex.getMessage().contains("Empty response"));
        }
    }
}
