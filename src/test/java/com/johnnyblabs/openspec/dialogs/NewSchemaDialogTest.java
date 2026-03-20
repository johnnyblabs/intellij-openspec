package com.johnnyblabs.openspec.dialogs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NewSchemaDialogTest {

    @Test
    void validKebabCase_simple() {
        assertTrue(NewSchemaDialog.isValidKebabCase("my-schema"));
    }

    @Test
    void validKebabCase_singleWord() {
        assertTrue(NewSchemaDialog.isValidKebabCase("schema"));
    }

    @Test
    void validKebabCase_withNumbers() {
        assertTrue(NewSchemaDialog.isValidKebabCase("schema-v2"));
    }

    @Test
    void validKebabCase_multipleSegments() {
        assertTrue(NewSchemaDialog.isValidKebabCase("my-custom-schema"));
    }

    @Test
    void invalidKebabCase_blank() {
        assertFalse(NewSchemaDialog.isValidKebabCase(""));
    }

    @Test
    void invalidKebabCase_null() {
        assertFalse(NewSchemaDialog.isValidKebabCase(null));
    }

    @Test
    void invalidKebabCase_uppercase() {
        assertFalse(NewSchemaDialog.isValidKebabCase("My-Schema"));
    }

    @Test
    void invalidKebabCase_spaces() {
        assertFalse(NewSchemaDialog.isValidKebabCase("my schema"));
    }

    @Test
    void invalidKebabCase_underscores() {
        assertFalse(NewSchemaDialog.isValidKebabCase("my_schema"));
    }

    @Test
    void invalidKebabCase_startsWithNumber() {
        assertFalse(NewSchemaDialog.isValidKebabCase("1-schema"));
    }

    @Test
    void invalidKebabCase_startsWithHyphen() {
        assertFalse(NewSchemaDialog.isValidKebabCase("-schema"));
    }

    @Test
    void invalidKebabCase_endsWithHyphen() {
        assertFalse(NewSchemaDialog.isValidKebabCase("schema-"));
    }

    @Test
    void invalidKebabCase_doubleHyphen() {
        assertFalse(NewSchemaDialog.isValidKebabCase("my--schema"));
    }
}
