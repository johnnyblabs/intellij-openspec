package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.services.RetireMarkerFix;

import java.util.List;

/** Test access to the dialogs' package-private display helpers. */
public final class SyncPreviewDialogAccess {
    private SyncPreviewDialogAccess() {
    }

    public static String markerLinkText(RetireMarkerFix.Offer offer, List<String> refusals) {
        return SyncPreviewDialog.markerLinkText(offer, refusals);
    }

    public static String problemText(String refusal) {
        return BulkArchiveDialog.problemText(refusal);
    }
}
