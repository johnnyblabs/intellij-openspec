package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model-not-listed check ({@link AiModelOptions#notListed}) and page completeness
 * ({@link DirectApiService#pageComplete}), over the captured provider lists in {@code fixtures/ai/} parsed by each
 * provider's real parser. The live Gemini list is untrimmed (65 models); the OpenAI list is the documented example,
 * with placeholder ids, so its case checks only the membership mechanics. The partial-page cases add the documented
 * page marker to a captured response; no edited fixture is committed.
 */
class AiModelNotListedTest {

    private static String fixture(String path) {
        try (InputStream in = AiModelNotListedTest.class.getResourceAsStream("/fixtures/ai/" + path)) {
            assertNotNull(in, "fixture not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final List<String> GEMINI_LIVE = DirectApiService.parseGeminiModels(fixture("gemini/live/models-list.json"));
    private static final List<String> GEMINI_DOC = DirectApiService.parseGeminiModels(fixture("gemini/models-list.json"));
    private static final List<String> CLAUDE_LIVE = DirectApiService.parseAnthropicModels(fixture("claude/models-list-live.json"));
    private static final List<String> OPENAI_DOC = DirectApiService.parseOpenAiModels(fixture("openai/models-list.json"));

    private static boolean notListed(AiProvider p, String model, List<String> list) {
        return AiModelOptions.notListed(p, model, list, true, true);
    }

    // --- membership ---

    @Test
    void aModelFromEachListIsListed() {
        assertFalse(notListed(AiProvider.GEMINI, GEMINI_LIVE.get(0), GEMINI_LIVE));
        assertFalse(notListed(AiProvider.CLAUDE, CLAUDE_LIVE.get(0), CLAUDE_LIVE));
        assertFalse(notListed(AiProvider.OPENAI, OPENAI_DOC.get(1), OPENAI_DOC));
    }

    @Test
    void geminiLatestAliasIsInTheLiveList() {
        assertTrue(GEMINI_LIVE.contains("gemini-flash-latest"), "sanity: the live capture lists the alias");
        assertFalse(notListed(AiProvider.GEMINI, "gemini-flash-latest", GEMINI_LIVE));
    }

    @Test
    void aModelMissingFromTheListIsNotListed() {
        assertFalse(GEMINI_DOC.contains("gemini-2.5-pro"), "sanity: the trimmed list lacks it");
        assertTrue(notListed(AiProvider.GEMINI, "gemini-2.5-pro", GEMINI_DOC));
        assertTrue(notListed(AiProvider.OPENAI, "model-id-9", OPENAI_DOC));
    }

    @Test
    void geminiPrefixComparesEqual() {
        assertFalse(notListed(AiProvider.GEMINI, "models/gemini-3.8-flash", GEMINI_LIVE));
        assertFalse(notListed(AiProvider.GEMINI, "  gemini-3.8-flash ", GEMINI_LIVE));
    }

    @Test
    void claudeAliasIsListedThroughItsDatedSnapshot() {
        assertTrue(CLAUDE_LIVE.contains("claude-sonnet-4-5-20250929"), "sanity: the live capture lists the dated id");
        assertFalse(CLAUDE_LIVE.contains("claude-sonnet-4-5"), "sanity: and not the alias");
        assertFalse(notListed(AiProvider.CLAUDE, "claude-sonnet-4-5", CLAUDE_LIVE));
    }

    @Test
    void claudeSnapshotRuleNeedsTheWholeDatedSuffix() {
        // claude-sonnet-4 is a prefix of claude-sonnet-4-5-20250929, but "-5-20250929" is not a dated suffix.
        assertTrue(notListed(AiProvider.CLAUDE, "claude-sonnet-4", CLAUDE_LIVE));
    }

    @Test
    void theSnapshotRuleIsClaudeOnly() {
        List<String> dated = List.of("gpt-5-20250807");
        assertTrue(notListed(AiProvider.OPENAI, "gpt-5", dated));
    }

    // --- guards ---

    @Test
    void noHintWithoutASuccessfulCompleteNonEmptyFetchOrAModel() {
        assertFalse(AiModelOptions.notListed(AiProvider.GEMINI, "gemini-2.5-pro", GEMINI_DOC, false, true), "failed fetch");
        assertFalse(AiModelOptions.notListed(AiProvider.GEMINI, "gemini-2.5-pro", GEMINI_DOC, true, false), "partial page");
        assertFalse(AiModelOptions.notListed(AiProvider.GEMINI, "gemini-2.5-pro", List.of(), true, true), "empty list");
        assertFalse(AiModelOptions.notListed(AiProvider.GEMINI, "gemini-2.5-pro", null, true, true), "no list");
        assertFalse(AiModelOptions.notListed(AiProvider.GEMINI, "  ", GEMINI_DOC, true, true), "blank model");
        assertFalse(AiModelOptions.notListed(AiProvider.GEMINI, null, GEMINI_DOC, true, true), "no model");
    }

    // --- page completeness ---

    @Test
    void capturedListsAreComplete() {
        assertTrue(DirectApiService.pageComplete(AiProvider.GEMINI, fixture("gemini/live/models-list.json")));
        assertTrue(DirectApiService.pageComplete(AiProvider.CLAUDE, fixture("claude/models-list-live.json")),
                "the live capture carries has_more: false");
        assertTrue(DirectApiService.pageComplete(AiProvider.OPENAI, fixture("openai/models-list.json")));
    }

    @Test
    void aPageMarkerMeansAPartialList() {
        JsonObject gemini = JsonParser.parseString(fixture("gemini/live/models-list.json")).getAsJsonObject();
        gemini.addProperty("nextPageToken", "next");
        assertFalse(DirectApiService.pageComplete(AiProvider.GEMINI, gemini.toString()));
        gemini.addProperty("nextPageToken", "");
        assertTrue(DirectApiService.pageComplete(AiProvider.GEMINI, gemini.toString()), "a blank token is the last page");

        JsonObject claude = JsonParser.parseString(fixture("claude/models-list-live.json")).getAsJsonObject();
        assertEquals(false, claude.get("has_more").getAsBoolean(), "sanity: the capture has the field");
        claude.addProperty("has_more", true);
        assertFalse(DirectApiService.pageComplete(AiProvider.CLAUDE, claude.toString()));
    }

    @Test
    void aMarkerOfAnotherTypeCountsAsPartialWithoutFailingTheFetch() {
        JsonObject gemini = JsonParser.parseString(fixture("gemini/live/models-list.json")).getAsJsonObject();
        gemini.add("nextPageToken", new JsonObject());
        assertFalse(DirectApiService.pageComplete(AiProvider.GEMINI, gemini.toString()));
        JsonObject claude = JsonParser.parseString(fixture("claude/models-list-live.json")).getAsJsonObject();
        claude.add("has_more", new JsonObject());
        assertFalse(DirectApiService.pageComplete(AiProvider.CLAUDE, claude.toString()));
        claude.addProperty("has_more", "false");
        assertFalse(DirectApiService.pageComplete(AiProvider.CLAUDE, claude.toString()), "a string isn't the boolean");
    }
}
