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
 * Contract tests for the three Direct-API response parsers. Claude is driven by live-captured
 * responses; OpenAI and Gemini by each provider's published example response shape (see
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
        void emptyChoicesThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseOpenAiResponse("{\"choices\":[]}"));
            assertTrue(ex.getMessage().contains("Empty response"));
        }
    }

    @Nested
    class GeminiParserContract {
        @Test
        void extractsTextFromDocumentedShape() throws Exception {
            String text = DirectApiService.parseGeminiResponse(loadFixture("gemini/generatecontent-response.json"));
            assertEquals(
                    "AI works by learning patterns from large amounts of data, then using those patterns to make predictions or generate new content.",
                    text);
        }

        @Test
        void emptyCandidatesThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseGeminiResponse("{\"candidates\":[]}"));
            assertTrue(ex.getMessage().contains("Empty response"));
        }
    }
}
