package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.SkillToolList.Entry;
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
 * {@link SkillToolList} reads every {@code .openspec-target} a real CLI wrote ({@code fixtures/cli/<version>/detect/}).
 * Each 2.x case with a list sets up exactly the tools it lists, every one with its files present, so upstream's own
 * detection verdict is the list's content; a 1.x file is the single id of the tool that rendered the shared folder.
 */
class OpenSpecTargetListContractTest {

    static final Set<String> REQUIRED = Set.of("1.14.3", "1.16.1", "1.17.0", "2.0.0", "2.1.0", "2.1.1");

    @TempDir
    Path tmp;

    @TestFactory
    Stream<DynamicTest> readsEveryCapturedList() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        int lists2x = 0;
        for (String v : FixtureVersions.versionsWith("detect", REQUIRED)) {
            List<Path> cases;
            try (Stream<Path> dirs = Files.list(FixtureVersions.CLI.resolve(v).resolve("detect"))) {
                cases = dirs.sorted().toList();
            }
            for (Path c : cases) {
                JsonObject layout = JsonParser.parseString(Files.readString(c.resolve("layout.json"))).getAsJsonObject();
                JsonElement target = layout.getAsJsonObject("project").get(".agents/skills/" + SkillToolList.FILE);
                if (target == null) continue;
                String content = target.getAsString();
                List<String> detected = new ArrayList<>();
                for (JsonElement id : JsonParser.parseString(Files.readString(c.resolve("detected.json"))).getAsJsonArray()) {
                    detected.add(id.getAsString());
                }
                boolean is2x = v.startsWith("2.");
                if (is2x) lists2x++;
                tests.add(DynamicTest.dynamicTest(v + "/" + c.getFileName(), () -> {
                    List<String> ids = SkillToolList.ids(SkillToolList.parse(content));
                    if (is2x) {
                        assertTrue(detected.containsAll(ids), "every listed tool is detected: " + ids + " vs " + detected);
                        // A case made by init alone sets up exactly the tools it lists. The builtin-add-* cases also
                        // hold a tool folder the plugin created without the CLI, detected from that folder, not the list.
                        if (!c.getFileName().toString().startsWith("builtin-add-")) {
                            assertEquals(detected, ids.stream().sorted().toList(), "the listed tools are upstream's verdict");
                        }
                        // own-files marks a tool OpenSpec gave files of its own: on 2.x, its links (no commands).
                        Set<String> linked = linkedTools(v);
                        for (Entry e : SkillToolList.parse(content)) {
                            if (!linked.contains(e.id()) && !unlinked(v).contains(e.id())) continue;
                            assertEquals(linked.contains(e.id()), e.ownFiles(),
                                    e.id() + " is own-files exactly when CLI " + v + " links it");
                        }
                    } else {
                        assertEquals(1, ids.size(), "a 1.x marker names one tool: " + content);
                        assertEquals(content.strip(), ids.getFirst());
                    }
                    // The file on disk reads the same as the text.
                    Path project = tmp.resolve(v).resolve(c.getFileName().toString());
                    Files.createDirectories(SkillToolList.path(project).getParent());
                    Files.writeString(SkillToolList.path(project), content);
                    assertEquals(SkillToolList.parse(content), SkillToolList.read(project));
                }));
            }
        }
        assertTrue(lists2x >= 40, "sanity: the 2.x captures hold lists");
        return tests.stream();
    }

    /** The tools CLI {@code version}'s invocation matrix gives a {@code linkPath}. */
    static Set<String> linkedTools(String version) throws IOException {
        return matrixTools(version, true);
    }

    static Set<String> unlinked(String version) throws IOException {
        return matrixTools(version, false);
    }

    private static Set<String> matrixTools(String version, boolean linked) throws IOException {
        Set<String> out = new TreeSet<>();
        JsonObject matrix = JsonParser.parseString(Files.readString(
                FixtureVersions.CLI.resolve(version).resolve("workflow-invocations.json"))).getAsJsonObject();
        for (JsonElement r : matrix.getAsJsonArray("rows")) {
            JsonObject row = r.getAsJsonObject();
            boolean hasLink = row.has("linkPath") && !row.get("linkPath").isJsonNull();
            if (hasLink == linked) out.add(row.get("tool").getAsString());
        }
        return out;
    }

    @Test
    void ownFilesIsPerTool() {
        assertEquals(List.of(new Entry("claude", true), new Entry("cursor", false), new Entry("github-copilot", false)),
                SkillToolList.parse("claude own-files\ncursor\ngithub-copilot\n"));
    }

    @Test
    void crlfBlankLinesAndSurroundingSpaceDontMatter() {
        assertEquals(List.of(new Entry("claude", true), new Entry("codex", false)),
                SkillToolList.parse("\r\n  claude   own-files  \r\n\r\n\tcodex\r\n"));
    }

    /** Defensive input the CLI doesn't write today (every capture writes {@code devin}): an older id, an unknown one. */
    @Test
    void aliasesResolveAndUnknownIdsAndKeepLinesAreSkipped() {
        assertEquals(List.of(new Entry("devin", true)),
                SkillToolList.parse("windsurf own-files\nno-such-tool\nkeep openspec-explore\n"));
    }

    @Test
    void aRepeatedToolIsListedOnceAndKeepsItsOwnFilesMark() {
        assertEquals(List.of(new Entry("claude", true)), SkillToolList.parse("claude\nclaude own-files\n"));
    }

    @Test
    void anythingAfterTheIdButOwnFilesIsIgnored() {
        assertEquals(List.of(new Entry("claude", false)), SkillToolList.parse("claude something-else\n"));
    }

    @Test
    void noFileOrNoKnownToolReadsAsEmpty() throws IOException {
        assertEquals(List.of(), SkillToolList.read(tmp));
        Files.createDirectories(SkillToolList.path(tmp).getParent());
        Files.writeString(SkillToolList.path(tmp), "");
        assertEquals(List.of(), SkillToolList.read(tmp));
        Files.writeString(SkillToolList.path(tmp), "keep openspec-explore\n");
        assertEquals(List.of(), SkillToolList.read(tmp));
    }

    @Test
    void aFolderWhereTheListShouldBeReadsAsEmpty() throws IOException {
        Files.createDirectories(SkillToolList.path(tmp));
        assertEquals(List.of(), SkillToolList.read(tmp));
    }

    @Test
    void only2xWritesSeveralToolsOrOwnFiles() {
        assertFalse(SkillToolList.looks2x("codex\n"));
        assertTrue(SkillToolList.looks2x("claude own-files\n"));
        assertTrue(SkillToolList.looks2x("antigravity\ncodex\n"));
    }
}
