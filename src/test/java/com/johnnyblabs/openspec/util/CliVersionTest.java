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

    // ---- two-digit minor ordering (1.10 is the first two-digit minor) --------

    @Test
    void twoDigitMinor_ordersNumericallyNotLexically() {
        // 1.10 is the first minor version whose numeral is two digits. A lexical string compare would
        // misplace "1.10.0" BELOW "1.9.0" ('1' < '9' at index 2). CliVersion.compare is segment-wise
        // numeric, so 1.10 sorts above 1.9. Every assertion below flips-and-fails under a lexical
        // regression, so this can't pass vacuously; it enforces the plugin-core spec's SHALL that the
        // version comparison order 1.10.0 above 1.9.0.
        assertTrue(CliVersion.compare("1.10.0", "1.9.0") > 0, "1.10.0 must sort above 1.9.0");
        assertTrue(CliVersion.compare("1.9.0", "1.10.0") < 0, "1.9.0 must sort below 1.10.0");
        assertTrue(CliVersion.atLeast("1.10.0", "1.9.0"), "1.10.0 is at least 1.9.0");
        assertFalse(CliVersion.atLeast("1.9.0", "1.10.0"), "1.9.0 is not at least 1.10.0");
        assertTrue(CliVersion.below("1.9.0", "1.10.0"), "1.9.0 is below 1.10.0");
        assertFalse(CliVersion.below("1.10.0", "1.9.0"), "1.10.0 is not below 1.9.0");
        // 1.11 keeps ordering correctly across two-digit minors: enforces the plugin-core spec's SHALL
        // that the version comparison order 1.11.0 above BOTH 1.10.0 (11 > 10, both two-digit) and 1.9.0
        // (11 > 9, two-digit vs single). A lexical regression misplaces "1.11.0" below "1.9.0".
        assertTrue(CliVersion.compare("1.11.0", "1.10.0") > 0, "1.11.0 must sort above 1.10.0");
        assertTrue(CliVersion.compare("1.11.0", "1.9.0") > 0, "1.11.0 must sort above 1.9.0");
        assertFalse(CliVersion.atLeast("1.10.0", "1.11.0"), "1.10.0 is not at least 1.11.0");
        // same numeric-vs-lexical property on a two-digit PATCH segment:
        assertTrue(CliVersion.atLeast("1.9.10", "1.9.9"), "1.9.10 is at least 1.9.9");
        // 1.12 keeps ordering correctly. The load-bearing ANTI-LEXICAL case is 1.12.0 vs the
        // single-digit-minor 1.9.0: numeric +1 (12 > 9) but lexical -1 ('1' < '9' at index 2), so a
        // lexical regression flips exactly this pair. (1.12.0 vs 1.11.0 is NOT anti-lexical — it's
        // true lexically too — so it only serves as a monotonicity check.)
        assertTrue(CliVersion.compare("1.12.0", "1.9.0") > 0, "1.12.0 must sort above 1.9.0 (numeric, not lexical)");
        assertTrue(CliVersion.compare("1.9.0", "1.12.0") < 0, "1.9.0 must sort below 1.12.0");
        assertTrue(CliVersion.compare("1.12.0", "1.11.0") > 0, "1.12.0 sorts above 1.11.0 (monotonic across two-digit minors)");
        assertFalse(CliVersion.atLeast("1.11.0", "1.12.0"), "1.11.0 is not at least 1.12.0");
        // 1.13: the same anti-lexical pair against single-digit 1.9.0, plus monotonicity and patch.
        assertTrue(CliVersion.compare("1.13.0", "1.9.0") > 0, "1.13.0 must sort above 1.9.0 (numeric, not lexical)");
        assertTrue(CliVersion.compare("1.9.0", "1.13.0") < 0, "1.9.0 must sort below 1.13.0");
        assertTrue(CliVersion.compare("1.13.0", "1.12.0") > 0, "1.13.0 sorts above 1.12.0");
        assertFalse(CliVersion.atLeast("1.12.0", "1.13.0"), "1.12.0 is not at least 1.13.0");
        assertTrue(CliVersion.atLeast("1.13.2", "1.13.0"), "1.13.2 is at least 1.13.0");
    }
}
