package com.johnnyblabs.openspec.services;

import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.CliFixtureSuite;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The plugin's per-release skill names are each captured release's own {@code SKILL_NAMES}, in order. */
@CliFixtureSuite
class SkillNamesContractTest {

    @Test
    void eachReleasesSkillNamesAreItsOwn() throws IOException {
        List<String> versions = FixtureVersions.versionsWith("skill-names.json");
        int checkedAdapters = 0;
        assertTrue(versions.size() >= 27, "sanity: " + versions);
        assertTrue(versions.containsAll(List.of("1.5.0", "1.6.0", "2.1.1", "2.2.0")), "both boundaries are captured");
        for (String v : versions) {
            List<String> upstream = new ArrayList<>();
            JsonParser.parseString(Files.readString(FixtureVersions.CLI.resolve(v).resolve("skill-names.json")))
                    .getAsJsonObject().getAsJsonArray("skillNames").forEach(e -> upstream.add(e.getAsString()));
            assertEquals(upstream, AiToolRegistry.skillNames(v), v);
            var adapters = JsonParser.parseString(Files.readString(FixtureVersions.CLI.resolve(v).resolve("skill-names.json")))
                    .getAsJsonObject().getAsJsonArray("adapterBackedWithLegacy");
            if (adapters != null && !com.johnnyblabs.openspec.util.CliVersion.below(v, "1.11.0")) {
                List<String> ids = new ArrayList<>();
                adapters.forEach(e -> ids.add(e.getAsString()));
                assertEquals(new java.util.TreeSet<>(ids), new java.util.TreeSet<>(AiToolRegistry.ADAPTER_BACKED_WITH_LEGACY),
                        v + ": the tools whose older-folder skills count whoever owns the shared folder");
                checkedAdapters++;
            }
        }
        assertTrue(checkedAdapters >= 15, "sanity: every release from 1.11.0, " + checkedAdapters);
    }

    @Test
    void theBoundaries() {
        assertEquals(11, AiToolRegistry.skillNames("1.5.9").size());
        assertEquals(12, AiToolRegistry.skillNames("1.6.0").size());
        assertEquals(12, AiToolRegistry.skillNames("2.1.9").size());
        assertEquals(13, AiToolRegistry.skillNames("2.2.0").size());
        assertEquals(AiToolRegistry.SKILL_NAMES, AiToolRegistry.skillNames(null), "no release: the newest list");
    }
}
