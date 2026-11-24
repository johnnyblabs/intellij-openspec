package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Whether this host can make directory links, decided by making one in the test's own temporary directory (the same
 * file system the test writes to).
 * <p>
 * Only a developer's Windows machine may lack link support: without Developer Mode or admin rights it refuses a
 * symbolic link. There a link case is skipped, and the skip says why. On any other host, and on any CI host (CI runners,
 * Windows included, run with link rights), a failed probe is a broken environment, so the test fails rather than
 * silently dropping the link cases. Call {@link #requireLinks} per test, never from {@code @BeforeAll} or a
 * constructor, so the cases that need no link always run.
 */
public final class SymlinkProbe {

    private SymlinkProbe() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** {@code null} when a directory link can be made under {@code dir}, otherwise why not. */
    public static String probe(Path dir) {
        Path target = dir.resolve(".probe-target-" + System.nanoTime());
        Path link = dir.resolve(".probe-link-" + System.nanoTime());
        try {
            Files.createDirectories(target);
            Files.createSymbolicLink(link, target.getFileName());
            boolean ok = Files.isSymbolicLink(link) && Files.isDirectory(link);
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            return ok ? null : "the link was made but doesn't read as a directory link";
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            try {
                Files.deleteIfExists(link);
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // best effort
            }
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** Whether the test may skip a link case: a Windows machine that isn't a CI runner. */
    public static boolean maySkip() {
        String ci = System.getenv("CI");
        return isWindows() && (ci == null || ci.isBlank() || ci.equalsIgnoreCase("false"));
    }

    /** Skips the calling test on a developer's Windows machine without link support; fails it anywhere else. */
    public static void requireLinks(Path dir) {
        String why = probe(dir);
        if (why == null) return;
        if (maySkip()) {
            Assumptions.abort("this Windows host can't create symbolic links (Developer Mode off?): " + why);
        }
        fail("this host should create symbolic links but can't: " + why);
    }
}
