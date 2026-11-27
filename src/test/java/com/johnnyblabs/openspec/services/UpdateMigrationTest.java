package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.UpdateMigration.Kind;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.FixtureVersions;
import com.johnnyblabs.openspec.util.UpdatePlanParser;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Change;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Plan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link UpdateMigration}'s reading of real plans: what 2.0.0, 2.1.0 and 2.1.1 planned for projects earlier releases
 * set up ({@code update-migration/}), and the dry-run captures that share part of the shape.
 */
class UpdateMigrationTest {

    static final List<String> RELEASES_2X = List.of("2.0.0", "2.1.0", "2.1.1", "2.2.0");

    /** The captured cases that move a 1.x layout to 2.x, and so show the banner on a 2.x CLI. */
    static final Set<String> MIGRATIONS = Set.of("unedited", "edited", "notes-beside-skill", "shared-folder",
            "nested-commands", "narrow-profile", "command-left-in-place", "shared-own-skill", "read-only-applied",
            "no-git", "no-network");
    /**
     * The captured cases that don't: a 2.x project changing its profile, Eve set up by 2.1, a project 2.0.0 already
     * migrated (no marker change), and Amazon Q alone (retired, so no shared skills are written).
     */
    static final Set<String> NOT_MIGRATIONS = Set.of("profile-shrink", "profile-empty", "eve-edited", "eve-from-2.1.0",
            "after-2.0.0-kept", "retired-tool-edited",
            "router-from-2.1.1", "router-unedited", "router-reference-edited", "router-copy-edited");

    static Plan plan(Path json, Path exit) throws IOException {
        var run = new CliRunner.CliResult(Integer.parseInt(Files.readString(exit).strip()), Files.readString(json), "");
        return assertInstanceOf(Plan.class, UpdatePlanParser.parse(run), json.toString());
    }

    /** Every captured migration plan: "<version>/<case>" to its plan. */
    static Map<String, Plan> migrationPlans() throws IOException {
        Map<String, Plan> plans = new TreeMap<>();
        for (String v : RELEASES_2X) {
            Path dir = FixtureVersions.CLI.resolve(v).resolve("update-migration");
            try (Stream<Path> cases = Files.list(dir)) {
                for (Path c : cases.filter(Files::isDirectory).toList()) {
                    plans.put(v + "/" + c.getFileName(), plan(c.resolve("plan.json"), c.resolve("plan.exit")));
                }
            }
        }
        assertTrue(plans.size() > 30, "sanity: captured cases " + plans.size());
        return plans;
    }

    static Plan dryRun(String version, String name) throws IOException {
        Path dir = FixtureVersions.CLI.resolve(version).resolve("update-dry-run");
        return plan(dir.resolve(name + ".json"), dir.resolve(name + ".exit"));
    }

    @Test
    void everyCapturedCaseIsReadAsTheMigrationItIsOrIsNot() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (var e : migrationPlans().entrySet()) {
            String version = e.getKey().substring(0, e.getKey().indexOf('/'));
            String name = e.getKey().substring(e.getKey().indexOf('/') + 1);
            assertTrue(MIGRATIONS.contains(name) || NOT_MIGRATIONS.contains(name), "unclassified case " + name);
            boolean expected = MIGRATIONS.contains(name);
            if (UpdateMigration.isMigration(e.getValue(), version) != expected) wrong.add(e.getKey());
            assertFalse(UpdateMigration.isMigration(e.getValue(), "1.17.0"), "a 1.x CLI never migrates: " + e.getKey());
            assertFalse(UpdateMigration.isMigration(e.getValue(), null), "an unknown CLI never migrates: " + e.getKey());
        }
        assertEquals(List.of(), wrong, "cases read the wrong way");
    }

    /** 1.6-era Antigravity moves to .agents on 2.x (a migration); on 1.17.0 the same shape is not the move to 2.x. */
    @Test
    void aFormerToolFolderMovesToTwoXOnlyOnATwoXCli() throws IOException {
        for (String v : RELEASES_2X) {
            assertTrue(UpdateMigration.isMigration(dryRun(v, "earlier-release"), v), v);
        }
        assertFalse(UpdateMigration.isMigration(dryRun("1.17.0", "earlier-release"), "1.17.0"));
    }

    /** A pre-skills project: the marker is added, but only 0.x `commands/openspec/*` files go, which are neither. */
    @Test
    void aPreSkillsProjectIsNotAMigration() throws IOException {
        for (String v : RELEASES_2X) {
            Plan p = dryRun(v, "legacy-unedited");
            assertTrue(p.changes().stream().anyMatch(c -> UpdateMigration.MARKER.equals(c.path())), "sanity " + v);
            assertFalse(UpdateMigration.isMigration(p, v), v);
        }
    }

    /** The replaced copies are every tool-folder skill delete, whatever tool value the release gave it. */
    @Test
    void cursorsCopiesAreReplacedCopiesUnderEitherToolTag() throws IOException {
        for (String v : RELEASES_2X) {
            Plan p = migrationPlans().get(v + "/unedited");
            List<Change> cursor = p.changes().stream()
                    .filter(c -> c.path().startsWith(".cursor/skills/") && c.action().equals("delete")).toList();
            assertEquals(6, cursor.size(), v);
            assertEquals(v.equals("2.0.0") ? Set.of("cursor") : Set.of("openspec"),
                    Set.copyOf(cursor.stream().map(Change::tool).toList()), "the capture's tags, " + v);
            cursor.forEach(c -> assertEquals(Kind.REPLACED_COPY, UpdateMigration.kind(c), v + " " + c.path()));
        }
    }

    @Test
    void nestedAndFlatCommandFoldersAreCommandFiles() throws IOException {
        for (String v : RELEASES_2X) {
            Plan p = migrationPlans().get(v + "/nested-commands");
            List<Change> commands = p.changes().stream().filter(c -> c.path().contains("/commands/")).toList();
            assertTrue(commands.stream().anyMatch(c -> c.path().equals(".gemini/commands/opsx/apply.toml")), v);
            assertTrue(commands.stream().anyMatch(c -> c.path().equals(".claude/commands/opsx/apply.md")), v);
            commands.forEach(c -> assertEquals(Kind.COMMAND_FILE, UpdateMigration.kind(c), v + " " + c.path()));
            Plan flat = migrationPlans().get(v + "/unedited");
            assertTrue(flat.changes().stream().filter(c -> c.path().startsWith(".cursor/commands/"))
                    .allMatch(c -> UpdateMigration.kind(c) == Kind.COMMAND_FILE), v);
        }
    }

    @Test
    void toolFolderLinksPointAtTheirSharedSkill() throws IOException {
        for (String v : RELEASES_2X) {
            List<Change> links = migrationPlans().get(v + "/unedited").changes().stream()
                    .filter(c -> UpdateMigration.kind(c) == Kind.LINK).toList();
            // 2.2 adds the router's link beside the six core workflow skills'.
            assertEquals(com.johnnyblabs.openspec.util.CliVersion.atLeast(v, "2.2.0") ? 7 : 6, links.size(), v);
            for (Change link : links) {
                assertTrue(link.path().startsWith(".claude/skills/openspec"), link.path());
                String skill = link.path().substring(".claude/skills/".length());
                assertEquals(".agents/skills/" + skill, UpdateMigration.linkTarget(link));
                assertEquals(link.path() + " → .agents/skills/" + skill, UpdatePreviewModel.linkRow(link));
            }
        }
    }

    /** CLI 2.2's router: its tool-folder link, added to a 2.1.1 project, is a link to the shared router. */
    @Test
    void theRouterLinkIsALinkToTheSharedRouter() throws IOException {
        List<Change> router = migrationPlans().get("2.2.0/router-from-2.1.1").changes().stream()
                .filter(c -> c.path().equals(".claude/skills/openspec")).toList();
        assertEquals(1, router.size(), "sanity: 2.2.0 adds the router's link");
        assertEquals(Kind.LINK, UpdateMigration.kind(router.get(0)));
        assertEquals(".agents/skills/openspec", UpdateMigration.linkTarget(router.get(0)));
        assertTrue(UpdateMigration.inSkillFolder(".agents/skills/openspec/references/answers.md"));
        assertFalse(UpdateMigration.inSkillFolder(".claude/skills/openspec.md"), "a file named like the router is not in it");
        assertFalse(UpdateMigration.inSkillFolder(".claude/skills/openspecx/SKILL.md"));
    }

    @Test
    void theSharedFolderAndTheHomeFolderAreNotToolChanges() throws IOException {
        for (Change c : migrationPlans().get("2.1.1/unedited").changes()) {
            if (c.outsideProject()) {
                assertEquals(Kind.OTHER, UpdateMigration.kind(c), c.path());
            } else if (c.path().startsWith(".agents/skills/")) {
                assertEquals(Kind.SHARED, UpdateMigration.kind(c), c.path());
            }
        }
    }

    /** Every 1.x command file in the captured 1.17.0 detection layouts is one of the derived command paths. */
    @Test
    void everyCapturedCommandFileIsADerivedCommandPath() throws IOException {
        Pattern opsx = Pattern.compile("(^|/)opsx[-/]");
        List<String> missing = new ArrayList<>();
        int seen = 0;
        try (Stream<Path> cases = Files.list(FixtureVersions.CLI.resolve("1.17.0").resolve("detect"))) {
            for (Path c : cases.filter(Files::isDirectory).toList()) {
                Path layout = c.resolve("layout.json");
                if (!Files.exists(layout)) continue;
                JsonObject project = JsonParser.parseString(Files.readString(layout)).getAsJsonObject()
                        .getAsJsonObject("project");
                for (String path : project.keySet()) {
                    if (!opsx.matcher(path).find()) continue;
                    seen++;
                    if (!UpdateMigration.isCommandPath(path)) missing.add(c.getFileName() + ": " + path);
                }
            }
        }
        assertTrue(seen > 30, "sanity: command files seen " + seen);
        assertEquals(List.of(), missing);
    }

    /** A plan whose only 1.x leftovers are command files (the copies already gone) still moves to 2.x. */
    @Test
    void commandFilesAloneMakeAMigration() throws IOException {
        Plan full = migrationPlans().get("2.1.1/unedited");
        Plan commandsOnly = new Plan(full.changes().stream()
                .filter(c -> UpdateMigration.kind(c) != Kind.REPLACED_COPY).toList(), full.notes(), full.failures());
        assertTrue(commandsOnly.changes().stream().anyMatch(c -> UpdateMigration.kind(c) == Kind.COMMAND_FILE), "sanity");
        assertTrue(UpdateMigration.isMigration(commandsOnly, "2.1.1"));
        Plan neither = new Plan(commandsOnly.changes().stream()
                .filter(c -> UpdateMigration.kind(c) != Kind.COMMAND_FILE).toList(), full.notes(), full.failures());
        assertFalse(UpdateMigration.isMigration(neither, "2.1.1"), "the marker alone is not a migration");
    }
}
