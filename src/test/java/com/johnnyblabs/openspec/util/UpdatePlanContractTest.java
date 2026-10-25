package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.util.UpdatePlanParser.Change;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Failed;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Plan;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link UpdatePlanParser} against real {@code openspec update --dry-run --json} runs captured by
 * {@code scripts/capture-update-dry-run-fixtures.py} (see {@code fixtures/cli/README.md}). Every failure case must come
 * back {@link Failed}, never as an empty plan.
 */
class UpdatePlanContractTest {

    static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = UpdatePlanContractTest.class.getResourceAsStream(path)) {
            if (is == null) return "";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The captured run of {@code case} under {@code 1.15.0/update-dry-run/}, or {@code 1.14.3/} for the older case. */
    static CliRunner.CliResult captured(String dir, String name) {
        String base = dir + "/" + name;
        String exit = fixture(base + ".exit").strip();
        assertFalse(exit.isEmpty(), "fixture missing: " + base + ".exit");
        return new CliRunner.CliResult(Integer.parseInt(exit), fixture(base + ".json"), fixture(base + ".stderr"));
    }

    static CliRunner.CliResult dryRun(String name) {
        return captured("1.15.0/update-dry-run", name);
    }

    static Plan plan(String name) {
        Result r = UpdatePlanParser.parse(dryRun(name));
        return assertInstanceOf(Plan.class, r, name + " must parse as a plan");
    }

    static Failed failed(CliRunner.CliResult run) {
        return assertInstanceOf(Failed.class, UpdatePlanParser.parse(run), "must be a failure, never an empty plan");
    }

    @Test
    void upToDateIsAnEmptyPlan() {
        assertTrue(plan("up-to-date").isEmpty());
    }

    @Test
    void firstRunAddsTheGlobalConfigOutsideTheProject() {
        Plan p = plan("first-run-global-config");
        assertEquals(List.of(new Change("~/.config/openspec/config.json", "add", "openspec")), p.changes());
        assertTrue(p.changes().get(0).outsideProject());
        assertFalse(p.isEmpty(), "a first run is not up to date");
    }

    @Test
    void staleSkillsAreInProjectUpdatesForTheirTool() {
        Plan p = plan("stale-skills");
        assertFalse(p.changes().isEmpty());
        assertTrue(p.changes().stream().allMatch(c -> c.action().equals("update") && c.tool().equals("claude")
                && c.path().startsWith(".claude/skills/") && !c.outsideProject()), p.changes().toString());
        assertTrue(p.notes().isEmpty() && p.failures().isEmpty());
    }

    @Test
    void legacyPendingIsANoteWithoutDeletes() {
        Plan p = plan("legacy-pending");
        assertTrue(p.hasNote(UpdatePlanParser.LEGACY_ARTIFACTS));
        assertTrue(p.changes().isEmpty(), "the plain plan leaves legacy files alone");
    }

    @Test
    void forcedLegacyPlanListsInProjectDeletes() {
        Plan p = plan("legacy-pending-force");
        assertEquals(List.of(".cursor/commands/openspec-apply.md", ".cursor/commands/openspec-archive.md",
                        ".cursor/commands/openspec-proposal.md"),
                p.changes().stream().filter(c -> c.action().equals("delete")).map(Change::path).toList());
        assertTrue(p.changes().stream().noneMatch(Change::outsideProject));
    }

    @Test
    void windsurfMoveCarriesItsNoteAndTheDeletesAndAdds() {
        Plan p = plan("windsurf-move");
        assertTrue(p.hasNote(UpdatePlanParser.LEGACY_TOOL_DIRECTORIES));
        long deletes = p.changes().stream().filter(c -> c.action().equals("delete")).count();
        long adds = p.changes().stream().filter(c -> c.action().equals("add")).count();
        assertEquals(12, deletes);
        assertEquals(12, adds);
        assertTrue(p.changes().stream().filter(c -> c.action().equals("delete")).allMatch(c -> c.path().startsWith(".windsurf/")));
    }

    @Test
    void readOnlyFileIsAFailureInsideAnExitZeroPlan() {
        Plan p = plan("read-only-failure");
        assertEquals(1, p.failures().size());
        assertEquals("claude", p.failures().get(0).tool());
        assertTrue(p.failures().get(0).error().startsWith("EACCES"), p.failures().get(0).error());
    }

    @Test
    void noOpenSpecDirectoryFailsWithTheCliMessage() {
        Failed f = failed(dryRun("no-openspec-dir"));
        assertTrue(f.message().contains("No OpenSpec directory found"), f.message());
    }

    @Test
    void jsonWithoutDryRunFailsWithStderr() {
        Failed f = failed(dryRun("json-without-dry-run"));
        assertTrue(f.message().contains("--json is only available with --dry-run"), f.message());
    }

    @Test
    void olderCliRejectsTheFlag() {
        Failed f = failed(captured("1.14.3", "update-dry-run-unknown-option"));
        assertTrue(f.message().contains("unknown option '--dry-run'"), f.message());
    }

    // ---------- Edge cases: none may read as an empty plan ----------

    @Test
    void aStatusErrorFailsEvenWithExitZero() {
        // The captured no-openspec-dir envelope, replayed with exit 0: only the status[] check can catch it.
        failed(new CliRunner.CliResult(0, fixture("1.15.0/update-dry-run/no-openspec-dir.json"), ""));
    }

    @Test
    void nonJsonOutputWithExitZeroFails() {
        failed(new CliRunner.CliResult(0, "Updated 3 files\n", ""));
    }

    @Test
    void emptyOutputWithExitZeroFails() {
        failed(new CliRunner.CliResult(0, "", ""));
    }

    @Test
    void aJsonArrayIsNotAPlan() {
        failed(new CliRunner.CliResult(0, "[]", ""));
    }

    @Test
    void aMissingListFails() {
        failed(new CliRunner.CliResult(0, "{\"changes\":[],\"notes\":[]}", ""));
    }

    @Test
    void anUnreadableEntryFailsRatherThanBeingDropped() {
        failed(new CliRunner.CliResult(0, "{\"changes\":[{\"path\":\"a.md\"}],\"notes\":[],\"failures\":[]}", ""));
        failed(new CliRunner.CliResult(0, "{\"changes\":[\"a.md\"],\"notes\":[],\"failures\":[]}", ""));
    }

    @Test
    void unknownActionsAndNoteCodesAreKept() {
        Plan p = assertInstanceOf(Plan.class, UpdatePlanParser.parse(new CliRunner.CliResult(0,
                "{\"changes\":[{\"path\":\"x.md\",\"action\":\"rename\",\"tool\":\"t\"}],"
                        + "\"notes\":[{\"code\":\"new-thing\",\"message\":\"Something new.\"}],\"failures\":[]}", "")));
        assertEquals("rename", p.changes().get(0).action());
        assertEquals("Something new.", p.notes().get(0).message());
    }

    @Test
    void outsideProjectCoversHomeAndAbsolutePathsOnEveryOs() {
        for (String outside : List.of("~/.minimax/skills/a.md", "/etc/x", "C:\\Users\\me\\x", "c:/x", "\\\\server\\share\\x")) {
            assertTrue(new Change(outside, "add", "t").outsideProject(), outside);
        }
        for (String inside : List.of(".claude/skills/a.md", "openspec/AGENTS.md", "a~b/c.md")) {
            assertFalse(new Change(inside, "add", "t").outsideProject(), inside);
        }
    }
}
