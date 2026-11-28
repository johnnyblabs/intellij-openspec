package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;
import com.johnnyblabs.openspec.util.KnownGaps;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Which tools the plugin shows as set up equals what upstream's own {@code getConfiguredTools} reported for each
 * captured layout ({@code detect/<case>/configured.json}), under each release's CLI.
 */
public class ConfiguredToolsContractTest extends BasePlatformTestCase {

    static final List<String> RELEASES = List.of("1.3.1", "1.4.0", "1.5.0", "1.6.0", "1.7.0", "1.8.0", "1.10.0", "1.11.0", "1.13.2",
            "1.14.0", "1.14.3", "1.16.1", "1.17.0", "2.0.0", "2.1.0", "2.1.1", "2.2.0");

    /**
     * Cases where the plugin's set-up status differs from 2.1's, older than the 2.1 tool registry and out of its scope:
     * a Codex recorded only by the tool list, and 1.6-era Kimi and Windsurf folders, which 2.1 no longer counts. A
     * listed case that starts matching fails, so the entry is removed when it's fixed.
     */
    static final java.util.Set<String> KNOWN_DIVERGENCES = java.util.Set.of();

    private Path home;
    private int aliased;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        home = Files.createTempDirectory("configured-home");
        AiToolDetectionService.setHomeForTest(home);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            AiToolDetectionService.setHomeForTest(null);
            AiToolDetectionService.setSavedScopeForTest(null);
            service().setCliVersionForTest(null);
            GlobalSkillsCache.getInstance(getProject()).setHomeForTest(null);
            FileUtil.delete(home.toFile());
        } finally {
            super.tearDown();
        }
    }

    private AiToolDetectionService service() {
        return getProject().getService(AiToolDetectionService.class);
    }

    private TreeSet<String> configured(String version, Path caseDir) throws Exception {
        Path base = Path.of(getProject().getBasePath());
        for (File f : base.toFile().listFiles() == null ? new File[0] : base.toFile().listFiles()) FileUtil.delete(f);
        FileUtil.delete(home.toFile());
        Files.createDirectories(home);
        FixtureLayout.materialize(JsonParser.parseString(Files.readString(caseDir.resolve("layout.json"))).getAsJsonObject(),
                base, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        GlobalSkillsCache.getInstance(getProject()).setHomeForTest(() -> home);
        service().setCliVersionForTest(() -> version);
        service().detect();
        TreeSet<String> ids = new TreeSet<>();
        for (AiToolDetectionService.ToolInfo info : service().getAllToolsWithStatus()) {
            if (info.status() == ToolStatus.CONFIGURED) ids.add(AiToolRegistry.byName(info.name()).id());
        }
        return ids;
    }

    private List<AiToolDetectionService.ToolInfo> toolsFor(String version, Path caseDir) throws Exception {
        Path base = Path.of(getProject().getBasePath());
        for (File f : base.toFile().listFiles() == null ? new File[0] : base.toFile().listFiles()) FileUtil.delete(f);
        FileUtil.delete(home.toFile());
        Files.createDirectories(home);
        FixtureLayout.materialize(JsonParser.parseString(Files.readString(caseDir.resolve("layout.json"))).getAsJsonObject(),
                base, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        GlobalSkillsCache.getInstance(getProject()).setHomeForTest(() -> home);
        service().setCliVersionForTest(() -> version);
        service().detect();
        // Off the UI thread, as the dialog reads it: there the home cache is read, not just scheduled.
        return com.intellij.openapi.application.ApplicationManager.getApplication()
                .executeOnPooledThread(() -> service().getAllToolsWithStatus()).get(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static TreeSet<String> strings(com.google.gson.JsonObject o, String key) {
        TreeSet<String> out = new TreeSet<>();
        o.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
        return out;
    }

    /**
     * Machine-wide installs (CLI 2.1+): set-up status is the CLI's own, and a detected tool is flagged as one that
     * {@code openspec init} installs for this machine exactly when the CLI's own {@code init --tools <tool>} left it not
     * set up for the project. The home-folder-as-project case is #751's.
     */
    public void testMachineWideInstallsFollowTheCli() throws Throwable {
        int checked = 0, flagged = 0, notFlagged = 0;
        for (String v : List.of("2.1.0", "2.1.1", "2.2.0")) {
            Path dir = FixtureVersions.CLI.resolve(v).resolve("machine-wide");
            assertEquals(v + " no-global-support list", strings(JsonParser.parseString(
                    Files.readString(dir.resolve("global-support.json"))).getAsJsonObject(), "noGlobalSupport"),
                    new TreeSet<>(AiToolRegistry.NO_GLOBAL_SKILLS));
            for (String name : List.of("agents-global", "home-copies-only", "links-only", "uncovered-tool",
                    "project-and-global", "project-commands-only", "saved-scope-project")) {
                Path c = dir.resolve(name);
                var before = JsonParser.parseString(Files.readString(c.resolve("probe.json"))).getAsJsonObject();
                var after = JsonParser.parseString(Files.readString(c.resolve("after.json"))).getAsJsonObject();
                AiToolDetectionService.setSavedScopeForTest(() -> name.equals("saved-scope-project") ? "project" : null);
                TreeSet<String> configured = new TreeSet<>(), forMachine = new TreeSet<>(), expected = new TreeSet<>();
                for (AiToolDetectionService.ToolInfo info : toolsFor(v, c)) {
                    String id = AiToolRegistry.byName(info.name()).id();
                    if (info.status() == ToolStatus.CONFIGURED) configured.add(id);
                    if (info.installsForMachine()) forMachine.add(id);
                    if (info.status() == ToolStatus.DETECTED && !strings(after, "configured").contains(id)
                            && strings(before, "available").contains(id)) {
                        expected.add(id);
                    }
                }
                String key = v + "/" + name;
                KnownGaps.check(v, "machine-wide", name, () -> {
                    // JUnit 5's assertion: KnownGaps counts only its AssertionFailedError as the gap.
                    org.junit.jupiter.api.Assertions.assertEquals(strings(before, "configured"), configured, key + " set up");
                    org.junit.jupiter.api.Assertions.assertEquals(expected, forMachine, key + " init installs for the machine");
                });
                flagged += forMachine.size();
                if (forMachine.isEmpty()) notFlagged++;
                checked++;
            }
        }
        AiToolDetectionService.setSavedScopeForTest(null);
        assertEquals(21, checked);
        assertTrue("sanity: some cases flag a tool and some don't: " + flagged + "/" + notFlagged, flagged >= 9 && notFlagged >= 9);
    }

    public void testSetUpToolsAreWhatUpstreamCountsAsConfigured() throws Exception {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        aliased = 0;
        for (String v : RELEASES) {
            List<Path> cases;
            try (Stream<Path> s = Files.list(FixtureVersions.CLI.resolve(v).resolve("detect"))) {
                cases = s.filter(d -> Files.exists(d.resolve("configured.json"))).sorted().toList();
            }
            for (Path c : cases) {
                TreeSet<String> upstream = new TreeSet<>();
                for (JsonElement e : JsonParser.parseString(Files.readString(c.resolve("configured.json"))).getAsJsonArray()) {
                    // 1.6 still called Devin Desktop "windsurf"; the plugin knows it by its later id.
                    upstream.add(SkillToolList.ALIASES.getOrDefault(e.getAsString(), e.getAsString()));
                    if (e.getAsString().equals("windsurf")) aliased++;
                }
                TreeSet<String> plugin = configured(v, c);
                boolean known = KNOWN_DIVERGENCES.contains(c.getFileName().toString());
                if (known == upstream.equals(plugin)) {
                    wrong.add(v + "/" + c.getFileName() + (known ? ": now matches, remove it from KNOWN_DIVERGENCES" : "")
                            + ": upstream " + upstream + ", plugin " + plugin);
                }
                checked++;
            }
        }
        assertTrue("sanity: cases checked " + checked, checked >= 200);
        assertTrue("sanity: 1.6.0's own windsurf id is read through the alias", aliased > 0);
        assertTrue("set-up tools that differ from upstream's:\n" + String.join("\n", wrong), wrong.isEmpty());
    }
}
