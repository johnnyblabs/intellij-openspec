package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.coordination.Diagnostic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Store Doctor action's message when the check itself failed: informational, never "healthy". */
class CoordinationPanelUnknownHealthTest {

    @Test
    void unknownHealthMessageNamesTheStoreAndTheCommand() {
        String text = CoordinationPanel.unknownHealthMessage("storea",
                Diagnostic.unknown("Couldn't check store 'storea': timed out\nsecond line"));
        assertTrue(text.contains("store 'storea'"));
        assertTrue(text.contains("timed out"));
        assertTrue(text.contains("openspec store doctor storea"));
        assertFalse(text.contains("second line"), "only the reason's first line is shown");
        assertFalse(text.contains("is healthy"), "never says healthy");
    }
}
