package com.johnnyblabs.openspec.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactStatusTest {

    @ParameterizedTest
    @CsvSource({
            "done, DONE",
            "DONE, DONE",
            "ready, READY",
            "READY, READY",
            "blocked, BLOCKED",
            "BLOCKED, BLOCKED",
            "generating, GENERATING",
            "GENERATING, GENERATING",
            "error, ERROR",
            "ERROR, ERROR",
            "unknown, UNKNOWN",
            "UNKNOWN, UNKNOWN"
    })
    void fromString_parsesAllSixStatuses(String input, ArtifactStatus expected) {
        assertEquals(expected, ArtifactStatus.fromString(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "  ", "pending"})
    void fromString_unknownReturnsUnknown(String input) {
        assertEquals(ArtifactStatus.UNKNOWN, ArtifactStatus.fromString(input));
    }

    @Test
    void toIcon_returnsNonEmptyForAllStatuses() {
        for (ArtifactStatus status : ArtifactStatus.values()) {
            String icon = status.toIcon();
            assertNotNull(icon, status.name() + " icon should not be null");
            assertFalse(icon.isEmpty(), status.name() + " icon should not be empty");
        }
    }

    @Test
    void toIcon_returnsExpectedSymbols() {
        assertEquals("\u2713", ArtifactStatus.DONE.toIcon());       // ✓
        assertEquals("\u25CB", ArtifactStatus.READY.toIcon());      // ○
        assertEquals("\u2212", ArtifactStatus.BLOCKED.toIcon());    // −
        assertEquals("\u25CF", ArtifactStatus.GENERATING.toIcon()); // ●
        assertEquals("\u2717", ArtifactStatus.ERROR.toIcon());      // ✗
        assertEquals("?", ArtifactStatus.UNKNOWN.toIcon());
    }

    @Test
    void allSixStatusesExist() {
        assertEquals(6, ArtifactStatus.values().length);
    }
}
