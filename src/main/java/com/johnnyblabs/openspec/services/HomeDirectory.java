package com.johnnyblabs.openspec.services;

import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * The user's home directory, resolved the way the OpenSpec CLI resolves it ({@code USERPROFILE ?? HOME ?? os.homedir()}
 * in {@code dist/core/shared/skill-paths.js}), so the plugin reads machine-wide skills exactly where the CLI wrote them.
 * On Windows {@code USERPROFILE} and {@code user.home} usually agree, but profile redirection or a {@code HOME} set by
 * Git Bash or MSYS can split them.
 * <p>
 * One deliberate divergence: upstream's {@code ??} accepts an empty {@code USERPROFILE} or {@code HOME}, which makes it
 * join a <em>relative</em> {@code .agents/skills} onto its working directory. That is no home, so blank values are
 * skipped here and the next source is used.
 */
public final class HomeDirectory {

    private HomeDirectory() {
    }

    /** The home directory from {@code env} and the JVM {@code properties}, or {@code null} when none is set. */
    public static @Nullable Path resolve(Map<String, String> env, Function<String, String> properties) {
        for (String candidate : new String[] {env.get("USERPROFILE"), env.get("HOME"), properties.apply("user.home")}) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                return Path.of(candidate);
            } catch (InvalidPathException e) {
                // A value this platform can't parse is no home; try the next.
            }
        }
        return null;
    }

    private static volatile @Nullable Map<String, String> envForTest;

    /** The home directory of this process, from its environment and system properties. */
    public static @Nullable Path current() {
        Map<String, String> env = envForTest;
        return resolve(env != null ? env : System.getenv(), System::getProperty);
    }

    /** Test seam: the environment {@link #current} reads; {@code null} restores the real one. */
    static void setEnvForTest(@Nullable Map<String, String> env) {
        envForTest = env;
    }
}
