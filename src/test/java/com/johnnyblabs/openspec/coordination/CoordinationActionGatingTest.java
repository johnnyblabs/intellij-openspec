package com.johnnyblabs.openspec.coordination;

import com.johnnyblabs.openspec.coordination.CoordinationActionGating.SelectionKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the pure enablement logic the store/workset {@code AnAction}s delegate to. Proves that write
 * actions are disabled outside the Full tier and below CLI 1.5.0, and that per-store / per-workset
 * actions are selection-sensitive — all decided from cached booleans alone, with no CLI or IO.
 */
class CoordinationActionGatingTest {

    // ---- T.4: base write gate (Full tier AND store model / CLI >= 1.5.0) ----

    @Test
    void writesEnabledOnlyAtFullTierAndStoreModel() {
        assertTrue(CoordinationActionGating.writeEnabled(true, true));
        assertFalse(CoordinationActionGating.writeEnabled(false, true), "not Full tier");
        assertFalse(CoordinationActionGating.writeEnabled(true, false), "CLI < 1.5.0 (no store model)");
        assertFalse(CoordinationActionGating.writeEnabled(false, false));
    }

    @Test
    void creationDisabledBelowTheBar() {
        assertTrue(CoordinationActionGating.creationEnabled(CoordinationActionGating.writeEnabled(true, true)));
        assertFalse(CoordinationActionGating.creationEnabled(CoordinationActionGating.writeEnabled(false, true)));
        assertFalse(CoordinationActionGating.creationEnabled(CoordinationActionGating.writeEnabled(true, false)));
    }

    // ---- T.4: selection sensitivity -----------------------------------------

    @Test
    void storeActionsRequireStoreSelection() {
        boolean write = CoordinationActionGating.writeEnabled(true, true);
        assertTrue(CoordinationActionGating.storeScopedEnabled(write, SelectionKind.STORE));
        assertFalse(CoordinationActionGating.storeScopedEnabled(write, SelectionKind.WORKSET));
        assertFalse(CoordinationActionGating.storeScopedEnabled(write, SelectionKind.MEMBER));
        assertFalse(CoordinationActionGating.storeScopedEnabled(write, SelectionKind.NONE));
    }

    @Test
    void worksetActionsRequireWorksetSelection() {
        boolean write = CoordinationActionGating.writeEnabled(true, true);
        assertTrue(CoordinationActionGating.worksetScopedEnabled(write, SelectionKind.WORKSET));
        assertFalse(CoordinationActionGating.worksetScopedEnabled(write, SelectionKind.STORE));
        assertFalse(CoordinationActionGating.worksetScopedEnabled(write, SelectionKind.NONE));
    }

    @Test
    void scopedActionsDisabledBelowTheBarRegardlessOfSelection() {
        boolean noWrite = CoordinationActionGating.writeEnabled(false, true);
        assertFalse(CoordinationActionGating.storeScopedEnabled(noWrite, SelectionKind.STORE));
        assertFalse(CoordinationActionGating.worksetScopedEnabled(noWrite, SelectionKind.WORKSET));
    }
}
