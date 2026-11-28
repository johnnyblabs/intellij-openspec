package com.johnnyblabs.openspec.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code docs/cli-parity.md} names only what exists, and names every contract test: each test class and fixture path it
 * mentions is in the tree (a {@code <v>} stands for any captured release), and every {@code *ContractTest} or
 * {@code @CliFixtureSuite} class appears in it. The page's content (whether a row is complete) is the parity sweep's to
 * judge; this keeps its names from drifting.
 */
class InventoryContractTest {

    private static final Path DOC = Path.of("docs/cli-parity.md");
    private static final Path TESTS = Path.of("src/test/java");
    private static final Path RESOURCES = Path.of("src/test/resources");

    private static Set<String> testClasses() throws IOException {
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.walk(TESTS)) {
            files.filter(f -> f.toString().endsWith(".java"))
                    .forEach(f -> out.add(f.getFileName().toString().replace(".java", "")));
        }
        return out;
    }

    private static Set<String> named(String doc) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("`([A-Z][A-Za-z0-9]*Test)`").matcher(doc);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void everyTestItNamesExists() throws IOException {
        Set<String> missing = new TreeSet<>(named(Files.readString(DOC)));
        missing.removeAll(testClasses());
        assertEquals(Set.of(), missing);
    }

    @Test
    void everyFixturePathItNamesExists() throws IOException {
        String doc = Files.readString(DOC);
        Matcher m = Pattern.compile("`(fixtures/cli/[^`]*)`").matcher(doc);
        List<String> missing = new ArrayList<>();
        int checked = 0;
        while (m.find()) {
            String path = m.group(1);
            checked++;
            if (!path.contains("<v>")) {
                if (!Files.exists(RESOURCES.resolve(path))) missing.add(path);
                continue;
            }
            String[] parts = path.split("<v>", 2);
            Path base = RESOURCES.resolve(parts[0]);
            String rest = parts[1].startsWith("/") ? parts[1].substring(1) : parts[1];
            boolean any;
            try (Stream<Path> releases = Files.list(base)) {
                any = releases.anyMatch(r -> r.getFileName().toString().matches("\\d+\\.\\d+\\.\\d+")
                        && Files.exists(rest.isEmpty() ? r : r.resolve(rest)));
            }
            if (!any) missing.add(path);
        }
        assertTrue(checked >= 20, "sanity: " + checked + " paths");
        assertEquals(List.of(), missing);
    }

    @Test
    void everyContractTestIsInTheInventory() throws IOException {
        Set<String> named = named(Files.readString(DOC));
        Set<String> unlisted = new TreeSet<>();
        Pattern fixtureSuite = Pattern.compile("(?m)^@CliFixtureSuite\\s*$");
        try (Stream<Path> files = Files.walk(TESTS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = f.getFileName().toString().replace(".java", "");
                if (name.equals("InventoryContractTest")) continue;
                if (name.endsWith("ContractTest") || fixtureSuite.matcher(Files.readString(f)).find()) {
                    if (!named.contains(name)) unlisted.add(name);
                }
            }
        }
        assertEquals(Set.of(), unlisted, "add each to docs/cli-parity.md: a port, a reader, or not a CLI port");
    }
}
