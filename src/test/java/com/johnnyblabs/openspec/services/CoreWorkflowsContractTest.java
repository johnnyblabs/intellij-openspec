package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin's core workflow set for the captured CLI version equals upstream's {@code CORE_WORKFLOWS}, in order. The
 * core set is what the default profile enables, so a divergence silently disables or offers the wrong actions. The
 * fixture is captured from the real CLI package by {@code scripts/capture-workflow-ids.py}.
 */
class CoreWorkflowsContractTest {

    private static JsonObject fixture() throws IOException {
        try (InputStream in = CoreWorkflowsContractTest.class.getResourceAsStream("/fixtures/cli/1.15.0/workflows.json")) {
            assertNotNull(in, "missing fixture 1.15.0/workflows.json");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void theCoreSetForTheCapturedVersionIsUpstreams() throws IOException {
        JsonObject fixture = fixture();
        List<String> upstreamCore = new ArrayList<>();
        for (var el : fixture.getAsJsonArray("core")) upstreamCore.add(el.getAsString());

        List<String> pluginCore = List.copyOf(WorkflowProfileService.coreWorkflows(fixture.get("cliVersion").getAsString()));

        assertEquals(upstreamCore, pluginCore);
    }
}
