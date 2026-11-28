package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.CliFixtureSuite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;
import com.johnnyblabs.openspec.util.KnownGaps;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiToolDetector} reaches upstream's verdicts: each case is a project captured from real
 * {@code openspec init --tools} runs or an edge case, with the tools the CLI's own {@code getAvailableTools}
 * reported for it ({@code fixtures/cli/<version>/detect/}), replayed for every captured release.
 * <p>
 * From 2.0 a layout can hold links ({@link FixtureLayout}); they are written as real links, or as copies where the
 * platform can't make links, which upstream's existence checks treat the same. 2.x cases the plugin doesn't handle
 * yet are {@link KnownGaps} entries.
 */
@CliFixtureSuite
class AiToolDetectionContractTest {

    static final Path CLI = Paths.get("src/test/resources/fixtures/cli");

    /** Each release's case count, so a lost case can't shrink the replay unnoticed. */
    static final Map<String, Integer> CASE_COUNT = Map.ofEntries(Map.entry("1.3.1", 1), Map.entry("1.4.0", 1), Map.entry("1.5.0", 1),
            Map.entry("1.6.0", 13), Map.entry("1.7.0", 20), Map.entry("1.8.0", 8), Map.entry("1.10.0", 3),
            Map.entry("1.11.0", 4), Map.entry("1.13.2", 3), Map.entry("1.14.0", 2), Map.entry("1.14.3", 31),
            Map.entry("1.16.1", 30), Map.entry("1.17.0", 43), Map.entry("2.0.0", 40), Map.entry("2.1.0", 53),
            Map.entry("2.1.1", 56), Map.entry("2.2.0", 58));

    @TempDir
    Path tmp;

    static List<String> ids(List<AiTool> tools) {
        return tools.stream().map(AiTool::id).sorted().toList();
    }

    /** Writes a captured side ({@code project} or {@code home}) of a layout under {@code root}. */
    static void materialize(JsonObject files, Path root) throws IOException {
        FixtureLayout.writeFiles(files, root);
    }

    @TestFactory
    Stream<DynamicTest> detectsWhatUpstreamDetects() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String v : FixtureVersions.versionsWith("detect", CASE_COUNT.keySet())) {
            List<Path> cases;
            try (Stream<Path> dirs = Files.list(CLI.resolve(v).resolve("detect"))) {
                cases = dirs.sorted().toList();
            }
            assertEquals(CASE_COUNT.get(v), cases.size(), "sanity: every captured " + v + " case is replayed");
            for (Path c : cases) {
                String name = c.getFileName().toString();
                tests.add(DynamicTest.dynamicTest(v + "/" + name, () -> KnownGaps.check(v, "detect", name, () -> {
                    JsonObject layout = JsonParser.parseString(Files.readString(c.resolve("layout.json"))).getAsJsonObject();
                    List<String> expected = new ArrayList<>();
                    for (JsonElement id : JsonParser.parseString(Files.readString(c.resolve("detected.json"))).getAsJsonArray()) {
                        // 1.6 still called Devin Desktop "windsurf"; the plugin knows it by its later id.
                        expected.add(SkillToolList.ALIASES.getOrDefault(id.getAsString(), id.getAsString()));
                    }
                    Path project = tmp.resolve(v).resolve(name).resolve("project");
                    Path home = tmp.resolve(v).resolve(name).resolve("home");
                    FixtureLayout.materialize(layout, project, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);

                    assertEquals(expected, ids(AiToolDetector.detect(project, home, DetectionRules.forVersion(v), v)));
                })));
            }
        }
        return tests.stream();
    }

    /** A tool added without the CLI is then detected, alone in an otherwise empty project. */
    @Test
    void whatBuiltInAddCreatesIsDetected() throws IOException {
        for (AiTool tool : AiToolRegistry.all()) {
            String dir = AiToolRegistry.builtInDir(tool);
            // A tool 2.1 added is never added without the CLI (BuiltInAddContractTest pins that it needs it).
            if (dir == null || AiToolRegistry.addedIn21(tool)) continue;
            Path project = tmp.resolve("add-" + tool.id());
            Files.createDirectories(project.resolve(dir));
            String marker = AiToolRegistry.builtInMarker(tool);
            if (marker != null) Files.writeString(project.resolve(marker), tool.id() + "\n");

            assertTrue(ids(AiToolDetector.detect(project, null)).contains(tool.id()),
                    tool.id() + " is not detected after creating " + dir);
        }
    }
}
