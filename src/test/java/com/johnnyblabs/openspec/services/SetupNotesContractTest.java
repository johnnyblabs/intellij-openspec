package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin's setup notes equal the {@code setupNote} of every tool in every captured registry, and its universal
 * set is the one the CLI's installer forms (the tools the skills-scope capture records as skipped).
 */
class SetupNotesContractTest {

    @Test
    void everyNoteIsTheCapturedRegistrysForThatRelease() throws IOException {
        List<String> wrong = new ArrayList<>();
        int notes = 0;
        List<Path> dumps;
        try (Stream<Path> s = Files.list(FixtureVersions.CLI)) {
            dumps = s.filter(d -> Files.exists(d.resolve("ai-tools.json"))).toList();
        }
        for (Path dir : dumps) {
            String v = dir.getFileName().toString();
            JsonObject dump = JsonParser.parseString(Files.readString(dir.resolve("ai-tools.json"))).getAsJsonObject();
            for (JsonElement e : dump.getAsJsonArray("tools")) {
                JsonObject t = e.getAsJsonObject();
                AiTool tool = AiToolRegistry.byId(t.get("value").getAsString());
                if (tool == null) continue; // ids renamed before the target (the registry contract covers those)
                String upstream = t.has("setupNote") ? t.get("setupNote").getAsString() : null;
                if (upstream != null) notes++;
                String plugin = AiToolRegistry.setupNote(tool, v);
                if (!java.util.Objects.equals(upstream, plugin)) wrong.add(v + " " + tool.id() + ": upstream " + upstream + ", plugin " + plugin);
            }
        }
        assertTrue(notes > 20, "sanity: notes read");
        assertEquals(List.of(), wrong);
    }

    /** What init printed, per tool and scope: the note follows the scope rule, in the notice's own wording. */
    @Test
    void theNotePassedOnIsWhatInitPrinted() throws IOException {
        int cases = 0;
        for (String v : List.of("1.17.0", "2.1.0", "2.1.1", "2.2.0")) {
            JsonObject printed = JsonParser.parseString(Files.readString(FixtureVersions.CLI.resolve(v).resolve("setup-notes.json")))
                    .getAsJsonObject();
            for (String key : printed.keySet()) {
                String[] k = key.split(" ");
                AiTool tool = AiToolRegistry.byId(k[0]);
                com.johnnyblabs.openspec.util.CliArgs args = "project".equals(k[1])
                        ? com.johnnyblabs.openspec.util.CliCommands.initInProject(k[0])
                        : com.johnnyblabs.openspec.util.CliCommands.initKeepingScope(k[0]);
                List<String> lines = new ArrayList<>();
                printed.getAsJsonArray(key).forEach(e -> lines.add(e.getAsString()));
                String note = AiToolRegistry.setupNoteAfter(tool, v, args);
                List<String> expected = note == null ? List.of() : List.of("Setup required for " + tool.name() + ": " + note);
                assertEquals(lines, expected, v + " " + key);
                cases++;
            }
        }
        assertEquals(13, cases, "sanity: 1.17's Hermes and the four cases of each 2.1.0, 2.1.1 and 2.2.0");
    }

    @Test
    void theUniversalToolsAreTheInstallers() throws IOException {
        JsonObject markers = JsonParser.parseString(Files.readString(
                FixtureVersions.CLI.resolve("2.1.1/skills-scope/machine-markers.json"))).getAsJsonObject();
        Set<String> skipped = new TreeSet<>();
        markers.getAsJsonArray("skipped").forEach(e -> skipped.add(e.getAsString()));
        assertEquals(skipped, new TreeSet<>(AiToolRegistry.UNIVERSAL_INSTALLER_TOOLS));
    }
}
