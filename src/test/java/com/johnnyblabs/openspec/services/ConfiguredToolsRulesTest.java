package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolInfo;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Each rule behind a tool's set-up status, on the captured layouts whose {@code configured.json} shows the CLI's own
 * verdict ({@code fixtures/cli/<version>/detect/}). {@link ConfiguredToolsContractTest} checks only which tools are set
 * up; these also check that a tool which isn't stays detected, and pin each rule to the release that introduced it.
 */
public class ConfiguredToolsRulesTest extends BasePlatformTestCase {

    private Path home;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        home = Files.createTempDirectory("configured-rules-home");
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

    /** Lays out {@code <capture>/detect/<caseName>} and runs detection under CLI {@code cli} ({@code null}: none). */
    private void layOut(String capture, String caseName, String cli, boolean withHome) throws Exception {
        Path base = Path.of(getProject().getBasePath());
        File[] old = base.toFile().listFiles();
        if (old != null) for (File f : old) FileUtil.delete(f);
        FileUtil.delete(home.toFile());
        Files.createDirectories(home);
        JsonObject layout = JsonParser.parseString(Files.readString(
                FixtureVersions.CLI.resolve(capture).resolve("detect").resolve(caseName).resolve("layout.json"))).getAsJsonObject();
        if (!withHome) layout.add("home", new JsonObject());
        FixtureLayout.materialize(layout, base, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        GlobalSkillsCache.getInstance(getProject()).setHomeForTest(() -> home);
        service().setCliVersionForTest(() -> cli);
        service().detect();
    }

    private void layOut(String release, String caseName) throws Exception {
        layOut(release, caseName, release, true);
    }

    /** The tool's row in Manage AI Tools, and its single-tool status, which must agree. */
    private ToolStatus status(String id) {
        String name = AiToolRegistry.byId(id).name();
        ToolStatus row = service().getAllToolsWithStatus().stream().filter(t -> t.name().equals(name))
                .map(ToolInfo::status).findFirst().orElse(ToolStatus.AVAILABLE);
        assertEquals(id + ": Manage AI Tools and getToolStatus agree", row, service().getToolStatus(name));
        return row;
    }

    public void testATheListNamesIsSetUpWithoutASkillOfItsOwn() throws Exception {
        for (String v : List.of("1.17.0", "2.0.0", "2.1.0", "2.1.1", "2.2.0")) {
            layOut(v, "builtin-add-codex-list-on-empty");
            assertEquals(v, ToolStatus.CONFIGURED, status("codex"));
        }
    }

    /** 1.x: the marker's tool owns .agents; the universal tool, detected there, is not set up from its skills. */
    public void testTheOwnerOfASharedSkillsFolderIsSetUpAndOnlyIt() throws Exception {
        layOut("1.17.0", "gsd");

        assertFalse("GSD has no .gsd folder, so it isn't detected",
                service().getDetectedTools().contains(AiToolRegistry.byId("gsd").name()));
        assertEquals("set up without being detected", ToolStatus.CONFIGURED, status("gsd"));
        assertEquals(ToolStatus.DETECTED, status(AiToolRegistry.UNIVERSAL_ID));
    }

    public void testAOneSixEraFolderIsDetectedNotSetUpFromOneSeven() throws Exception {
        for (String v : List.of("1.7.0", "1.17.0", "2.0.0", "2.1.1", "2.2.0")) {
            layOut(v, "kimi-dir-from-1.6");
            assertEquals(v, ToolStatus.DETECTED, status("kimi"));
            layOut(v, "windsurf-dir-from-1.6");
            assertEquals(v, ToolStatus.DETECTED, status("devin"));
        }
    }

    public void testAOneSixEraFolderIsSetUpBeforeOneSeven() throws Exception {
        layOut("1.6.0", "kimi-dir-from-1.6");
        assertEquals(ToolStatus.CONFIGURED, status("kimi"));
        layOut("1.6.0", "windsurf-dir-from-1.6");
        assertEquals(ToolStatus.CONFIGURED, status("devin"));
    }

    /** With no CLI detected a 1.x project follows the newest 1.x rules. */
    public void testNoCliFollowsTheNewestOneXRules() throws Exception {
        layOut("1.17.0", "kimi-dir-from-1.6", null, true);
        assertEquals(ToolStatus.DETECTED, status("kimi"));
        layOut("1.17.0", "gsd", null, true);
        assertEquals(ToolStatus.CONFIGURED, status("gsd"));
        assertEquals(ToolStatus.DETECTED, status(AiToolRegistry.UNIVERSAL_ID));
    }

    /**
     * An older skills folder the CLI lists for the tool still counts, with no command beside it: the captures remove
     * Codex's HOME prompts and Antigravity's {@code .agent/workflows}, which would set each up alone.
     */
    public void testAnOlderSkillsFolderTheCliListsStillCounts() throws Exception {
        for (String v : List.of("1.7.0", "1.17.0")) {
            layOut(v, "codex-skills-only-from-1.6");
            assertEquals(v, ToolStatus.CONFIGURED, status("codex"));
            layOut(v, "antigravity-skills-only-from-1.6");
            assertEquals(v, ToolStatus.CONFIGURED, status("antigravity"));
        }
    }

    /** Before 1.8 the CLI's verdict doesn't read the marker: the list alone sets nothing up. */
    public void testTheMarkerCountsOnlyFromOneEight() throws Exception {
        layOut("1.7.0", "builtin-add-codex-list-on-empty");
        assertFalse(ToolStatus.CONFIGURED.equals(status("codex")));
    }

    /** 2.x: listed own-files, with its links gone and nothing else of its own; the shared copy isn't its own. */
    public void testAToolListedOwnFilesWithNothingOfItsOwnIsNotSetUp() throws Exception {
        for (String v : List.of("2.0.0", "2.1.0", "2.1.1", "2.2.0")) {
            layOut(v, "own-files-links-removed");
            assertEquals(v, ToolStatus.DETECTED, status("claude"));
        }
    }
}
