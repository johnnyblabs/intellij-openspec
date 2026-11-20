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

    @Nested
    class DisabledDescription {

        @Test
        void onTheCoreProfileACustomProfileIsWhatWouldEnableIt() {
            assertEquals("Requires custom profile. Change in Settings \u2192 Tools \u2192 OpenSpec.",
                    OpenSpecBaseAction.disabledDescription(false));
        }

        @Test
        void onTheCustomProfileTheWorkflowIsNotInTheListAndPointsToCustomizeWorkflows() {
            String text = OpenSpecBaseAction.disabledDescription(true);
            assertFalse(text.contains("Requires custom profile"), text);
            assertTrue(text.startsWith("Not in your custom profile."), text);
            assertTrue(text.contains("Customize workflows"), text);
        }

        @Test
        void onTheCustomProfileTheSuffixIsNotAppended() {
            assertEquals("Revise", OpenSpecBaseAction.applyCustomProfileSuffix("Revise", false));
        }
    }
}
