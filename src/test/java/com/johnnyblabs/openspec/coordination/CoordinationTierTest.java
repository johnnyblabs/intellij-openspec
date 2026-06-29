package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinationTierTest {

    @Test
    void hiddenWhenNoStateAndNotCoordinationMode() {
        assertEquals(CoordinationTier.HIDDEN, CoordinationTier.resolve(false, false, false));
        assertEquals(CoordinationTier.HIDDEN, CoordinationTier.resolve(false, false, true));
    }

    @Test
    void awarenessWhenStatePresentButCliBelowFloor() {
        assertEquals(CoordinationTier.AWARENESS, CoordinationTier.resolve(true, false, false));
    }

    @Test
    void fullWhenStatePresentAndCliAtOrAboveFloor() {
        assertEquals(CoordinationTier.FULL, CoordinationTier.resolve(true, false, true));
    }

    @Test
    void coordinationModeAloneLeavesSurfaceVisibleEvenWithoutState() {
        assertEquals(CoordinationTier.AWARENESS, CoordinationTier.resolve(false, true, false));
        assertEquals(CoordinationTier.FULL, CoordinationTier.resolve(false, true, true));
    }

    @Test
    void onlyFullTierAllowsWriteActions() {
        assertTrue(CoordinationTier.FULL.allowsWriteActions());
        assertFalse(CoordinationTier.AWARENESS.allowsWriteActions());
        assertFalse(CoordinationTier.HIDDEN.allowsWriteActions());
    }
}
