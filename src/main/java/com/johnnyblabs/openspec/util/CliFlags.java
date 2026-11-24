package com.johnnyblabs.openspec.util;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which OpenSpec CLI releases accept each command the plugin runs and each flag it passes: the release that introduced
 * it and, for one that was dropped, the first release without it.
 * <p>
 * Kept by hand and held to the CLI's own record by {@code CliFlagTableContractTest}, which compares every entry with the
 * {@code --help} captured from each supported release. Captures cover the newest patch of each 1.x minor and every
 * 2.x release, so a {@code since} names the minor's first release ({@code 1.5.0}). {@link CliArgs#resolve} reads this
 * table to fit an invocation to the installed CLI.
 * <p>
 * To pass a new flag, add it here with the release that introduced it; the contract test fails until the entry agrees
 * with the captured help.
 */
public final class CliFlags {

    /** Releases {@code [since, until)}; {@code until} is null while the CLI still has it. */
    public record Range(String since, @Nullable String until) {

        /** Whether {@code version} is in range; a null version stands for "newer than any known release". */
        public boolean includes(@Nullable String version) {
            if (version == null) return until == null;
            return CliVersion.compare(version, since) >= 0 && (until == null || CliVersion.compare(version, until) < 0);
        }
    }

    /** A flag's releases, and whether it takes a value ({@code --schema <name>}) or is a switch ({@code --json}). */
    public record Flag(Range range, boolean takesValue) {
    }

    private static final Map<String, Range> COMMANDS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Flag>> FLAGS = new LinkedHashMap<>();

    static {
        command("init", "1.3.0").value("--tools").value("--scope", "2.1.0");
        command("update", "1.3.0").flag("--dry-run", "1.15.0").flag("--json", "1.15.0").flag("--force");
        command("validate", "1.3.0").flag("--all").value("--type").flag("--json").flag("--strict");
        command("show", "1.3.0").value("--type").flag("--json");
        command("archive", "1.3.0").flag("--yes").flag("--json", "1.5.0").flag("--skip-specs");
        command("list", "1.3.0").flag("--specs").flag("--json");
        command("status", "1.3.0").value("--change").flag("--json");
        command("instructions", "1.3.0").value("--change").flag("--json");
        command("new change", "1.3.0").value("--schema");
        command("schemas", "1.3.0").flag("--json");
        command("templates", "1.3.0").value("--schema").flag("--json");
        command("schema fork", "1.3.0").flag("--json");
        command("schema init", "1.3.0").flag("--json").flag("--no-default").value("--description").value("--artifacts");
        command("schema validate", "1.3.0").flag("--json");
        command("schema which", "1.3.0").flag("--json");
        command("config list", "1.3.0").flag("--json");
        command("config profile", "1.3.0");

        // The 1.4 coordination commands, replaced by store and workset in 1.5.0.
        command("workspace list", "1.4.0", "1.5.0").flag("--json");
        command("workspace setup", "1.4.0", "1.5.0").value("--name").flag("--no-interactive");
        command("context-store list", "1.4.0", "1.5.0").flag("--json");
        command("context-store doctor", "1.4.0", "1.5.0").flag("--json");
        command("context-store setup", "1.4.0", "1.5.0");
        command("initiative list", "1.4.0", "1.5.0").flag("--json");
        command("initiative create", "1.4.0", "1.5.0").value("--title");

        command("store list", "1.5.0").flag("--json");
        command("store doctor", "1.5.0").flag("--json");
        command("store setup", "1.5.0").value("--path").flag("--json");
        command("store register", "1.5.0").flag("--yes").flag("--json");
        command("store unregister", "1.5.0").flag("--json");
        command("store remove", "1.5.0").flag("--yes").flag("--json");
        command("workset list", "1.5.0").flag("--json");
        command("workset create", "1.5.0").value("--member").flag("--json");
        command("workset remove", "1.5.0").flag("--yes").flag("--json");
        command("doctor", "1.5.0").value("--store").flag("--json");
    }

    private CliFlags() {
    }

    /** Every command the plugin runs, by its path ({@code "schema init"}). */
    public static Map<String, Range> commands() {
        return Collections.unmodifiableMap(COMMANDS);
    }

    /** The flags the plugin passes to {@code command}, keyed with their dashes ({@code "--json"}); empty if none. */
    public static Map<String, Flag> flags(String command) {
        return Collections.unmodifiableMap(FLAGS.getOrDefault(command, Map.of()));
    }

    private static Entry command(String path, String since) {
        return command(path, since, null);
    }

    private static Entry command(String path, String since, @Nullable String until) {
        Range range = new Range(since, until);
        if (COMMANDS.put(path, range) != null) throw new IllegalStateException("duplicate command " + path);
        FLAGS.put(path, new LinkedHashMap<>());
        return new Entry(path, range);
    }

    /** Adds flags to one command; a flag's releases default to the command's. */
    private record Entry(String path, Range range) {
        Entry flag(String name) {
            return add(name, range, false);
        }

        Entry flag(String name, String since) {
            return add(name, new Range(since, range.until()), false);
        }

        Entry value(String name) {
            return add(name, range, true);
        }

        Entry value(String name, String since) {
            return add(name, new Range(since, range.until()), true);
        }

        private Entry add(String name, Range r, boolean takesValue) {
            if (FLAGS.get(path).put(name, new Flag(r, takesValue)) != null) {
                throw new IllegalStateException("duplicate flag " + path + " " + name);
            }
            return this;
        }
    }
}
