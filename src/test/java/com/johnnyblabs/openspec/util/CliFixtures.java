package com.johnnyblabs.openspec.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads captured CLI fixtures from {@code src/test/resources/fixtures/cli/} (see its README for provenance). */
public final class CliFixtures {

    private CliFixtures() {
    }

    /** The fixture at {@code fixtures/cli/<path>} as UTF-8 text. */
    public static String text(String path) {
        String resource = "/fixtures/cli/" + path;
        try (InputStream is = CliFixtures.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resource);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fixture: " + resource, e);
        }
    }

    /** The plugin's target CLI, {@code openspecTargetVersion} in gradle.properties, so target-scoped fixtures follow it. */
    public static String targetVersion() {
        try {
            Matcher m = Pattern.compile("(?m)^openspecTargetVersion=(\\S+)$")
                    .matcher(Files.readString(Paths.get("gradle.properties")));
            if (!m.find()) throw new IllegalStateException("gradle.properties has no openspecTargetVersion");
            return m.group(1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A captured failure run from {@code 1.14.3/errors/<name>.{stdout,stderr,exit}}. */
    public static CliRunner.CliResult error(String name) {
        return error("1.14.3", name);
    }

    /** A captured failure run from {@code <version>/errors/<name>.{stdout,stderr,exit}}. */
    public static CliRunner.CliResult error(String version, String name) {
        String base = version + "/errors/" + name;
        return new CliRunner.CliResult(Integer.parseInt(text(base + ".exit").trim()),
                text(base + ".stdout"), text(base + ".stderr"));
    }

    /** {@code stdout} cut to its first half: a truncated payload made from a real capture. */
    public static String truncated(String stdout) {
        return stdout.substring(0, stdout.length() / 2);
    }
}
