package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.util.SymlinkProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Which release line's detection rules apply: from the CLI version, or from the files when no CLI is detected. */
class DetectionRulesTest {

    @TempDir
    Path tmp;

    @Test
    void eachVersionGetsItsLine() {
        assertNull(DetectionRules.forVersion(null));
        assertNull(DetectionRules.forVersion(" "));
        assertEquals(DetectionRules.V1, DetectionRules.forVersion("1.3.0"));
        assertEquals(DetectionRules.V1, DetectionRules.forVersion("1.17.0"));
        assertEquals(DetectionRules.V2_0, DetectionRules.forVersion("2.0.0"));
        assertEquals(DetectionRules.V2_0, DetectionRules.forVersion("2.0.9"));
        assertEquals(DetectionRules.V2_1, DetectionRules.forVersion("2.1.0"));
        assertEquals(DetectionRules.V2_1_1, DetectionRules.forVersion("2.1.1"));
        assertEquals(DetectionRules.V2_1_1, DetectionRules.forVersion("2.2.0"));
    }

    @Test
    void onlyTheLineDifferencesUpstreamHas() {
        AiToolRegistry.AiTool minimax = AiToolRegistry.byId("minimax-code");
        assertEquals(".minimax", DetectionRules.V2_0.globalSkillsDir(minimax));
        assertNull(DetectionRules.V2_1.globalSkillsDir(minimax));
        assertEquals(".minimax", DetectionRules.V2_1_1.skillsDir(minimax));
        assertFalse(DetectionRules.V2_0.knows(AiToolRegistry.byId("amazon-q")));
        assertTrue(DetectionRules.V1.knows(AiToolRegistry.byId("amazon-q")));
        assertFalse(DetectionRules.V2_0.resolvesListAliases());
        assertTrue(DetectionRules.V2_1.resolvesListAliases());
        assertFalse(DetectionRules.V2_1.readsSkillsLock());
        assertTrue(DetectionRules.V2_1_1.readsSkillsLock());
    }

    @Test
    void anEmptyProjectOrA1xMarkerIs1x() throws IOException {
        assertEquals(DetectionRules.V1, DetectionRules.fromDisk(tmp));
        writeList("codex\n");
        assertEquals(DetectionRules.V1, DetectionRules.fromDisk(tmp));
    }

    @Test
    void severalListedToolsOrOwnFilesIs2x() throws IOException {
        writeList("antigravity\ncodex\n");
        assertEquals(DetectionRules.V2_1_1, DetectionRules.fromDisk(tmp));
        writeList("claude own-files\n");
        assertEquals(DetectionRules.V2_1_1, DetectionRules.fromDisk(tmp));
    }

    @Test
    void linkTextFromAToolFolderIs2x() throws IOException {
        Files.createDirectories(tmp.resolve(".agents/skills/openspec-explore"));
        Files.createDirectories(tmp.resolve(".claude/skills"));
        Files.writeString(tmp.resolve(".claude/skills/openspec-explore"), "../../.agents/skills/openspec-explore");
        assertEquals(DetectionRules.V2_1_1, DetectionRules.fromDisk(tmp));
    }

    @Test
    void aRealLinkFromAToolFolderIs2x() throws IOException {
        SymlinkProbe.requireLinks(tmp);
        Files.createDirectories(tmp.resolve(".agents/skills/openspec-explore"));
        Files.createDirectories(tmp.resolve(".claude/skills"));
        Files.createSymbolicLink(tmp.resolve(".claude/skills/openspec-explore"), Path.of("../../.agents/skills/openspec-explore"));
        assertEquals(DetectionRules.V2_1_1, DetectionRules.fromDisk(tmp));
    }

    @Test
    void a1xCopyInAToolFolderIs1x() throws IOException {
        Files.createDirectories(tmp.resolve(".claude/skills/openspec-explore"));
        Files.writeString(tmp.resolve(".claude/skills/openspec-explore/SKILL.md"), "copy\n");
        assertEquals(DetectionRules.V1, DetectionRules.fromDisk(tmp));
    }

    private void writeList(String content) throws IOException {
        Files.createDirectories(SkillToolList.path(tmp).getParent());
        Files.writeString(SkillToolList.path(tmp), content);
    }
}
