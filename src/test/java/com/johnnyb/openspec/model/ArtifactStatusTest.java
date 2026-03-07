package com.johnnyb.openspec.model;

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
            "BLOCKED, BLOCKED"
    })
    void fromString_parsesValidStatuses(String input, ArtifactStatus expected) {
        assertEquals(expected, ArtifactStatus.fromString(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "  ", "pending"})
    void fromString_unknownReturnsUnknown(String input) {
        assertEquals(ArtifactStatus.UNKNOWN, ArtifactStatus.fromString(input));
    }

    @Test
    void toIcon_returnsExpectedSymbols() {
        assertEquals("\u2713", ArtifactStatus.DONE.toIcon());     // ✓
        assertEquals("\u25CB", ArtifactStatus.READY.toIcon());    // ○
        assertEquals("\u2212", ArtifactStatus.BLOCKED.toIcon());  // −
        assertEquals("?", ArtifactStatus.UNKNOWN.toIcon());
    }

    @Test
    void allStatusesExist() {
        assertEquals(4, ArtifactStatus.values().length);
    }
}
