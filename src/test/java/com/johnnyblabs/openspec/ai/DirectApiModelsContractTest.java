package com.johnnyblabs.openspec.ai;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the three list-models parsers, driven by captured/documented provider example
 * responses under {@code fixtures/ai/<provider>/models-list.json} (see that dir's README for
 * provenance: Gemini live-captured, OpenAI/Anthropic doc-sourced pending a keyed re-capture). Expected
 * values are read from the committed fixtures — real contract checks against the providers' stated
 * shapes, not the hand-authored inline literals the repo's contract-test discipline forbids.
 */
class DirectApiModelsContractTest {

    private static String loadFixture(String path) {
        try (InputStream in = DirectApiModelsContractTest.class.getResourceAsStream("/fixtures/ai/" + path)) {
            assertNotNull(in, "fixture not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to load fixture " + path, e);
        }
    }

    @Nested
    class OpenAiModels {
        @Test
        void extractsAllIdsFromDocumentedShape() {
            List<String> ids = DirectApiService.parseOpenAiModels(loadFixture("openai/models-list.json"));
            // Full-set equality — a single .contains() would still pass if the parser silently dropped
            // entries or read the wrong field.
            assertEquals(List.of("model-id-0", "model-id-1", "model-id-2"), ids);
        }

        @Test
        void emptyDataYieldsEmptyList() {
            assertTrue(DirectApiService.parseOpenAiModels("{\"data\":[]}").isEmpty());
        }
    }

    @Nested
    class AnthropicModels {
        @Test
        void extractsIdNotDisplayNameFromDocumentedShape() {
            List<String> ids = DirectApiService.parseAnthropicModels(loadFixture("claude/models-list.json"));
            assertEquals(List.of("claude-opus-5"), ids);
            // Load-bearing: the parser must read `id`, never `display_name` — a wrong-field read would
            // surface "Claude Opus 5" here yet still pass a size-only or loose-substring check.
            assertFalse(ids.contains("Claude Opus 5"), "must read id, not display_name");
        }

        @Test
        void emptyDataYieldsEmptyList() {
            assertTrue(DirectApiService.parseAnthropicModels("{\"data\":[]}").isEmpty());
        }
    }

    @Nested
    class GeminiModels {
        // Fixture is a verbatim subset of a LIVE capture (see README): 3 generateContent models +
        // 3 negatives (an embedding, an aqa/generateAnswer, and a bidiGenerateContent model).
        @Test
        void keepsOnlyGenerateContentModels_stripsModelsPrefix() {
            List<String> ids = DirectApiService.parseGeminiModels(loadFixture("gemini/models-list.json"));

            // Full filtered+stripped set — the strongest form (order preserved from the response).
            assertEquals(List.of("gemini-2.5-flash", "gemini-3.5-flash", "gemini-3.8-flash"), ids);

            // Strip proven both ways: the bare id is present AND the models/ form is not (asserting only
            // the bare form would leave the strip untested if the source id were already bare).
            assertTrue(ids.contains("gemini-2.5-flash"));
            assertFalse(ids.contains("models/gemini-2.5-flash"), "the models/ prefix must be stripped");
        }

        @Test
        void dropsNonGenerateContentModels() {
            List<String> ids = DirectApiService.parseGeminiModels(loadFixture("gemini/models-list.json"));
            // Load-bearing negative branch: an embedding model and an aqa model must be filtered out.
            assertFalse(ids.contains("gemini-embedding-001"), "embedding model must be dropped");
            assertFalse(ids.contains("models/gemini-embedding-001"));
            assertFalse(ids.contains("aqa"), "generateAnswer-only model must be dropped");
            // A third real negative: a model exposing only bidiGenerateContent (live audio) is not
            // generateContent and must be dropped. (Not a substring-trap guard — the filter uses
            // .equals, and "bidiGenerateContent" does not even contain the lowercase literal
            // "generateContent"; the capital G breaks it. It is simply one more genuine non-match.)
            assertFalse(ids.contains("gemini-3.5-transcribe-live"),
                    "a bidiGenerateContent-only model must be dropped");
        }

        @Test
        void emptyModelsYieldsEmptyList() {
            assertTrue(DirectApiService.parseGeminiModels("{\"models\":[]}").isEmpty());
        }
    }
}
