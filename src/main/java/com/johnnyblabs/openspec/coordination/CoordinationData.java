package com.johnnyblabs.openspec.coordination;

import java.util.List;

/**
 * Immutable snapshot of the coordination surface plus the resolved {@link CoordinationTier}.
 * Produced by {@code CoordinationService} on a background thread and handed to the UI for a
 * single atomic render.
 *
 * <p>The snapshot spans both models: the OpenSpec 1.5 lead model ({@link #stores} /
 * {@link #worksets}) and the legacy 1.4 model ({@link #workspaces} / {@link #contextStores} /
 * {@link #initiatives}). When {@link #storesAreLeadModel} is true (the detected CLI is at or
 * above the 1.5 store floor) the store/workset groups are canonical and any legacy state on
 * disk is demoted to a muted, read-only group — shown only when {@link #legacyStateExists}.
 *
 * @param workspaces         known legacy coordination workspaces
 * @param contextStores      legacy locally registered context stores
 * @param initiatives        legacy initiatives across registered context stores
 * @param stores             OpenSpec 1.5 registered stores (with lazy doctor health)
 * @param worksets           OpenSpec 1.5 local worksets and their members
 * @param tier               the presentation tier for this snapshot
 * @param sourcedFromCli     whether the legacy collections came from the CLI vs the on-disk fallback
 * @param storesSourcedFromCli whether stores/worksets came from the CLI vs the on-disk fallback
 * @param storesAreLeadModel whether the CLI is at or above the 1.5 store floor (stores lead, legacy demoted)
 * @param legacyStateExists  whether any legacy workspace/context-store/initiative state exists on disk
 */
public record CoordinationData(
        List<WorkspaceEntry> workspaces,
        List<ContextStoreEntry> contextStores,
        List<InitiativeEntry> initiatives,
        List<StoreEntry> stores,
        List<WorksetEntry> worksets,
        CoordinationTier tier,
        boolean sourcedFromCli,
        boolean storesSourcedFromCli,
        boolean storesAreLeadModel,
        boolean legacyStateExists) {

    public CoordinationData {
        workspaces = workspaces != null ? List.copyOf(workspaces) : List.of();
        contextStores = contextStores != null ? List.copyOf(contextStores) : List.of();
        initiatives = initiatives != null ? List.copyOf(initiatives) : List.of();
        stores = stores != null ? List.copyOf(stores) : List.of();
        worksets = worksets != null ? List.copyOf(worksets) : List.of();
    }

    /** True when any legacy collection has at least one entry. */
    public boolean hasAnyLegacyState() {
        return !workspaces.isEmpty() || !contextStores.isEmpty() || !initiatives.isEmpty();
    }

    /** True when any store or workset was resolved. */
    public boolean hasAnyStoreState() {
        return !stores.isEmpty() || !worksets.isEmpty();
    }

    /** True when any collection (legacy or 1.5) has at least one entry. */
    public boolean hasAnyState() {
        return hasAnyLegacyState() || hasAnyStoreState();
    }

    public static final CoordinationData EMPTY_HIDDEN =
            new CoordinationData(List.of(), List.of(), List.of(), List.of(), List.of(),
                    CoordinationTier.HIDDEN, false, false, false, false);
}
