package com.johnnyblabs.openspec.coordination;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The four initiative lifecycle states defined by OpenSpec 1.4's initiative schema
 * ({@code exploring | active | complete | archived}). Mirrors the CLI's
 * {@code INITIATIVE_STATUSES} so the coordination surface can render a status badge
 * per initiative.
 */
public enum InitiativeStatus {
    EXPLORING("exploring", "Exploring"),
    ACTIVE("active", "Active"),
    COMPLETE("complete", "Complete"),
    ARCHIVED("archived", "Archived"),
    /** Fallback for a value the CLI emits that this plugin version does not recognize. */
    UNKNOWN("unknown", "Unknown");

    private final String wireValue;
    private final String displayLabel;

    InitiativeStatus(String wireValue, String displayLabel) {
        this.wireValue = wireValue;
        this.displayLabel = displayLabel;
    }

    /** The lowercase token as it appears in {@code initiative.yaml} / CLI JSON. */
    public String wireValue() {
        return wireValue;
    }

    /** Human-readable label for the UI. */
    public String displayLabel() {
        return displayLabel;
    }

    /**
     * Parses a status token (case-insensitive). Null, empty, or unrecognized values
     * map to {@link #UNKNOWN} rather than throwing, so a forward-compatible CLI status
     * never breaks the listing.
     */
    @NotNull
    public static InitiativeStatus fromString(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (InitiativeStatus status : values()) {
            if (status.wireValue.equals(normalized)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
