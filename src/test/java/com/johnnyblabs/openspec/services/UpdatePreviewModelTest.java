package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.UpdatePlanParser;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Change;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Plan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The preview's rules, worked out from captured plans. */
class UpdatePreviewModelTest {

    private static Plan plan(String name) {
        return plan("1.15.0", name);
    }

    private static Plan plan(String version, String name) {
        String base = "/fixtures/cli/" + version + "/update-dry-run/" + name;
        try (InputStream json = UpdatePreviewModelTest.class.getResourceAsStream(base + ".json");
             InputStream exit = UpdatePreviewModelTest.class.getResourceAsStream(base + ".exit")) {
            assertNotNull(json, base);
            assertNotNull(exit, base);
            var run = new CliRunner.CliResult(Integer.parseInt(new String(exit.readAllBytes(), StandardCharsets.UTF_8).strip()),
                    new String(json.readAllBytes(), StandardCharsets.UTF_8), "");
            return assertInstanceOf(Plan.class, UpdatePlanParser.parse(run));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void windsurfMoveIsFlaggedAndDeletesComeFirst() {
        UpdatePreviewModel m = UpdatePreviewModel.of(plan("windsurf-move"), null);
        assertEquals("24 changes: 12 deletes, 12 adds", m.summary());
        assertEquals(1, m.notes().size());
        assertTrue(m.notes().get(0).warning(), "the tool-directory move is a warning");
        UpdatePreviewModel.Group devin = m.groups().get(0);
        assertEquals("devin (24)", devin.label());
        assertTrue(devin.changes().subList(0, 12).stream().allMatch(c -> c.action().equals("delete")), "deletes first");
        assertTrue(m.cancelIsDefault(), "a plan that deletes makes Cancel the default");
        assertTrue(m.applyEnabled());
    }

    @Test
    void theGlobalConfigGoesInTheOutsideGroupLast() {
        UpdatePreviewModel m = UpdatePreviewModel.of(plan("first-run-global-config"), null);
        assertEquals(1, m.groups().size());
        UpdatePreviewModel.Group g = m.groups().get(0);
        assertTrue(g.outsideProject());
        assertEquals(UpdatePreviewModel.OUTSIDE_PROJECT, g.tool());
        assertFalse(m.cancelIsDefault(), "nothing is deleted");
    }

    @Test
    void outsideEntriesNeverMixIntoATool() {
        List<Change> changes = new ArrayList<>(plan("stale-skills").changes());
        changes.add(new Change("~/.minimax/skills/openspec-propose/SKILL.md", "update", "minimax-code"));
        changes.add(new Change("/fixture/home/.codex/prompts/openspec-apply.md", "delete", "codex"));
        UpdatePreviewModel m = UpdatePreviewModel.of(new Plan(changes, List.of(), List.of()), null);

        assertEquals(List.of("claude", UpdatePreviewModel.OUTSIDE_PROJECT), m.groups().stream().map(UpdatePreviewModel.Group::tool).toList());
        UpdatePreviewModel.Group outside = m.groups().get(1);
        assertEquals(2, outside.changes().size());
        assertEquals("delete", outside.changes().get(0).action(), "deletes first in the outside group too");
        assertTrue(m.groups().get(0).changes().stream().noneMatch(Change::outsideProject));
    }

    @Test
    void theFocusToolComesFirstThenToolsByName() {
        List<Change> changes = List.of(new Change("b/1", "update", "zed"), new Change("a/1", "update", "alpha"),
                new Change("c/1", "update", "claude"));
        Plan p = new Plan(changes, List.of(), List.of());
        assertEquals(List.of("alpha", "claude", "zed"),
                UpdatePreviewModel.of(p, null).groups().stream().map(UpdatePreviewModel.Group::tool).toList());
        assertEquals(List.of("zed", "alpha", "claude"),
                UpdatePreviewModel.of(p, "zed").groups().stream().map(UpdatePreviewModel.Group::tool).toList());
        assertEquals(List.of("alpha", "claude", "zed"),
                UpdatePreviewModel.of(p, "not-in-plan").groups().stream().map(UpdatePreviewModel.Group::tool).toList());
    }

    @Test
    void failuresDisableApply() {
        UpdatePreviewModel m = UpdatePreviewModel.of(plan("read-only-failure"), null);
        assertFalse(m.applyEnabled());
        assertEquals(1, m.failures().size());
    }

    @Test
    void aNotesOnlyPlanSaysThereAreNoFileChanges() {
        UpdatePreviewModel m = UpdatePreviewModel.of(plan("legacy-pending"), null);
        assertEquals("No file changes", m.summary());
        assertFalse(m.notes().get(0).warning(), "legacy artifacts are informational");
        assertTrue(m.groups().isEmpty());
    }

    @Test
    void anUnknownNoteIsStillShown() {
        Plan p = new Plan(List.of(), List.of(new UpdatePlanParser.Note("new-thing", "Something new.")), List.of());
        UpdatePreviewModel m = UpdatePreviewModel.of(p, null);
        assertEquals("Something new.", m.notes().get(0).message());
        assertFalse(m.notes().get(0).warning());
    }

    @Test
    void summaryPluralizesAndKeepsUnknownActions() {
        assertEquals("1 change: 1 update", UpdatePreviewModel.summary(List.of(new Change("a", "update", "t"))));
        assertEquals("3 changes: 1 delete, 1 add, 1 rename", UpdatePreviewModel.summary(List.of(
                new Change("a", "rename", "t"), new Change("b", "add", "t"), new Change("c", "delete", "t"))));
    }

    // --- CLI 1.17.0 cleanup plans ---

    @Test
    void unaskedLegacyDeletesSitInTheirToolGroupAndMakeCancelTheDefault() {
        Plan plan = plan("1.17.0", "legacy-unedited");
        UpdatePreviewModel m = UpdatePreviewModel.of(plan, null);
        assertTrue(m.cancelIsDefault(), "1.17 deletes the unedited legacy files, so Cancel is the default");
        assertTrue(m.notes().isEmpty());
        UpdatePreviewModel.Group openspec = m.groups().stream().filter(g -> g.tool().equals("openspec")).findFirst()
                .orElseThrow();
        long deletes = plan.changes().stream().filter(c -> c.tool().equals("openspec") && c.action().equals("delete"))
                .count();
        assertEquals(4, deletes);
        assertTrue(openspec.changes().subList(0, (int) deletes).stream().allMatch(c -> c.action().equals("delete")),
                "deletes are listed first in the group");
        assertEquals("openspec (" + openspec.changes().size() + ")", openspec.label(), "other tools keep their id");
    }

    @Test
    void theEarlierReleaseGroupIsNamedInWordsWithItsCount() {
        Plan plan = plan("1.17.0", "earlier-release");
        UpdatePreviewModel m = UpdatePreviewModel.of(plan, null);
        long swept = plan.changes().stream().filter(c -> c.tool().equals(UpdatePreviewModel.EARLIER_RELEASE_TOOL)).count();
        assertEquals(6, swept);
        List<String> labels = m.groups().stream().map(UpdatePreviewModel.Group::label).toList();
        assertEquals(List.of("antigravity (13)", "Left by an earlier OpenSpec release (" + swept + ")"), labels);
    }

    // --- The move to OpenSpec 2 (update-migration captures) ---

    private static Plan migrationPlan(String version, String name) {
        String base = "/fixtures/cli/" + version + "/update-migration/" + name + "/plan";
        try (InputStream json = UpdatePreviewModelTest.class.getResourceAsStream(base + ".json");
             InputStream exit = UpdatePreviewModelTest.class.getResourceAsStream(base + ".exit")) {
            assertNotNull(json, base);
            assertNotNull(exit, base);
            var run = new CliRunner.CliResult(Integer.parseInt(new String(exit.readAllBytes(), StandardCharsets.UTF_8).strip()),
                    new String(json.readAllBytes(), StandardCharsets.UTF_8), "");
            return assertInstanceOf(Plan.class, UpdatePlanParser.parse(run));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> groupNames(UpdatePreviewModel m) {
        return m.groups().stream().map(UpdatePreviewModel.Group::tool).toList();
    }

    @Test
    void aMigrationGroupsTheSharedSkillsCopiesAndCommandsAcrossTools() {
        for (String v : List.of("2.0.0", "2.1.0", "2.1.1")) {
            Plan plan = migrationPlan(v, "unedited");
            UpdatePreviewModel m = UpdatePreviewModel.of(plan, null, v);
            assertTrue(m.migration(), v);
            assertEquals(List.of(UpdatePreviewModel.SHARED_SKILLS_GROUP, UpdatePreviewModel.REPLACED_COPIES_GROUP,
                    UpdatePreviewModel.COMMAND_FILES_GROUP, "claude", UpdatePreviewModel.OUTSIDE_PROJECT), groupNames(m), v);
            var byName = new java.util.HashMap<String, UpdatePreviewModel.Group>();
            m.groups().forEach(g -> byName.put(g.tool(), g));
            assertEquals(12, byName.get(UpdatePreviewModel.REPLACED_COPIES_GROUP).changes().size(),
                    "both tools' six copies, whatever their tool value, " + v);
            assertEquals(12, byName.get(UpdatePreviewModel.COMMAND_FILES_GROUP).changes().size(), v);
            assertTrue(byName.get("claude").changes().stream().allMatch(c -> UpdatePreviewModel.linkRow(c) != null),
                    "Claude's group holds its links: " + v);
            int total = m.groups().stream().mapToInt(g -> g.changes().size()).sum();
            assertEquals(plan.changes().size(), total, "no change is dropped or repeated, " + v);
            assertTrue(m.cancelIsDefault());
        }
    }

    @Test
    void theSameMigrationPlanIsGroupedByToolWithoutATwoXCli() {
        UpdatePreviewModel m = UpdatePreviewModel.of(migrationPlan("2.1.1", "unedited"), null, null);
        assertFalse(m.migration());
        assertTrue(groupNames(m).containsAll(List.of("claude", "cursor", "openspec")), groupNames(m).toString());
        assertFalse(groupNames(m).contains(UpdatePreviewModel.REPLACED_COPIES_GROUP));
    }

    @Test
    void plansThatAreNotAMigrationKeepTheirToolGroups() {
        for (String v : List.of("2.0.0", "2.1.0", "2.1.1")) {
            for (String name : List.of("up-to-date", "stale-skills", "legacy-unedited", "global-skills-removed")) {
                if (UpdatePreviewModelTest.class.getResource("/fixtures/cli/" + v + "/update-dry-run/" + name + ".json") == null) {
                    continue;
                }
                UpdatePreviewModel m = UpdatePreviewModel.of(plan(v, name), null, v);
                assertFalse(m.migration(), v + " " + name);
                assertEquals(UpdatePreviewModel.of(plan(v, name), null).groups(), m.groups(), v + " " + name);
            }
            UpdatePreviewModel shrink = UpdatePreviewModel.of(migrationPlan(v, "profile-shrink"), null, v);
            assertFalse(shrink.migration(), v);
        }
    }


    @Test
    void filesAtRiskHoldApplyUntilTheBackupIsConfirmed() {
        var risk = List.of(new UpdateMigration.AtRisk(".claude/skills/openspec-explore/SKILL.md", "delete"));
        UpdatePreviewModel m = UpdatePreviewModel.of(migrationPlan("2.1.1", "edited"), null, "2.1.1", risk);
        assertTrue(m.applyEnabled());
        assertFalse(m.canApply(false));
        assertTrue(m.canApply(true));
        UpdatePreviewModel none = UpdatePreviewModel.of(migrationPlan("2.1.1", "edited"), null, "2.1.1", List.of());
        assertTrue(none.canApply(false), "nothing at risk: no confirmation needed");
    }

    @Test
    void failuresStillBlockApplyWhateverTheBackup() {
        var risk = List.of(new UpdateMigration.AtRisk(".claude/skills/openspec-explore/SKILL.md", "delete"));
        UpdatePreviewModel m = UpdatePreviewModel.of(plan("read-only-failure"), null, "1.15.0", risk);
        assertFalse(m.applyEnabled());
        assertFalse(m.canApply(true));
        assertFalse(m.canApply(false));
    }
}
