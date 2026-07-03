package com.johnnyblabs.openspec.coordination;

/**
 * Pure, side-effect-free enablement logic for the store/workset {@code AnAction}s. Extracted from
 * the actions themselves so that {@code AnAction.update()} can decide enablement from cached state
 * alone — no CLI invocation, no disk IO — and so the decision can be unit tested without a running
 * IDE or a constructed {@code AnActionEvent}.
 *
 * <p>Every method here takes only cached booleans and the current tree {@link SelectionKind}. The
 * base gate is {@code writeEnabled} = <em>Full tier AND CLI &gt;= 1.5.0</em>; below it every store
 * and workset write action is disabled.
 */
public final class CoordinationActionGating {

    private CoordinationActionGating() {
    }

    /** What kind of coordination node the tree selection currently points at. */
    public enum SelectionKind {
        NONE,
        STORE,
        WORKSET,
        MEMBER,
        OTHER
    }

    /**
     * Whether store/workset write actions are permitted at all: the surface is at the Full tier and
     * the store model leads (which is true exactly when the detected CLI is at or above 1.5.0).
     */
    public static boolean writeEnabled(boolean fullTier, boolean storesAreLeadModel) {
        return fullTier && storesAreLeadModel;
    }

    /**
     * Creation / registration actions (New Store, Register Existing Store, New Workset) — enabled
     * whenever writes are permitted, regardless of the current selection.
     */
    public static boolean creationEnabled(boolean writeEnabled) {
        return writeEnabled;
    }

    /**
     * Per-store actions (Doctor, Open Root, Unregister, Remove) — require writes permitted AND a
     * store node selected.
     */
    public static boolean storeScopedEnabled(boolean writeEnabled, SelectionKind selection) {
        return writeEnabled && selection == SelectionKind.STORE;
    }

    /**
     * Per-workset actions (Open, Remove) — require writes permitted AND a workset node selected.
     */
    public static boolean worksetScopedEnabled(boolean writeEnabled, SelectionKind selection) {
        return writeEnabled && selection == SelectionKind.WORKSET;
    }
}
