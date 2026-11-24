package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin's machine tool markers against the installer's own, as {@code capture-skills-scope.py} recorded them from
 * the newest release: the same tickable entries both ways, and each one, alone on a fresh home folder, ticks exactly
 * the tools the installer reported for it.
 */
class MachineToolMarkersContractTest {

    static final String NEWEST = "2.1.1";

    @TempDir
    Path tmp;

    static List<JsonObject> captured() throws IOException {
        JsonObject doc = JsonParser.parseString(Files.readString(
                FixtureVersions.CLI.resolve(NEWEST).resolve("skills-scope/machine-markers.json"))).getAsJsonObject();
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : doc.getAsJsonArray("entries")) out.add(e.getAsJsonObject());
        return out;
    }

    static List<String> tools(JsonObject entry) {
        List<String> out = new ArrayList<>();
        entry.getAsJsonArray("tools").forEach(t -> out.add(t.getAsString()));
        return out;
    }

    static String key(String base, String path) {
        return base + " " + path;
    }

    @Test
    void theShippedTableIsTheCapturedTickableEntries() throws IOException {
        Map<String, List<String>> expected = new TreeMap<>();
        for (JsonObject e : captured()) {
            if (!e.get("tickable").getAsBoolean()) continue;
            expected.put(key(e.get("base").getAsString(), e.get("path").getAsString()), tools(e));
        }
        Map<String, List<String>> shipped = new TreeMap<>();
        for (MachineToolMarkers.Marker m : MachineToolMarkers.all()) {
            shipped.put(key(m.base() == MachineToolMarkers.Base.HOME ? "home" : "configHome", m.path()), m.tools());
        }
        assertEquals(expected, shipped);
        assertTrue(shipped.size() > 50, "sanity: the table was read");
    }

    /**
     * A marker tool the plugin doesn't list can't be ticked. Every such tool must be one upstream 2.1.1 lists and the
     * plugin's registry doesn't yet: the 2.x tool list gap ({@code tool-registry-2x} in KnownGaps). Once the registry
     * has them, they tick with no change here.
     */
    @Test
    void theTableNeverTicksTheUniversalEntryAndNamesOnlyUpstreamTools() throws IOException {
        JsonObject registry = JsonParser.parseString(Files.readString(FixtureVersions.CLI.resolve(NEWEST).resolve("ai-tools.json")))
                .getAsJsonObject();
        Set<String> upstream = new HashSet<>();
        registry.getAsJsonArray("tools").forEach(t -> upstream.add(t.getAsJsonObject().get("value").getAsString()));
        Set<String> notListedYet = new TreeSet<>();
        for (MachineToolMarkers.Marker m : MachineToolMarkers.all()) {
            assertFalse(m.tools().contains("agents"), m.toString());
            for (String id : m.tools()) {
                assertTrue(upstream.contains(id), "not an upstream tool: " + id);
                if (AiToolRegistry.byId(id) == null) notListedYet.add(id);
            }
        }
        assertTrue(MachineToolMarkers.all().stream().flatMap(m -> m.tools().stream()).anyMatch(id -> AiToolRegistry.byId(id) != null),
                "sanity: some marker tools are installable now");
        // Report, not fail: the registry gap is tracked separately.
        System.out.println("marker tools the registry doesn't list yet: " + notListedYet);
    }

    /** Each marker created alone (after its parent folder, as the capture did) ticks its tools and its parents'. */
    @TestFactory
    Stream<DynamicTest> eachMarkerAloneTicksTheToolsTheInstallerReported() throws IOException {
        return captured().stream().filter(e -> e.get("tickable").getAsBoolean()).map(e -> DynamicTest.dynamicTest(
                e.get("base").getAsString() + " " + e.get("path").getAsString(), () -> {
                    Path home = Files.createTempDirectory(tmp, "home");
                    Path xdg = home.resolve("xdg-config");
                    boolean inConfig = e.get("base").getAsString().equals("configHome");
                    String path = e.get("path").getAsString();
                    Files.createDirectories((inConfig ? xdg : home).resolve(path));

                    Set<String> expected = new LinkedHashSet<>(tools(e));
                    // A marker inside another marker's folder also creates that folder (.gemini for .gemini/antigravity).
                    for (MachineToolMarkers.Marker m : MachineToolMarkers.all()) {
                        boolean sameBase = (m.base() == MachineToolMarkers.Base.CONFIG_HOME) == inConfig;
                        if (sameBase && path.startsWith(m.path() + "/")) expected.addAll(m.tools());
                    }
                    assertEquals(new TreeSet<>(expected), new TreeSet<>(MachineToolMarkers.installed(home, xdg.toString())));
                }));
    }

    @Test
    void theConfigHomeIsXdgConfigHomeWhenSetAndDotConfigOtherwise() throws IOException {
        Path home = tmp.resolve("h");
        Files.createDirectories(home.resolve(".config/zed"));
        assertTrue(MachineToolMarkers.installed(home, null).contains("zed"));
        assertFalse(MachineToolMarkers.installed(home, tmp.resolve("elsewhere").toString()).contains("zed"),
                "with XDG_CONFIG_HOME set, ~/.config is not the config home");
        Files.createDirectories(tmp.resolve("elsewhere/zed"));
        assertTrue(MachineToolMarkers.installed(home, tmp.resolve("elsewhere").toString()).contains("zed"));
    }
}
