package com.johnnyblabs.openspec.coordination;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An initiative as presented in the IDE — derived from
 * {@code openspec initiative list --json} or from {@code initiative.yaml} on disk in the
 * fallback path. An initiative's artifacts ({@code initiative.yaml}, {@code requirements.md},
 * {@code design.md}, {@code decisions.md}, {@code questions.md}, {@code tasks.md}) live
 * directly in its {@link #root} directory, which the CLI reports as
 * {@code <storeRoot>/initiatives/<id>/}.
 *
 * @param id      the initiative id
 * @param title   the initiative title
 * @param summary the initiative summary (may be empty)
 * @param status  the resolved lifecycle status
 * @param created the creation timestamp string, or null if unknown
 * @param owners  the owners list (never null; empty when none)
 * @param store   the id of the context store this initiative belongs to, or null if unknown
 * @param root    the initiative's own directory (containing its artifacts), or null if unknown
 */
public record InitiativeEntry(
        String id,
        String title,
        String summary,
        InitiativeStatus status,
        @Nullable String created,
        List<String> owners,
        @Nullable String store,
        @Nullable String root) {

    public InitiativeEntry {
        owners = owners != null ? List.copyOf(owners) : List.of();
    }
}
