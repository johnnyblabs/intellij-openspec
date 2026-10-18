package com.johnnyblabs.openspec.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads OpenSpec CLI {@code --json} output.
 *
 * <p>{@link #read} is the one way a caller turns a {@link CliRunner.CliResult} into either the command's answer or a
 * failure. A CLI 1.9+ failure prints one JSON document on stdout carrying {@code status: [{severity, code, message,
 * target?, fix?}]}; any error-severity entry there makes the run a failure whatever its code, because the codes are
 * not a closed set (the no-root code alone is {@code no_openspec_root} or {@code no_root_with_registered_stores},
 * depending on whether any store is registered).
 *
 * <p>{@link #extractJsonPayload} tolerates text before the JSON. {@link CliRunner} keeps the CLI's telemetry and
 * update notices off stdout; this is the defensive second layer for a stray line from any source.
 */
public final class CliJson {

    private CliJson() {
    }

    /** How a CLI run failed. */
    public enum Kind {
        /** The CLI answered with an error {@code status} entry: a definite "no". */
        ENVELOPE,
        /** No usable JSON: the output could not be read, or the run failed without saying why in JSON. */
        UNREADABLE
    }

    /** The result of reading a CLI run: {@link Ok} or {@link Failed}. */
    public sealed interface Response permits Ok, Failed {
    }

    /** The command answered; {@code payload} is its JSON. */
    public record Ok(@NotNull JsonElement payload, int exitCode) implements Response {
        public @Nullable JsonObject object() {
            return payload.isJsonObject() ? payload.getAsJsonObject() : null;
        }
    }

    /**
     * The command failed. {@code message} is the CLI's own reason where it gave one; {@code payload} is the parsed
     * envelope for {@link Kind#ENVELOPE}, null for {@link Kind#UNREADABLE}.
     */
    public record Failed(@NotNull Kind kind, @NotNull String message, @Nullable String fix, @Nullable String code,
                         int exitCode, @Nullable JsonObject payload) implements Response {
        /** The message, followed by the CLI's suggested fix when it gave one. */
        public String describe() {
            return fix == null || fix.isBlank() ? message : message + " " + fix;
        }
    }

    /**
     * Reads a run of a command whose non-zero exit always means failure. A parsed payload with a non-zero exit and no
     * error envelope is {@link Kind#UNREADABLE}: the CLI failed without saying why in its JSON.
     */
    public static Response read(CliRunner.CliResult result) {
        Response response = readReport(result);
        if (response instanceof Ok ok && ok.exitCode() != 0) {
            return unreadable(result);
        }
        return response;
    }

    /**
     * Reads a run of a command that exits 1 with a normal report when the report finds problems ({@code validate}'s
     * {@code items}, {@code schema validate}'s {@code {valid:false}}). The exit code alone never makes this a failure;
     * an error envelope or unreadable output does.
     */
    public static Response readReport(CliRunner.CliResult result) {
        String stdout = result.stdout() == null ? "" : result.stdout();
        JsonElement payload;
        try {
            String json = extractJsonPayload(stdout);
            payload = json == null || json.isBlank() ? null : JsonParser.parseString(json);
        } catch (JsonParseException | IllegalStateException e) {
            payload = null;
        }
        if (payload == null || !(payload.isJsonObject() || payload.isJsonArray())) {
            return unreadable(result);
        }
        if (payload.isJsonObject()) {
            JsonObject error = firstErrorStatus(payload.getAsJsonObject());
            if (error != null) {
                String message = string(error, "message");
                return new Failed(Kind.ENVELOPE,
                        message == null || message.isBlank() ? "The OpenSpec CLI reported an error." : message,
                        string(error, "fix"), string(error, "code"), result.exitCode(), payload.getAsJsonObject());
            }
        }
        return new Ok(payload, result.exitCode());
    }

    private static Failed unreadable(CliRunner.CliResult result) {
        String stderr = CliText.stripAnsi(result.stderr() == null ? "" : result.stderr()).trim();
        String message;
        if (!stderr.isEmpty()) {
            message = stderr;
        } else if (result.exitCode() != 0) {
            message = "The OpenSpec CLI exited with code " + result.exitCode() + " and no readable output.";
        } else {
            message = "The OpenSpec CLI returned no readable JSON.";
        }
        return new Failed(Kind.UNREADABLE, message, null, null, result.exitCode(), null);
    }

    /** The first top-level {@code status} entry with severity {@code error}, or null. */
    private static @Nullable JsonObject firstErrorStatus(JsonObject root) {
        if (!root.has("status") || !root.get("status").isJsonArray()) {
            return null;
        }
        JsonArray status = root.getAsJsonArray("status");
        for (JsonElement element : status) {
            if (element.isJsonObject() && "error".equalsIgnoreCase(string(element.getAsJsonObject(), "severity"))) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private static @Nullable String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /**
     * Returns the JSON payload embedded in {@code raw}: from the first line whose first non-blank character is
     * {@code '{'} or {@code '['} and begins JSON, so a preamble line such as {@code "[WARN] ..."} is skipped. When
     * no line qualifies, the payload starts at the first {@code '{'} or {@code '['} anywhere (noise on the same line
     * as the JSON).
     *
     * @param raw the raw stdout captured from the CLI (may be null)
     * @return the payload, or the original string (trimmed) when no JSON opener is present or {@code raw} is null
     */
    public static String extractJsonPayload(String raw) {
        if (raw == null) {
            return null;
        }
        int lineStart = 0;
        while (lineStart < raw.length()) {
            int i = lineStart;
            while (i < raw.length() && (raw.charAt(i) == ' ' || raw.charAt(i) == '\t')) i++;
            if (i < raw.length() && (raw.charAt(i) == '{' || raw.charAt(i) == '[') && looksLikeJsonStart(raw, i)) {
                return raw.substring(i);
            }
            int next = raw.indexOf('\n', lineStart);
            if (next < 0) break;
            lineStart = next + 1;
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

    /**
     * Whether the opener at {@code i} begins JSON rather than a bracketed tag such as {@code [WARN]}: the next
     * non-blank character must be one that can follow the opener in the CLI's JSON.
     */
    private static boolean looksLikeJsonStart(String raw, int i) {
        char opener = raw.charAt(i);
        int j = i + 1;
        while (j < raw.length() && Character.isWhitespace(raw.charAt(j))) j++;
        if (j >= raw.length()) return false;
        char c = raw.charAt(j);
        if (opener == '{') return c == '"' || c == '}';
        // Bare true/false/null elements are left out: "[note] ..." must not read as JSON, and the CLI's arrays hold
        // objects, strings or numbers.
        return c == '{' || c == '[' || c == '"' || c == ']' || c == '-' || Character.isDigit(c);
    }
}
