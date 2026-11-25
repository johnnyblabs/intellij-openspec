package com.johnnyblabs.openspec.root;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * The outcome of resolving a project's OpenSpec root, in the CLI's terms ({@code openspec context --json}).
 *
 * @param root    the directory that holds {@code openspec/}, canonicalized; {@code null} when nothing resolved
 * @param source  how it resolved; {@code null} when nothing resolved
 * @param storeId the store id for a store-backed root
 * @param problem why a declared or default store could not be used; {@code null} when there is none to show
 * @param warning a non-fatal note, such as a {@code store:} pointer ignored next to a local planning tree
 */
public record RootResolution(@Nullable Path root, @Nullable Source source, @Nullable String storeId,
                             @Nullable Problem problem, @Nullable String warning) {

    /** Where a root came from. {@link #cliName()} is the CLI's {@code root.source} value. */
    public enum Source {
        NEAREST("nearest"), DECLARED("declared"), GLOBAL_DEFAULT("global_default");

        private final String cliName;

        Source(String cliName) {
            this.cliName = cliName;
        }

        public String cliName() {
            return cliName;
        }
    }

    /** A reason a store root can't be used, with the CLI's diagnostic code. */
    public record Problem(String code, String message, @Nullable String fix) {
    }

    static RootResolution none() {
        return new RootResolution(null, null, null, null, null);
    }

    static RootResolution problem(Problem problem) {
        return new RootResolution(null, null, null, problem, null);
    }

    public boolean resolved() {
        return root != null;
    }

    public boolean storeBacked() {
        return storeId != null;
    }

    /** The {@code openspec/} directory under the root, or {@code null} when nothing resolved. */
    public @Nullable Path openspecDir() {
        return root == null ? null : root.resolve(RootResolver.OPENSPEC_DIR);
    }
}
