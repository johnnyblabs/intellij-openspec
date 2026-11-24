package com.johnnyblabs.openspec.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One release's captured {@code --help} sweep ({@code fixtures/cli/<version>/help/}): which commands it has, and which
 * long flags each command accepts and whether they take a value.
 * <p>
 * Flags are read only from the {@code Options:} block, and only from lines that start with whitespace and {@code -}.
 * Description text can mention a flag ({@code --specs} in the top-level command list), so reading the whole text
 * would see flags a command doesn't take. The block ends at the first blank line or the next {@code Xxx:} header.
 */
public final class CliHelp {

    private static final Pattern LONG = Pattern.compile("--([a-z][a-z0-9-]*)(\\s+[<\\[])?");

    private final String version;
    private final Map<String, String> files = new LinkedHashMap<>();
    private final Map<String, Map<String, Boolean>> flags = new HashMap<>();

    private CliHelp(String version, JsonObject index) {
        this.version = version;
        for (Map.Entry<String, JsonElement> e : index.getAsJsonObject("commands").entrySet()) {
            JsonElement file = e.getValue().getAsJsonObject().get("file");
            files.put(e.getKey(), file.isJsonNull() ? null : file.getAsString());
        }
    }

    /** The sweep captured for {@code version}; fails when there is none. */
    public static CliHelp of(String version) {
        Path index = FixtureVersions.CLI.resolve(version).resolve("help/index.json");
        try {
            return new CliHelp(version, JsonParser.parseString(Files.readString(index)).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("no help sweep for " + version, e);
        }
    }

    public String version() {
        return version;
    }

    /** Whether the sweep asked about {@code command} at all (otherwise nothing is known about it). */
    public boolean records(String command) {
        if (files.containsKey(command)) return true;
        // The sweep asks about a subcommand only when its parent exists, so a missing parent records it as absent.
        int space = command.lastIndexOf(' ');
        return space > 0 && records(command.substring(0, space)) && !hasCommand(command.substring(0, space));
    }

    /** Whether this release has {@code command} ({@code "schema init"}, {@code "doctor"}). */
    public boolean hasCommand(String command) {
        return files.get(command) != null;
    }

    /** The long flags {@code command} accepts, without the leading dashes, mapped to whether each takes a value. */
    public Map<String, Boolean> flags(String command) {
        return flags.computeIfAbsent(command, this::read);
    }

    public boolean hasFlag(String command, String flag) {
        return flags(command).containsKey(strip(flag));
    }

    /** Whether {@code flag} takes a value; fails when the command doesn't accept it. */
    public boolean takesValue(String command, String flag) {
        Boolean v = flags(command).get(strip(flag));
        if (v == null) throw new IllegalArgumentException(version + " " + command + " has no " + flag);
        return v;
    }

    private Map<String, Boolean> read(String command) {
        String file = files.get(command);
        if (file == null) throw new IllegalArgumentException(version + " has no command " + command);
        String text;
        try {
            text = Files.readString(FixtureVersions.CLI.resolve(version).resolve("help").resolve(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parseOptions(text);
    }

    /** The flags in a help text's {@code Options:} block. */
    static Map<String, Boolean> parseOptions(String text) {
        Map<String, Boolean> out = new TreeMap<>();
        boolean inside = false;
        for (String line : text.split("\\R")) {
            if (line.equals("Options:")) {
                inside = true;
            } else if (inside && (line.isBlank() || line.matches("^\\S.*:$"))) {
                inside = false;
            } else if (inside && line.startsWith("  -")) {
                // Commander indents flag lines by exactly two spaces; a wrapped description is indented further, so a
                // description line that happens to start with "--x" is never read as a flag.
                // A flag line: "  -y, --yes   Skip...", "  --schema <name>  Use..."; description text follows two
                // or more spaces, so only the part before it names flags.
                String head = line.trim().split("\\s{2,}", 2)[0];
                Matcher m = LONG.matcher(head);
                while (m.find()) out.put(m.group(1), m.group(2) != null);
            }
        }
        return out;
    }

    /**
     * The most positional arguments {@code command} takes, from its {@code Usage:} line: each {@code <x>} or
     * {@code [x]} after {@code [options]} counts once ({@code schema fork [options] <source> [name]} takes two).
     */
    public int maxPositionals(String command) {
        String file = files.get(command);
        if (file == null) throw new IllegalArgumentException(version + " has no command " + command);
        String text;
        try {
            text = Files.readString(FixtureVersions.CLI.resolve(version).resolve("help").resolve(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parseMaxPositionals(text);
    }

    static int parseMaxPositionals(String text) {
        String usage = text.lines().filter(l -> l.startsWith("Usage:")).findFirst().orElse("");
        int at = usage.indexOf("[options]");
        String rest = at < 0 ? "" : usage.substring(at + "[options]".length());
        if (rest.contains("...")) return Integer.MAX_VALUE;
        Matcher m = Pattern.compile("<[^>]+>|\\[[^]]+]").matcher(rest);
        int n = 0;
        while (m.find()) if (!m.group().equals("[command]")) n++;
        return n;
    }

    private static String strip(String flag) {
        return flag.startsWith("--") ? flag.substring(2) : flag;
    }
}
