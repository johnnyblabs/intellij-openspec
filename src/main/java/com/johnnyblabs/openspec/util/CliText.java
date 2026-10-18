package com.johnnyblabs.openspec.util;

import java.util.regex.Pattern;

/**
 * Plain-text handling for OpenSpec CLI output. {@link CliRunner} asks the CLI for uncoloured output
 * ({@code NO_COLOR}/{@code FORCE_COLOR=0}); this is the second layer, for a wrapper or shell that forces colour anyway.
 */
public final class CliText {

    // CSI sequences (ESC [ params intermediates final), which is all the CLI's colour library emits.
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");

    private CliText() {
    }

    /** {@code text} with ANSI escape sequences removed; null stays null. */
    public static String stripAnsi(String text) {
        if (text == null || text.indexOf('\u001B') < 0) {
            return text;
        }
        return ANSI.matcher(text).replaceAll("");
    }
}
