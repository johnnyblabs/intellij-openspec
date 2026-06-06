package com.johnnyblabs.openspec.actions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenSpecBaseActionTest {

    @Nested
    class ApplyCustomProfileSuffix {

        @Test
        void appendsSuffix_whenProfileGatedDisabled() {
            assertEquals("Continue (custom)",
                    OpenSpecBaseAction.applyCustomProfileSuffix("Continue", true));
        }

        @Test
        void stripsSuffix_whenNotProfileGated() {
            assertEquals("Continue",
                    OpenSpecBaseAction.applyCustomProfileSuffix("Continue (custom)", false));
        }

        @Test
        void leavesUnsuffixedTextAlone_whenNotProfileGated() {
            assertEquals("Continue",
                    OpenSpecBaseAction.applyCustomProfileSuffix("Continue", false));
        }

        @Test
        void doesNotDoubleAppend_whenAlreadySuffixed() {
            assertEquals("Continue (custom)",
                    OpenSpecBaseAction.applyCustomProfileSuffix("Continue (custom)", true));
        }

        @Test
        void handlesNullText() {
            assertNull(OpenSpecBaseAction.applyCustomProfileSuffix(null, true));
            assertNull(OpenSpecBaseAction.applyCustomProfileSuffix(null, false));
        }
    }

    @Nested
    class StripCustomProfileSuffix {

        @Test
        void stripsSuffix_whenPresent() {
            assertEquals("Verify",
                    OpenSpecBaseAction.stripCustomProfileSuffix("Verify (custom)"));
        }

        @Test
        void leavesUnsuffixedTextAlone() {
            assertEquals("Verify",
                    OpenSpecBaseAction.stripCustomProfileSuffix("Verify"));
        }

        @Test
        void handlesNull() {
            assertNull(OpenSpecBaseAction.stripCustomProfileSuffix(null));
        }

        @Test
        void doesNotStripPartialMatch() {
            // The suffix must match exactly — "(custom-foo)" should not be stripped.
            assertEquals("Action (custom-foo)",
                    OpenSpecBaseAction.stripCustomProfileSuffix("Action (custom-foo)"));
        }
    }
}
