package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.ExplorePromptService.Instructions;
import com.johnnyblabs.openspec.util.FixtureVersions;
import com.johnnyblabs.openspec.util.KnownGaps;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Workflow handoffs name exactly what upstream tells the user to type, for every workflow, tool and delivery mode.
 * {@code workflow-invocations.json} is captured from upstream's own functions by
 * {@code scripts/capture-workflow-invocations.py}. Each row's files are seeded into a fresh project with the row's tool
 * as the only candidate; explore rows go through Explore's own lookup, the others through {@link WorkflowInvocation#find}.
 * <p>
 * The plugin is held to the target and every later captured release. A release whose matrix the plugin doesn't match
 * yet is one {@link KnownGaps} entry for the whole release, closed when every row matches. The check of upstream's
 * matrix against what a real init wrote compares upstream with itself, so it runs for every release and always passes.
 */
class WorkflowInvocationContractTest {

    private static final String TARGET = AiToolRegistryContractTest.TARGET;

    /** Each release's row count: 2 workflows x tools with skills x deliveries (one delivery from 2.0). */
    private static final Map<String, Integer> ROWS = Map.of("1.17.0", 294, "2.0.0", 96, "2.1.0", 174, "2.1.1", 174);

    @TempDir Path root;

    private static List<String> releases() {
        return FixtureVersions.atLeast(FixtureVersions.versionsWith("workflow-invocations.json", ROWS.keySet()), TARGET);
    }

    private static List<JsonObject> rows(String version) throws IOException {
        Path matrix = AiToolRegistryContractTest.FIXTURES.resolve(version).resolve("workflow-invocations.json");
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement row : JsonParser.parseString(Files.readString(matrix)).getAsJsonObject().getAsJsonArray("rows")) {
            rows.add(row.getAsJsonObject());
        }
        assertEquals(ROWS.get(version), rows.size(), "sanity: CLI " + version + "'s matrix rows");
        return rows;
    }

    private static List<String> deliveries(String version) {
        return FixtureVersions.compare(version, "2.0.0") >= 0 ? List.of("skills") : List.of("both", "skills", "commands");
    }

    private static String stringOrNull(JsonObject row, String key) {
        return row.get(key) == null || row.get(key).isJsonNull() ? null : row.get(key).getAsString();
    }

    /** The registry tool for upstream's tool value: the registry's id (devin), not its CLI alias (windsurf). */
    private static AiTool byUpstreamId(String id) {
        AiTool tool = AiToolRegistry.byId(id);
        assertNotNull(tool, "no registry tool with id " + id);
        return tool;
    }

    private String pluginInvocation(JsonObject row, Path project, AiTool tool) {
        String workflow = row.get("workflow").getAsString();
        String skill = row.get("skill").getAsString();
        if (!WorkflowInvocation.EXPLORE.equals(workflow)) {
            WorkflowInvocation.Found found = WorkflowInvocation.find(project, List.of(tool), workflow, skill);
            return found == null ? null : found.invocation();
        }
        Project p = mock(Project.class);
        when(p.getBasePath()).thenReturn(project.toString());
        ExplorePromptService service = new ExplorePromptService(p);
        service.setCandidateToolsForTest(() -> List.of(tool));
        Instructions instructions = service.loadInstructions();
        return instructions.tool() == null ? null : WorkflowInvocation.of(tool.id(), workflow, skill,
                AiToolRegistry.exploreCommandPath(tool), instructions.hasCommand(), instructions.hasSkill());
    }

    /**
     * How a 2.x row's tool-folder link is laid out: a real link (where this host can make one), a copy (Windows project
     * installs, and 2.0 without link support), and the text git leaves for a link without link support. Copies and link
     * text need no link support, so they run on every host.
     */
    enum Layout { SYMLINK, COPY, LINK_TEXT }

    private static List<Layout> layouts(String version) {
        return FixtureVersions.compare(version, "2.0.0") >= 0 ? List.of(Layout.values()) : List.of(Layout.COPY);
    }

    @TestFactory
    Stream<DynamicTest> everyWorkflowToolAndDeliveryGetsUpstreamsInvocation() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String v : releases()) {
            for (Layout layout : layouts(v)) {
                tests.add(DynamicTest.dynamicTest(v + " [" + layout + "]", () -> KnownGaps.check(v, "invocation", "every-row",
                        () -> invocationsMatch(v, layout))));
            }
        }
        return tests.stream();
    }

    private void invocationsMatch(String version, Layout layout) throws IOException {
        if (layout == Layout.SYMLINK) com.johnnyblabs.openspec.util.SymlinkProbe.requireLinks(root);
        boolean is2x = FixtureVersions.compare(version, "2.0.0") >= 0;
        List<String> wrong = new ArrayList<>();
        List<String> workflows = new ArrayList<>();
        java.util.Set<String> unknown = new java.util.TreeSet<>();
        int checked = 0, skipped = 0;
        for (JsonObject row : rows(version)) {
            String workflow = row.get("workflow").getAsString();
            String tool = row.get("tool").getAsString();
            String delivery = row.get("delivery").getAsString();
            String expected = stringOrNull(row, "invocation");
            if (!workflows.contains(workflow)) workflows.add(workflow);
            if (AiToolRegistry.byId(tool) == null) {
                // A tool only a later registry has: the registry change adds it (KnownGaps "registry" entries).
                unknown.add(tool);
                skipped++;
                continue;
            }

            Path project = Files.createDirectories(root.resolve(version).resolve(layout.name())
                    .resolve(workflow + "-" + tool + "-" + delivery));
            for (String path : new String[] {stringOrNull(row, "commandPath"), stringOrNull(row, "skillPath")}) {
                if (path == null) continue;
                Path file = project.resolve(path);
                Files.createDirectories(file.getParent());
                Files.writeString(file, path.endsWith(".toml") ? "prompt = \"\"\"Run.\"\"\"\n" : "Run.\n");
            }
            if (is2x) layOutWhatInitAdds(project, row, tool, layout);
            String actual = pluginInvocation(row, project, byUpstreamId(tool));
            if (!Objects.equals(expected, actual)) {
                wrong.add(workflow + " " + tool + "/" + delivery + ": upstream " + expected + ", plugin " + actual);
            }
            checked++;
        }
        assertEquals(List.of("explore", "update"), workflows);
        assertEquals(ROWS.get(version), checked + skipped, "every row of CLI " + version + "'s matrix is seen");
        assertTrue(checked >= 90, "sanity: rows checked for CLI " + version);
        if (FixtureVersions.compare(version, "2.1.0") < 0) assertEquals(java.util.Set.of(), unknown, "every tool is known");
        assertTrue(wrong.isEmpty(), "invocations that differ from upstream's:\n" + String.join("\n", wrong));
    }

    /**
     * What a 2.x {@code init --tools <tool>} writes beside the shared skill: the tool's line in
     * {@code .agents/skills/.openspec-target}, marked {@code own-files} when it gets links (captured as
     * {@code explore/agents-openspec-target.txt} {@code codex} and {@code -claude.txt} {@code claude own-files}), and the
     * link from its own folder, laid out as {@code layout} says.
     */
    private static void layOutWhatInitAdds(Path project, JsonObject row, String tool, Layout layout) throws IOException {
        String link = stringOrNull(row, "linkPath");
        Path list = SkillToolList.path(project);
        Files.createDirectories(list.getParent());
        Files.writeString(list, tool + (link != null ? " own-files" : "") + "\n");
        if (link == null) return;
        Path spot = project.resolve(link);
        Path shared = project.resolve(stringOrNull(row, "skillPath")).getParent();
        Files.createDirectories(spot.getParent());
        String relative = spot.getParent().relativize(shared).toString().replace('\\', '/');
        switch (layout) {
            case SYMLINK -> Files.createSymbolicLink(spot, Path.of(relative));
            case COPY -> {
                Files.createDirectories(spot);
                Files.copy(shared.resolve("SKILL.md"), spot.resolve("SKILL.md"));
            }
            case LINK_TEXT -> Files.writeString(spot, relative);
        }
    }

    @TestFactory
    Stream<DynamicTest> theMatrixPathsAreTheRegistrysPaths() {
        return releases().stream().map(v -> DynamicTest.dynamicTest(v,
                () -> KnownGaps.check(v, "invocation", "registry-paths", () -> matrixPathsAreRegistryPaths(v))));
    }

    private void matrixPathsAreRegistryPaths(String version) throws IOException {
        DetectionRules rules = DetectionRules.forVersion(version);
        SkillLinkDirs.Line line = FixtureVersions.compare(version, "2.1.0") >= 0 ? SkillLinkDirs.Line.V2_1 : SkillLinkDirs.Line.V2_0;
        boolean is2x = rules.is2x();
        List<String> drift = new ArrayList<>();
        for (JsonObject row : rows(version)) {
            if (AiToolRegistry.byId(row.get("tool").getAsString()) == null) continue;
            AiTool tool = byUpstreamId(row.get("tool").getAsString());
            String workflow = row.get("workflow").getAsString();
            String skill = row.get("skill").getAsString();
            String commandPath = stringOrNull(row, "commandPath");
            String skillPath = stringOrNull(row, "skillPath");
            String linkPath = stringOrNull(row, "linkPath");
            if (commandPath != null && !commandPath.equals(AiToolRegistry.commandPath(tool, workflow))) {
                drift.add(workflow + " " + tool.id() + " command: upstream " + commandPath + ", registry "
                        + AiToolRegistry.commandPath(tool, workflow));
            }
            if (skillPath != null && !skillPath.equals(AiToolRegistry.skillPath(tool, skill, rules))) {
                drift.add(workflow + " " + tool.id() + " skill: upstream " + skillPath + ", registry "
                        + AiToolRegistry.skillPath(tool, skill, rules));
            }
            if (is2x && !Objects.equals(linkPath, SkillLinkDirs.linkPath(line, tool.id(), skill))) {
                drift.add(workflow + " " + tool.id() + " link: upstream " + linkPath + ", plugin "
                        + SkillLinkDirs.linkPath(line, tool.id(), skill));
            }
        }
        assertTrue(drift.isEmpty(), String.join("\n", drift));
    }

    /**
     * The update rows' paths are exactly the update files a real {@code openspec init} wrote, per tool and delivery:
     * upstream's matrix against upstream's own init, for every release. From 2.0 a tool folder can hold a link into
     * {@code .agents/skills/}; the listing records it as {@code <path> -> <target>} and the matrix as {@code linkPath}.
     * Where upstream can't link it copies the skill, which also satisfies {@code linkPath}.
     */
    @TestFactory
    Stream<DynamicTest> theUpdatePathsAreWhatInitWrites() {
        return FixtureVersions.atLeast(FixtureVersions.versionsWith("update/listing-skills.txt", ROWS.keySet()), TARGET)
                .stream().map(v -> DynamicTest.dynamicTest(v, () -> updatePathsAreWhatInitWrites(v)));
    }

    private void updatePathsAreWhatInitWrites(String version) throws IOException {
        Path listings = AiToolRegistryContractTest.FIXTURES.resolve(version).resolve("update");
        List<String> drift = new ArrayList<>();
        int compared = 0;
        for (String delivery : deliveries(version)) {
            java.util.Map<String, java.util.Set<String>> written = new java.util.HashMap<>();
            for (String line : Files.readAllLines(listings.resolve("listing-" + delivery + ".txt"))) {
                if (line.isBlank()) continue;
                String[] parts = line.split(" ", 3);
                assertEquals("P", parts[1], "update files are written in the project: " + line);
                String path = parts[2].contains(" -> ") ? parts[2].substring(0, parts[2].indexOf(" -> ")) : parts[2];
                written.computeIfAbsent(parts[0], k -> new java.util.TreeSet<>()).add(path);
            }
            for (JsonObject row : rows(version)) {
                if (!"update".equals(row.get("workflow").getAsString()) || !delivery.equals(row.get("delivery").getAsString())) {
                    continue;
                }
                java.util.Set<String> matrix = new java.util.TreeSet<>();
                for (String key : new String[] {"commandPath", "skillPath", "linkPath"}) {
                    if (stringOrNull(row, key) != null) matrix.add(stringOrNull(row, key));
                }
                java.util.Set<String> init = new java.util.TreeSet<>(
                        written.getOrDefault(row.get("tool").getAsString(), java.util.Set.of()));
                // Where upstream can't make a link it copies the skill instead (shared/skill-links.js createSkillLink):
                // 2.1's installer does that for eve, whose folder is agent/. Either form is upstream's.
                String link = stringOrNull(row, "linkPath");
                if (link != null && init.remove(link + "/SKILL.md")) init.add(link);
                if (!matrix.equals(init)) drift.add(row.get("tool").getAsString() + "/" + delivery + ": matrix " + matrix + ", init " + init);
                compared++;
            }
        }
        assertEquals(ROWS.get(version) / 2, compared, "every update row of CLI " + version + " is compared");
        assertTrue(drift.isEmpty(), "update paths that differ from what init wrote:\n" + String.join("\n", drift));
    }

    @Test
    void theHandoffAddsTheArgumentsOrNamesTheSkillInWords() {
        String explore = WorkflowInvocation.EXPLORE_SKILL;
        String update = WorkflowInvocation.UPDATE_SKILL;
        assertEquals("/opsx:explore why caching", WorkflowInvocation.handoff("/opsx:explore", explore, "why caching"));
        assertEquals("/opsx:explore", WorkflowInvocation.handoff("/opsx:explore", explore, "  "));
        assertEquals("Use the openspec-explore skill: why caching",
                WorkflowInvocation.handoff(WorkflowInvocation.plainWords(explore), explore, "why caching"));
        assertEquals("Use the openspec-explore skill.",
                WorkflowInvocation.handoff(WorkflowInvocation.plainWords(explore), explore, null));
        assertEquals("Use the openspec-update-change skill: add-auth drop export",
                WorkflowInvocation.handoff(WorkflowInvocation.plainWords(update), update, "add-auth drop export"));
    }
}
