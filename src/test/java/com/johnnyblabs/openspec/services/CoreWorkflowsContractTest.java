package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.CliFixtureSuite;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.CliFixtures;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin's core workflow set for the captured CLI version equals upstream's {@code CORE_WORKFLOWS}, in order. The
 * core set is what the default profile enables, so a divergence silently disables or offers the wrong actions. The
 * fixture is captured from the real CLI package by {@code scripts/capture-workflow-ids.py}.
 */
@CliFixtureSuite
class CoreWorkflowsContractTest {

    /** Every release with a captured workflow set; a missing capture fails instead of shrinking the matrix. */
    static final Set<String> FLOOR = Set.of("1.15.0", "1.16.0", "1.16.1", "1.17.0", "2.0.0", "2.1.0", "2.1.1", "2.2.0");

    private static JsonObject fixture(String version) throws IOException {
        try (InputStream in = CoreWorkflowsContractTest.class.getResourceAsStream("/fixtures/cli/" + version + "/workflows.json")) {
            assertNotNull(in, "missing fixture " + version + "/workflows.json");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static List<String> releases() {
        List<String> found = FixtureVersions.versionsWith("workflows.json", FLOOR);
        assertTrue(found.contains(CliFixtures.targetVersion()), "the target release is checked");
        return found;
    }

    @TestFactory
    Stream<DynamicTest> theCoreSetForEachCapturedReleaseIsUpstreams() {
        return releases().stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            JsonObject fixture = fixture(v);
            List<String> upstreamCore = new ArrayList<>();
            for (var el : fixture.getAsJsonArray("core")) upstreamCore.add(el.getAsString());
            List<String> pluginCore = List.copyOf(WorkflowProfileService.coreWorkflows(fixture.get("cliVersion").getAsString()));
            assertEquals(upstreamCore, pluginCore, v);
        }));
    }

    @TestFactory
    Stream<DynamicTest> eachFixtureWasCapturedFromItsRelease() {
        return releases().stream().map(v -> DynamicTest.dynamicTest(v, () ->
                assertEquals(v, fixture(v).get("cliVersion").getAsString(),
                        "workflows.json under " + v + "/ must be a capture from that CLI; re-run scripts/capture-workflow-ids.py")));
    }
}
