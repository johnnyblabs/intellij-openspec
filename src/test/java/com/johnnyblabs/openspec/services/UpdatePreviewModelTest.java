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
        String base = "/fixtures/cli/1.15.0/update-dry-run/" + name;
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
}
