package com.johnnyblabs.openspec.util;

import org.jetbrains.annotations.Nullable;

/**
 * The installed OpenSpec CLI doesn't accept a command, or a flag the command can't work without. Raised before any
 * process starts, so the user sees which release they need instead of the CLI's "unknown option".
 */
public final class UnsupportedCliArgumentException extends CliRunner.CliException {

    private final String command;
    private final @Nullable String flag;

    UnsupportedCliArgumentException(String command, @Nullable String flag, CliFlags.Range range, String version) {
        super(message(command, flag, range, version));
        this.command = command;
        this.flag = flag;
    }

    public String command() {
        return command;
    }

    /** The refused flag, or null when the command itself is missing. */
    public @Nullable String flag() {
        return flag;
    }

    private static String message(String command, @Nullable String flag, CliFlags.Range range, String version) {
        String what = flag == null ? "openspec " + command : "openspec " + command + " " + flag;
        String needs = range.until() == null
                ? "needs OpenSpec CLI " + range.since() + " or newer"
                : "needs an OpenSpec CLI from " + range.since() + " up to, but not including, " + range.until();
        return what + " " + needs + "; the installed CLI is " + version + ".";
    }
}
