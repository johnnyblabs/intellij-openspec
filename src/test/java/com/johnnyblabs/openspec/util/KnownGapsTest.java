package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.CliFixtureSuite;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every {@link KnownGaps} entry names a case that runs. A mistyped key never matches a test, so its gap would never
 * be checked and would linger after the gap closed; this test fails on it instead.
 */
@CliFixtureSuite
class KnownGapsTest {

    /** The case names each family's contract test passes to {@link KnownGaps#check}, beyond per-capture names. */
    static final Map<String, Set<String>> FIXED_CASES = Map.of(
            "registry", Set.of("tools-in-order", "tool-fields", "aliases", "passed-ids-accepted"),
            "invocation", Set.of("every-row", "registry-paths"));

    static final Set<String> CLOSERS = Set.of("skills-layout-2x", "openspec-target-2x",
            "init-skills-scope", "update-migration-2x", "detection-1x-release-rules", "skills-scope-parity");

    @Test
    void everyKeyNamesACaseThatRuns() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> gap : KnownGaps.GAPS.entrySet()) {
            String[] parts = gap.getKey().split("/", 3);
            if (parts.length != 3) {
                bad.add(gap.getKey() + ": not <version>/<family>/<case>");
                continue;
            }
            Path release = FixtureVersions.CLI.resolve(parts[0]);
            boolean real = switch (parts[1]) {
                case "detect" -> Files.isRegularFile(release.resolve("detect").resolve(parts[2]).resolve("layout.json"));
                case "machine-wide" -> Files.isRegularFile(release.resolve("machine-wide").resolve(parts[2]).resolve("probe.json"));
                case "update-dry-run" -> Files.isRegularFile(release.resolve("update-dry-run").resolve(parts[2] + ".exit"));
                case "registry" -> Files.isRegularFile(release.resolve("ai-tools.json"))
                        && FIXED_CASES.get("registry").contains(parts[2]);
                case "invocation" -> Files.isRegularFile(release.resolve("workflow-invocations.json"))
                        && FIXED_CASES.get("invocation").contains(parts[2]);
                default -> false;
            };
            if (!real) bad.add(gap.getKey() + ": no such capture or case");
            if (!CLOSERS.contains(gap.getValue())) bad.add(gap.getKey() + ": unknown closing change " + gap.getValue());
        }
        assertEquals(List.of(), bad);
    }

    /** A gap to exercise the mechanism with, since none is open. */
    static final Map<String, String> SAMPLE = Map.of("2.1.1/registry/tools-in-order", "tool-registry-2x");

    @Test
    void aListedCaseMustStillFail() throws Throwable {
        String key = SAMPLE.keySet().iterator().next();
        String[] p = key.split("/", 3);
        // Still failing: the gap holds, so check passes.
        KnownGaps.check(SAMPLE, p[0], p[1], p[2], () -> fail("still diverges"));
        // Now passing: the gap closed, so check fails and asks for the entry to go.
        AssertionFailedError e = assertThrows(AssertionFailedError.class, () -> KnownGaps.check(SAMPLE, p[0], p[1], p[2], () -> {
        }));
        assertTrue(e.getMessage().contains("gap fixed: remove KnownGaps entry " + key), e.getMessage());
    }

    @Test
    void aListedCaseThatCrashesIsNotTheGap() {
        String key = SAMPLE.keySet().iterator().next();
        String[] p = key.split("/", 3);
        assertThrows(AssertionFailedError.class, () -> KnownGaps.check(SAMPLE, p[0], p[1], p[2], () -> {
            throw new IllegalStateException("a parser crash is a failure, not the gap");
        }));
    }

    @Test
    void anUnlistedCaseRunsDirectly() {
        assertThrows(AssertionFailedError.class, () -> KnownGaps.check("1.17.0", "detect", "claude", () -> fail("real")));
    }
}
