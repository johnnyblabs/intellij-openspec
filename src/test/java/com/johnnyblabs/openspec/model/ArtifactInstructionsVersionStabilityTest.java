package com.johnnyblabs.openspec.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The artifact instructions the plugin builds its generation and regeneration prompts from come from a live
 * {@code openspec instructions <artifact> --json} call, so users get their own CLI's wording. These captures pin that
 * wording per release: every release from 1.17.0 up returns the same design and specs instructions as 1.17.0, field
 * for field ({@code fixtures/cli/<version>/instructions/}, by {@code scripts/capture-cli-generation.py}).
 * <p>
 * When a new release rewords an instruction, this fails on purpose: check that the prompt still reads well with the
 * new text (and that {@link RegenerationNotePromptTest} covers the capture), then record the release as a new
 * baseline rather than editing a fixture.
 */
class ArtifactInstructionsVersionStabilityTest {

    static final String BASELINE = "1.17.0";
    static final List<String> REQUIRED = List.of("1.17.0", "2.0.0", "2.1.0", "2.1.1");

    static JsonObject read(String version, String artifact) throws IOException {
        Path file = FixtureVersions.CLI.resolve(version).resolve("instructions/instructions-" + artifact + ".json");
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @TestFactory
    Stream<DynamicTest> laterReleasesKeepTheBaselineInstructions() {
        List<String> releases = FixtureVersions.atLeast(
                FixtureVersions.versionsWith("instructions/instructions-design.json", REQUIRED), BASELINE);
        List<DynamicTest> tests = new ArrayList<>();
        for (String v : releases) {
            for (String artifact : List.of("design", "specs")) {
                tests.add(DynamicTest.dynamicTest(v + "/" + artifact, () -> {
                    JsonObject baseline = read(BASELINE, artifact);
                    JsonObject captured = read(v, artifact);
                    assertFalse(captured.get("instruction").getAsString().isBlank(), "sanity: an instruction");
                    assertEquals(baseline.keySet(), captured.keySet(), v + " " + artifact + ": fields");
                    for (String field : baseline.keySet()) {
                        assertEquals(baseline.get(field), captured.get(field), v + " " + artifact + ": " + field);
                    }
                }));
            }
        }
        return tests.stream();
    }
}
