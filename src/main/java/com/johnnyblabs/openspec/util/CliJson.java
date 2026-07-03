package com.johnnyblabs.openspec.util;

/**
 * Helpers for tolerating non-JSON preamble on OpenSpec CLI stdout.
 *
 * <p>On its first invocation in a fresh environment the OpenSpec CLI prints a one-time telemetry
 * notice ({@code "Note: OpenSpec collects anonymous usage stats. Opt out: OPENSPEC_TELEMETRY=0"})
 * to <em>stdout</em>, prepended before the JSON of a {@code --json} command. A strict
 * {@code Gson.fromJson} on that raw stdout throws, which silently drops callers to their fallback
 * path. {@link com.johnnyblabs.openspec.util.CliRunner} now sets {@code OPENSPEC_TELEMETRY=0} on the
 * child environment so the notice is never emitted; this helper is the defensive second layer,
 * stripping any leading non-JSON so a stray banner from any source can't break a parse.
 */
public final class CliJson {

    private CliJson() {
    }

    /**
     * Returns the JSON payload embedded in {@code raw}, discarding any preamble before the first
     * top-level {@code '{'} or {@code '['}. OpenSpec {@code --json} output is always a JSON object or
     * array, so trimming to the first structural character is safe.
     *
     * @param raw the raw stdout captured from the CLI (may be null)
     * @return the substring from the first {@code '{'}/{@code '['} onward, or the original string
     *         (trimmed) when no JSON opener is present or {@code raw} is null
     */
    public static String extractJsonPayload(String raw) {
        if (raw == null) {
            return null;
        }
        int brace = raw.indexOf('{');
        int bracket = raw.indexOf('[');
        int start;
        if (brace < 0) {
            start = bracket;
        } else if (bracket < 0) {
            start = brace;
        } else {
            start = Math.min(brace, bracket);
        }
        if (start < 0) {
            return raw.trim();
        }
        return raw.substring(start);
    }
}
