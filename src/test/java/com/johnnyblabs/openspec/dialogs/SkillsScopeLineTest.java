package com.johnnyblabs.openspec.dialogs;

import com.google.gson.JsonParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.HomeSkills;
import com.johnnyblabs.openspec.services.ProjectSkillsScope;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The scope line's text, when it shows, and when it offers "Install into this project", read from a real project
 * directory recreated from what the CLI left, with the CLI at each release that changes the answer.
 */
public class SkillsScopeLineTest extends HeavyPlatformTestCase {

    private Path base;
    private Path home;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Path.of(getProject().getBasePath());
        Files.createDirectories(base);
        home = Files.createTempDirectory("scope-line-home");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            FileUtil.delete(home.toFile());
        } finally {
            super.tearDown();
        }
    }

    private void cliAt(String version) {
        CliDetectionService cli = mock(CliDetectionService.class);
        when(cli.isAvailable()).thenReturn(version != null);
        when(cli.getDetectedVersion()).thenReturn(version);
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, cli, getTestRootDisposable());
    }

    private HomeSkills seed(String layout) throws Exception {
        FixtureLayout.materialize(JsonParser.parseString(Files.readString(
                        FixtureVersions.CLI.resolve(layout).resolve("layout.json"))).getAsJsonObject(),
                base, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        return HomeSkills.scan(home);
    }

    private SkillsScopeLine applied(SkillsScopeLine.State state, boolean withInstall) {
        SkillsScopeLine line = new SkillsScopeLine(getProject(), withInstall ? () -> { } : null);
        line.apply(state);
        return line;
    }

    public void testAMachineScopedProjectOn211OffersTheInstall() throws Exception {
        cliAt("2.1.1");
        SkillsScopeLine.State state = SkillsScopeLine.read(getProject(), seed("2.1.1/skills-scope/machine-only"));
        assertEquals(ProjectSkillsScope.ON_MACHINE, state.scope());
        SkillsScopeLine line = applied(state, true);
        assertTrue(line.shownForTest());
        assertEquals("Skills: on this machine (~/.agents/skills)", line.textForTest());
        assertTrue(line.installShownForTest());
        assertFalse("Settings' line has no action", applied(state, false).installShownForTest());
    }

    public void testTheSameProjectOn200OffersNoAction() throws Exception {
        cliAt("2.0.0");
        SkillsScopeLine line = applied(SkillsScopeLine.read(getProject(), seed("2.1.1/skills-scope/machine-only")), true);
        assertTrue("machine skills make scope a concept", line.shownForTest());
        assertEquals("Skills: on this machine (~/.agents/skills)", line.textForTest());
        assertFalse(line.installShownForTest());
    }

    public void testAProjectScopedProjectWithARecordedChoice() throws Exception {
        cliAt("2.1.1");
        HomeSkills skills = seed("2.1.1/skills-scope/project-add-keep");
        Files.createDirectories(base.resolve("openspec"));
        Files.copy(FixtureVersions.CLI.resolve("2.1.1/skills-scope/remembered-config.yaml"), base.resolve("openspec/config.yaml"));
        SkillsScopeLine line = applied(SkillsScopeLine.read(getProject(), skills), true);
        assertEquals("Skills: in this project (.agents/skills) · recorded in openspec/config.yaml", line.textForTest());
        assertFalse(line.installShownForTest());
    }

    public void testAnInvalidRecordedValueIsNotMentioned() throws Exception {
        cliAt("2.1.1");
        HomeSkills skills = seed("2.1.1/skills-scope/project-add-keep");
        Files.createDirectories(base.resolve("openspec"));
        Files.copy(FixtureVersions.CLI.resolve("2.1.1/skills-scope/config-team.yaml"), base.resolve("openspec/config.yaml"));
        assertEquals("Skills: in this project (.agents/skills)",
                applied(SkillsScopeLine.read(getProject(), skills), true).textForTest());
    }

    public void testAPure1xProjectHidesTheLine() throws Exception {
        cliAt("1.17.0");
        Files.createDirectories(base.resolve(".claude/skills/openspec-explore"));
        Files.writeString(base.resolve(".claude/skills/openspec-explore/SKILL.md"), "x\n");
        SkillsScopeLine.State state = SkillsScopeLine.read(getProject(), HomeSkills.scan(home));
        assertFalse(applied(state, true).shownForTest());
    }

    public void testNothingInstalledOn211() throws Exception {
        cliAt("2.1.1");
        SkillsScopeLine line = applied(SkillsScopeLine.read(getProject(), HomeSkills.scan(home)), true);
        assertTrue("CLI 2.1 makes scope a concept", line.shownForTest());
        assertEquals("Skills: not installed", line.textForTest());
        assertFalse(line.installShownForTest());
    }

    public void testTheRules() {
        assertTrue(SkillsScopeLine.canInstall(ProjectSkillsScope.ON_MACHINE, "2.1.0"));
        assertFalse(SkillsScopeLine.canInstall(ProjectSkillsScope.ON_MACHINE, "2.0.0"));
        assertFalse(SkillsScopeLine.canInstall(ProjectSkillsScope.ON_MACHINE, null));
        assertFalse(SkillsScopeLine.canInstall(ProjectSkillsScope.IN_PROJECT, "2.1.1"));
        assertFalse(SkillsScopeLine.canInstall(ProjectSkillsScope.NONE, "2.1.1"));
        assertTrue(SkillsScopeLine.visible("2.1.0", false, false));
        assertTrue(SkillsScopeLine.visible(null, true, false));
        assertTrue(SkillsScopeLine.visible("1.17.0", false, true));
        assertFalse(SkillsScopeLine.visible("2.0.0", false, false));
        assertFalse(SkillsScopeLine.visible(null, false, false));
    }
}
