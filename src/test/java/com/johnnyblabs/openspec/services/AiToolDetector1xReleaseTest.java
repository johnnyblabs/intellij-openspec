package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each 1.x shape change at its release, and the no-release path keeping 1.17's shapes and rules with no release filter
 * (callers with no CLI version, such as the install dialog's ticks, which also run with a 2.x CLI).
 */
class AiToolDetector1xReleaseTest {

    @TempDir
    Path project;

    private static AiTool tool(String id) {
        return AiToolRegistry.byId(id);
    }

    @Test
    void eachShapeChangesAtItsRelease() {
        assertEquals(".kimi", AiToolRegistry.asOf1x(tool("kimi"), "1.6.9").skillsDir());
        assertEquals(".kimi-code", AiToolRegistry.asOf1x(tool("kimi"), "1.7.0").skillsDir());
        assertEquals(".windsurf", AiToolRegistry.asOf1x(tool("devin"), "1.6.9").skillsDir());
        assertEquals(".devin", AiToolRegistry.asOf1x(tool("devin"), "1.7.0").skillsDir());
        assertEquals(".codex", AiToolRegistry.asOf1x(tool("codex"), "1.7.5").skillsDir());
        assertEquals(".agents", AiToolRegistry.asOf1x(tool("codex"), "1.8.0").skillsDir());
        assertNull(AiToolRegistry.asOf1x(tool("agents"), "1.7.5").skillsDir());
        assertEquals(".agents", AiToolRegistry.asOf1x(tool("agents"), "1.8.0").skillsDir());
        assertEquals(".agent", AiToolRegistry.asOf1x(tool("antigravity"), "1.10.9").skillsDir());
        assertEquals(".agents", AiToolRegistry.asOf1x(tool("antigravity"), "1.11.0").skillsDir());
        assertSame(tool("codex"), AiToolRegistry.asOf1x(tool("codex"), null), "no release: the record itself");
        assertSame(tool("claude"), AiToolRegistry.asOf1x(tool("claude"), "1.3.0"), "an unchanged tool: the record itself");
    }

    private List<String> detect(String release) {
        return AiToolDetector.detect(project, null, DetectionRules.V1, release).stream().map(AiTool::id).sorted().toList();
    }

    @Test
    void withNoReleaseA21ToolIsStillDetected() throws IOException {
        Files.createDirectories(project.resolve(".aider-desk/skills"));
        assertEquals(List.of("aider-desk"), detect(null), "today's behavior: no release filter");
        assertEquals(List.of(), detect("1.17.0"), "1.17.0 has no AiderDesk");
    }

    @Test
    void withNoReleaseABareCodexFolderIsNothing() throws IOException {
        Files.createDirectories(project.resolve(".codex"));
        Files.writeString(project.resolve(".codex/config.toml"), "");
        assertEquals(List.of(), detect(null), "1.17's shapes, not the oldest");
        assertEquals(List.of("codex"), detect("1.7.0"));
    }

    @Test
    void withNoReleaseAMarkerStillNamesItsOwner() throws IOException {
        Files.createDirectories(project.resolve(".agents/skills"));
        Files.writeString(project.resolve(".agents/skills/.openspec-target"), "codex\n");
        assertEquals(List.of("codex"), detect(null), "1.17's reconciliation applies");
        assertEquals(List.of(), detect("1.7.0"), "1.7.0 reads no marker");
    }

    @Test
    void releasesCompareAsVersionsNotText() throws IOException {
        Files.createDirectories(project.resolve(".zed"));
        Files.createDirectories(project.resolve(".agents/skills"));
        Files.writeString(project.resolve(".agents/skills/.openspec-target"), "codex\n");
        assertEquals(List.of("codex"), detect("1.10.0"), "1.10.0 is before 1.11.0, though it sorts after 1.1 as text");
        assertEquals(List.of("codex", "zed"), detect("1.11.0"));
    }

    @Test
    void theInstallTicksStillTickA21Tool() throws IOException {
        Files.createDirectories(project.resolve(".aider-desk/skills"));
        assertTrue(AiToolDetector.detect(project, null, DetectionRules.fromDisk(project)).stream()
                .anyMatch(t -> t.id().equals("aider-desk")), "the no-release entry point InstallToolTicks uses");
    }
}
