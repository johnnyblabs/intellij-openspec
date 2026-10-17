package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
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
 * reported for it ({@code fixtures/cli/1.14.3/detect/}).
 */
class AiToolDetectionContractTest {

    static final Path CASES = Paths.get("src/test/resources/fixtures/cli/1.14.3/detect");

    @TempDir
    Path tmp;

    static List<String> ids(List<AiTool> tools) {
        return tools.stream().map(AiTool::id).sorted().toList();
    }

    /** Writes a captured side ({@code project} or {@code home}) of a layout under {@code root}. */
    static void materialize(JsonObject files, Path root) throws IOException {
        Files.createDirectories(root);
        for (Map.Entry<String, JsonElement> f : files.entrySet()) {
            Path path = root.resolve(f.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, f.getValue().getAsString());
        }
    }

    @TestFactory
    Stream<DynamicTest> detectsWhatUpstreamDetects() throws IOException {
        List<Path> cases;
        try (Stream<Path> dirs = Files.list(CASES)) {
            cases = dirs.sorted().toList();
        }
        assertEquals(30, cases.size(), "sanity: every captured case is replayed");
        return cases.stream().map(c -> DynamicTest.dynamicTest(c.getFileName().toString(), () -> {
            JsonObject layout = JsonParser.parseString(Files.readString(c.resolve("layout.json"))).getAsJsonObject();
            List<String> expected = new ArrayList<>();
            for (JsonElement id : JsonParser.parseString(Files.readString(c.resolve("detected.json"))).getAsJsonArray()) {
                expected.add(id.getAsString());
            }
            Path project = tmp.resolve(c.getFileName().toString()).resolve("project");
            Path home = tmp.resolve(c.getFileName().toString()).resolve("home");
            materialize(layout.getAsJsonObject("project"), project);
            materialize(layout.getAsJsonObject("home"), home);

            assertEquals(expected, ids(AiToolDetector.detect(project, home)));
        }));
    }

    /** A tool added without the CLI is then detected, alone in an otherwise empty project. */
    @Test
    void whatBuiltInAddCreatesIsDetected() throws IOException {
        for (AiTool tool : AiToolRegistry.all()) {
            String dir = AiToolRegistry.builtInDir(tool);
            if (dir == null) continue;
            Path project = tmp.resolve("add-" + tool.id());
            Files.createDirectories(project.resolve(dir));
            String marker = AiToolRegistry.builtInMarker(tool);
            if (marker != null) Files.writeString(project.resolve(marker), tool.id() + "\n");

            assertTrue(ids(AiToolDetector.detect(project, null)).contains(tool.id()),
                    tool.id() + " is not detected after creating " + dir);
        }
    }
}
