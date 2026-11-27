package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.CliFixtureSuite;

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
@CliFixtureSuite
class BuiltInAddContractTest {

    static final Set<String> REQUIRED = Set.of("1.14.3", "1.16.1", "1.17.0", "2.0.0", "2.1.0", "2.1.1", "2.2.0");

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

    /** Every path under {@code root}, links not followed: the project's shape, to prove an undo is exact. */
    static Set<String> shape(Path root) throws IOException {
        try (Stream<Path> all = Files.walk(root)) {
            return all.map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    /** Removes what {@link #apply} created: the list file if it wasn't there, then the folder from its first new parent. */
    static void undo(Path project, @org.jetbrains.annotations.Nullable Path createdFrom, @org.jetbrains.annotations.Nullable Path createdList)
            throws IOException {
        if (createdList != null) Files.deleteIfExists(createdList);
        if (createdFrom != null && Files.exists(createdFrom, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> walk = Files.walk(createdFrom)) {
                for (Path f : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(f);
            }
        }
    }

    /** The first path from {@code project} down to {@code target} that doesn't exist yet, or {@code null} if all do. */
    static @org.jetbrains.annotations.Nullable Path firstMissing(Path project, Path target) {
        Path p = project;
        for (Path part : project.relativize(target)) {
            p = p.resolve(part);
            if (!Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return p;
        }
        return null;
    }

    /**
     * One test per release and layout, checking every tool the release knows. The layout is laid out once; after each
     * tool, exactly what the add created is removed and the project's shape is checked against the original, so tools
     * can't see each other's adds. A failure lists every release/layout/tool that broke a rule.
     */
    @TestFactory
    Stream<DynamicTest> addingWithoutTheCliNeverMisstatesTheListAndWorksWhereItIsAllowed() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        int[] checked = {0};
        int[] refused2x = {0}, applied2x = {0};
        for (String v : FixtureVersions.versionsWith("detect", REQUIRED)) {
            DetectionRules rules = DetectionRules.forVersion(v);
            List<Path> cases;
            try (Stream<Path> dirs = Files.list(FixtureVersions.CLI.resolve(v).resolve("detect"))) {
                cases = dirs.sorted().toList();
            }
            for (Path c : cases) {
                String captured = c.getFileName().toString();
                tests.add(DynamicTest.dynamicTest(v + "/" + captured, () -> {
                    JsonObject layout = JsonParser.parseString(Files.readString(c.resolve("layout.json"))).getAsJsonObject();
                    List<String> wrong = new ArrayList<>();
                    checkLayout(v, rules, captured, layout, wrong, checked, refused2x, applied2x);
                    // The damage case no capture has: a 2.x project whose list was deleted, its links left. Derived by
                    // removing only the captured list file.
                    if (rules.is2x() && layout.has("links")
                            && layout.getAsJsonObject("project").has(".agents/skills/" + SkillToolList.FILE)) {
                        JsonObject derived = layout.deepCopy();
                        derived.getAsJsonObject("project").remove(".agents/skills/" + SkillToolList.FILE);
                        checkLayout(v, rules, captured + "-list-deleted", derived, wrong, checked, refused2x, applied2x);
                    }
                    assertEquals(List.of(), wrong, String.join("\n", wrong));
                }));
            }
        }
        // Both outcomes must occur on 2.x layouts, or a decision stuck at one answer would pass; and folding the cases
        // into fewer tests must not check fewer of them.
        tests.add(DynamicTest.dynamicTest("both-outcomes-on-2x, every pair checked", () -> {
            assertTrue(refused2x[0] > 0, "some tool is refused on a 2.x layout");
            assertTrue(applied2x[0] > 0, "some tool is still added on a 2.x layout");
            assertTrue(checked[0] >= 30_000, "sanity: layout/tool pairs checked " + checked[0]);
        }));
        return tests.stream();
    }

    private void checkLayout(String v, DetectionRules rules, String name, JsonObject layout, List<String> wrong,
                             int[] checked, int[] refused2x, int[] applied2x) throws IOException {
        Path root = tmp.resolve(v).resolve(name);
        Path project = root.resolve("project");
        Path home = root.resolve("home");
        FixtureLayout.materialize(layout, project, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        Path list = SkillToolList.path(project);
        String listBefore = Files.exists(list) ? Files.readString(list, StandardCharsets.UTF_8) : null;
        boolean shows2x = DetectionRules.fromDisk(project).is2x();
        List<AiTool> before = AiToolDetector.detect(project, home, rules);
        Set<String> original = shape(project);
        for (AiTool tool : AiToolRegistry.all()) {
            if (AiToolRegistry.builtInDir(tool) == null || !rules.knows(tool)) continue;
            String id = v + "/" + name + "/" + tool.id();
            BuiltInAdd decision = AiToolRegistry.builtInAdd(tool, project);
            if (shows2x && rules.is2x()) {
                if (decision == BuiltInAdd.NEEDS_CLI) refused2x[0]++; else applied2x[0]++;
            }
            Path createdFrom = firstMissing(project, project.resolve(AiToolRegistry.builtInDir(tool)));
            String marker = AiToolRegistry.builtInMarker(tool);
            Path createdList = marker != null && !Files.exists(project.resolve(marker)) ? project.resolve(marker) : null;
            apply(project, tool, decision);
            String listAfter = Files.exists(list) ? Files.readString(list, StandardCharsets.UTF_8) : null;
            if (listBefore != null && !listBefore.equals(listAfter)) {
                wrong.add(id + " " + decision + ": an existing tool list is never changed");
            }
            if (shows2x && listBefore == null && listAfter != null) {
                wrong.add(id + " " + decision + ": no tool list is created on a 2.x layout");
            }
            List<AiTool> after = AiToolDetector.detect(project, home, rules);
            List<AiTool> lost = new ArrayList<>(before);
            lost.removeAll(after);
            // A tool given a folder of its own may take over the shared folder, as the CLI's own rule decides from
            // those files (1.x gsd: the list already names it); writing the list never may.
            if (decision == BuiltInAdd.CREATE_DIR && Objects.equals(tool.skillsDir(), ".agents")) {
                lost.removeIf(t -> Objects.equals(t.skillsDir(), ".agents"));
            }
            if (!lost.isEmpty()) {
                wrong.add(id + " " + decision + ": the add loses a tool that was set up: before " + before + ", after " + after);
            }
            if (decision != BuiltInAdd.NEEDS_CLI && !after.contains(tool)) {
                wrong.add(id + " " + decision + ": added but not detected by CLI " + v + "'s rules");
            }
            undo(project, createdFrom, createdList);
            assertEquals(original, shape(project), id + ": the undo must restore the layout exactly");
            checked[0]++;
        }
    }

    /**
     * What the add leaves, as the real CLI judges it ({@code detect/builtin-add-*}): Cursor's folder beside Claude is
     * detected; Codex's list on an empty project is detected; on 2.x Codex's folder alone, beside Claude, is not, which
     * is why the add refuses there.
     */
    @TestFactory
    Stream<DynamicTest> theCapturedOutcomesMatchTheDecision() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String v : FixtureVersions.versionsWith("detect/builtin-add-cursor-beside-claude", Set.of("1.17.0", "2.0.0", "2.1.0", "2.1.1", "2.2.0"))) {
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
