package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;

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

    static final List<String> RELEASES = List.of("2.1.0", "2.1.1");

    /**
     * Cases where the plugin's set-up status differs from 2.1's, older than the 2.1 tool registry and out of its scope:
     * a Codex recorded only by the tool list, and 1.6-era Kimi and Windsurf folders, which 2.1 no longer counts. A
     * listed case that starts matching fails, so the entry is removed when it's fixed.
     */
    static final java.util.Set<String> KNOWN_DIVERGENCES = java.util.Set.of(
            "builtin-add-codex-list-on-empty", "kimi-dir-from-1.6", "windsurf-dir-from-1.6");

    private Path home;

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

    public void testSetUpToolsAreWhatUpstreamCountsAsConfigured() throws Exception {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (String v : RELEASES) {
            List<Path> cases;
            try (Stream<Path> s = Files.list(FixtureVersions.CLI.resolve(v).resolve("detect"))) {
                cases = s.filter(d -> Files.exists(d.resolve("configured.json"))).sorted().toList();
            }
            for (Path c : cases) {
                TreeSet<String> upstream = new TreeSet<>();
                for (JsonElement e : JsonParser.parseString(Files.readString(c.resolve("configured.json"))).getAsJsonArray()) {
                    upstream.add(e.getAsString());
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
        assertTrue("sanity: cases checked", checked > 40);
        assertTrue("set-up tools that differ from upstream's:\n" + String.join("\n", wrong), wrong.isEmpty());
    }
}
