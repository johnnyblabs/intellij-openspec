package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.CliFixtureSuite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code --help} sweep ({@code fixtures/cli/<version>/help/}, from {@code scripts/capture-help-sweep.py}) is the
 * per-release record of which commands and flags the CLI accepts, which a flag check compares the plugin's command
 * lines against. These tests keep that record whole: every supported line has a sweep, every index entry points at a
 * help text that exists and is that command's, and a missing command is recorded rather than silently absent.
 */
@CliFixtureSuite
class HelpSweepContractTest {

    static final Path CLI = Paths.get("src/test/resources/fixtures/cli");

    /** One sweep per supported line: the newest patch of each 1.x minor from the floor, and each 2.x release. */
    static final Set<String> FLOOR = Set.of("1.3.1", "1.4.1", "1.5.0", "1.6.0", "1.7.0", "1.8.0", "1.9.0", "1.10.0",
            "1.11.0", "1.12.0", "1.13.2", "1.14.3", "1.15.0", "1.16.1", "1.17.0", "2.0.0", "2.1.0", "2.1.1", "2.2.0");

    /** Commands every release from the floor has, so their help must always be captured. */
    static final List<String> ALWAYS = List.of("openspec", "init", "update", "validate", "status", "instructions",
            "archive", "list", "show", "new change", "config", "schemas", "templates");

    static List<Path> sweeps() throws IOException {
        try (Stream<Path> dirs = Files.list(CLI)) {
            return dirs.filter(d -> Files.isRegularFile(d.resolve("help/index.json"))).sorted().toList();
        }
    }

    static JsonObject index(Path versionDir) throws IOException {
        return JsonParser.parseString(Files.readString(versionDir.resolve("help/index.json"))).getAsJsonObject();
    }

    @Test
    void everySupportedLineHasASweep() throws IOException {
        Set<String> found = new TreeSet<>();
        for (Path d : sweeps()) found.add(d.getFileName().toString());
        assertTrue(found.containsAll(FLOOR), "missing sweeps: " + new TreeSet<>(difference(FLOOR, found)));
    }

    @TestFactory
    Stream<DynamicTest> everyIndexIsWhole() throws IOException {
        List<Path> sweeps = sweeps();
        assertFalse(sweeps.isEmpty(), "sanity: there are sweeps to check");
        return sweeps.stream().map(d -> DynamicTest.dynamicTest(d.getFileName().toString(), () -> {
            JsonObject index = index(d);
            assertEquals(d.getFileName().toString(), index.get("cliVersion").getAsString(), "index names its release");
            JsonObject commands = index.getAsJsonObject("commands");
            for (String always : ALWAYS) {
                assertTrue(commands.has(always), always + " is not in the index");
                assertFalse(commands.getAsJsonObject(always).get("file").isJsonNull(), always + " has no help text");
            }
            for (Map.Entry<String, JsonElement> e : commands.entrySet()) {
                JsonObject entry = e.getValue().getAsJsonObject();
                if (entry.get("file").isJsonNull()) {
                    continue; // a command this release doesn't have, recorded with its exit code
                }
                Path help = d.resolve("help").resolve(entry.get("file").getAsString());
                assertTrue(Files.isRegularFile(help), e.getKey() + ": " + help + " is missing");
                String usage = Files.readString(help).lines().filter(l -> l.startsWith("Usage:")).findFirst()
                        .orElse("").replaceAll("\\|\\S+", "");
                String expected = "Usage: openspec" + (e.getKey().equals("openspec") ? "" : " " + e.getKey()) + " ";
                assertTrue(usage.startsWith(expected), e.getKey() + ": help is another command's: " + usage);
            }
        }));
    }

    /** The guard against reading the top-level help as a command's: 1.3.1 had no store, and says so in the index. */
    @Test
    void aCommandAReleaseLacksIsRecordedAsMissing() throws IOException {
        JsonObject store = index(CLI.resolve("1.3.1")).getAsJsonObject("commands").getAsJsonObject("store");
        assertNotNull(store, "1.3.1 index records store");
        assertTrue(store.get("file").isJsonNull(), "1.3.1 has no store command");
        assertTrue(index(CLI.resolve("2.1.1")).getAsJsonObject("commands").has("store register"));
    }

    /**
     * The coordination commands the plugin gates by release: {@code workspace}, {@code context-store} and
     * {@code initiative} exist only in 1.4.x, and {@code doctor} from 1.5.0. Every sweep records each of them, with a
     * help text exactly where the release has the command.
     */
    @TestFactory
    Stream<DynamicTest> versionScopedCommandsAppearExactlyWhereTheReleaseHasThem() throws IOException {
        return sweeps().stream().map(d -> DynamicTest.dynamicTest(d.getFileName().toString(), () -> {
            String version = d.getFileName().toString();
            JsonObject commands = index(d).getAsJsonObject("commands");
            boolean onePointFour = version.startsWith("1.4.");
            Map<String, Boolean> expected = Map.of("workspace", onePointFour, "context-store", onePointFour,
                    "initiative", onePointFour, "doctor", FixtureVersions.compare(version, "1.5.0") >= 0);
            for (Map.Entry<String, Boolean> e : expected.entrySet()) {
                assertTrue(commands.has(e.getKey()), version + " index does not record " + e.getKey());
                boolean captured = !commands.getAsJsonObject(e.getKey()).get("file").isJsonNull();
                assertEquals(e.getValue(), captured, version + " " + e.getKey() + " help captured");
            }
        }));
    }

    /** 2.1.0 is the first release whose init takes --scope: the record the scope flag is gated on. */
    @Test
    void scopeArrivesIn210() throws IOException {
        assertFalse(Files.readString(CLI.resolve("2.0.0/help/init.txt")).contains("--scope"));
        assertTrue(Files.readString(CLI.resolve("2.1.0/help/init.txt")).contains("--scope <scope>"));
        assertTrue(Files.readString(CLI.resolve("2.1.1/help/init.txt")).contains("--scope <scope>"));
        assertFalse(Files.readString(CLI.resolve("1.17.0/help/init.txt")).contains("--scope"));
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> d = new HashSet<>(a);
        d.removeAll(b);
        return d;
    }
}
