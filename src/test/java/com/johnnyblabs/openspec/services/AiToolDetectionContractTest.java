package com.johnnyblabs.openspec.services;

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
class AiToolDetectionContractTest {

    static final Path CLI = Paths.get("src/test/resources/fixtures/cli");

    /** Each release's case count, so a lost case can't shrink the replay unnoticed. */
    static final Map<String, Integer> CASE_COUNT = Map.of("1.6.0", 8, "1.7.0", 11, "1.14.3", 30, "1.16.1", 30, "1.17.0", 35,
            "2.0.0", 40, "2.1.0", 53, "2.1.1", 53);

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

                    assertEquals(expected, ids(AiToolDetector.detect(project, home, DetectionRules.forVersion(v))));
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
