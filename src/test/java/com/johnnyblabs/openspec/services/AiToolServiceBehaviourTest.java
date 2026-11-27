package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolInfo;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The detection service on real project layouts: status from the files OpenSpec writes, the detected CLI's version
 * gating which tool ids are offered or passed, and names saved by earlier versions.
 */
public class AiToolServiceBehaviourTest extends BasePlatformTestCase {

    private final List<File> created = new ArrayList<>();
    private Path home;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        home = Files.createTempDirectory("aitools-home");
        AiToolDetectionService.setHomeForTest(home);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            AiToolDetectionService.setHomeForTest(null);
            service().setCliVersionForTest(null);
            OpenSpecSettings.getInstance(getProject()).setPreferredTool("");
            created.forEach(FileUtil::delete);
            FileUtil.delete(home.toFile());
        } finally {
            super.tearDown();
        }
    }

    private AiToolDetectionService service() {
        return getProject().getService(AiToolDetectionService.class);
    }

    /** Lays a captured {@code init --tools} project ({@code fixtures/cli/1.14.3/detect/<case>}) under the base path. */
    private void layOut(String caseName) throws Exception {
        layOut("1.14.3", caseName);
    }

    /** {@link #layOut(String)} from another release's captures. */
    private void layOut(String release, String caseName) throws Exception {
        JsonObject layout = JsonParser.parseString(Files.readString(
                AiToolDetectionContractTest.CLI.resolve(release + "/detect").resolve(caseName).resolve("layout.json"))).getAsJsonObject();
        Path base = Path.of(getProject().getBasePath());
        for (String path : layout.getAsJsonObject("project").keySet()) {
            created.add(base.resolve(path.substring(0, path.contains("/") ? path.indexOf('/') : path.length())).toFile());
        }
        AiToolDetectionContractTest.materialize(layout.getAsJsonObject("project"), base);
        AiToolDetectionContractTest.materialize(layout.getAsJsonObject("home"), home);
    }

    private ToolInfo info(String name) {
        return service().getAllToolsWithStatus().stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    public void testToolsSetUpByTheCliShowAsConfiguredWhereItNowWritesThem() throws Exception {
        layOut("devin-via-windsurf-id");
        layOut("kimi");
        layOut("codex-and-antigravity");

        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus("Devin Desktop (formerly Windsurf)"));
        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus("Kimi Code"));
        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus("Codex"));
        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus("Antigravity"));
        assertEquals("a tool sharing .agents without owning it is not set up",
                ToolStatus.AVAILABLE, service().getToolStatus("Zed Agent"));
    }

    /** With no CLI detected the newest rules apply: a 1.6-era folder is only a detection path from 1.7 on. */
    public void testOlderDirectoriesAreDetectedNotSetUpWithNoCli() throws Exception {
        layOut("windsurf-dir-from-1.6");
        layOut("kimi-dir-from-1.6");

        assertEquals(ToolStatus.DETECTED, service().getToolStatus("Devin Desktop (formerly Windsurf)"));
        assertEquals(ToolStatus.DETECTED, service().getToolStatus("Kimi Code"));
    }

    public void testAGlobalToolIsConfiguredFromTheHomeDirectory() throws Exception {
        layOut("minimax-global");

        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus("MiniMax Code"));
    }

    public void testADetectedToolWithoutOpenSpecFilesIsDetectedNotConfigured() throws Exception {
        layOut("gsd-dir-only");

        assertEquals(ToolStatus.DETECTED, service().getToolStatus("GSD"));
    }

    /** CLI 2.0 retired Amazon Q: not listed on 2.x, even for a project 1.17 set it up in; listed as before on 1.17. */
    public void testAmazonQIsListedOnlyBelow200() throws Exception {
        layOut("1.17.0", "amazon-q-from-1.17");
        for (String v : List.of("2.0.0", "2.1.1", "2.2.0")) {
            service().setCliVersionForTest(() -> v);
            service().detect();
            assertNull(v + ": not listed, as 2.x doesn't report it", info("Amazon Q Developer"));
            assertFalse(v, service().getDetectedCliToolIds().contains("amazon-q"));
        }
        service().setCliVersionForTest(() -> "1.17.0");
        service().detect();
        assertNotNull("1.17 still lists it", info("Amazon Q Developer"));
        assertEquals("amazon-q", info("Amazon Q Developer").cliId());

        FileUtil.delete(Path.of(getProject().getBasePath(), ".amazonq").toFile());
        service().setCliVersionForTest(() -> "2.1.1");
        service().detect();
        assertNull("nor offered to add on 2.x", info("Amazon Q Developer"));
    }

    public void testAnOlderCliIsNotOfferedOrPassedNewerTools() throws Exception {
        layOut("amp");
        layOut("claude");
        service().setCliVersionForTest(() -> "1.13.2");
        service().detect();

        assertTrue("sanity: Amp is detected", service().getDetectedTools().contains("Amp"));
        assertEquals(List.of("claude"), service().getDetectedCliToolIds());
        ToolInfo amp = info("Amp");
        assertNotNull("a tool the project already has is still listed", amp);
        assertNull("but not offered for init", amp.cliId());
        assertNull("an undetected newer tool is not listed", info("Warp"));
        assertEquals("claude", info("Claude Code").cliId());
    }

    public void testTheTargetCliIsPassedEveryDetectedTool() throws Exception {
        layOut("amp");
        layOut("devin-via-windsurf-id");
        service().setCliVersionForTest(() -> "1.14.3");
        service().detect();

        assertEquals(List.of("amp", "windsurf"), service().getDetectedCliToolIds().stream().sorted().toList());
        assertEquals("amp", info("Amp").cliId());
        assertNotNull(info("Warp"));
    }

    public void testWithNoCliNothingIsFiltered() {
        service().setCliVersionForTest(() -> null);

        assertEquals("with no CLI, every tool of every release", AiToolRegistry.all().size(), service().getAllToolsWithStatus().size());
    }

    public void testANameSavedByAnEarlierVersionMapsToTheCurrentOne() throws Exception {
        layOut("devin-via-windsurf-id");
        service().detect();
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.getState().preferredTool = "Windsurf";

        assertEquals("Devin Desktop (formerly Windsurf)", settings.getPreferredTool());
        assertEquals("Devin Desktop (formerly Windsurf)", service().getPreferredToolLabel());
        settings.setPreferredTool("Roo Code");
        assertEquals("Zoo Code", settings.getState().preferredTool);
    }

    /** The same files, the installed CLI's verdict: a lone {@code .gsd} folder is the shared-folder tool to 1.x, GSD to 2.x. */
    public void testDetectionFollowsTheDetectedCliLine() throws Exception {
        layOut("gsd");
        String universal = AiToolRegistry.byId("agents").name();
        String gsd = AiToolRegistry.byId("gsd").name();

        service().setCliVersionForTest(() -> "1.17.0");
        service().detect();
        assertEquals(List.of(universal), service().getDetectedTools());

        service().setCliVersionForTest(() -> "2.1.1");
        service().detect();
        assertEquals(List.of(gsd), service().getDetectedTools());

        service().setCliVersionForTest(() -> "2.0.0");
        service().detect();
        assertEquals(List.of(gsd), service().getDetectedTools());
    }

    /** With no CLI, a 1.x layout is read by 1.x rules. */
    public void testNoCliReadsA1xLayoutBy1xRules() throws Exception {
        layOut("gsd");
        service().setCliVersionForTest(() -> null);
        service().detect();
        assertEquals(List.of(AiToolRegistry.byId("agents").name()), service().getDetectedTools());
    }

    /** With no CLI, a 2.x list (several tools, or own-files) switches to 2.x rules. */
    public void testNoCliReadsA2xListBy2xRules() throws Exception {
        Path base = Path.of(getProject().getBasePath());
        created.add(base.resolve(".agents").toFile());
        Path skills = Files.createDirectories(base.resolve(".agents/skills/openspec-explore"));
        Files.writeString(skills.resolve("SKILL.md"), "");
        Files.writeString(base.resolve(".agents/skills/.openspec-target"), "cursor\ngithub-copilot\n");
        service().setCliVersionForTest(() -> null);
        assertEquals(DetectionRules.V2_1_1, service().rules(base));
        service().detect();
        assertEquals(List.of(AiToolRegistry.byId("cursor").name(), AiToolRegistry.byId("github-copilot").name()),
                service().getDetectedTools());
    }

    /** On 2.x a skills-only tool has no folder of its own: listed with the shared skills, it is set up. */
    public void testAListed2xSkillsOnlyToolIsConfigured() throws Exception {
        Path base = Path.of(getProject().getBasePath());
        created.add(base.resolve(".agents").toFile());
        Path skill = Files.createDirectories(base.resolve(".agents/skills/openspec-explore"));
        Files.writeString(skill.resolve("SKILL.md"), "");
        Files.writeString(base.resolve(".agents/skills/.openspec-target"), "cursor\n");
        service().setCliVersionForTest(() -> "2.1.1");
        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus(AiToolRegistry.byId("cursor").name()));
    }
}
