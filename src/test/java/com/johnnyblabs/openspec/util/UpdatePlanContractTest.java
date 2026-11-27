package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.util.UpdatePlanParser.Change;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Failed;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Plan;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Result;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link UpdatePlanParser} against real {@code openspec update --dry-run --json} runs captured by
 * {@code scripts/capture-update-dry-run-fixtures.py} (see {@code fixtures/cli/README.md}). Every failure case must come
 * back {@link Failed}, never as an empty plan.
 * <p>
 * The release-wide tests run over every captured release ({@link FixtureVersions}); the others pin the behaviour of
 * the release named in their fixture path.
 */
class UpdatePlanContractTest {

    /** Releases with a captured update-dry-run set, and how many cases each holds; each must be present. */
    static final java.util.Map<String, Integer> CASE_COUNT = java.util.Map.of("1.15.0", 9, "1.16.1", 9, "1.17.0", 13,
            "2.0.0", 12, "2.1.0", 16, "2.1.1", 16, "2.2.0", 16);
    static final java.util.Set<String> RELEASES = CASE_COUNT.keySet();

    /** A capture that must exist: a missing file fails rather than reading as empty output. */
    static String required(String name) {
        String text = fixture(name);
        assertNotNull(UpdatePlanContractTest.class.getResource("/fixtures/cli/" + name), "fixture missing: " + name);
        return text;
    }

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
        failed(new CliRunner.CliResult(0, required("1.15.0/update-dry-run/no-openspec-dir.json"), ""));
    }

    // ---------- Every captured release ----------

    /** The case names captured for {@code version}, from its {@code .exit} files. */
    static List<String> cases(String version) throws IOException {
        Path dir = FixtureVersions.CLI.resolve(version).resolve("update-dry-run");
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                String n = f.getFileName().toString();
                if (n.endsWith(".exit")) names.add(n.substring(0, n.length() - ".exit".length()));
            }
        }
        assertEquals(CASE_COUNT.get(version), names.size(), "sanity: " + version + "'s update-dry-run cases " + names);
        return names;
    }

    /** Exit 0 is a plan; anything else is a failure, never an empty plan. Upstream's own exit decides which. */
    @TestFactory
    Stream<DynamicTest> everyCapturedRunParsesAsItsExitSays() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String v : FixtureVersions.versionsWith("update-dry-run", RELEASES)) {
            for (String c : cases(v)) {
                tests.add(DynamicTest.dynamicTest(v + "/" + c, () -> KnownGaps.check(v, "update-dry-run", c, () -> {
                    CliRunner.CliResult run = captured(v + "/update-dry-run", c);
                    Result r = UpdatePlanParser.parse(run);
                    if (run.exitCode() == 0) {
                        assertInstanceOf(Plan.class, r, "exit 0 must parse as a plan");
                    } else {
                        assertInstanceOf(Failed.class, r, "exit " + run.exitCode() + " must be a failure");
                    }
                })));
            }
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> upToDateIsEmptyAndAStatusErrorFailsOnEveryRelease() {
        return FixtureVersions.versionsWith("update-dry-run", RELEASES).stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            assertTrue(assertInstanceOf(Plan.class, UpdatePlanParser.parse(captured(v + "/update-dry-run", "up-to-date")))
                    .isEmpty(), v + " up-to-date");
            Failed f = failed(captured(v + "/update-dry-run", "no-openspec-dir"));
            assertTrue(f.message().contains("No OpenSpec directory found"), f.message());
            failed(new CliRunner.CliResult(0, required(v + "/update-dry-run/no-openspec-dir.json"), ""));
        }));
    }

    // --- CLI 2.1: the skills kept for the machine (~/.agents/skills) are refreshed by update, as notes ---

    @TestFactory
    Stream<DynamicTest> machineWideRefreshIsANoteNotAProjectChange() {
        return FixtureVersions.versionsWith("update-dry-run/global-skills-added.json", List.of("2.1.0", "2.1.1", "2.2.0")).stream()
                .map(v -> DynamicTest.dynamicTest(v, () -> {
                    Plan added = assertInstanceOf(Plan.class,
                            UpdatePlanParser.parse(captured(v + "/update-dry-run", "global-skills-added")));
                    assertTrue(added.hasNote("global-skills"), added.notes().toString());
                    assertFalse(added.hasNote("removed-skills"), "adding a workflow removes no skill");
                    assertTrue(added.changes().stream().noneMatch(c -> c.path().startsWith(".agents/")),
                            "the machine's skills are not project changes: " + added.changes());

                    Plan removed = assertInstanceOf(Plan.class,
                            UpdatePlanParser.parse(captured(v + "/update-dry-run", "global-skills-removed")));
                    assertTrue(removed.hasNote("global-skills") && removed.hasNote("removed-skills"), removed.notes().toString());

                    Plan current = assertInstanceOf(Plan.class,
                            UpdatePlanParser.parse(captured(v + "/update-dry-run", "global-skills-up-to-date")));
                    assertFalse(current.hasNote("global-skills"), "nothing to refresh: " + current.notes());
                }));
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

    // --- CLI 1.17.0: unedited legacy files are removed without --force (fixtures/cli/1.17.0/update-dry-run/) ---

    static Plan plan117(String name) {
        Result r = UpdatePlanParser.parse(captured("1.17.0/update-dry-run", name));
        return assertInstanceOf(Plan.class, r, name + " must parse as a plan");
    }

    private static List<String> deletes(Plan p, String tool) {
        return p.changes().stream().filter(c -> c.action().equals("delete") && c.tool().equals(tool))
                .map(Change::path).sorted().toList();
    }

    @Test
    void unedited117LegacyFilesAreOrdinaryDeletesWithNoNote() {
        Plan p = plan117("legacy-unedited");
        assertFalse(p.hasNote(UpdatePlanParser.LEGACY_ARTIFACTS), "1.17 removes unedited legacy files unasked");
        assertEquals(List.of(".claude/commands/openspec/apply.md", ".claude/commands/openspec/archive.md",
                        ".claude/commands/openspec/proposal.md", "openspec/AGENTS.md"),
                deletes(p, "openspec"));
        assertTrue(p.changes().stream().filter(c -> c.action().equals("delete")).noneMatch(Change::outsideProject));
        assertTrue(p.failures().isEmpty());
    }

    @Test
    void oneEdited117LegacyFileLeavesOnlyTheNote() {
        Plan p = plan117("legacy-one-edited");
        assertTrue(p.hasNote(UpdatePlanParser.LEGACY_ARTIFACTS));
        assertTrue(p.changes().isEmpty(), "cleanup is all-or-nothing: nothing is removed when one file was edited");
        assertEquals(deletes(plan117("legacy-unedited"), "openspec"),
                deletes(plan117("legacy-one-edited-force"), "openspec"), "--force lists the same cleanup");
    }

    @Test
    void earlierReleaseSweepIsItsOwnTool() {
        Plan p = plan117("earlier-release");
        List<String> swept = deletes(p, "earlier-release");
        assertEquals(List.of(".agent/workflows/opsx-apply.md", ".agent/workflows/opsx-archive.md",
                ".agent/workflows/opsx-explore.md", ".agent/workflows/opsx-propose.md",
                ".agent/workflows/opsx-sync.md", ".agent/workflows/opsx-update.md"), swept);
        assertEquals(6, deletes(p, "antigravity").size(), "the former-root skills move under the tool's own id");
        assertTrue(p.notes().isEmpty());
    }
}
