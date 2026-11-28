package com.johnnyblabs.openspec.services;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/** An archive the OpenSpec CLI (or the plugin's own archive) refused, with OpenSpec's reason and suggested fix. */
public final class ArchiveRefusedException extends IOException {
    private final @Nullable String fix;

    public ArchiveRefusedException(String message, @Nullable String fix) {
        super(message);
        this.fix = fix;
    }

    private transient volatile @Nullable RetireMarkerFix.Offer markerOffer;

    /** The offer to add the retirement marker, decided off the UI thread after the refusal; {@code null} for none. */
    public @Nullable RetireMarkerFix.Offer markerOffer() {
        return markerOffer;
    }

    void markerOffer(@Nullable RetireMarkerFix.Offer offer) {
        this.markerOffer = offer;
    }

    public @Nullable String fix() {
        return fix;
    }

    /** The reason, followed by the fix when there is one, for a notification. */
    public String describe() {
        return fix == null || fix.isBlank() ? getMessage() : getMessage() + " " + fix;
    }
}
