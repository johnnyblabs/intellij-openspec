package com.johnnyblabs.openspec.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryModeTest {

    @Test
    void displayNames() {
        assertEquals("Copy to Clipboard", DeliveryMode.CLIPBOARD.getDisplayName());
        assertEquals("Open in Editor Tab", DeliveryMode.EDITOR_TAB.getDisplayName());
        assertEquals("Generate via API", DeliveryMode.DIRECT_API.getDisplayName());
    }

    @Test
    void allModesPresent() {
        DeliveryMode[] values = DeliveryMode.values();
        assertEquals(3, values.length);
    }

    @Test
    void valueOfRoundTrips() {
        for (DeliveryMode mode : DeliveryMode.values()) {
            assertEquals(mode, DeliveryMode.valueOf(mode.name()));
        }
    }

    @Test
    void invalidValueOfThrows() {
        assertThrows(IllegalArgumentException.class, () -> DeliveryMode.valueOf("INVALID"));
    }
}
