package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.CliFixtureSuite;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin's merge against real {@code openspec archive} runs ({@code fixtures/cli/<version>/retire/<case>/}, from
 * {@code scripts/capture-spec-retirement.py}): on each case's own inputs, it retires where that release deleted the
 * spec, and otherwise refuses with that release's {@code archive_spec_validation_failed} message and fix, word for word.
 */
@CliFixtureSuite
class RetirementContractTest {

    static final Set<String> REQUIRED = Set.of("1.7.0", "1.8.0", "1.11.0", "1.16.1", "2.2.0");
    /** Malformed markers: their reasons are checked by the marker reader's own contract test. */
    static final Set<String> MARKER_CASES = Set.of("unhonorable-marker", "marker-not-boolean", "marker-unknown-schema");

    static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @TestFactory
    Stream<DynamicTest> theMergeRetiresOrRefusesAsTheReleasesArchiveDoes() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        int retired = 0, refused = 0;
        for (String v : FixtureVersions.versionsWith("retire", REQUIRED)) {
            List<Path> cases;
            try (Stream<Path> dirs = Files.list(FixtureVersions.CLI.resolve(v).resolve("retire"))) {
                cases = dirs.filter(Files::isDirectory).sorted().toList();
            }
            for (Path c : cases) {
                String name = c.getFileName().toString();
                if (MARKER_CASES.contains(name)) continue;
                JsonObject input = json(c.resolve("case.json"));
                JsonObject cli = json(c.resolve("json.out"));
                boolean cliRetired = Files.readString(c.resolve("json.exit")).strip().equals("0");
                if (cliRetired) retired++; else refused++;
                tests.add(DynamicTest.dynamicTest(v + "/" + name, () -> {
                    boolean declared = input.get("metadata").getAsString().contains("retire_capabilities: true");
                    SpecMerge.Result r = SpecMerge.merge("foo", "c", input.get("delta").getAsString(),
                            input.get("spec").getAsString(), "openspec/specs/foo/spec.md", v,
                            new SpecMerge.RetirementMarker(declared, null));
                    if (cliRetired) {
                        assertTrue(json(c.resolve("after.json")).keySet().stream().noneMatch(k -> k.endsWith("foo/spec.md")),
                                "sanity: the CLI deleted the spec");
                        assertTrue(r.retired(), v + " " + name + " retired, the plugin did not: " + r.refusal());
                        assertNull(r.refusal());
                    } else {
                        JsonObject status = cli.getAsJsonArray("status").get(0).getAsJsonObject();
                        assertEquals("archive_spec_validation_failed", status.get("code").getAsString(), "sanity");
                        String expected = status.get("message").getAsString() + " " + status.get("fix").getAsString();
                        assertFalse(r.retired(), v + " " + name + " was refused, the plugin retired it");
                        assertEquals(expected, r.refusal(), v + " " + name);
                    }
                }));
            }
        }
        assertTrue(retired >= 10 && refused >= 30, "sanity: retired " + retired + ", refused " + refused);
        return tests.stream();
    }
}
