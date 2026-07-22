package com.johnnyblabs.openspec.toolwindow;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helpers for anchoring the preview to a requirement's section.
 *
 * <p>Selecting a Requirement node renders its parent spec and then scrolls the preview to that
 * requirement. The scroll target is an HTML anchor injected next to each requirement heading:
 * {@link #injectAnchors(String)} adds an {@code <a name="...">} whose name equals {@link
 * #anchorId(String)} of the requirement's name, and the selection handler calls {@code
 * JEditorPane.scrollToReference(anchorId(name))}. {@code <a name>} (not {@code id}) is used because
 * that is the anchor form the Swing {@code HTMLEditorKit} resolves for {@code scrollToReference}.
 *
 * <p>The falsifiable part — the slug and the injection — is pure and round-trippable: for any
 * requirement name, {@code injectAnchors} emits an anchor equal to {@code anchorId(name)}.
 */
public final class RequirementAnchors {

    /** Prefix keeps anchors namespaced and guarantees a non-empty, letter-initial id. */
    private static final String PREFIX = "req-";

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    // A rendered requirement heading: <hN>Requirement: NAME</hN> (case-insensitive keyword,
    // matching the CLI's case-insensitive requirement parsing). NAME is captured for the anchor.
    private static final Pattern REQ_HEADING = Pattern.compile(
            "(<h[1-6]>)(Requirement:\\s*)(.*?)(</h[1-6]>)",
            Pattern.CASE_INSENSITIVE);

    private RequirementAnchors() {
    }

    /**
     * A stable, URL-safe slug for a requirement name: lowercased, non-alphanumerics collapsed to
     * {@code -}, trimmed, and prefixed. The same name always yields the same id.
     */
    public static String anchorId(String name) {
        String base = name == null ? "" : name;
        String slug = NON_SLUG.matcher(base.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = trimDashes(slug);
        return PREFIX + slug;
    }

    /**
     * Returns {@code html} with an {@code <a name="anchorId(name)">} injected immediately inside each
     * requirement heading. A {@code null} input is returned unchanged; a heading already carrying an
     * anchor is left as-is (the injected anchor sits before the {@code Requirement:} text, so the
     * pattern no longer matches from the heading tag).
     */
    public static String injectAnchors(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher matcher = REQ_HEADING.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = stripTags(matcher.group(3)).trim();
            String anchor = "<a name=\"" + anchorId(name) + "\"></a>";
            String replacement = matcher.group(1) + anchor
                    + matcher.group(2) + matcher.group(3) + matcher.group(4);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String trimDashes(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '-') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '-') {
            end--;
        }
        return s.substring(start, end);
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]+>", "");
    }
}
