package com.johnnyblabs.openspec.coordination;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An initiative as presented in the IDE — derived from
 * {@code openspec initiative list --json} or from {@code initiative.yaml} on disk in the
 * fallback path. An initiative lives at {@code <storeRoot>/<id>/} alongside its markdown
 * artifacts ({@code requirements.md}, {@code design.md}, {@code decisions.md},
 * {@code questions.md}, {@code tasks.md}).
 *
 * @param id        the initiative id
 * @param title     the initiative title
 * @param summary   the initiative summary (may be empty)
 * @param status    the resolved lifecycle status
 * @param created   the creation timestamp string, or null if unknown
 * @param owners    the owners list (never null; empty when none)
 * @param storeRoot the root of the context store this initiative belongs to, or null if unknown
 */
public record InitiativeEntry(
        String id,
        String title,
        String summary,
        InitiativeStatus status,
        @Nullable String created,
        List<String> owners,
        @Nullable String storeRoot) {

    public InitiativeEntry {
        owners = owners != null ? List.copyOf(owners) : List.of();
    }
}
