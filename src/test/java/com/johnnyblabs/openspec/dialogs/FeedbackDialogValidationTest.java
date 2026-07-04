package com.johnnyblabs.openspec.dialogs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the pure validation rule that {@code FeedbackDialog.doValidate()} delegates to:
 * an empty message blocks submission, so no CLI call can happen for an empty message.
 */
class FeedbackDialogValidationTest {

    @Test
    void emptyMessageBlocksSubmission() {
        assertNotNull(FeedbackDialog.validateMessage(""));
        assertNotNull(FeedbackDialog.validateMessage("   "));
        assertNotNull(FeedbackDialog.validateMessage(null));
    }

    @Test
    void nonEmptyMessageIsSubmittable() {
        assertNull(FeedbackDialog.validateMessage("The schema tooling is great"));
    }
}
