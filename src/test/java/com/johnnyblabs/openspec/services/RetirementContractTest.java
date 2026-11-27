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
    /**
     * The one case whose refusal can't be matched word for word: the CLI's reason quotes its own schema validator on an
     * invalid project override, which the plugin doesn't port. It refuses the same way, leaving that reason out.
     */
    static final Set<String> REASON_LEFT_OUT = Set.of("marker-invalid-project-schema");

    @org.junit.jupiter.api.io.TempDir
    Path tmp;

    static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    static List<String> warnings(JsonObject cli) {
        List<String> out = new ArrayList<>();
        JsonObject archive = cli.getAsJsonObject("archive");
        if (archive != null && archive.has("warnings")) archive.getAsJsonArray("warnings").forEach(w -> out.add(w.getAsString()));
        return out;
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
                JsonObject input = json(c.resolve("case.json"));
                JsonObject cli = json(c.resolve("json.out"));
                boolean cliRetired = Files.readString(c.resolve("json.exit")).strip().equals("0");
                if (cliRetired) retired++; else refused++;
                tests.add(DynamicTest.dynamicTest(v + "/" + name, () -> {
                    // The case's project files on disk, for the schema lookup; no CLI accepts the captured override.
                    Path root = tmp.resolve(v).resolve(name);
                    Files.createDirectories(root.resolve("openspec"));
                    for (var e : input.getAsJsonObject("extra").entrySet()) {
                        Path f = root.resolve(e.getKey());
                        Files.createDirectories(f.getParent());
                        Files.writeString(f, e.getValue().getAsString());
                    }
                    SpecMerge.RetirementMarker marker = RetirementMarkers.parse(input.get("metadata").getAsString(),
                            schema -> SpecSyncService.schemaVerdict(schema, root.resolve("openspec"),
                                    tmp.resolve("no-user-schemas"), n -> false));
                    String spec = input.get("spec").isJsonNull() ? null : input.get("spec").getAsString();
                    SpecMerge.Result r = SpecMerge.merge("foo", "c", input.get("delta").getAsString(), spec,
                            "openspec/specs/foo/spec.md", v, marker);
                    if (cliRetired && spec == null) {
                        // Already retired: the CLI archives, writing no spec, with its warning.
                        assertNull(r.refusal(), v + " " + name + ": " + r.refusal());
                        assertFalse(r.retired());
                        assertEquals(warnings(cli), r.warnings(), v + " " + name);
                        assertNull(r.projected(), v + " " + name + ": no spec is written");
                    } else if (cliRetired) {
                        assertTrue(json(c.resolve("after.json")).keySet().stream().noneMatch(k -> k.endsWith("foo/spec.md")),
                                "sanity: the CLI deleted the spec");
                        assertTrue(r.retired(), v + " " + name + " retired, the plugin did not: " + r.refusal());
                        assertNull(r.refusal());
                    } else {
                        JsonObject status = cli.getAsJsonArray("status").get(0).getAsJsonObject();
                        assertEquals("archive_spec_validation_failed", status.get("code").getAsString(), "sanity");
                        String expected = status.get("message").getAsString() + " " + status.get("fix").getAsString();
                        if (REASON_LEFT_OUT.contains(name)) {
                            expected = expected.replaceFirst(" The marker present now cannot be honored \\(.*\\)\\.$", "");
                        }
                        assertFalse(r.retired(), v + " " + name + " was refused, the plugin retired it");
                        assertEquals(expected, r.refusal(), v + " " + name);
                    }
                }));
            }
        }
        assertTrue(retired >= 14 && refused >= 70, "sanity: retired " + retired + ", refused " + refused);
        return tests.stream();
    }
}
