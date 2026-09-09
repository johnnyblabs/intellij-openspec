package com.johnnyblabs.openspec.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AiModelOptions#resolve} — the pure model-selector logic behind the settings
 * Test action's populate step. Extracting it off the Swing panel is what makes these assertions
 * possible without a live dialog. Each test pins a behavior a laxer check would miss (fallback to the
 * exact static list, preservation vs default-to-first, editable override survival).
 */
class DirectApiModelSelectionTest {

    @Test
    void fetchFailure_fallsBackToTheStaticListSpecifically() {
        List<String> fallback = AiProvider.OPENAI.getModels();
        AiModelOptions.Resolution r = AiModelOptions.resolve(null, false, fallback, "gpt-4o");
        // Equals the static list specifically — a non-empty-but-wrong list would pass a laxer check.
        assertEquals(fallback, r.options());
    }

    @Test
    void emptyFetch_fallsBackToTheStaticList() {
        List<String> fallback = AiProvider.GEMINI.getModels();
        // fetchOk=true but an empty result is still a fallback trigger (nothing to populate with).
        AiModelOptions.Resolution r = AiModelOptions.resolve(List.of(), true, fallback, "");
        assertEquals(fallback, r.options());
        assertEquals(fallback.get(0), r.selection());
    }

    @Test
    void successfulFetch_preservesCurrentSelection_evenWhenNotFirst() {
        List<String> fetched = List.of("model-a", "model-b", "model-c");
        AiModelOptions.Resolution r = AiModelOptions.resolve(fetched, true, List.of("static"), "model-b");
        assertEquals(fetched, r.options());
        assertEquals("model-b", r.selection());
        // The trap: a test where current == first can't tell preservation from "default to first".
        assertNotEquals(fetched.get(0), r.selection());
    }

    @Test
    void typedInModelNotInList_isPreservedAndKept() {
        List<String> fetched = List.of("model-a", "model-b");
        AiModelOptions.Resolution r = AiModelOptions.resolve(fetched, true, List.of(), "my-custom-model");
        // Editable-override guarantee: the typed model is selected AND survives in the options,
        // not silently replaced by the first fetched model.
        assertEquals("my-custom-model", r.selection());
        assertTrue(r.options().contains("my-custom-model"));
        assertEquals("my-custom-model", r.options().get(0));
    }

    @Test
    void emptyCurrentSelection_defaultsToFirstFetched() {
        List<String> fetched = List.of("model-a", "model-b");
        AiModelOptions.Resolution r = AiModelOptions.resolve(fetched, true, List.of("static"), "");
        assertEquals("model-a", r.selection());
    }

    @Test
    void nullArgs_yieldEmptyOptionsAndSelection_withoutThrowing() {
        // Defensive branches: a failed fetch with a null fallback and null current selection must not
        // NPE — it resolves to nothing rather than crashing the Test handler.
        AiModelOptions.Resolution r = AiModelOptions.resolve(null, false, null, null);
        assertTrue(r.options().isEmpty());
        assertEquals("", r.selection());
    }
}
