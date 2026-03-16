package com.johnnyblabs.openspec.dialogs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FfDialogTest {

    @Test
    void deriveKebabName_simpleDescription() {
        assertEquals("add-user-auth", FfDialog.deriveKebabName("add user auth"));
    }

    @Test
    void deriveKebabName_withPunctuation() {
        assertEquals("fix-login-redirect", FfDialog.deriveKebabName("Fix login redirect!"));
    }

    @Test
    void deriveKebabName_longDescription_truncates() {
        assertEquals("add-a-very-long-description",
                FfDialog.deriveKebabName("add a very long description that should be truncated"));
    }

    @Test
    void deriveKebabName_emptyString() {
        assertEquals("unnamed-change", FfDialog.deriveKebabName(""));
    }

    @Test
    void deriveKebabName_null() {
        assertEquals("unnamed-change", FfDialog.deriveKebabName(null));
    }

    @Test
    void deriveKebabName_alreadyKebab() {
        assertEquals("my-change", FfDialog.deriveKebabName("my-change"));
    }

    @Test
    void deriveKebabName_mixedCase() {
        assertEquals("add-user-authentication", FfDialog.deriveKebabName("Add User Authentication"));
    }

    @Test
    void deriveKebabName_extraWhitespace() {
        assertEquals("fix-the-bug", FfDialog.deriveKebabName("  fix  the  bug  "));
    }
}
