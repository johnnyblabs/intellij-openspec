package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The CLI 2.x cases the plugin doesn't handle yet, each named with the change that closes it.
 * <p>
 * The contract tests run over every captured CLI version. A case listed here still runs, and must still fail its
 * assertion: once the change that closes a gap lands, the case passes, {@link #check} fails, and the entry has to be
 * removed in that change. A gap can't be added without a reason, and a fixed one can't linger. Only an assertion
 * failure counts as the gap: any other exception (a reshaped fixture, a parser crash) fails the test as usual.
 * <p>
 * Keys are {@code <version>/<family>/<case>}. {@code KnownGapsTest} checks that every key names a real capture.
 */
public final class KnownGaps {

    /**
     * Key to the change that closes the gap. Closers: {@code skills-layout-2x} (skills under {@code .agents/skills/}
     * with links, and lookup; it closed every detection gap, including 2.0's {@code keep} lines, which detection
     * ignores) and {@code tool-registry-2x} (the 2.x tool list) closed every gap they owned, so none is open.
     */
    public static final Map<String, String> GAPS = new TreeMap<>(Map.ofEntries(
    ));

    private KnownGaps() {
    }

    public static String key(String version, String family, String caseName) {
        return version + "/" + family + "/" + caseName;
    }

    /** Runs {@code assertion}: directly for a case that isn't a gap, expecting it to fail for one that is. */
    public static void check(String version, String family, String caseName, Executable assertion) throws Throwable {
        check(GAPS, version, family, caseName, assertion);
    }

    /** {@link #check} against {@code gaps}, so the mechanism can be tested while no gap is open. */
    static void check(Map<String, String> gaps, String version, String family, String caseName, Executable assertion)
            throws Throwable {
        String key = key(version, family, caseName);
        String closer = gaps.get(key);
        if (closer == null) {
            assertion.execute();
            return;
        }
        assertThrows(AssertionFailedError.class, assertion,
                "gap fixed: remove KnownGaps entry " + key + " (closed by " + closer + ")");
    }
}
