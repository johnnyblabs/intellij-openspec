package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.CliFixtureSuite;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MigrationNotice#applies} over the captured projects before and after each real 2.x update
 * ({@code <version>/update-migration/<case>/{before,after}.json}).
 */
@CliFixtureSuite
class MigrationNoticeTest {

    /** The cases a 1.x release set up with tool-folder skill copies: a 2.x CLI should offer the migration. */
    static final Set<String> SET_UP_BY_1X = Set.of("unedited", "edited", "notes-beside-skill", "shared-folder",
            "nested-commands", "narrow-profile", "command-left-in-place", "retired-tool-edited", "shared-own-skill",
            "read-only-applied", "no-git", "no-network");

    /** The cases with nothing a 1.x release wrote in a tool folder to migrate. */
    static final Set<String> NOT_SET_UP_BY_1X = Set.of("after-2.0.0-kept", "eve-edited", "eve-from-2.1.0",
            "profile-shrink", "profile-empty",
            "router-from-2.1.1", "router-unedited", "router-reference-edited", "router-copy-edited");

    @TempDir
    Path tmp;

    private int n;

    boolean applies(Path caseDir, String layout, String version) throws IOException {
        Path root = tmp.resolve("p" + n++);
        JsonObject texts = JsonParser.parseString(Files.readString(caseDir.getParent().resolve("texts.json")))
                .getAsJsonObject();
        FixtureLayout.materialize(JsonParser.parseString(Files.readString(caseDir.resolve(layout))).getAsJsonObject(),
                root.resolve("project"), root.resolve("home"), FixtureLayout.LinkMode.SYMLINK_OR_COPY, texts);
        return MigrationNotice.applies(root.resolve("project"), version);
    }

    @Test
    void aProjectSetUpBy1xIsToldAndAMigratedOneIsNot() throws IOException {
        List<String> wrong = new ArrayList<>();
        int told = 0;
        for (String v : UpdateMigrationTest.RELEASES_2X) {
            try (var cases = Files.list(FixtureVersions.CLI.resolve(v).resolve("update-migration"))) {
                for (Path c : cases.filter(Files::isDirectory).sorted().toList()) {
                    String name = c.getFileName().toString();
                    assertTrue(SET_UP_BY_1X.contains(name) || NOT_SET_UP_BY_1X.contains(name), "unclassified case " + name);
                    boolean before = applies(c, "before.json", v);
                    if (before != SET_UP_BY_1X.contains(name)) wrong.add(v + "/" + name + " before: " + before);
                    if (before) told++;
                    // A failed update changed nothing, so the project is still 1.x; a finished one never is.
                    boolean failed = !Files.readString(c.resolve("applied.exit")).strip().equals("0");
                    if (applies(c, "after.json", v) != (failed && before)) {
                        wrong.add(v + "/" + name + " after the update" + (failed ? " (failed)" : ""));
                    }
                }
            }
        }
        assertTrue(told >= 20, "sanity: projects told " + told);
        assertEquals(List.of(), wrong, String.join("\n", wrong));
    }

    /** 2.0.0 keeps edited copies, which are not released files: the migrated project isn't told again. */
    @Test
    void theEditedCopies200KeptDoNotBringTheNoticeBack() throws IOException {
        Path edited = FixtureVersions.CLI.resolve("2.0.0/update-migration/edited");
        JsonObject after = JsonParser.parseString(Files.readString(edited.resolve("after.json"))).getAsJsonObject()
                .getAsJsonObject("project");
        assertTrue(after.has(".cursor/skills/openspec-explore/SKILL.md"), "sanity: 2.0.0 kept the copy");
        assertFalse(applies(edited, "after.json", "2.1.1"));
    }

    @Test
    void aOneXCliOrNoCliIsNeverTold() throws IOException {
        Path unedited = FixtureVersions.CLI.resolve("2.1.1/update-migration/unedited");
        assertTrue(applies(unedited, "before.json", "2.1.1"), "sanity");
        assertFalse(applies(unedited, "before.json", "1.17.0"));
        assertFalse(applies(unedited, "before.json", null));
    }

    /** A machine-scoped 2.1 project holds no project skills, so there is nothing to migrate. */
    @Test
    void aMachineScopedProjectIsNotTold() throws IOException {
        Path machineOnly = FixtureVersions.CLI.resolve("2.1.1/skills-scope/machine-only");
        Path root = tmp.resolve("machine");
        FixtureLayout.materialize(JsonParser.parseString(Files.readString(machineOnly.resolve("layout.json")))
                .getAsJsonObject(), root.resolve("project"), root.resolve("home"), FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        assertFalse(MigrationNotice.applies(root.resolve("project"), "2.1.1"));
    }

    @Test
    void theSharedFolderIsNeverAToolFolder() {
        assertFalse(MigrationNotice.toolFolders().contains(".agents"));
        assertTrue(MigrationNotice.toolFolders().containsAll(List.of(".claude", ".cursor", ".windsurf", ".agent")));
    }
}
