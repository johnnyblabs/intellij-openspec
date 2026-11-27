package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SkillLinkDirs} is upstream's link folder per tool, for each captured 2.x release: equal to the
 * {@code effective.linkDir} upstream's {@code skill-paths.js} reports ({@code ai-tools.json}), and to the {@code linkPath}
 * of every row of the invocation matrix, which records what {@code init} actually linked.
 */
class SkillLinkDirsContractTest {

    static final Set<String> REQUIRED = Set.of("2.0.0", "2.1.0", "2.1.1", "2.2.0");

    static SkillLinkDirs.Line line(String version) {
        return version.startsWith("2.0.") ? SkillLinkDirs.Line.V2_0 : SkillLinkDirs.Line.V2_1;
    }

    private static JsonObject read(String version, String file) throws IOException {
        return JsonParser.parseString(Files.readString(FixtureVersions.CLI.resolve(version).resolve(file))).getAsJsonObject();
    }

    private static String stringOrNull(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    @TestFactory
    Stream<DynamicTest> equalsUpstreamsEffectiveLinkDir() {
        List<String> versions = FixtureVersions.versionsWith("ai-tools.json", REQUIRED).stream()
                .filter(v -> v.startsWith("2.")).toList();
        return versions.stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            JsonObject effective = read(v, "ai-tools.json").getAsJsonObject("effective");
            List<String> drift = new ArrayList<>();
            int linked = 0;
            // Upstream's tools with a skills folder and the plugin's accepted ones are the same set: neither side skips.
            java.util.Set<String> accepted = new java.util.TreeSet<>();
            for (AiToolRegistry.AiTool tool : AiToolRegistry.all()) {
                if (AiToolRegistry.acceptedBy(tool, v) && tool.skillsDir() != null) accepted.add(tool.id());
            }
            java.util.Set<String> upstreamIds = new java.util.TreeSet<>(effective.keySet());
            if (!v.startsWith("2.0")) accepted.add("minimax-code"); // its project folder is 2.1's (DetectionRules)
            assertEquals(upstreamIds, accepted, "CLI " + v + "'s tools with a skills folder");
            for (AiToolRegistry.AiTool tool : AiToolRegistry.all()) {
                JsonElement entry = effective.get(tool.id());
                if (entry == null) {
                    assertFalse(accepted.contains(tool.id()), tool.id() + " is accepted by " + v + " but has no effective entry");
                    continue;
                }
                String upstream = stringOrNull(entry.getAsJsonObject(), "linkDir");
                if (upstream != null) linked++;
                String plugin = SkillLinkDirs.linkDir(line(v), tool.id());
                if (!Objects.equals(upstream, plugin)) drift.add(tool.id() + ": upstream " + upstream + ", plugin " + plugin);
            }
            assertTrue(drift.isEmpty(), "link folders that differ from CLI " + v + "'s:\n" + String.join("\n", drift));
            assertTrue(linked >= 10, "sanity: CLI " + v + " links some known tools");
        }));
    }

    @TestFactory
    Stream<DynamicTest> equalsWhatInitLinked() {
        List<String> versions = FixtureVersions.versionsWith("workflow-invocations.json", REQUIRED).stream()
                .filter(v -> v.startsWith("2.")).toList();
        return versions.stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            List<String> drift = new ArrayList<>();
            int checked = 0;
            for (JsonElement e : read(v, "workflow-invocations.json").getAsJsonArray("rows")) {
                JsonObject row = e.getAsJsonObject();
                String tool = row.get("tool").getAsString();
                assertNotNull(AiToolRegistry.byId(tool), "CLI " + v + " linked a tool the registry lacks: " + tool);
                String upstream = stringOrNull(row, "linkPath");
                String plugin = SkillLinkDirs.linkPath(line(v), tool, row.get("skill").getAsString());
                if (!Objects.equals(upstream, plugin)) drift.add(tool + ": upstream " + upstream + ", plugin " + plugin);
                checked++;
            }
            assertTrue(drift.isEmpty(), "links that differ from what CLI " + v + " wrote:\n" + String.join("\n", drift));
            assertTrue(checked >= 90, "sanity: rows checked for " + v);
        }));
    }

    @org.junit.jupiter.api.Test
    void aProjectFrom20KeepsItsLinksUnder21() {
        // EasyCode is linked on 2.0 only, Junie on 2.1 only, Claude on both.
        assertEquals(List.of(".easycode/skills"), SkillLinkDirs.linkDirsAnyLine("easycode"));
        assertEquals(List.of(".junie/skills"), SkillLinkDirs.linkDirsAnyLine("junie"));
        assertEquals(List.of(".claude/skills"), SkillLinkDirs.linkDirsAnyLine("claude"));
        assertEquals(List.of(), SkillLinkDirs.linkDirsAnyLine("cursor"));
    }
}
