package com.johnnyblabs.openspec.model;

import java.util.List;

/**
 * Result of {@code openspec schema validate <name> --json}.
 *
 * <p>Two failure axes are distinct: {@code valid == false} means the CLI examined the schema
 * and reported problems ({@link #issues}); a non-null {@link #cliError} means the CLI call
 * itself failed (non-zero exit without parseable JSON) and carries its error output.
 */
public record SchemaValidationReport(
        String name,
        boolean valid,
        List<Issue> issues,
        String cliError
) {
    /** One problem reported by the CLI: severity level, offending path, message. */
    public record Issue(String level, String path, String message) {
    }

    public static SchemaValidationReport cliFailure(String name, String stderr) {
        return new SchemaValidationReport(name, false, List.of(), stderr == null ? "" : stderr);
    }

    public boolean isCliFailure() {
        return cliError != null;
    }
}
