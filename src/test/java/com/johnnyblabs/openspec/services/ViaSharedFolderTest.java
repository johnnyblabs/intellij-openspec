package com.johnnyblabs.openspec.services;

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

/**
 * The "via shared .agents skills" line, on the captured 2.1.1 {@code init --tools claude,codex,cursor --scope project}
 * project: Cursor reaches its skills only through the shared copy; Claude links them into its own folder; Codex's own
 * folder is {@code .agents}.
 */
public class ViaSharedFolderTest extends BasePlatformTestCase {

    private Path home;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        home = Files.createTempDirectory("via-home");
        AiToolDetectionService.setHomeForTest(home);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            AiToolDetectionService.setHomeForTest(null);
            service().setCliVersionForTest(null);
            Path base = Path.of(getProject().getBasePath());
            File[] files = base.toFile().listFiles();
            if (files != null) for (File f : files) FileUtil.delete(f);
            FileUtil.delete(home.toFile());
        } finally {
            super.tearDown();
        }
    }

    private AiToolDetectionService service() {
        return getProject().getService(AiToolDetectionService.class);
    }

    private ToolInfo row(String id) {
        String name = AiToolRegistry.byId(id).name();
        return service().getAllToolsWithStatus().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    private void layOut(String version, FixtureLayout.LinkMode mode) throws Exception {
        FixtureLayout.materialize(JsonParser.parseString(Files.readString(
                        FixtureVersions.CLI.resolve(version).resolve("detect/scope-project/layout.json"))).getAsJsonObject(),
                Path.of(getProject().getBasePath()), home, mode);
    }

    public void testOnlyASkillsOnlyToolSaysItReadsTheSharedFolder() throws Exception {
        layOut("2.1.1", FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        service().setCliVersionForTest(() -> "2.1.1");
        service().detect();
        assertEquals(ToolStatus.CONFIGURED, row("cursor").status());
        assertTrue("Cursor has no folder of its own here", row("cursor").viaShared());
        assertEquals(ToolStatus.CONFIGURED, row("claude").status());
        assertFalse("Claude links into its own folder", row("claude").viaShared());
        assertFalse("Codex's own folder is the shared one", row("codex").viaShared());
    }

    /** A link Git checked out as a text file still counts as the tool's own. */
    public void testALinkAsTextIsStillTheToolsOwn() throws Exception {
        layOut("2.1.1", FixtureLayout.LinkMode.LINK_TEXT);
        service().setCliVersionForTest(() -> "2.1.1");
        service().detect();
        assertFalse(row("claude").viaShared());
        assertTrue(row("cursor").viaShared());
    }

    public void testNotOn1x() throws Exception {
        layOut("2.1.1", FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        service().setCliVersionForTest(() -> "1.17.0");
        service().detect();
        for (ToolInfo info : service().getAllToolsWithStatus()) assertFalse(info.name(), info.viaShared());
    }
}
