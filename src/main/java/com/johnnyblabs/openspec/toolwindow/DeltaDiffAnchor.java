package com.johnnyblabs.openspec.toolwindow;

/**
 * Pure, round-trippable encoding of the "diff this capability" cross-link used in the consolidated
 * change-deltas view. Each capability section emits an {@code <a href="openspec-diff:<capability>">};
 * the preview pane's {@code HyperlinkListener} maps an activated link back to the capability via
 * {@link #capabilityFromHref(String)} and opens the delta-vs-main diff.
 *
 * <p>The scheme is a prefix, resolved by prefix-strip rather than {@code split(":")}, so a capability
 * name containing a colon or hyphen round-trips intact. A non-{@code openspec-diff:} href (a real
 * {@code http} link, say) resolves to {@code null} — that's the guard that keeps normal links working.
 */
public final class DeltaDiffAnchor {

    /** The pseudo-scheme prefix identifying a capability diff link. */
    public static final String PREFIX = "openspec-diff:";

    private DeltaDiffAnchor() {
    }

    /** The href for a capability's diff cross-link. */
    public static String diffAnchorHref(String capability) {
        return PREFIX + (capability == null ? "" : capability);
    }

    /**
     * The capability encoded in {@code href}, or {@code null} if {@code href} is not one of our diff
     * links (so the {@code HyperlinkListener} leaves other links alone). Prefix-strip preserves colons
     * and hyphens in the capability name.
     */
    public static String capabilityFromHref(String href) {
        if (href == null || !href.startsWith(PREFIX)) {
            return null;
        }
        return href.substring(PREFIX.length());
    }
}
