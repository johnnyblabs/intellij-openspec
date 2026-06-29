package com.johnnyblabs.openspec.coordination;

/**
 * The presentation tier for the coordination surface, resolved from detected state, the
 * active workflow mode, and CLI availability.
 *
 * <ul>
 *   <li>{@link #HIDDEN} — no coordination state and the active mode is not a coordination
 *       mode: the surface stays out of the way.</li>
 *   <li>{@link #AWARENESS} — coordination state exists (or a coordination mode is active)
 *       but the CLI is unavailable or below the floor: read-only listing, write actions
 *       disabled.</li>
 *   <li>{@link #FULL} — coordination state/mode plus a CLI at or above the floor: listing,
 *       initiative-artifact navigation, and CLI-delegated write actions.</li>
 * </ul>
 */
public enum CoordinationTier {
    HIDDEN,
    AWARENESS,
    FULL;

    /**
     * Resolves the tier as a pure function of the three inputs.
     *
     * @param hasCoordinationState whether any workspace, context store, or initiative was detected
     * @param coordinationMode     whether the active workflow mode is a coordination mode
     *                             (a non-default mode such as {@code workspace-planning})
     * @param cliAtOrAboveFloor    whether the CLI is available at or above the coordination floor
     */
    public static CoordinationTier resolve(boolean hasCoordinationState,
                                           boolean coordinationMode,
                                           boolean cliAtOrAboveFloor) {
        if (!hasCoordinationState && !coordinationMode) {
            return HIDDEN;
        }
        return cliAtOrAboveFloor ? FULL : AWARENESS;
    }

    /** Whether CLI-delegated write actions are enabled in this tier. */
    public boolean allowsWriteActions() {
        return this == FULL;
    }
}
