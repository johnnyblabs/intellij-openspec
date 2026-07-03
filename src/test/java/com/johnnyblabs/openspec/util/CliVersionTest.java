package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliVersionTest {

    // ---- below (upper-bound companion to atLeast) ----------------------------

    @Test
    void below_trueWhenStrictlyLessThanCeiling() {
        assertTrue(CliVersion.below("1.4.1", "1.5.0"));
        assertTrue(CliVersion.below("1.4.9", "1.5.0"));
        assertTrue(CliVersion.below("1.3.0", "1.5.0"));
    }

    @Test
    void below_falseAtOrAboveCeiling() {
        assertFalse(CliVersion.below("1.5.0", "1.5.0"), "equal to ceiling is not below (half-open)");
        assertFalse(CliVersion.below("1.5.1", "1.5.0"));
        assertFalse(CliVersion.below("1.6.0", "1.5.0"));
    }

    @Test
    void below_falseForNullOrEmpty() {
        assertFalse(CliVersion.below(null, "1.5.0"));
        assertFalse(CliVersion.below("", "1.5.0"));
    }

    // ---- inRange (half-open window [floor, ceiling)) -------------------------

    @Test
    void inRange_trueOnlyWithinHalfOpenWindow() {
        assertTrue(CliVersion.inRange("1.4.0", "1.4.0", "1.5.0"), "floor is inclusive");
        assertTrue(CliVersion.inRange("1.4.9", "1.4.0", "1.5.0"));
    }

    @Test
    void inRange_falseBelowFloorOrAtOrAboveCeiling() {
        assertFalse(CliVersion.inRange("1.3.9", "1.4.0", "1.5.0"), "below floor");
        assertFalse(CliVersion.inRange("1.5.0", "1.4.0", "1.5.0"), "ceiling is exclusive");
        assertFalse(CliVersion.inRange("1.6.0", "1.4.0", "1.5.0"));
    }

    @Test
    void inRange_falseForNullOrEmpty() {
        assertFalse(CliVersion.inRange(null, "1.4.0", "1.5.0"));
        assertFalse(CliVersion.inRange("", "1.4.0", "1.5.0"));
    }
}
