package com.johnnyblabs.openspec.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The requirement-header line locator used to jump the editor caret onto the selected requirement.
 * Must be fence-aware (mirror the parser) so a {@code ### Requirement:} inside a code fence is not
 * treated as a header — otherwise navigation would land on the wrong line.
 */
class RequirementLineFinderTest {

    private static final String SPEC = String.join("\n",
            "# Greeting",            // 0
            "",                       // 1
            "## Requirements",        // 2
            "",                       // 3
            "### Requirement: Alpha", // 4
            "Body of alpha.",         // 5
            "",                       // 6
            "### Requirement: Beta",  // 7
            "Body of beta.",          // 8
            "");

    @Test
    void findsTheFirstRequirementHeaderLine() {
        assertEquals(4, RequirementLineFinder.findRequirementLine(SPEC, "Alpha"));
    }

    @Test
    void findsALaterRequirementHeaderLine() {
        assertEquals(7, RequirementLineFinder.findRequirementLine(SPEC, "Beta"));
    }

    @Test
    void ignoresRequirementHeaderInsideACodeFence() {
        // The only "### Requirement: Gamma" is fenced — it is NOT a real header, so the finder must
        // not match it and returns 0 (top of file) rather than the fenced line.
        String fenced = String.join("\n",
                "# Doc",                     // 0
                "",                           // 1
                "```",                        // 2  fence open
                "### Requirement: Gamma",     // 3  fenced — not a header
                "```",                        // 4  fence close
                "");
        assertEquals(0, RequirementLineFinder.findRequirementLine(fenced, "Gamma"));
    }

    @Test
    void tildeFenceAlsoMasks() {
        String fenced = String.join("\n",
                "# Doc",                     // 0
                "~~~",                        // 1  tilde fence open
                "### Requirement: Delta",     // 2  fenced
                "~~~",                        // 3  close
                "### Requirement: Delta",     // 4  the real one
                "");
        assertEquals(4, RequirementLineFinder.findRequirementLine(fenced, "Delta"));
    }

    @Test
    void singleLineBacktickCodeDoesNotHideLaterRequirements() {
        // CLI 1.16 parity: ```npm test``` is inline code, so Epsilon below is a real header, not fenced text.
        String doc = String.join("\n",
                "# Doc",                          // 0
                "```npm test```",                 // 1  inline code, not a fence
                "### Requirement: Epsilon",       // 2  the real one
                "");
        assertEquals(2, RequirementLineFinder.findRequirementLine(doc, "Epsilon"));
    }

    @Test
    void unknownRequirementReturnsTopOfFile() {
        assertEquals(0, RequirementLineFinder.findRequirementLine(SPEC, "Missing"));
    }

    @Test
    void nullInputsReturnZero() {
        assertEquals(0, RequirementLineFinder.findRequirementLine(null, "x"));
        assertEquals(0, RequirementLineFinder.findRequirementLine("x", null));
    }
}
