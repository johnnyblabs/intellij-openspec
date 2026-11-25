package com.johnnyblabs.openspec.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureLayout.LinkMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FixtureLayout} writes a recorded link in each of the forms a checkout can hold. The layout mirrors a real 2.x
 * capture: one project link into {@code .agents/skills/} relative to the link's folder, and one home link recorded with
 * an absolute {@code /fixture/home} target, as a machine-wide install leaves it.
 */
class FixtureLayoutTest {

    static final String LAYOUT = """
            {
              "project": {".agents/skills/openspec-explore/SKILL.md": "/openspec-explore\\n"},
              "home": {".agents/skills/openspec-propose/SKILL.md": "/openspec-propose\\n"},
              "links": {
                "project": {".claude/skills/openspec-explore": "../../.agents/skills/openspec-explore"},
                "home": {".claude/skills/openspec-propose": "/fixture/home/.agents/skills/openspec-propose"}
              }
            }""";

    @TempDir
    Path tmp;

    Path project() {
        return tmp.resolve("project");
    }

    Path home() {
        return tmp.resolve("home");
    }

    void materialize(LinkMode mode) throws IOException {
        FixtureLayout.materialize(JsonParser.parseString(LAYOUT).getAsJsonObject(), project(), home(), mode);
    }

    @Test
    void symlinkModeWritesRealLinksThatReachTheirTargets() throws IOException {
        materialize(LinkMode.SYMLINK);
        Path projectLink = project().resolve(".claude/skills/openspec-explore");
        Path homeLink = home().resolve(".claude/skills/openspec-propose");
        assertTrue(Files.isSymbolicLink(projectLink));
        assertTrue(Files.isSymbolicLink(homeLink));
        assertEquals(Paths.get("../../.agents/skills/openspec-explore"), Files.readSymbolicLink(projectLink));
        assertFalse(Files.readSymbolicLink(homeLink).isAbsolute(), "an absolute fixture target becomes relative");
        assertEquals("/openspec-explore\n", Files.readString(projectLink.resolve("SKILL.md")));
        assertEquals("/openspec-propose\n", Files.readString(homeLink.resolve("SKILL.md")));
    }

    @Test
    void copyModeWritesARealFolderHoldingTheTargetsFiles() throws IOException {
        materialize(LinkMode.COPY);
        Path projectLink = project().resolve(".claude/skills/openspec-explore");
        assertTrue(Files.isDirectory(projectLink, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(projectLink));
        assertEquals("/openspec-explore\n", Files.readString(projectLink.resolve("SKILL.md")));
        assertEquals("/openspec-propose\n",
                Files.readString(home().resolve(".claude/skills/openspec-propose/SKILL.md")));
    }

    @Test
    void linkTextModeWritesAFileHoldingExactlyTheRecordedTarget() throws IOException {
        materialize(LinkMode.LINK_TEXT);
        Path projectLink = project().resolve(".claude/skills/openspec-explore");
        assertTrue(Files.isRegularFile(projectLink, LinkOption.NOFOLLOW_LINKS));
        // Byte-exact, with no trailing newline: what git writes for a link when core.symlinks is off.
        assertEquals("../../.agents/skills/openspec-explore", Files.readString(projectLink));
        // The file's text, not a path: the recorded absolute target, byte for byte.
        String homeLinkText = Files.readString(home().resolve(".claude/skills/openspec-propose"));
        assertEquals("/fixture/home/.agents/skills/openspec-propose", homeLinkText);
    }

    @Test
    void symlinkOrCopyModeAlwaysLeavesTheSkillReadable() throws IOException {
        materialize(LinkMode.SYMLINK_OR_COPY);
        assertEquals("/openspec-explore\n",
                Files.readString(project().resolve(".claude/skills/openspec-explore/SKILL.md")));
    }

    @Test
    void aLayoutWithoutLinksWritesOnlyItsFiles() throws IOException {
        JsonObject layout = JsonParser.parseString(LAYOUT).getAsJsonObject();
        layout.remove("links");
        FixtureLayout.materialize(layout, project(), home(), LinkMode.SYMLINK);
        assertTrue(Files.isRegularFile(project().resolve(".agents/skills/openspec-explore/SKILL.md")));
        assertFalse(Files.exists(project().resolve(".claude"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void aTargetThatLeavesTheLayoutIsRefused() {
        JsonObject layout = JsonParser.parseString("""
                {"project": {}, "home": {}, "links": {"project": {"a/b": "../../../outside"}}}""").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,
                () -> FixtureLayout.materialize(layout, project(), home(), LinkMode.COPY));
    }

    /** The update-migration captures store each text once: a {@code "@<key>"} value is resolved from that map. */
    @Test
    void aStoredTextReferenceIsWrittenAsTheText() throws IOException {
        JsonObject layout = JsonParser.parseString("""
                {"project": {"a/SKILL.md": "@k1", "b.txt": "@plain"}, "home": {"c.md": "@k1"}}""").getAsJsonObject();
        JsonObject texts = JsonParser.parseString("""
                {"k1": "stored text\\n", "plain": "second\\n"}""").getAsJsonObject();
        FixtureLayout.materialize(layout, project(), home(), LinkMode.SYMLINK_OR_COPY, texts);
        assertEquals("stored text\n", Files.readString(project().resolve("a/SKILL.md")));
        assertEquals("second\n", Files.readString(project().resolve("b.txt")));
        assertEquals("stored text\n", Files.readString(home().resolve("c.md")));
    }

    @Test
    void anUnknownTextReferenceIsRefused() {
        JsonObject layout = JsonParser.parseString("""
                {"project": {"a.md": "@missing"}, "home": {}}""").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () ->
                FixtureLayout.materialize(layout, project(), home(), LinkMode.SYMLINK_OR_COPY, new JsonObject()));
    }

    /** Without a text map, a value is written as it is, even one starting with {@code @}. */
    @Test
    void withoutATextMapValuesAreWrittenAsRecorded() throws IOException {
        JsonObject layout = JsonParser.parseString("""
                {"project": {"a.md": "@not-a-key"}, "home": {}}""").getAsJsonObject();
        FixtureLayout.materialize(layout, project(), home(), LinkMode.SYMLINK_OR_COPY);
        assertEquals("@not-a-key", Files.readString(project().resolve("a.md")));
    }
}
