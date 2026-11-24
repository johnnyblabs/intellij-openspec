package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.services.GlobalSkillsCache;
import com.johnnyblabs.openspec.services.GlobalSkillsCacheTestSupport;
import com.johnnyblabs.openspec.services.SkillsWritten;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliCommands;
import com.johnnyblabs.openspec.util.SymlinkProbe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;

/**
 * Lookup and refresh on a real project on disk (a heavy project: a light one lives in an in-memory file system with no
 * links and nothing for a refresh to see). The 2.x layouts resolve to the right tool through the platform services,
 * and after the plugin's own init or update the IDE's file system sees what the CLI wrote, links included, and a spot
 * whose type changed.
 */
public class SkillPathResolutionContractTest extends HeavyPlatformTestCase {

    private static final String SKILL = WorkflowInvocation.EXPLORE_SKILL;
    private Path base;
    private Path home;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Paths.get(getProject().getBasePath());
        home = Files.createTempDirectory("skills-home");
        GlobalSkillsCacheTestSupport.setHome(GlobalSkillsCache.getInstance(getProject()), () -> home);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            GlobalSkillsCacheTestSupport.setHome(GlobalSkillsCache.getInstance(getProject()), null);
            OpenSpecSettings.getInstance(getProject()).setPreferredTool("");
            com.intellij.openapi.util.io.FileUtil.delete(home.toFile());
        } finally {
            super.tearDown();
        }
    }

    private static Path shared(Path root) throws Exception {
        Path file = root.resolve(".agents/skills").resolve(SKILL).resolve("SKILL.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "---\nname: openspec-explore\n---\nRun /openspec-explore.\n");
        return file;
    }

    private void list(String content) throws Exception {
        Files.writeString(base.resolve(".agents/skills/.openspec-target"), content);
    }

    private String toolFound() {
        WorkflowInvocation.Found found = WorkflowInvocation.find(getProject(), WorkflowInvocation.EXPLORE, SKILL);
        return found == null ? null : found.tool().id() + " " + found.invocation();
    }

    /**
     * False on a developer's Windows machine that can't make links (the case returns early, with the reason printed;
     * JUnit 3 has no skip). On CI and every other host a failed probe fails the test, so no link case passes unrun there.
     */
    private boolean canLink() throws Exception {
        Path dir = Files.createTempDirectory("probe");
        String why = SymlinkProbe.probe(dir);
        com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        if (why == null) return true;
        if (SymlinkProbe.maySkip()) {
            System.out.println("SKIPPED " + getName() + ": this Windows host can't create symbolic links: " + why);
            return false;
        }
        fail("this host should create symbolic links but can't: " + why);
        return false;
    }

    public void testASkillsOnlyCursorProjectResolvesToCursor() throws Exception {
        shared(base);
        list("cursor\n");
        assertEquals("cursor /openspec-explore", toolFound());
    }

    public void testACopilotProjectResolvesToCopilot() throws Exception {
        shared(base);
        list("github-copilot\n");
        assertEquals("github-copilot /openspec-explore", toolFound());
    }

    public void testClaudeThroughItsLink() throws Exception {
        if (!canLink()) return;
        shared(base);
        Files.createDirectories(base.resolve(".claude/skills"));
        Files.createSymbolicLink(base.resolve(".claude/skills").resolve(SKILL), Path.of("../../.agents/skills/" + SKILL));
        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Claude Code");
        assertEquals("claude /openspec-explore", toolFound());
    }

    public void testA1xCopyStillResolves() throws Exception {
        Path copy = base.resolve(".cursor/skills").resolve(SKILL).resolve("SKILL.md");
        Files.createDirectories(copy.getParent());
        Files.writeString(copy, "Run /openspec-explore.\n");
        assertEquals("cursor /openspec-explore", toolFound());
    }

    public void testAMachineWideInstallResolvesOffTheEdt() throws Exception {
        Path link = home.resolve(".claude/skills").resolve(SKILL).resolve("SKILL.md");
        Files.createDirectories(link.getParent());
        Files.writeString(link, "Run /openspec-explore.\n");
        String found = GlobalSkillsCacheTestSupport.offEdt(() -> toolFound());
        assertEquals("claude /openspec-explore", found);
    }

    public void testALinkTheCliWroteIsVisibleThroughTheVfsAfterTheRun() throws Exception {
        if (!canLink()) return;
        shared(base);
        // The VFS already knows .claude/skills and its (empty) children, so only a refresh can show what's added.
        Files.createDirectories(base.resolve(".claude/skills"));
        VirtualFile known = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base.resolve(".claude/skills"));
        assertNotNull(known);
        assertEquals(0, known.getChildren().length);
        // What init does behind the IDE's back: the tool's link into the shared copy.
        Files.createSymbolicLink(base.resolve(".claude/skills").resolve(SKILL), Path.of("../../.agents/skills/" + SKILL));

        SkillsWritten.afterRun(getProject(), CliCommands.initInProject("claude"));
        Path skill = base.resolve(".claude/skills").resolve(SKILL).resolve("SKILL.md");
        waitFor(() -> LocalFileSystem.getInstance().findFileByNioFile(skill) != null);
        assertNotNull("the linked skill is visible through the VFS", LocalFileSystem.getInstance().findFileByNioFile(skill));
    }

    /** What update does on Windows: the text file git left for a link becomes a copy folder. */
    public void testASpotThatChangedTypeIsSeenAsAFolder() throws Exception {
        shared(base);
        Path spot = base.resolve(".claude/skills").resolve(SKILL);
        Files.createDirectories(spot.getParent());
        Files.writeString(spot, "../../.agents/skills/" + SKILL);
        VirtualFile asFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(spot);
        assertNotNull(asFile);
        assertFalse(asFile.isDirectory());

        Files.delete(spot);
        Files.createDirectories(spot);
        Files.writeString(spot.resolve("SKILL.md"), "copy\n");
        SkillsWritten.afterRun(getProject(), CliCommands.update());

        waitFor(() -> {
            VirtualFile now = LocalFileSystem.getInstance().findFileByNioFile(spot);
            return now != null && now.isDirectory() && now.findChild("SKILL.md") != null;
        });
        VirtualFile now = LocalFileSystem.getInstance().findFileByNioFile(spot);
        assertTrue("the spot is a folder now", now != null && now.isDirectory());
        assertNotNull(now.findChild("SKILL.md"));
    }

    public void testADryRunRefreshesNothingAndKeepsTheCache() throws Exception {
        GlobalSkillsCache cache = GlobalSkillsCache.getInstance(getProject());
        GlobalSkillsCacheTestSupport.offEdt(() -> cache.snapshot());
        assertTrue(GlobalSkillsCacheTestSupport.isCached(cache));
        SkillsWritten.afterRun(getProject(), CliCommands.updatePlan(false));
        assertTrue("a dry run writes nothing", GlobalSkillsCacheTestSupport.isCached(cache));
        SkillsWritten.afterRun(getProject(), CliCommands.update());
        assertFalse("a real update may have written skills", GlobalSkillsCacheTestSupport.isCached(cache));
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end && !GlobalSkillsCacheTestSupport.isCached(cache)) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        assertTrue("and it is read again in the background", GlobalSkillsCacheTestSupport.isCached(cache));
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end && !condition.getAsBoolean()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
    }
}
