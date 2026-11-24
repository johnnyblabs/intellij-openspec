package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.util.SymlinkProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SkillLocator} finds a tool's skill in each place a release leaves it, in the project and in the home
 * directory, and never hands a tool a shared skill that isn't its. The shapes Windows hosts have (copies, link text)
 * run everywhere; real links run where the host can make them.
 */
class SkillLocatorTest {

    static final String SKILL = WorkflowInvocation.EXPLORE_SKILL;

    @TempDir
    Path tmp;

    private Path project() throws IOException {
        return Files.createDirectories(tmp.resolve("project"));
    }

    private Path home() throws IOException {
        return Files.createDirectories(tmp.resolve("home"));
    }

    private static AiTool tool(String id) {
        return AiToolRegistry.byId(id);
    }

    private static Path shared(Path root) throws IOException {
        Path file = root.resolve(".agents/skills").resolve(SKILL).resolve("SKILL.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "shared\n");
        return file;
    }

    private static void list(Path project, String content) throws IOException {
        Files.createDirectories(SkillToolList.path(project).getParent());
        Files.writeString(SkillToolList.path(project), content);
    }

    private static Path find(Path project, HomeSkills home, String tool, boolean preferred) {
        return SkillLocator.find(project, home, tool(tool), SKILL, preferred);
    }

    @Test
    void aListedSkillsOnlyToolReadsTheSharedCopy() throws IOException {
        Path p = project();
        Path file = shared(p);
        list(p, "cursor\ngithub-copilot\n");
        assertEquals(file, find(p, HomeSkills.NONE, "cursor", false));
        assertEquals(file, find(p, HomeSkills.NONE, "github-copilot", false));
    }

    /** Every 2.x tool's skills sit in .agents/skills, so the folder alone names no tool. */
    @Test
    void anUnlistedToolWithNoFilesOfItsOwnGetsNothing() throws IOException {
        Path p = project();
        shared(p);
        list(p, "cursor\n");
        assertNull(find(p, HomeSkills.NONE, "github-copilot", false));
        assertNull(find(p, HomeSkills.NONE, "claude", false));
    }

    /** Amp's, Codex's and Zed's own folder is .agents: a list naming another tool makes the shared copy not theirs. */
    @Test
    void aSharedFolderToolTheListLeavesOutGetsNothing() throws IOException {
        Path p = project();
        shared(p);
        list(p, "cursor\n");
        assertNull(find(p, HomeSkills.NONE, "amp", false));
        assertNull(find(p, HomeSkills.NONE, "codex", false));
        list(p, "codex\n");
        assertNotNull(find(p, HomeSkills.NONE, "codex", false), "a 1.x marker names its owner");
    }

    @Test
    void aCopyInTheLinkFolderIsFoundWithoutAList() throws IOException {
        Path p = project();
        shared(p);
        Path copy = p.resolve(".claude/skills").resolve(SKILL).resolve("SKILL.md");
        Files.createDirectories(copy.getParent());
        Files.writeString(copy, "copy\n");
        assertEquals(copy, find(p, HomeSkills.NONE, "claude", false));
    }

    @Test
    void linkTextLeadsToTheSharedCopy() throws IOException {
        Path p = project();
        Path file = shared(p);
        Files.createDirectories(p.resolve(".claude/skills"));
        Files.writeString(p.resolve(".claude/skills").resolve(SKILL), "../../.agents/skills/" + SKILL);
        assertEquals(file, find(p, HomeSkills.NONE, "claude", false));
    }

    @Test
    void linkTextWithNoSharedCopyFindsNothing() throws IOException {
        Path p = project();
        Files.createDirectories(p.resolve(".claude/skills"));
        Files.writeString(p.resolve(".claude/skills").resolve(SKILL), "../../.agents/skills/" + SKILL);
        assertNull(find(p, HomeSkills.NONE, "claude", false));
    }

    /** A project set up by 2.0 keeps EasyCode's link folder, which 2.1 no longer uses. */
    @Test
    void a20LinkFolderIsStillRead() throws IOException {
        Path p = project();
        Path copy = p.resolve(".easycode/skills").resolve(SKILL).resolve("SKILL.md");
        Files.createDirectories(copy.getParent());
        Files.writeString(copy, "copy\n");
        assertEquals(copy, find(p, HomeSkills.NONE, "easycode", false));
    }

    @Test
    void a1xCopyInTheToolsOwnFolderIsFound() throws IOException {
        Path p = project();
        Path copy = p.resolve(".cursor/skills").resolve(SKILL).resolve("SKILL.md");
        Files.createDirectories(copy.getParent());
        Files.writeString(copy, "1.x copy\n");
        assertEquals(copy, find(p, HomeSkills.NONE, "cursor", false));
    }

    @Test
    void aSharedFolderToolReadsItsOwnFolderAsBefore() throws IOException {
        Path p = project();
        Path file = shared(p);
        assertEquals(file, find(p, HomeSkills.NONE, "codex", false));
    }

    @Test
    void aLegacyFolderIsFound() throws IOException {
        Path p = project();
        Path legacy = p.resolve(".codex/skills").resolve(SKILL).resolve("SKILL.md");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "legacy\n");
        assertEquals(legacy, find(p, HomeSkills.NONE, "codex", false));
    }

    @Nested
    class HomeDirectory {
        @Test
        void aHomeLinkFolderCopyIsFound() throws IOException {
            Path h = home();
            Path copy = h.resolve(".claude/skills").resolve(SKILL).resolve("SKILL.md");
            Files.createDirectories(copy.getParent());
            Files.writeString(copy, "home copy\n");
            assertEquals(copy, find(project(), HomeSkills.scan(h), "claude", false));
        }

        @Test
        void theHomeSharedFolderIsForSharedFolderToolsAndThePreferredTool() throws IOException {
            Path h = home();
            Path file = shared(h);
            HomeSkills home = HomeSkills.scan(h);
            Path p = project();
            assertEquals(file, find(p, home, "codex", false));
            assertNull(find(p, home, "cursor", false), "an unpreferred skills-only tool doesn't claim a machine-wide skill");
            assertEquals(file, find(p, home, "cursor", true));
        }

        @Test
        void a1xHomeToolFolderIsFound() throws IOException {
            Path h = home();
            Path file = h.resolve(".minimax/skills").resolve(SKILL).resolve("SKILL.md");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "minimax\n");
            assertEquals(file, find(project(), HomeSkills.scan(h), "minimax-code", false));
        }

        @Test
        void theProjectWinsOverHome() throws IOException {
            Path p = project();
            Path projectFile = shared(p);
            list(p, "cursor\n");
            shared(home());
            assertEquals(projectFile, find(p, HomeSkills.scan(tmp.resolve("home")), "cursor", true));
        }

        @Test
        void theSnapshotIsReadOnce() throws IOException {
            Path h = home();
            HomeSkills home = HomeSkills.scan(h);
            shared(h); // written after the scan
            assertNull(find(project(), home, "codex", false), "a snapshot never reads the disk again");
            assertTrue(home.isEmpty());
        }
    }

    @Nested
    class Links {
        @Test
        void aLinkIsFollowed() throws IOException {
            SymlinkProbe.requireLinks(tmp);
            Path p = project();
            shared(p);
            Files.createDirectories(p.resolve(".claude/skills"));
            Files.createSymbolicLink(p.resolve(".claude/skills").resolve(SKILL), Path.of("../../.agents/skills/" + SKILL));
            Path found = find(p, HomeSkills.NONE, "claude", false);
            assertNotNull(found);
            assertEquals("shared\n", Files.readString(found));
        }

        @Test
        void aBrokenLinkFindsNothing() throws IOException {
            SymlinkProbe.requireLinks(tmp);
            Path p = project();
            Files.createDirectories(p.resolve(".claude/skills"));
            Files.createSymbolicLink(p.resolve(".claude/skills").resolve(SKILL), Path.of("../../.agents/skills/" + SKILL));
            assertNull(find(p, HomeSkills.NONE, "claude", false));
        }

        @Test
        void aMachineWideLinkIsFollowed() throws IOException {
            SymlinkProbe.requireLinks(tmp);
            Path h = home();
            shared(h);
            Files.createDirectories(h.resolve(".claude/skills"));
            Files.createSymbolicLink(h.resolve(".claude/skills").resolve(SKILL), Path.of("../../.agents/skills/" + SKILL));
            assertNotNull(find(project(), HomeSkills.scan(h), "claude", false));
        }
    }
}
