package com.johnnyblabs.openspec.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared recognition of {@code ### Requirement:} header lines.
 *
 * <p>The OpenSpec CLI parses the requirement-header token case-insensitively since 1.4.0
 * (upstream PR #1154), so {@code ### requirement:} and {@code ### REQUIREMENT:} are valid
 * spec content. Every plugin surface that recognizes requirement headers must go through
 * this single pattern; per-file copies are how the matchers drifted from the CLI in the
 * first place. Writers still emit the canonical {@code ### Requirement:} casing — only
 * recognition is relaxed.
 */
public final class SpecPatterns {

    /**
     * Header-token prefix for composing into larger patterns (e.g. followed by a quoted
     * requirement name). Case-insensitive token; requires the {@code ###} at line start.
     */
    public static final String REQUIREMENT_HEADER_PREFIX_REGEX = "^###\\s+(?i:requirement):\\s*";

    /** Canonical casing used whenever the plugin writes or rewrites a requirement header. */
    public static final String CANONICAL_HEADER_PREFIX = "### Requirement: ";

    /** Matches a requirement header line in multi-line text; group 1 is the requirement name. */
    public static final Pattern REQUIREMENT_HEADER =
            Pattern.compile(REQUIREMENT_HEADER_PREFIX_REGEX + "(.+)$", Pattern.MULTILINE);

    private static final Pattern SINGLE_LINE =
            Pattern.compile(REQUIREMENT_HEADER_PREFIX_REGEX + "(.+)$");

    private SpecPatterns() {
    }

    /**
     * The requirement name from the first line of the given text, or {@code null} when that
     * line is not a requirement header. Leading whitespace disqualifies it, matching the
     * {@code ^###} anchoring used everywhere else; text after the first line is ignored
     * (PSI elements in plain-text files can span multiple lines).
     */
    public static String requirementName(String text) {
        if (text == null) return null;
        int nl = text.indexOf('\n');
        String line = (nl >= 0 ? text.substring(0, nl) : text).stripTrailing();
        Matcher m = SINGLE_LINE.matcher(line);
        return m.matches() ? m.group(1).trim() : null;
    }
}
