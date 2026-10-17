package com.johnnyblabs.openspec.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code openspec archive <name> --yes --json} (CLI 1.5+; shapes captured in {@code fixtures/cli/archive-1.5.0/}
 * and {@code archive-1.14.3/}). Success: {@code {archive: {change, archivedAs, path, specsUpdated, totals?, warnings?}}}.
 * Refusal: exit 1 with {@code {archive: null, status: [{severity, code, message, fix?}]}}.
 */
public final class ArchiveJson {

    private ArchiveJson() {
    }

    /** A successful archive: the folder it went to, whether main specs changed, and any warnings. */
    public record Outcome(String archivedAs, boolean specsUpdated, List<String> warnings) {
    }

    /**
     * The outcome of one CLI run. A non-zero exit is always a refusal: OpenSpec's {@code status} entry when the output
     * has one, otherwise stderr (e.g. 1.4 rejecting {@code --json}). Exit 0 without an archive result is never treated
     * as success.
     */
    public static Outcome parse(String stdout, String stderr, int exitCode) throws ArchiveRefusedException {
        JsonObject json = object(stdout);
        if (exitCode != 0) {
            JsonObject status = firstStatus(json);
            if (status != null && status.has("message")) {
                throw new ArchiveRefusedException(status.get("message").getAsString(), string(status, "fix"));
            }
            String err = stderr == null ? "" : stderr.strip();
            throw new ArchiveRefusedException(err.isEmpty() ? "openspec archive exited with code " + exitCode : err, null);
        }
        JsonObject archive = json != null && json.has("archive") && json.get("archive").isJsonObject()
                ? json.getAsJsonObject("archive") : null;
        if (archive == null || string(archive, "archivedAs") == null) {
            throw new ArchiveRefusedException("openspec archive reported success without an archive result", null);
        }
        List<String> warnings = new ArrayList<>();
        if (archive.has("warnings") && archive.get("warnings").isJsonArray()) {
            for (JsonElement w : archive.getAsJsonArray("warnings")) warnings.add(w.getAsString());
        }
        boolean specsUpdated = archive.has("specsUpdated") && archive.get("specsUpdated").getAsBoolean();
        return new Outcome(string(archive, "archivedAs"), specsUpdated, List.copyOf(warnings));
    }

    private static @Nullable JsonObject object(String stdout) {
        if (stdout == null || stdout.isBlank()) return null;
        try {
            JsonElement e = JsonParser.parseString(stdout);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable JsonObject firstStatus(@Nullable JsonObject json) {
        if (json == null || !json.has("status") || !json.get("status").isJsonArray()) return null;
        JsonArray status = json.getAsJsonArray("status");
        return status.isEmpty() || !status.get(0).isJsonObject() ? null : status.get(0).getAsJsonObject();
    }

    private static @Nullable String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
}
