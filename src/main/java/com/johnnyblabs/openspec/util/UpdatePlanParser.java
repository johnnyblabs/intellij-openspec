package com.johnnyblabs.openspec.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads {@code openspec update --dry-run --json} (CLI 1.15.0+): {@code {changes:[{path,action,tool}],
 * notes:[{code,message}], failures:[{tool,error}]}}.
 *
 * <p>The result is a {@link Plan} or {@link Failed}, never an empty plan standing in for a failure: a non-zero exit, an
 * error {@code status} entry, output that isn't JSON, or a plan missing one of its three arrays or holding an entry it
 * can't read all fail. A plan the plugin can't read in full is a failure, so the preview never shows less than the CLI
 * will do. Unknown {@code action} values and note codes are kept as they are.
 */
public final class UpdatePlanParser {

    /** Moves out of a renamed tool directory (e.g. {@code .windsurf} to {@code .devin}), done without asking. */
    public static final String LEGACY_TOOL_DIRECTORIES = "legacy-tool-directories";
    /** Legacy artifacts the plan leaves alone; {@code --dry-run --force} lists their removal. */
    public static final String LEGACY_ARTIFACTS = "legacy-artifacts";

    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^(?:[A-Za-z]:[\\\\/]|\\\\\\\\).*");

    private UpdatePlanParser() {
    }

    /** {@link Plan} or {@link Failed}. */
    public sealed interface Result permits Plan, Failed {
    }

    /** What {@code openspec update} would do. Held only for one preview; never persisted. */
    public record Plan(List<Change> changes, List<Note> notes, List<Failure> failures) implements Result {
        /** No changes, notes or failures: there is nothing to update. */
        public boolean isEmpty() {
            return changes.isEmpty() && notes.isEmpty() && failures.isEmpty();
        }

        public boolean hasNote(String code) {
            return notes.stream().anyMatch(n -> code.equals(n.code()));
        }
    }

    /** One planned file change. {@code action} is {@code add}, {@code update}, {@code delete}, or a value kept as-is. */
    public record Change(String path, String action, String tool) {
        /** Under the user's home ({@code ~/…}) or otherwise absolute, on POSIX or Windows. */
        public boolean outsideProject() {
            return path.equals("~") || path.startsWith("~/") || path.startsWith("/")
                    || WINDOWS_ABSOLUTE.matcher(path).matches();
        }
    }

    public record Note(String code, String message) {
    }

    public record Failure(String tool, String error) {
    }

    /** The dry run failed or its output couldn't be read; {@code message} is the CLI's reason where it gave one. */
    public record Failed(String message) implements Result {
    }

    public static @NotNull Result parse(CliRunner.CliResult result) {
        CliJson.Response response = CliJson.read(result);
        if (response instanceof CliJson.Failed failed) {
            return new Failed(failed.describe());
        }
        JsonObject root = ((CliJson.Ok) response).object();
        if (root == null) {
            return unreadable("the output is not a plan object");
        }
        try {
            List<Change> changes = new ArrayList<>();
            for (JsonObject o : objects(root, "changes")) {
                changes.add(new Change(required(o, "path"), required(o, "action"), required(o, "tool")));
            }
            List<Note> notes = new ArrayList<>();
            for (JsonObject o : objects(root, "notes")) {
                notes.add(new Note(required(o, "code"), required(o, "message")));
            }
            List<Failure> failures = new ArrayList<>();
            for (JsonObject o : objects(root, "failures")) {
                failures.add(new Failure(required(o, "tool"), required(o, "error")));
            }
            return new Plan(List.copyOf(changes), List.copyOf(notes), List.copyOf(failures));
        } catch (IllegalArgumentException e) {
            return unreadable(e.getMessage());
        }
    }

    private static Failed unreadable(String why) {
        return new Failed("The update plan couldn't be read: " + why + ".");
    }

    private static List<JsonObject> objects(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("it has no \"" + key + "\" list");
        }
        JsonArray array = element.getAsJsonArray();
        List<JsonObject> out = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("an entry in \"" + key + "\" is not an object");
            }
            out.add(e.getAsJsonObject());
        }
        return out;
    }

    private static String required(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            throw new IllegalArgumentException("an entry has no \"" + key + "\"");
        }
        return e.getAsString();
    }
}
