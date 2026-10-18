package com.johnnyblabs.openspec.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

    /** A captured failure run from {@code 1.14.3/errors/<name>.{stdout,stderr,exit}}. */
    public static CliRunner.CliResult error(String name) {
        String base = "1.14.3/errors/" + name;
        return new CliRunner.CliResult(Integer.parseInt(text(base + ".exit").trim()),
                text(base + ".stdout"), text(base + ".stderr"));
    }

    /** {@code stdout} cut to its first half: a truncated payload made from a real capture. */
    public static String truncated(String stdout) {
        return stdout.substring(0, stdout.length() / 2);
    }
}
