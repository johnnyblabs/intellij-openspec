package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.util.CliCommands;
import com.johnnyblabs.openspec.util.SymlinkProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Which runs may have written skills, which folders are refreshed after one, and the tool-folder event check. */
class SkillsWrittenTest {

    @TempDir
    Path tmp;

    @Test
    void initAndUpdateWriteSkillsADryRunAndOtherCommandsDont() {
        assertTrue(SkillsWritten.writesSkills(CliCommands.initInProject("claude")));
        assertTrue(SkillsWritten.writesSkills(CliCommands.initKeepingScope("claude")), "a scope-keeping add writes skills too");
        assertTrue(SkillsWritten.writesSkills(CliCommands.update()));
        assertTrue(SkillsWritten.writesSkills(CliCommands.updateWithoutPrompts()));
        assertFalse(SkillsWritten.writesSkills(CliCommands.updatePlan(false)));
        assertFalse(SkillsWritten.writesSkills(CliCommands.updatePlan(true)));
    }

    @Test
    void theRefreshedFoldersAreEachToolsSkillsFolderNeverASingleSpot() throws IOException {
        Files.createDirectories(tmp.resolve(".agents/skills/openspec-explore"));
        Files.createDirectories(tmp.resolve(".claude/skills/openspec-explore"));
        Files.createDirectories(tmp.resolve(".junie/skills"));
        Files.createDirectories(tmp.resolve(".minimax/skills"));
        List<Path> folders = SkillsWritten.folders(tmp);
        assertTrue(folders.contains(tmp.resolve(".agents/skills")));
        assertTrue(folders.contains(tmp.resolve(".claude/skills")));
        assertTrue(folders.contains(tmp.resolve(".junie/skills")), "a 2.1-only link folder");
        assertTrue(folders.contains(tmp.resolve(".minimax/skills")), "MiniMax's 2.1 project folder");
        assertTrue(folders.stream().allMatch(p -> p.getFileName().toString().equals("skills")));
        assertFalse(folders.contains(tmp.resolve(".cursor/skills")), "only folders that exist");
    }

    @Test
    void aToolFolderEventIsMatchedFromTheProject() {
        assertTrue(ToolFolderEvents.matches("/p", "/p/.agents/skills/x", null, null));
        assertTrue(ToolFolderEvents.matches("/p", "/p/.claude", null, null));
        assertFalse(ToolFolderEvents.matches("/p", "/p/src/Main.java", null, null));
        assertFalse(ToolFolderEvents.matches("/p", "/p/.agentsx", null, null));
    }

    @Test
    void anEventUnderTheRealAgentsFolderMatches() {
        assertTrue(ToolFolderEvents.matches("/p", "/shared/agents/skills/x", null, "/shared/agents"));
        assertTrue(ToolFolderEvents.matches("/p", "/p/elsewhere", "/shared/agents/skills/x", "/shared/agents"));
        assertFalse(ToolFolderEvents.matches("/p", "/shared/agentsx", null, "/shared/agents"));
    }

    @Test
    void realAgentsIsNullUnlessAgentsIsALink() throws IOException {
        assertNull(ToolFolderEvents.realAgents(tmp));
        Files.createDirectories(tmp.resolve("project/.agents"));
        assertNull(ToolFolderEvents.realAgents(tmp.resolve("project").toRealPath()));
    }

    @Test
    void realAgentsFollowsALinkedAgentsFolder() throws IOException {
        SymlinkProbe.requireLinks(tmp);
        Path elsewhere = Files.createDirectories(tmp.resolve("elsewhere/agents"));
        Path project = Files.createDirectories(tmp.resolve("project"));
        Files.createSymbolicLink(project.resolve(".agents"), elsewhere);
        assertEquals(elsewhere.toRealPath().toString().replace('\\', '/'), ToolFolderEvents.realAgents(project));
    }

    /** .agents stays part of the project: the plugin registers nothing that would exclude it from indexing or VFS. */
    @Test
    void theAgentsFolderIsNeverExcluded() throws IOException {
        String pluginXml = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"));
        assertFalse(pluginXml.contains("directoryIndexExcludePolicy"), "an exclude policy would hide .agents");
        assertFalse(pluginXml.contains("excludeFolder"));
    }
}
