package com.johnnyblabs.openspec.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * One OpenSpec CLI invocation, described without a version: a command path, its positional arguments and flags, and
 * any extra environment. {@link CliRunner} fits it to the installed CLI with {@link #resolve} just before starting the
 * process, using {@link CliFlags}.
 * <p>
 * Only {@link CliCommands} builds these: the mutators are package-private, so a caller can't add a flag the version
 * matrix tests never saw.
 */
public final class CliArgs {

    private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?([.+-].*)?$");

    /** A positional argument, or a flag with an optional value. An optional flag is dropped where it's unsupported. */
    private record Part(@Nullable String flag, @Nullable String value, boolean optional) {
    }

    /** The argv for one release, and the optional flags left out because that release lacks them. */
    public record Resolved(List<String> argv, List<String> dropped, String version) {
    }

    private final String command;
    private final List<String> words;
    private final List<Part> parts = new ArrayList<>();
    private final Map<String, String> env = new LinkedHashMap<>();

    private CliArgs(String... words) {
        if (words.length == 0) throw new IllegalArgumentException("a command needs at least one word");
        this.words = List.of(words);
        this.command = String.join(" ", words);
        if (!CliFlags.commands().containsKey(command)) {
            throw new IllegalStateException("openspec " + command + " is not in CliFlags");
        }
    }

    static CliArgs command(String... words) {
        return new CliArgs(words);
    }

    CliArgs arg(String value) {
        parts.add(new Part(null, Objects.requireNonNull(value, "argument"), false));
        return this;
    }

    CliArgs flag(String name) {
        return add(name, null, false);
    }

    CliArgs flag(String name, String value) {
        return add(name, Objects.requireNonNull(value, name), false);
    }

    /** A flag that is left out, rather than refused, on a release without it. */
    CliArgs optional(String name) {
        return add(name, null, true);
    }

    /** {@link #optional(String)} for a flag with a value; the value goes with it. */
    CliArgs optional(String name, String value) {
        return add(name, Objects.requireNonNull(value, name), true);
    }

    CliArgs env(String key, String value) {
        env.put(key, value);
        return this;
    }

    private CliArgs add(String name, @Nullable String value, boolean optional) {
        CliFlags.Flag known = CliFlags.flags(command).get(name);
        if (known == null) throw new IllegalStateException("openspec " + command + " " + name + " is not in CliFlags");
        if (known.takesValue() != (value != null)) {
            throw new IllegalStateException("openspec " + command + " " + name
                    + (known.takesValue() ? " takes a value" : " is a switch"));
        }
        parts.add(new Part(name, value, optional));
        return this;
    }

    /** The command path, such as {@code "schema init"}. */
    public String command() {
        return command;
    }

    /** Extra environment variables for the process. */
    public Map<String, String> env() {
        return Collections.unmodifiableMap(env);
    }

    /** Every argument as built, before any release is applied: what tests and messages show. */
    public List<String> requested() {
        List<String> out = new ArrayList<>(words);
        for (Part p : parts) addTo(out, p);
        return out;
    }

    /** The command as a user would type it, with every requested flag: {@code openspec validate --all --json}. */
    public String display() {
        return "openspec " + String.join(" ", requested());
    }

    /**
     * The argv for the installed CLI {@code version}. A null or unreadable version is taken as the plugin's target
     * release; when that is unknown too, as the newest known release.
     *
     * @throws UnsupportedCliArgumentException when that release lacks the command or a flag that isn't optional
     */
    public Resolved resolve(@Nullable String version) throws UnsupportedCliArgumentException {
        String effective = effectiveVersion(version);
        String shown = effective == null ? "unknown" : effective;
        CliFlags.Range commandRange = CliFlags.commands().get(command);
        if (!commandRange.includes(effective)) {
            throw new UnsupportedCliArgumentException(command, null, commandRange, shown);
        }
        List<String> argv = new ArrayList<>(words);
        List<String> dropped = new ArrayList<>();
        for (Part p : parts) {
            if (p.flag() != null) {
                CliFlags.Range range = CliFlags.flags(command).get(p.flag()).range();
                if (!range.includes(effective)) {
                    if (!p.optional()) throw new UnsupportedCliArgumentException(command, p.flag(), range, shown);
                    dropped.add(p.flag());
                    continue;
                }
            }
            addTo(argv, p);
        }
        return new Resolved(List.copyOf(argv), List.copyOf(dropped), shown);
    }

    /** The release {@link #resolve} applies for {@code version}: itself, the target release, or null for "newest". */
    static @Nullable String effectiveVersion(@Nullable String version) {
        if (readable(version)) return version.trim();
        String target = PluginVersions.targetCliVersion();
        return readable(target) ? target : null;
    }

    private static boolean readable(@Nullable String version) {
        return version != null && VERSION.matcher(version.trim()).matches();
    }

    private static void addTo(List<String> out, Part p) {
        if (p.flag() != null) out.add(p.flag());
        if (p.value() != null) out.add(p.value());
    }

    @Override
    public @NotNull String toString() {
        return display();
    }
}
