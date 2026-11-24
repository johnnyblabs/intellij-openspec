package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.util.FixtureVersions;
import com.johnnyblabs.openspec.util.KnownGaps;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin's tools equal the OpenSpec CLI's registry, captured from every published CLI by
 * {@code scripts/capture-ai-tools-fixtures.py} ({@code fixtures/cli/<version>/ai-tools.json}).
 * <p>
 * The plugin-equality checks run for the target and every later captured release; where the plugin doesn't match a
 * 2.x registry yet, the case is a {@link KnownGaps} entry. Checks of upstream against itself (its help text against
 * its registry) run for every release that has the capture.
 */
class AiToolRegistryContractTest {

    static final Path FIXTURES = Paths.get("src/test/resources/fixtures/cli");
    /** The plugin's target CLI, read from {@code openspecTargetVersion} in gradle.properties so it can't drift. */
    static final String TARGET = targetVersion();

    private static String targetVersion() {
        try {
            Matcher m = Pattern.compile("(?m)^openspecTargetVersion=(\\S+)$")
                    .matcher(Files.readString(Paths.get("gradle.properties")));
            if (!m.find()) throw new IllegalStateException("gradle.properties has no openspecTargetVersion");
            return m.group(1);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Each release's tool count, so a reshaped or truncated dump can't pass as a registry. */
    static final Map<String, Integer> TOOL_COUNT = Map.of("1.17.0", 50, "2.0.0", 49, "2.1.0", 87, "2.1.1", 87);

    /** The target and every later captured release: the releases the plugin's registry is held to. */
    static List<String> heldTo() {
        return FixtureVersions.atLeast(FixtureVersions.versionsWith("ai-tools.json",
                List.of(TARGET, "2.0.0", "2.1.0", "2.1.1")), TARGET);
    }

    static Stream<DynamicTest> perRelease(String caseName, ThrowingCheck check) {
        return heldTo().stream().map(v -> DynamicTest.dynamicTest(v,
                () -> KnownGaps.check(v, "registry", caseName, () -> check.run(v))));
    }

    @FunctionalInterface
    interface ThrowingCheck {
        void run(String version) throws Throwable;
    }

    private static JsonObject dump(String version) throws IOException {
        return JsonParser.parseString(Files.readString(FIXTURES.resolve(version).resolve("ai-tools.json"))).getAsJsonObject();
    }

    /** Every captured dump, oldest first. */
    private static List<JsonObject> dumps() throws IOException {
        List<JsonObject> out = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(FIXTURES)) {
            for (Path dir : dirs.filter(d -> Files.exists(d.resolve("ai-tools.json"))).toList()) {
                out.add(dump(dir.getFileName().toString()));
            }
        }
        out.sort(Comparator.comparing(d -> version(d.get("version").getAsString())));
        return out;
    }

    private static String version(String v) {
        StringBuilder key = new StringBuilder();
        for (String part : v.split("\\.")) key.append(String.format("%05d", Integer.parseInt(part)));
        return key.toString();
    }

    private static List<String> strings(JsonObject o, String field) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(field);
        if (e != null) for (JsonElement s : e.getAsJsonArray()) out.add(s.getAsString());
        return out;
    }

    private static String stringOrNull(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static Set<String> ids(JsonObject dump) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement t : dump.getAsJsonArray("tools")) ids.add(t.getAsJsonObject().get("value").getAsString());
        return ids;
    }

    @TestFactory
    Stream<DynamicTest> theToolsAreTheCliRegistryInItsOrder() {
        return perRelease("tools-in-order", v -> {
            List<String> upstream = new ArrayList<>(ids(dump(v)));
            List<String> plugin = accepted(v).stream().map(AiTool::id).toList();

            assertEquals(TOOL_COUNT.get(v), upstream.size(), "sanity: CLI " + v + "'s tool count");
            assertEquals(upstream, plugin);
        });
    }

    /** The plugin's tools a release accepts: the one list, filtered by each tool's release range. */
    private static List<AiTool> accepted(String version) {
        return AiToolRegistry.all().stream().filter(t -> AiToolRegistry.acceptedBy(t, version)).toList();
    }

    @TestFactory
    Stream<DynamicTest> eachToolsFieldsEqualTheRegistrys() {
        return perRelease("tool-fields", this::fieldsEqual);
    }

    private void fieldsEqual(String version) throws IOException {
        for (JsonElement e : dump(version).getAsJsonArray("tools")) {
            JsonObject t = e.getAsJsonObject();
            String id = t.get("value").getAsString();
            AiTool tool = AiToolRegistry.byId(id);
            assertNotNull(tool, id);
            // Folders as that release's line has them (MiniMax Code moved into the project in 2.1).
            DetectionRules rules = DetectionRules.forVersion(version);
            assertEquals(t.get("name").getAsString(), tool.name(), id + " name");
            assertEquals(stringOrNull(t, "skillsDir"), rules.skillsDir(tool), id + " skillsDir");
            assertEquals(stringOrNull(t, "globalSkillsDir"), rules.globalSkillsDir(tool), id + " globalSkillsDir");
            assertEquals(strings(t, "detectionPaths"), tool.detectionPaths(), id + " detectionPaths");
            assertEquals(strings(t, "legacySkillsDirs"), tool.legacySkillsDirs(), id + " legacySkillsDirs");
        }
    }

    @TestFactory
    Stream<DynamicTest> anIdOtherThanTheToolsIsOneTheCliAliasesToIt() {
        return perRelease("aliases", v -> {
            JsonObject aliases = dump(v).getAsJsonObject("aliases");
            assertNotNull(aliases, v + " dump has no aliases");
            for (AiTool tool : AiToolRegistry.all()) {
                if (tool.cliId().equals(tool.id())) continue;
                assertTrue(aliases.has(tool.cliId()), tool.cliId() + " is not a CLI alias");
                assertEquals(tool.id(), aliases.get(tool.cliId()).getAsString());
            }
            assertEquals("windsurf", AiToolRegistry.byId("devin").cliId(), "Devin Desktop is passed as windsurf");
        });
    }

    /** Every id the plugin passes is one the release accepts, as an id or an alias. */
    @TestFactory
    Stream<DynamicTest> everyPassedIdIsAccepted() {
        return perRelease("passed-ids-accepted", v -> {
            JsonObject d = dump(v);
            List<String> refused = accepted(v).stream().map(AiTool::cliId)
                    .filter(id -> !ids(d).contains(id) && !d.getAsJsonObject("aliases").has(id)).toList();
            assertEquals(List.of(), refused, "CLI " + v + " refuses these ids");
        });
    }

    /**
     * Each tool's range is the captured releases that accept the id it passes, as an id or an alias: {@code sinceCli}
     * is the first, {@code untilCli} the first after it that refuses it (null when none does), with no gap between.
     */
    @Test
    void sinceAndUntilCliAreTheCapturedReleasesAcceptingThePassedId() throws IOException {
        List<JsonObject> dumps = dumps();
        assertEquals("1.3.0", dumps.getFirst().get("version").getAsString(), "sanity: dumps start at the CLI floor");
        assertTrue(dumps.stream().anyMatch(d -> d.get("version").getAsString().equals("2.1.1")), "sanity: 2.x dumps read");
        for (AiTool tool : AiToolRegistry.all()) {
            String first = null;
            String refused = null;
            boolean gap = false;
            for (JsonObject d : dumps) {
                String v = d.get("version").getAsString();
                boolean accepts = ids(d).contains(tool.cliId()) || d.getAsJsonObject("aliases").has(tool.cliId());
                if (accepts && first == null) first = v;
                else if (!accepts && first != null && refused == null) refused = v;
                else if (accepts && refused != null) gap = true;
                assertEquals(accepts, AiToolRegistry.acceptedBy(tool, v), tool.id() + " acceptedBy " + v);
            }
            assertEquals(first, tool.sinceCli(), tool.id() + " sinceCli");
            assertEquals(refused, tool.untilCli(), tool.id() + " untilCli");
            assertFalse(gap, tool.cliId() + " is accepted again after " + refused);
        }
    }

    /** CLI 2.0 retired Amazon Q: its init refuses the id, as captured on 2.0.0 and 2.1.1. */
    @Test
    void amazonQIsRefusedFrom200AsTheCliRefusesIt() throws IOException {
        AiTool amazonQ = AiToolRegistry.byId("amazon-q");
        for (String v : List.of("2.0.0", "2.1.1")) {
            Path errors = FIXTURES.resolve(v).resolve("errors");
            assertEquals("1", Files.readString(errors.resolve("init-amazon-q.exit")).strip(), v);
            assertTrue(Files.readString(errors.resolve("init-amazon-q.stderr")).contains("Amazon Q Developer is no longer supported"), v);
            assertFalse(AiToolRegistry.acceptedBy(amazonQ, v), v);
        }
        assertTrue(AiToolRegistry.acceptedBy(amazonQ, "1.17.0"));
        assertTrue(AiToolRegistry.acceptedBy(amazonQ, null), "with no CLI nothing is passed, so nothing is filtered");
    }

    /** The tools sharing a project folder on 2.1 are the groups upstream's installer forms ({@code agentsByProjectFolder}). */
    @TestFactory
    Stream<DynamicTest> theFolderGroupsAreUpstreams() {
        return Stream.of("2.1.0", "2.1.1").map(v -> DynamicTest.dynamicTest(v, () -> {
            JsonObject groups = dump(v).getAsJsonObject("agentsByProjectFolder");
            assertNotNull(groups, v + " dump has no agentsByProjectFolder");
            Map<String, List<String>> upstream = new TreeMap<>();
            for (String folder : groups.keySet()) upstream.put(folder, strings(groups, folder));
            Map<String, List<String>> plugin = new TreeMap<>();
            for (AiTool tool : AiToolRegistry.all()) {
                List<AiTool> group = AiToolRegistry.folderGroup(tool);
                if (!group.isEmpty()) plugin.put(tool.skillsDir() + "/skills", group.stream().map(AiTool::id).toList());
            }
            assertEquals(upstream, plugin);
            assertEquals(3, plugin.size(), "sanity: Trae, Qoder and Zencoder");
        }));
    }

    /** Upstream against itself: each release's init help lists exactly its registry's ids. */
    @TestFactory
    Stream<DynamicTest> theCliHelpListsTheSameIds() {
        List<String> versions = FixtureVersions.versionsWith("init-help.txt", List.of(TARGET, "2.0.0", "2.1.0", "2.1.1"));
        return versions.stream().map(v -> DynamicTest.dynamicTest(v, () -> helpListsRegistry(v)));
    }

    private static void helpListsRegistry(String version) throws IOException {
        String help = Files.readString(FIXTURES.resolve(version).resolve("init-help.txt"));
        Matcher m = Pattern.compile("comma-separated list of:(.*?)(?:\\n\\s*\\n|\\n\\s{2}-|$)", Pattern.DOTALL).matcher(help);
        assertTrue(m.find(), help);
        String list = m.group(1).replaceAll("\\s+", " ");
        int stop = list.indexOf('.');
        if (stop >= 0) list = list.substring(0, stop);
        Set<String> helpIds = new LinkedHashSet<>();
        for (String id : list.split(",")) helpIds.add(id.trim());

        assertEquals(ids(dump(version)), helpIds);
    }

    @Test
    void namesAndFormerNamesResolveToOneToolEach() {
        Map<String, String> seen = new HashMap<>();
        for (AiTool tool : AiToolRegistry.all()) {
            for (String name : concat(tool.name(), tool.aliases())) {
                assertNull(seen.put(name, tool.id()), name + " names two tools");
                assertSame(tool, AiToolRegistry.byName(name));
            }
        }
        assertEquals(List.of("Amazon Q", "Augment", "Bob Shell", "CodeBuddy", "Costrict", "Factory", "Gemini",
                        "Kilocode", "Kimi CLI", "Qwen", "Roo Code", "Windsurf"),
                AiToolRegistry.all().stream().flatMap(t -> t.aliases().stream()).sorted().toList(),
                "every name the plugin used before keeps working");
    }

    private static List<String> concat(String first, List<String> rest) {
        List<String> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all;
    }

    @Test
    void aToolIsAcceptedOnlyFromItsFirstCliVersion() {
        AiTool amp = AiToolRegistry.byId("amp");
        assertEquals("1.14.0", amp.sinceCli());
        assertFalse(AiToolRegistry.acceptedBy(amp, "1.3.0"));
        assertFalse(AiToolRegistry.acceptedBy(amp, "1.13.2"));
        assertTrue(AiToolRegistry.acceptedBy(amp, "1.14.0"));
        assertTrue(AiToolRegistry.acceptedBy(amp, "1.14.3"));
        assertTrue(AiToolRegistry.acceptedBy(amp, null), "with no CLI nothing is passed, so nothing is filtered");
        assertTrue(AiToolRegistry.acceptedBy(AiToolRegistry.byId("devin"), "1.3.0"));
    }

    @Test
    void addingWithoutTheCliCreatesAPathUpstreamDetects() {
        assertEquals(".claude", AiToolRegistry.builtInDir(AiToolRegistry.byId("claude")));
        assertEquals(".devin", AiToolRegistry.builtInDir(AiToolRegistry.byId("devin")));
        assertEquals(".github/instructions", AiToolRegistry.builtInDir(AiToolRegistry.byId("github-copilot")));
        assertEquals(".agents/workflows", AiToolRegistry.builtInDir(AiToolRegistry.byId("antigravity")));
        assertEquals(".amp", AiToolRegistry.builtInDir(AiToolRegistry.byId("amp")));
        assertEquals(".hermes", AiToolRegistry.builtInDir(AiToolRegistry.byId("hermes")));
        assertEquals(".agents/skills", AiToolRegistry.builtInDir(AiToolRegistry.byId("codex")));
        assertEquals(".agents/skills/.openspec-target", AiToolRegistry.builtInMarker(AiToolRegistry.byId("codex")));
        assertNull(AiToolRegistry.builtInMarker(AiToolRegistry.byId("amp")));
        assertNull(AiToolRegistry.builtInDir(AiToolRegistry.byId("minimax-code")), "a global tool has no project directory");
    }
}
