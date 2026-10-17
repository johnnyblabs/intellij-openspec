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
        JsonObject layout = JsonParser.parseString(Files.readString(
                AiToolDetectionContractTest.CASES.resolve(caseName).resolve("layout.json"))).getAsJsonObject();
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

    public void testOlderDirectoriesStillCountAsConfigured() throws Exception {
        layOut("windsurf-dir-from-1.6");
        layOut("kimi-dir-from-1.6");

        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus("Devin Desktop (formerly Windsurf)"));
        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus("Kimi Code"));
    }

    public void testAGlobalToolIsConfiguredFromTheHomeDirectory() throws Exception {
        layOut("minimax-global");

        assertEquals(ToolStatus.CONFIGURED, service().getToolStatus("MiniMax Code"));
    }

    public void testADetectedToolWithoutOpenSpecFilesIsDetectedNotConfigured() throws Exception {
        layOut("gsd-dir-only");

        assertEquals(ToolStatus.DETECTED, service().getToolStatus("GSD"));
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

        assertEquals(50, service().getAllToolsWithStatus().size());
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
}
