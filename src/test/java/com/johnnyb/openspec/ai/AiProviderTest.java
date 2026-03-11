package com.johnnyb.openspec.ai;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderTest {

    @Nested
    class DisplayNames {

        @Test
        void providerDisplayNames() {
            assertEquals("None", AiProvider.NONE.getDisplayName());
            assertEquals("Claude", AiProvider.CLAUDE.getDisplayName());
            assertEquals("OpenAI", AiProvider.OPENAI.getDisplayName());
            assertEquals("Gemini", AiProvider.GEMINI.getDisplayName());
        }
    }

    @Nested
    class FromString {

        @Test
        void parsesDisplayName() {
            assertEquals(AiProvider.CLAUDE, AiProvider.fromString("Claude"));
            assertEquals(AiProvider.OPENAI, AiProvider.fromString("OpenAI"));
            assertEquals(AiProvider.GEMINI, AiProvider.fromString("Gemini"));
            assertEquals(AiProvider.NONE, AiProvider.fromString("None"));
        }

        @Test
        void parsesEnumName() {
            assertEquals(AiProvider.CLAUDE, AiProvider.fromString("CLAUDE"));
            assertEquals(AiProvider.OPENAI, AiProvider.fromString("OPENAI"));
            assertEquals(AiProvider.GEMINI, AiProvider.fromString("GEMINI"));
            assertEquals(AiProvider.NONE, AiProvider.fromString("NONE"));
        }

        @Test
        void caseInsensitive() {
            assertEquals(AiProvider.CLAUDE, AiProvider.fromString("claude"));
            assertEquals(AiProvider.OPENAI, AiProvider.fromString("openai"));
            assertEquals(AiProvider.GEMINI, AiProvider.fromString("gemini"));
        }

        @Test
        void handlesWhitespace() {
            assertEquals(AiProvider.CLAUDE, AiProvider.fromString("  Claude  "));
            assertEquals(AiProvider.OPENAI, AiProvider.fromString("  OPENAI  "));
        }

        @Test
        void nullReturnsNone() {
            assertEquals(AiProvider.NONE, AiProvider.fromString(null));
        }

        @Test
        void emptyReturnsNone() {
            assertEquals(AiProvider.NONE, AiProvider.fromString(""));
            assertEquals(AiProvider.NONE, AiProvider.fromString("   "));
        }

        @Test
        void unknownReturnsNone() {
            assertEquals(AiProvider.NONE, AiProvider.fromString("Unknown Provider"));
            assertEquals(AiProvider.NONE, AiProvider.fromString("gpt-4o"));
        }
    }

    @Nested
    class Models {

        @Test
        void noneHasNoModels() {
            assertTrue(AiProvider.NONE.getModels().isEmpty());
            assertEquals("", AiProvider.NONE.getDefaultModel());
        }

        @Test
        void claudeHasModels() {
            assertFalse(AiProvider.CLAUDE.getModels().isEmpty());
            assertNotNull(AiProvider.CLAUDE.getDefaultModel());
            assertFalse(AiProvider.CLAUDE.getDefaultModel().isEmpty());
        }

        @Test
        void openaiHasModels() {
            assertFalse(AiProvider.OPENAI.getModels().isEmpty());
            assertNotNull(AiProvider.OPENAI.getDefaultModel());
        }

        @Test
        void geminiHasModels() {
            assertFalse(AiProvider.GEMINI.getModels().isEmpty());
            assertNotNull(AiProvider.GEMINI.getDefaultModel());
        }

        @Test
        void defaultModelIsFirstInList() {
            for (AiProvider p : AiProvider.values()) {
                if (!p.getModels().isEmpty()) {
                    assertEquals(p.getModels().get(0), p.getDefaultModel(),
                            p.name() + " default model should be first in list");
                }
            }
        }
    }
}
