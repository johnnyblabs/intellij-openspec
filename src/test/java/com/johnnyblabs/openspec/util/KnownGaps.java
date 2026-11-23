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
     * with links, detection and lookup), {@code tool-registry-2x} (the 2.x tool list), {@code openspec-target-2x}
     * (the multi-line {@code .openspec-target}, including 2.0's {@code keep} lines).
     */
    public static final Map<String, String> GAPS = new TreeMap<>(Map.ofEntries(
            Map.entry("2.0.0/detect/agents-unmarked-codex-syntax", "skills-layout-2x"),
            Map.entry("2.0.0/detect/agents-unmarked-generic", "skills-layout-2x"),
            Map.entry("2.0.0/detect/antigravity", "skills-layout-2x"),
            Map.entry("2.0.0/detect/bob", "skills-layout-2x"),
            Map.entry("2.0.0/detect/claude", "skills-layout-2x"),
            Map.entry("2.0.0/detect/codex-and-antigravity", "skills-layout-2x"),
            Map.entry("2.0.0/detect/codex-legacy-beside-unmarked-agents", "skills-layout-2x"),
            Map.entry("2.0.0/detect/devin-via-windsurf-id", "skills-layout-2x"),
            Map.entry("2.0.0/detect/github-copilot", "skills-layout-2x"),
            Map.entry("2.0.0/detect/gsd", "skills-layout-2x"),
            Map.entry("2.0.0/detect/kimi", "skills-layout-2x"),
            Map.entry("2.0.0/detect/mixed-1.17-and-2x", "skills-layout-2x"),
            Map.entry("2.0.0/detect/roocode", "skills-layout-2x"),
            Map.entry("2.0.0/detect/rovodev", "skills-layout-2x"),
            Map.entry("2.0.0/detect/several", "skills-layout-2x"),
            Map.entry("2.0.0/detect/user-skill-kept", "openspec-target-2x"),
            Map.entry("2.0.0/invocation/every-row", "skills-layout-2x"),
            Map.entry("2.0.0/invocation/registry-paths", "skills-layout-2x"),
            Map.entry("2.0.0/registry/passed-ids-accepted", "tool-registry-2x"),
            Map.entry("2.0.0/registry/tools-in-order", "tool-registry-2x"),
            Map.entry("2.1.0/detect/agents-unmarked-codex-syntax", "skills-layout-2x"),
            Map.entry("2.1.0/detect/agents-unmarked-generic", "skills-layout-2x"),
            Map.entry("2.1.0/detect/antigravity", "skills-layout-2x"),
            Map.entry("2.1.0/detect/bob", "skills-layout-2x"),
            Map.entry("2.1.0/detect/claude", "skills-layout-2x"),
            Map.entry("2.1.0/detect/codex-and-antigravity", "skills-layout-2x"),
            Map.entry("2.1.0/detect/codex-legacy-beside-unmarked-agents", "skills-layout-2x"),
            Map.entry("2.1.0/detect/devin-via-windsurf-id", "skills-layout-2x"),
            Map.entry("2.1.0/detect/github-copilot", "skills-layout-2x"),
            Map.entry("2.1.0/detect/gsd", "skills-layout-2x"),
            Map.entry("2.1.0/detect/kimi", "skills-layout-2x"),
            Map.entry("2.1.0/detect/minimax-global", "skills-layout-2x"),
            Map.entry("2.1.0/detect/mixed-1.17-and-2x", "skills-layout-2x"),
            Map.entry("2.1.0/detect/roocode", "skills-layout-2x"),
            Map.entry("2.1.0/detect/rovodev", "skills-layout-2x"),
            Map.entry("2.1.0/detect/scope-project", "skills-layout-2x"),
            Map.entry("2.1.0/detect/several", "skills-layout-2x"),
            Map.entry("2.1.0/detect/skills-lock-agents", "skills-layout-2x"),
            Map.entry("2.1.0/detect/user-skill-kept", "openspec-target-2x"),
            Map.entry("2.1.0/invocation/every-row", "skills-layout-2x"),
            Map.entry("2.1.0/invocation/registry-paths", "skills-layout-2x"),
            Map.entry("2.1.0/registry/passed-ids-accepted", "tool-registry-2x"),
            Map.entry("2.1.0/registry/tool-fields", "tool-registry-2x"),
            Map.entry("2.1.0/registry/tools-in-order", "tool-registry-2x"),
            Map.entry("2.1.1/detect/agents-unmarked-codex-syntax", "skills-layout-2x"),
            Map.entry("2.1.1/detect/agents-unmarked-generic", "skills-layout-2x"),
            Map.entry("2.1.1/detect/antigravity", "skills-layout-2x"),
            Map.entry("2.1.1/detect/bob", "skills-layout-2x"),
            Map.entry("2.1.1/detect/claude", "skills-layout-2x"),
            Map.entry("2.1.1/detect/codex-and-antigravity", "skills-layout-2x"),
            Map.entry("2.1.1/detect/codex-legacy-beside-unmarked-agents", "skills-layout-2x"),
            Map.entry("2.1.1/detect/devin-via-windsurf-id", "skills-layout-2x"),
            Map.entry("2.1.1/detect/github-copilot", "skills-layout-2x"),
            Map.entry("2.1.1/detect/gsd", "skills-layout-2x"),
            Map.entry("2.1.1/detect/kimi", "skills-layout-2x"),
            Map.entry("2.1.1/detect/minimax-global", "skills-layout-2x"),
            Map.entry("2.1.1/detect/mixed-1.17-and-2x", "skills-layout-2x"),
            Map.entry("2.1.1/detect/roocode", "skills-layout-2x"),
            Map.entry("2.1.1/detect/rovodev", "skills-layout-2x"),
            Map.entry("2.1.1/detect/scope-project", "skills-layout-2x"),
            Map.entry("2.1.1/detect/several", "skills-layout-2x"),
            Map.entry("2.1.1/detect/user-skill-kept", "openspec-target-2x"),
            Map.entry("2.1.1/invocation/every-row", "skills-layout-2x"),
            Map.entry("2.1.1/invocation/registry-paths", "skills-layout-2x"),
            Map.entry("2.1.1/registry/passed-ids-accepted", "tool-registry-2x"),
            Map.entry("2.1.1/registry/tool-fields", "tool-registry-2x"),
            Map.entry("2.1.1/registry/tools-in-order", "tool-registry-2x")
    ));

    private KnownGaps() {
    }

    public static String key(String version, String family, String caseName) {
        return version + "/" + family + "/" + caseName;
    }

    /** Runs {@code assertion}: directly for a case that isn't a gap, expecting it to fail for one that is. */
    public static void check(String version, String family, String caseName, Executable assertion) throws Throwable {
        String key = key(version, family, caseName);
        String closer = GAPS.get(key);
        if (closer == null) {
            assertion.execute();
            return;
        }
        assertThrows(AssertionFailedError.class, assertion,
                "gap fixed: remove KnownGaps entry " + key + " (closed by " + closer + ")");
    }
}
