package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adding a tool without the CLI, replayed on every project layout a real CLI wrote
 * ({@code fixtures/cli/<version>/detect/}), for every tool. What the add may write ({@link AiToolRegistry#builtInAdd})
 * is applied exactly as Manage AI Tools applies it; the outcome is judged by the capture's own release line
 * ({@link AiToolDetector} with {@link DetectionRules#forVersion}), never by a hand-written expectation:
 * <ul>
 *   <li>an existing tool list is never changed, and none is created on a layout whose files show 2.x;</li>
 *   <li>a tool the add didn't refuse is then detected.</li>
 * </ul>
 */
class BuiltInAddContractTest {

    static final Set<String> REQUIRED = Set.of("1.14.3", "1.16.1", "1.17.0", "2.0.0", "2.1.0", "2.1.1");

    @TempDir
    Path tmp;

    /** What Manage AI Tools does for {@code decision}: the folder, then the list when allowed and absent. */
    static void apply(Path project, AiTool tool, BuiltInAdd decision) throws IOException {
        if (decision == BuiltInAdd.NEEDS_CLI) return;
        Files.createDirectories(project.resolve(AiToolRegistry.builtInDir(tool)));
        String marker = AiToolRegistry.builtInMarker(tool);
        if (decision == BuiltInAdd.CREATE_DIR_AND_LIST && marker != null && !Files.exists(project.resolve(marker))) {
            Files.writeString(project.resolve(marker), tool.id() + "\n");
        }
    }

    @TestFactory
    Stream<DynamicTest> addingWithoutTheCliNeverMisstatesTheListAndWorksWhereItIsAllowed() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        int refused2x = 0, applied2x = 0;
        for (String v : FixtureVersions.versionsWith("detect", REQUIRED)) {
            DetectionRules rules = DetectionRules.forVersion(v);
            List<Path> cases;
            try (Stream<Path> dirs = Files.list(FixtureVersions.CLI.resolve(v).resolve("detect"))) {
                cases = dirs.sorted().toList();
            }
            List<Map.Entry<String, JsonObject>> layouts = new ArrayList<>();
            for (Path c : cases) {
                JsonObject layout = JsonParser.parseString(Files.readString(c.resolve("layout.json"))).getAsJsonObject();
                layouts.add(Map.entry(c.getFileName().toString(), layout));
                // The damage case no capture has: a 2.x project whose list was deleted, its links left. Derived by
                // removing only the captured list file.
                if (rules.is2x() && layout.has("links")
                        && layout.getAsJsonObject("project").has(".agents/skills/" + SkillToolList.FILE)) {
                    JsonObject derived = layout.deepCopy();
                    derived.getAsJsonObject("project").remove(".agents/skills/" + SkillToolList.FILE);
                    layouts.add(Map.entry(c.getFileName() + "-list-deleted", derived));
                }
            }
            for (Map.Entry<String, JsonObject> entry : layouts) {
                String name = entry.getKey();
                JsonObject layout = entry.getValue();
                for (AiTool tool : AiToolRegistry.all()) {
                    if (AiToolRegistry.builtInDir(tool) == null || !rules.knows(tool)) continue;
                    Path root = tmp.resolve(v).resolve(name).resolve(tool.id());
                    Path project = root.resolve("project");
                    Path home = root.resolve("home");
                    FixtureLayout.materialize(layout, project, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);
                    Path list = SkillToolList.path(project);
                    String listBefore = Files.exists(list) ? Files.readString(list, StandardCharsets.UTF_8) : null;
                    boolean shows2x = DetectionRules.fromDisk(project).is2x();
                    BuiltInAdd decision = AiToolRegistry.builtInAdd(tool, project);
                    List<AiTool> before = AiToolDetector.detect(project, home, rules);
                    if (shows2x && rules.is2x()) {
                        if (decision == BuiltInAdd.NEEDS_CLI) refused2x++; else applied2x++;
                    }
                    tests.add(DynamicTest.dynamicTest(v + "/" + name + "/" + tool.id() + " " + decision, () -> {
                        apply(project, tool, decision);
                        String listAfter = Files.exists(list) ? Files.readString(list, StandardCharsets.UTF_8) : null;
                        if (listBefore != null) assertEquals(listBefore, listAfter, "an existing tool list is never changed");
                        if (shows2x) assertNull(listBefore == null ? listAfter : null, "no tool list is created on a 2.x layout");
                        List<AiTool> after = AiToolDetector.detect(project, home, rules);
                        List<AiTool> lost = new ArrayList<>(before);
                        lost.removeAll(after);
                        // A tool given a folder of its own may take over the shared folder, as the CLI's own rule
                        // decides from those files (1.x gsd: the list already names it); writing the list never may.
                        if (decision == BuiltInAdd.CREATE_DIR && Objects.equals(tool.skillsDir(), ".agents")) {
                            lost.removeIf(t -> Objects.equals(t.skillsDir(), ".agents"));
                        }
                        assertEquals(List.of(), lost, "the add loses no tool that was set up: before " + before + ", after " + after);
                        if (decision != BuiltInAdd.NEEDS_CLI) {
                            assertTrue(after.contains(tool), tool.id() + " was added but isn't detected by CLI " + v + "'s rules");
                        }
                    }));
                }
            }
        }
        // Both outcomes must occur on 2.x layouts, or a decision stuck at one answer would pass.
        int r = refused2x, a = applied2x;
        tests.add(DynamicTest.dynamicTest("both-outcomes-on-2x", () -> {
            assertTrue(r > 0, "some tool is refused on a 2.x layout");
            assertTrue(a > 0, "some tool is still added on a 2.x layout");
        }));
        return tests.stream();
    }

    /**
     * What the add leaves, as the real CLI judges it ({@code detect/builtin-add-*}): Cursor's folder beside Claude is
     * detected; Codex's list on an empty project is detected; on 2.x Codex's folder alone, beside Claude, is not, which
     * is why the add refuses there.
     */
    @TestFactory
    Stream<DynamicTest> theCapturedOutcomesMatchTheDecision() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String v : FixtureVersions.versionsWith("detect/builtin-add-codex-list-on-empty", Set.of("1.17.0", "2.0.0", "2.1.0", "2.1.1"))) {
            Path detect = FixtureVersions.CLI.resolve(v).resolve("detect");
            tests.add(DynamicTest.dynamicTest(v + "/cursor-beside-claude", () -> {
                assertEquals(BuiltInAdd.CREATE_DIR, decide(v, detect.resolve("claude"), "cursor"));
                assertEquals(".cursor", AiToolRegistry.builtInDir(AiToolRegistry.byId("cursor")));
                assertEquals(List.of("claude", "cursor"), detected(detect.resolve("builtin-add-cursor-beside-claude")));
            }));
            tests.add(DynamicTest.dynamicTest(v + "/codex-list-on-empty", () -> {
                assertEquals(BuiltInAdd.CREATE_DIR_AND_LIST, decide(v, detect.resolve("agents-empty-dir"), "codex"));
                assertEquals(".agents/skills/" + SkillToolList.FILE, AiToolRegistry.builtInMarker(AiToolRegistry.byId("codex")));
                assertEquals(List.of("codex"), detected(detect.resolve("builtin-add-codex-list-on-empty")));
            }));
            if (v.startsWith("2.")) {
                tests.add(DynamicTest.dynamicTest(v + "/codex-folder-beside-claude", () -> {
                    assertEquals(BuiltInAdd.NEEDS_CLI, decide(v, detect.resolve("claude"), "codex"));
                    assertFalse(detected(detect.resolve("builtin-add-codex-folder-beside-claude")).contains("codex"),
                            "2.x doesn't detect Codex from the shared folder alone");
                }));
            }
        }
        return tests.stream();
    }

    private BuiltInAdd decide(String version, Path captured, String toolId) throws IOException {
        JsonObject layout = JsonParser.parseString(Files.readString(captured.resolve("layout.json"))).getAsJsonObject();
        Path root = tmp.resolve("decide").resolve(version).resolve(captured.getFileName().toString()).resolve(toolId);
        FixtureLayout.materialize(layout, root.resolve("project"), root.resolve("home"), FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        return AiToolRegistry.builtInAdd(AiToolRegistry.byId(toolId), root.resolve("project"));
    }

    private static List<String> detected(Path captured) throws IOException {
        List<String> ids = new ArrayList<>();
        JsonParser.parseString(Files.readString(captured.resolve("detected.json"))).getAsJsonArray()
                .forEach(e -> ids.add(e.getAsString()));
        return ids;
    }

    @Test
    void onlyTheListOnlyToolsAreEverRefused() {
        Set<String> listOnly = new TreeSet<>();
        for (AiTool tool : AiToolRegistry.all()) {
            if (AiToolRegistry.builtInMarker(tool) != null && !AiToolRegistry.addedIn21(tool)) listOnly.add(tool.id());
        }
        assertEquals(Set.of("agents", "codex"), listOnly, "the 1.x tools whose only home is .agents/skills");
        assertNull(AiToolRegistry.builtInMarker(AiToolRegistry.byId("trae")), "Trae CN sharing .trae doesn't give Trae a marker");
        for (AiTool tool : AiToolRegistry.all()) {
            if (AiToolRegistry.addedIn21(tool) && AiToolRegistry.builtInDir(tool) != null) {
                assertEquals(BuiltInAdd.NEEDS_CLI, AiToolRegistry.builtInAdd(tool, Path.of("unused")), tool.id());
            }
        }
    }

    @Test
    void aListNamingAnotherToolRefusesCodexEvenOn1x() throws IOException {
        Path project = tmp.resolve("other");
        Files.createDirectories(SkillToolList.path(project).getParent());
        Files.writeString(SkillToolList.path(project), "cursor\n");
        assertEquals(BuiltInAdd.NEEDS_CLI, AiToolRegistry.builtInAdd(AiToolRegistry.byId("codex"), project));
        assertEquals(BuiltInAdd.CREATE_DIR, AiToolRegistry.builtInAdd(AiToolRegistry.byId("cursor"), project));
    }

    @Test
    void anEmptyProjectAddsCodexWithItsList() {
        assertEquals(BuiltInAdd.CREATE_DIR_AND_LIST, AiToolRegistry.builtInAdd(AiToolRegistry.byId("codex"), tmp));
    }
}
