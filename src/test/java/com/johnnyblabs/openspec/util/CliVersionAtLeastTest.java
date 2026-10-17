package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CliVersion#atLeast(String, String)}. The utility is the shared comparison
 * point between {@code SchemaService}'s feature gate and {@code OpenSpecProjectService.StartupDetection}'s
 * floor notification — both rely on identical semantics, so the tests pin all the edge cases.
 */
class CliVersionAtLeastTest {

    @Test
    void exactMatch_returnsTrue() {
        assertTrue(CliVersion.atLeast("1.3.0", "1.3.0"));
    }

    @Test
    void patchAbove_returnsTrue() {
        assertTrue(CliVersion.atLeast("1.3.1", "1.3.0"));
    }

    @Test
    void minorAbove_returnsTrue() {
        assertTrue(CliVersion.atLeast("1.4.0", "1.3.0"));
    }

    @Test
    void majorAbove_returnsTrue() {
        assertTrue(CliVersion.atLeast("2.0.0", "1.3.0"));
    }

    @Test
    void patchBelow_returnsFalse() {
        assertFalse(CliVersion.atLeast("1.2.99", "1.3.0"));
    }

    @Test
    void minorBelow_returnsFalse() {
        assertFalse(CliVersion.atLeast("1.2.0", "1.3.0"));
    }

    @Test
    void legacyVersion_1_0_0_belowFloor_returnsFalse() {
        assertFalse(CliVersion.atLeast("1.0.0", "1.3.0"));
    }

    @Test
    void legacyVersion_1_1_0_belowFloor_returnsFalse() {
        assertFalse(CliVersion.atLeast("1.1.0", "1.3.0"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullOrEmptyDetected_returnsFalse(String detected) {
        assertFalse(CliVersion.atLeast(detected, "1.3.0"),
                "Null/empty detected version must return false, not throw");
    }

    @Test
    void garbageDetected_returnsFalseForFloor() {
        // Unparseable parts fall back to 0, so "garbage" → 0.0.0 → below 1.3.0
        assertFalse(CliVersion.atLeast("garbage", "1.3.0"));
    }

    @Test
    void semverWithSuffix_stripsSuffixForComparison() {
        // "1.3.0-beta" → 1.3.0 for comparison purposes (mirrors prior SchemaService behavior)
        assertTrue(CliVersion.atLeast("1.3.0-beta", "1.3.0"),
                "Suffix on version is stripped during comparison; treat as 1.3.0");
    }

    @Test
    void shortVersionString_handlesGracefully() {
        // "1.3" → 1.3.0 (missing parts default to 0)
        assertTrue(CliVersion.atLeast("1.3", "1.3.0"));
        // "1.3" → 1.3.0; required 1.3.1 → false
        assertFalse(CliVersion.atLeast("1.3", "1.3.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.3.0", "1.3.1", "1.4.0", "1.4.1", "1.5.0", "1.6.0", "1.7.0", "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.0", "1.13.2", "1.14.0", "1.14.3", "2.0.0"})
    void allSupportedVersions_meetFloor(String version) {
        // Every declared supported line (1.3.x–1.14.x) clears the 1.3.0 floor; 2.0.0 asserts the
        // no-upper-cap property so a future line flows through without an allowlist edit. This is the
        // supported-line ENUMERATION assertion; the two-digit 1.10-vs-1.11 ordering itself is pinned by
        // CliVersionTest.twoDigitMinor_ordersNumericallyNotLexically.
        assertTrue(CliVersion.atLeast(version, "1.3.0"),
                version + " should meet 1.3.0 floor");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.9.0", "1.0.0", "1.0.99", "1.1.0", "1.1.5", "1.2.0", "1.2.99"})
    void allPre1_3Versions_fallBelowFloor(String version) {
        assertFalse(CliVersion.atLeast(version, "1.3.0"),
                version + " should fall below 1.3.0 floor");
    }
}
