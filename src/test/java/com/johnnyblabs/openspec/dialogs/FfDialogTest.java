package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.toolwindow.WorkflowActionPanel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FfDialogTest {

    @Test
    void deriveKebabName_simpleDescription() {
        assertEquals("add-user-auth", WorkflowActionPanel.deriveKebabName("add user auth"));
    }

    @Test
    void deriveKebabName_withPunctuation() {
        assertEquals("fix-login-redirect", WorkflowActionPanel.deriveKebabName("Fix login redirect!"));
    }

    @Test
    void deriveKebabName_longDescription_truncates() {
        assertEquals("add-a-very-long-description",
                WorkflowActionPanel.deriveKebabName("add a very long description that should be truncated"));
    }

    @Test
    void deriveKebabName_emptyString() {
        assertEquals("unnamed-change", WorkflowActionPanel.deriveKebabName(""));
    }

    @Test
    void deriveKebabName_null() {
        assertEquals("unnamed-change", WorkflowActionPanel.deriveKebabName(null));
    }

    @Test
    void deriveKebabName_alreadyKebab() {
        assertEquals("my-change", WorkflowActionPanel.deriveKebabName("my-change"));
    }

    @Test
    void deriveKebabName_mixedCase() {
        assertEquals("add-user-authentication", WorkflowActionPanel.deriveKebabName("Add User Authentication"));
    }

    @Test
    void deriveKebabName_extraWhitespace() {
        assertEquals("fix-the-bug", WorkflowActionPanel.deriveKebabName("  fix  the  bug  "));
    }
}
