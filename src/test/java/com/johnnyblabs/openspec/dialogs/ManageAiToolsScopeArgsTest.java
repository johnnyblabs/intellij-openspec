package com.johnnyblabs.openspec.dialogs;

import com.google.gson.JsonParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.johnnyblabs.openspec.services.HomeSkills;
import com.johnnyblabs.openspec.util.CliArgs;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Manage AI Tools' Add and Configure on a real project directory recreated from what the CLI left: a project with its
 * own skills, or none yet, gets the project scope; a project using only the machine's skills gets no scope, so 2.1
 * keeps it on the machine. On 2.0.0 neither passes {@code --scope}.
 */
public class ManageAiToolsScopeArgsTest extends HeavyPlatformTestCase {

    private Path base;
    private Path home;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Path.of(getProject().getBasePath());
        home = Files.createTempDirectory("scope-home");
        Files.createDirectories(base);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            FileUtil.delete(home.toFile());
        } finally {
            super.tearDown();
        }
    }

    private CliArgs argsFor(String layout) throws Exception {
        FixtureLayout.materialize(JsonParser.parseString(Files.readString(
                        FixtureVersions.CLI.resolve(layout).resolve("layout.json"))).getAsJsonObject(),
                base, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        return ManageAiToolsDialog.addArgs(base, HomeSkills.scan(home), "claude");
    }

    public void testAProjectWithItsOwnSkillsGetsTheProjectScope() throws Exception {
        CliArgs args = argsFor("2.1.1/skills-scope/project-add-keep");
        assertEquals(List.of("init", "--tools", "claude", "--scope", "project"), args.resolve("2.1.1").argv());
        assertEquals(Map.of("OPENSPEC_SKILLS_SCOPE", "project"), args.env());
    }

    public void testAMachineScopedProjectGetsNoScope() throws Exception {
        CliArgs args = argsFor("2.1.1/skills-scope/machine-only");
        assertEquals(List.of("init", "--tools", "claude"), args.resolve("2.1.1").argv());
        assertEquals("no backstop either, or 2.1 would move the project", Map.of(), args.env());
    }

    public void testAnEmptyProjectGetsTheProjectScope() throws Exception {
        CliArgs args = ManageAiToolsDialog.addArgs(base, HomeSkills.scan(home), "claude");
        assertEquals(List.of("init", "--tools", "claude", "--scope", "project"), args.resolve("2.1.1").argv());
    }

    public void testOn200NeitherPassesTheFlag() throws Exception {
        assertEquals(List.of("init", "--tools", "claude"),
                argsFor("2.1.1/skills-scope/machine-only").resolve("2.0.0").argv());
        assertEquals(List.of("init", "--tools", "claude"),
                ManageAiToolsDialog.addArgs(null, HomeSkills.NONE, "claude").resolve("2.0.0").argv());
    }

    /** Both buttons build their command through the decision, on the background thread. */
    public void testAddAndConfigureUseTheDecision() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/johnnyblabs/openspec/dialogs/ManageAiToolsDialog.java"));
        assertEquals(2, source.split("runCliAction\\(\\(\\) -> addArgs\\(project, tool\\.cliId\\(\\)\\),", -1).length - 1);
        assertEquals("init is built only by addArgs (two scopes) and installArgs", 3,
                source.split("CliCommands\\.init", -1).length - 1);
    }
}
