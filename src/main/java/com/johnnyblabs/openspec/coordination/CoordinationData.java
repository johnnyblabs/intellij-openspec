package com.johnnyblabs.openspec.coordination;

import java.util.List;

/**
 * Immutable snapshot of the three coordination collections plus the resolved
 * {@link CoordinationTier}. Produced by {@code CoordinationService} on a background thread
 * and handed to the UI for a single atomic render.
 *
 * @param workspaces    known coordination workspaces
 * @param contextStores locally registered context stores
 * @param initiatives   initiatives across registered context stores
 * @param tier          the presentation tier for this snapshot
 * @param sourcedFromCli whether the snapshot came from the CLI (true) or the on-disk fallback (false)
 */
public record CoordinationData(
        List<WorkspaceEntry> workspaces,
        List<ContextStoreEntry> contextStores,
        List<InitiativeEntry> initiatives,
        CoordinationTier tier,
        boolean sourcedFromCli) {

    public CoordinationData {
        workspaces = workspaces != null ? List.copyOf(workspaces) : List.of();
        contextStores = contextStores != null ? List.copyOf(contextStores) : List.of();
        initiatives = initiatives != null ? List.copyOf(initiatives) : List.of();
    }

    /** True when any of the three collections has at least one entry. */
    public boolean hasAnyState() {
        return !workspaces.isEmpty() || !contextStores.isEmpty() || !initiatives.isEmpty();
    }

    public static final CoordinationData EMPTY_HIDDEN =
            new CoordinationData(List.of(), List.of(), List.of(), CoordinationTier.HIDDEN, false);
}
