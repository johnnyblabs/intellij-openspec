package com.johnnyblabs.openspec.util;

/**
 * Utility for comparing OpenSpec CLI version strings (e.g., {@code "1.3.0"} vs {@code "1.2.99"}).
 *
 * <p>Provides a single {@link #atLeast(String, String)} entry point that both
 * {@code SchemaService} (gating schema-management features) and the v0.3.0 startup floor
 * notification share so the comparison logic isn't duplicated. Numeric semver-style
 * comparison with sensible handling of trailing non-numeric suffixes ({@code "1.2.0-beta"}
 * is treated as {@code "1.2.0"}).
 */
public final class CliVersion {

    private CliVersion() {}

    /**
     * Returns {@code true} when {@code detected} is non-null, non-empty, and at least
     * {@code required} in semver-style numeric comparison. Returns {@code false} for
     * null or empty {@code detected}, and for unparseable strings (component falls back
     * to {@code 0}, mirroring the prior {@code SchemaService} behavior).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code atLeast("1.3.0", "1.3.0")} → {@code true}
     *   <li>{@code atLeast("1.3.1", "1.3.0")} → {@code true}
     *   <li>{@code atLeast("1.2.99", "1.3.0")} → {@code false}
     *   <li>{@code atLeast(null, "1.3.0")} → {@code false}
     *   <li>{@code atLeast("", "1.3.0")} → {@code false}
     *   <li>{@code atLeast("garbage", "1.3.0")} → {@code false}
     * </ul>
     */
    public static boolean atLeast(String detected, String required) {
        if (detected == null || detected.isEmpty()) {
            return false;
        }
        return compare(detected, required) >= 0;
    }

    /**
     * Returns {@code true} when {@code detected} is non-null, non-empty, parseable, and strictly
     * less than {@code ceiling} in the same semver-style numeric comparison used by
     * {@link #atLeast(String, String)}. Returns {@code false} for null or empty {@code detected}
     * (a missing version is not "below" anything, mirroring {@code atLeast}'s null/empty handling)
     * and for {@code detected >= ceiling}.
     *
     * <p>This is the upper-bound companion to {@link #atLeast(String, String)}: together they let a
     * caller express "this feature exists only up to (but not including) version X" — e.g. a CLI
     * command that was removed in a later release.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code below("1.4.1", "1.5.0")} → {@code true}
     *   <li>{@code below("1.5.0", "1.5.0")} → {@code false}
     *   <li>{@code below("1.5.1", "1.5.0")} → {@code false}
     *   <li>{@code below(null, "1.5.0")} → {@code false}
     *   <li>{@code below("", "1.5.0")} → {@code false}
     * </ul>
     */
    public static boolean below(String detected, String ceiling) {
        if (detected == null || detected.isEmpty()) {
            return false;
        }
        return compare(detected, ceiling) < 0;
    }

    /**
     * Returns {@code true} when {@code detected} falls in the half-open window
     * {@code [floorInclusive, ceilingExclusive)} — i.e. at or above {@code floorInclusive} and
     * strictly below {@code ceilingExclusive}. Implemented as
     * {@code atLeast(detected, floorInclusive) && below(detected, ceilingExclusive)}, so a
     * null/empty/unparseable {@code detected} yields {@code false}.
     *
     * <p>Example: {@code inRange(v, "1.4.0", "1.5.0")} is {@code true} only for CLI versions in the
     * {@code 1.4.x} line and {@code false} on {@code 1.3.x} or {@code >= 1.5.0}.
     */
    public static boolean inRange(String detected, String floorInclusive, String ceilingExclusive) {
        return atLeast(detected, floorInclusive) && below(detected, ceilingExclusive);
    }

    /**
     * Compares two semantic version strings.
     *
     * @return negative if {@code a < b}, 0 if equal, positive if {@code a > b}
     */
    static int compare(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int maxLen = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < maxLen; i++) {
            int aNum = i < aParts.length ? parsePart(aParts[i]) : 0;
            int bNum = i < bParts.length ? parsePart(bParts[i]) : 0;
            if (aNum != bNum) {
                return Integer.compare(aNum, bNum);
            }
        }
        return 0;
    }

    private static int parsePart(String part) {
        try {
            // Strip non-numeric suffixes like "0-beta"
            String numeric = part.replaceAll("[^0-9].*", "");
            return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
