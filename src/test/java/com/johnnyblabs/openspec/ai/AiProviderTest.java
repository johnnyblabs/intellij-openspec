package com.johnnyblabs.openspec.ai;

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
        void claudeDefaultIsDatelessAlias() {
            // C1 regression guard. The prior default `claude-sonnet-4-5-20250514` was a fabricated
            // dated ID that 404'd; the old test only checked a `claude-` prefix, so it passed.
            // Assert the structural invariant (dateless family alias) without pinning an exact
            // minor version — a legitimate `claude-sonnet-4-6` refresh must stay green.
            String def = AiProvider.CLAUDE.getDefaultModel();
            assertFalse(def.isBlank());
            assertTrue(def.matches("^claude-(sonnet|opus|haiku)-\\d+(-\\d+)?$"),
                    "Claude default must be a dateless alias claude-<family>-<major>[-<minor>], was: " + def);
            assertFalse(def.matches(".*-\\d{8}$"),
                    "Claude default must not carry an 8-digit snapshot date (the C1 fabrication bug): " + def);
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
        void geminiDefaultIsNotARetiringModel() {
            // Gemini 2.5 access is limited to recent users, and its defaults 404'd on fresh AI Studio
            // keys. gemini-3.5-flash is deprecated (2026-10-08) and silently routes to gemini-3.6-flash.
            // Guard the whole list without pinning exact versions, so a legitimate refresh stays green.
            String def = AiProvider.GEMINI.getDefaultModel();
            assertTrue(def.startsWith("gemini-"), "Gemini default must be a gemini- model: " + def);
            for (String model : AiProvider.GEMINI.getModels()) {
                assertFalse(model.startsWith("gemini-2.5") || model.equals("gemini-3.5-flash"),
                        "Gemini fallback list must not carry a deprecated model: " + model);
            }
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
