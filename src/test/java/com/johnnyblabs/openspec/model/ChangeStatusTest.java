package com.johnnyblabs.openspec.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ChangeStatusTest {

    @ParameterizedTest
    @CsvSource({
            "proposed, PROPOSED",
            "PROPOSED, PROPOSED",
            "applied, APPLIED",
            "APPLIED, APPLIED",
            "archived, ARCHIVED",
            "ARCHIVED, ARCHIVED"
    })
    void fromString_parsesValidStatuses(String input, ChangeStatus expected) {
        assertEquals(expected, ChangeStatus.fromString(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "  ", "draft"})
    void fromString_unknownReturnsUnknown(String input) {
        assertEquals(ChangeStatus.UNKNOWN, ChangeStatus.fromString(input));
    }

    @Test
    void fromString_handlesMixedCase() {
        assertEquals(ChangeStatus.PROPOSED, ChangeStatus.fromString("Proposed"));
        assertEquals(ChangeStatus.APPLIED, ChangeStatus.fromString("aPpLiEd"));
    }

    @Test
    void toLabel_wrapsInBrackets() {
        assertEquals("[proposed]", ChangeStatus.PROPOSED.toLabel());
        assertEquals("[applied]", ChangeStatus.APPLIED.toLabel());
        assertEquals("[archived]", ChangeStatus.ARCHIVED.toLabel());
        assertEquals("[unknown]", ChangeStatus.UNKNOWN.toLabel());
    }

    @Test
    void allStatusesExist() {
        // Ensure the OpenSpec status lifecycle is complete
        assertNotNull(ChangeStatus.PROPOSED);
        assertNotNull(ChangeStatus.APPLIED);
        assertNotNull(ChangeStatus.ARCHIVED);
        assertNotNull(ChangeStatus.UNKNOWN);
        assertEquals(4, ChangeStatus.values().length);
    }
}
