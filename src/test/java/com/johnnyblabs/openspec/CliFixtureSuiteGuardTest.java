package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the CLI-fixture suite whole: every plain-JUnit (Jupiter) test class that replays captured CLI releases carries
 * {@link CliFixtureSuite}, so it runs in {@code cliFixtureTest}; no platform (JUnit 3) class carries it, because the tag
 * can't select those. Runs in {@code test}.
 */
class CliFixtureSuiteGuardTest {

    private static final Path TESTS = Path.of("src/test/java");
    private static final Pattern READS_RELEASES = Pattern.compile("FixtureVersions\\.");
    private static final Pattern PLATFORM = Pattern.compile("extends\\s+\\w*(TestCase|TestBase)\\b");
    private static final Pattern CLI_MATRIX = Pattern.compile("@Tag\\(\"cli-matrix\"\\)");

    static List<String> untagged(Stream<Path> sources) {
        List<String> wrong = new ArrayList<>();
        sources.forEach(p -> {
            String s;
            try {
                s = Files.readString(p);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            boolean tagged = s.contains("@CliFixtureSuite");
            boolean platform = PLATFORM.matcher(s).find();
            if (platform && tagged) wrong.add(p + ": a platform test can't be selected by the tag");
            if (platform || CLI_MATRIX.matcher(s).find() || !s.contains("org.junit.jupiter")) return;
            if (READS_RELEASES.matcher(s).find() && !tagged) wrong.add(p + ": reads captured releases but lacks @CliFixtureSuite");
        });
        return wrong;
    }

    @Test
    void everyPlainJUnitClassThatReplaysCapturedReleasesIsInTheFixtureSuite() throws IOException {
        List<Path> sources;
        try (Stream<Path> all = Files.walk(TESTS)) {
            sources = all.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("CliFixtureSuiteGuardTest.java")).toList();
        }
        assertEquals(List.of(), untagged(sources.stream()), "tag these with @CliFixtureSuite (or remove it)");
        long suite = sources.stream().filter(p -> {
            try {
                return Files.readString(p).contains("@CliFixtureSuite\n");
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }).count();
        assertTrue(suite >= 25, "sanity: fixture-suite classes " + suite);
    }
}
