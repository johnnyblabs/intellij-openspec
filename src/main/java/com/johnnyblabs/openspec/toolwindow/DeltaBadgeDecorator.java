package com.johnnyblabs.openspec.toolwindow;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure HTML post-processor that badges a delta spec's operation headers.
 *
 * <p>A change's delta spec groups its requirements under {@code ## ADDED Requirements},
 * {@code ## MODIFIED Requirements}, {@code ## REMOVED Requirements}, or {@code ## RENAMED
 * Requirements}. After the markdown is rendered to HTML those become {@code <h2>ADDED
 * Requirements</h2>}. This decorator wraps the leading operation keyword in a stable, themeable
 * marker span ({@code class="openspec-op-badge openspec-op-added"} etc.) so a change's proposed
 * operations read at a glance.
 *
 * <p>Contract: string in, string out, no platform dependency. <b>Idempotent</b> — a header already
 * carrying the badge span is left untouched (the keyword is no longer immediately after the
 * {@code <hN>} tag, so the pattern cannot re-match). A plain {@code ## Requirements} main-spec
 * heading gets <b>no</b> badge; this decorator is applied only on the delta-spec render path (see
 * {@link SpecPreviewRenderer}), never to a main spec.
 */
public final class DeltaBadgeDecorator {

    /** Stable marker class asserted by tests and styled by the preview CSS. */
    public static final String BADGE_CLASS = "openspec-op-badge";

    // Matches an operation keyword sitting immediately after an opening heading tag. Once wrapped in
    // a <span>, the keyword is no longer adjacent to <hN>, which is what makes decoration idempotent.
    private static final Pattern OP_HEADER =
            Pattern.compile("(<h[1-6]>)(ADDED|MODIFIED|REMOVED|RENAMED)\\b");

    private DeltaBadgeDecorator() {
    }

    /**
     * CSS for the badge markers. Kept as a plain constant (no {@code JBUI}/{@code JBColor}) so it is
     * headless-safe; the fixed hues read acceptably in both light and dark themes.
     */
    public static String badgeCss() {
        return ".openspec-op-badge { font-weight: bold; font-size: 9pt; }"
                + ".openspec-op-added { color: #4a9e5c; }"
                + ".openspec-op-modified { color: #4a90d9; }"
                + ".openspec-op-removed { color: #d9534f; }"
                + ".openspec-op-renamed { color: #a878d0; }";
    }

    /**
     * Returns {@code html} with each delta operation header keyword wrapped in a badge span.
     * A {@code null} input is returned unchanged.
     */
    public static String decorate(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher matcher = OP_HEADER.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String op = matcher.group(2);
            String replacement = matcher.group(1)
                    + "<span class=\"" + BADGE_CLASS + " openspec-op-" + op.toLowerCase(Locale.ROOT) + "\">"
                    + op + "</span>";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
