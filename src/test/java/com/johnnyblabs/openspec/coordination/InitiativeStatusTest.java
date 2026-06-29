package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitiativeStatusTest {

    @ParameterizedTest
    @CsvSource({
            "exploring,EXPLORING",
            "active,ACTIVE",
            "complete,COMPLETE",
            "archived,ARCHIVED",
            "ACTIVE,ACTIVE",
            "  Active  ,ACTIVE"
    })
    void parsesKnownStatuses(String input, InitiativeStatus expected) {
        assertEquals(expected, InitiativeStatus.fromString(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "in-progress", "garbage"})
    void unknownOrBlankMapsToUnknown(String input) {
        assertEquals(InitiativeStatus.UNKNOWN, InitiativeStatus.fromString(input));
    }

    @Test
    void displayLabelsAreHumanReadable() {
        assertEquals("Exploring", InitiativeStatus.EXPLORING.displayLabel());
        assertEquals("Active", InitiativeStatus.ACTIVE.displayLabel());
        assertEquals("Complete", InitiativeStatus.COMPLETE.displayLabel());
        assertEquals("Archived", InitiativeStatus.ARCHIVED.displayLabel());
    }
}
