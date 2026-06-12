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
