package com.johnnyblabs.openspec.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * The captured CLI versions that hold a fixture family, for contract tests that run once per version.
 * <p>
 * A version-parameterized test must never pass by running nothing, so every lookup names the versions it requires
 * and fails when one is missing: deleting a capture can't silently shrink the matrix.
 */
public final class FixtureVersions {

    public static final Path CLI = Paths.get("src/test/resources/fixtures/cli");

    private FixtureVersions() {
    }

    /** Version-named folders under {@code fixtures/cli/} that contain {@code relative}, oldest first. */
    public static List<String> versionsWith(String relative) {
        List<String> out = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(CLI)) {
            for (Path d : dirs.toList()) {
                String name = d.getFileName().toString();
                if (name.matches("\\d+\\.\\d+\\.\\d+") && Files.exists(d.resolve(relative))) out.add(name);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(FixtureVersions::compare);
        return out;
    }

    /** {@link #versionsWith}, failing unless every version in {@code required} is among them. */
    public static List<String> versionsWith(String relative, Collection<String> required) {
        List<String> found = versionsWith(relative);
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new AssertionError("captures missing " + relative + " for " + missing + " (found " + found + ")");
        }
        return found;
    }

    /** {@code versions} from {@code floor} up. */
    public static List<String> atLeast(List<String> versions, String floor) {
        return versions.stream().filter(v -> compare(v, floor) >= 0).toList();
    }

    public static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i]));
            if (c != 0) return c;
        }
        return 0;
    }
}
