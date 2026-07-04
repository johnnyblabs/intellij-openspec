package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared requirement-header pattern: case-insensitive on the header token
 * (OpenSpec CLI 1.4+ parity), line-anchored, canonical on write.
 */
class SpecPatternsTest {

    @Test
    void canonicalHeader_matches() {
        assertEquals("User login", matchName("### Requirement: User login"));
    }

    @Test
    void lowercaseToken_matches() {
        assertEquals("User login", matchName("### requirement: User login"));
    }

    @Test
    void uppercaseToken_matches() {
        assertEquals("User login", matchName("### REQUIREMENT: User login"));
    }

    @Test
    void mixedCaseToken_matches() {
        assertEquals("User login", matchName("### ReQuIrEmEnT: User login"));
    }

    @Test
    void requirementNameCasing_isPreserved() {
        assertEquals("The System SHALL Log In", matchName("### requirement: The System SHALL Log In"));
    }

    @Test
    void multipleSpacesAfterHashes_match() {
        assertEquals("User login", matchName("### \t requirement: User login"));
    }

    @Test
    void midLineToken_doesNotMatch() {
        assertNull(matchName("Prose mentioning ### Requirement: inline"));
    }

    @Test
    void indentedHeader_doesNotMatch() {
        Matcher m = SpecPatterns.REQUIREMENT_HEADER.matcher("  ### Requirement: Indented");
        assertFalse(m.find(), "^### anchoring must reject leading whitespace");
    }

    @Test
    void wrongHeadingLevel_doesNotMatch() {
        assertNull(matchName("## Requirement: Wrong level"));
        assertNull(matchName("#### Requirement: Wrong level"));
    }

    @Test
    void scenarioHeader_doesNotMatch() {
        assertNull(matchName("#### Scenario: Not a requirement"));
    }

    @Test
    void multilineText_matchesEachHeaderLine() {
        String text = """
                # Spec

                ### Requirement: First
                Body.

                ### requirement: Second
                Body.
                """;
        Matcher m = SpecPatterns.REQUIREMENT_HEADER.matcher(text);
        assertTrue(m.find());
        assertEquals("First", m.group(1).trim());
        assertTrue(m.find());
        assertEquals("Second", m.group(1).trim());
        assertFalse(m.find());
    }

    @Test
    void crlfContent_capturesNameWithoutCarriageReturn() {
        String text = "# Spec\r\n\r\n### requirement: Windows line\r\nBody.\r\n";
        Matcher m = SpecPatterns.REQUIREMENT_HEADER.matcher(text);
        assertTrue(m.find());
        assertEquals("Windows line", m.group(1).trim());
    }

    @Test
    void requirementName_firstLineOfMultilineText() {
        assertEquals("Header", SpecPatterns.requirementName("### requirement: Header\nrest of element text"));
        assertNull(SpecPatterns.requirementName("prose first line\n### Requirement: later"));
        assertNull(SpecPatterns.requirementName(null));
        assertNull(SpecPatterns.requirementName("   ### Requirement: indented"));
    }

    @Test
    void canonicalPrefix_isTheWriteForm() {
        assertEquals("### Requirement: ", SpecPatterns.CANONICAL_HEADER_PREFIX);
        assertNotNull(SpecPatterns.requirementName(SpecPatterns.CANONICAL_HEADER_PREFIX + "Round trip"));
    }

    private static String matchName(String line) {
        Matcher m = SpecPatterns.REQUIREMENT_HEADER.matcher(line);
        return m.find() ? m.group(1).trim() : null;
    }
}
