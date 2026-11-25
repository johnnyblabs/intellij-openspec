package com.johnnyblabs.openspec.services;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.UpdatePlanParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The preview-and-apply path with captured CLI output. Every CLI call is recorded with its exact arguments and
 * environment, so "Apply runs once", "Cancel changes nothing" and "a failure is never up to date" are checked on the
 * call list, not on a contains().
 */
public class UpdatePreviewServiceTest extends BasePlatformTestCase {

    private static final List<String> DRY = List.of("update", "--dry-run", "--json");
    private static final List<String> APPLY = List.of("update");
    private static final List<String> FORCED = List.of("update", "--dry-run", "--force", "--json");

    private record Call(List<String> args, Map<String, String> env) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final List<Notification> raised = new ArrayList<>();
    private UpdatePreviewService service;
    private CliDetectionService detection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        calls.clear();
        raised.clear();
        detection = new CliDetectionService(getProject());
        CliDetectionTestSupport.fakeAvailable(detection, "1.15.0");
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        service = new UpdatePreviewService(getProject());
        ServiceContainerUtil.replaceService(getProject(), UpdatePreviewService.class, service, getTestRootDisposable());
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
        PropertiesComponent state = PropertiesComponent.getInstance(getProject());
        state.unsetValue(UpdateLegacyCleanupService.DISMISSED_SET_KEY);
        state.unsetValue(UpdateLegacyCleanupService.REGENERATING_SET_KEY);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT);
        } finally {
            super.tearDown();
        }
    }

    private static CliRunner.CliResult captured(String name) {
        String base = "/fixtures/cli/1.15.0/update-dry-run/" + name;
        return new CliRunner.CliResult(Integer.parseInt(read(base + ".exit").strip()), read(base + ".json"), read(base + ".stderr"));
    }

    private static String read(String path) {
        try (InputStream is = UpdatePreviewServiceTest.class.getResourceAsStream(path)) {
            return is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A CLI that answers the dry run, the apply and the forced dry run from the given results, recording each call. */
    private UpdatePreviewService.UpdateCli cli(CliRunner.CliResult dry, CliRunner.CliResult apply, CliRunner.CliResult forced) {
        return (project, cliArgs) -> {
            String[] args = cliArgs.requested().toArray(String[]::new);
            java.util.Map<String, String> env = cliArgs.env();
            List<String> a = List.of(args);
            calls.add(new Call(a, Map.copyOf(env)));
            if (a.equals(DRY)) return dry;
            if (a.equals(FORCED)) return forced;
            if (a.equals(APPLY)) return apply;
            throw new AssertionError("unexpected CLI call " + a);
        };
    }

    private static final UpdatePreviewService.Decider NEVER = model -> {
        throw new AssertionError("the preview must not be shown");
    };

    private List<List<String>> callArgs() {
        return calls.stream().map(Call::args).toList();
    }

    private List<String> messages(NotificationType type) {
        return raised.stream().filter(n -> n.getType() == type).map(Notification::getContent).toList();
    }

    private static final CliRunner.CliResult OK = new CliRunner.CliResult(0, "Updated.\n", "");

    // ---------- The floor ----------

    public void testThePreviewNeedsCli115() {
        for (String older : List.of("1.14.3", "1.14.99", "1.3.0")) {
            CliDetectionTestSupport.fakeAvailable(detection, older);
            assertFalse(older, service.supportsPreview());
        }
        for (String newer : List.of("1.15.0", "1.15.2", "1.16.0", "2.0.0")) {
            CliDetectionTestSupport.fakeAvailable(detection, newer);
            assertTrue(newer, service.supportsPreview());
        }
        CliDetectionTestSupport.fakeUnavailable(detection);
        assertFalse("no CLI, no preview", service.supportsPreview());
    }

    // ---------- Nothing to do, or a failure: the preview isn't shown and nothing is applied ----------

    public void testAnEmptyPlanReportsUpToDateAndRunsNothing() {
        service.setSeamsForTest(cli(captured("up-to-date"), null, null), NEVER);
        List<CliRunner.CliResult> applied = new ArrayList<>();

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, applied::add);

        assertEquals(List.of(DRY), callArgs());
        assertTrue(applied.isEmpty());
        assertEquals(1, messages(NotificationType.INFORMATION).size());
        assertTrue(messages(NotificationType.INFORMATION).get(0).contains("Already up to date"));
    }

    public void testAFailedDryRunIsAFailureNotUpToDate() {
        service.setSeamsForTest(cli(captured("no-openspec-dir"), null, null), NEVER);
        List<CliRunner.CliResult> applied = new ArrayList<>();

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, applied::add);

        assertEquals(List.of(DRY), callArgs());
        assertTrue(applied.isEmpty());
        assertTrue("no up-to-date message: " + raised, messages(NotificationType.INFORMATION).isEmpty());
        List<String> warnings = messages(NotificationType.WARNING);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("No OpenSpec directory found"));
    }

    public void testAStatusErrorWithExitZeroIsStillAFailure() {
        CliRunner.CliResult envelope = new CliRunner.CliResult(0, captured("no-openspec-dir").stdout(), "");
        service.setSeamsForTest(cli(envelope, null, null), NEVER);

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> fail("must not apply"));

        assertEquals(List.of(DRY), callArgs());
        assertEquals(1, messages(NotificationType.WARNING).size());
    }

    public void testACliThatCannotRunIsAFailure() {
        service.setSeamsForTest((project, cliArgs) -> {
            String[] args = cliArgs.requested().toArray(String[]::new);
            java.util.Map<String, String> env = cliArgs.env();
            calls.add(new Call(List.of(args), env));
            throw new CliRunner.CliException("OpenSpec CLI command timed out after 30s");
        }, NEVER);

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> fail("must not apply"));

        assertEquals(List.of(DRY), callArgs());
        assertTrue(messages(NotificationType.WARNING).get(0).contains("timed out"));
    }

    // ---------- onFinished: exactly once, whatever the outcome ----------

    private int finishedCount(UpdatePreviewService.UpdateCli cli, UpdatePreviewService.Decider decider,
                              List<CliRunner.CliResult> applied) {
        service.setSeamsForTest(cli, decider);
        int[] finished = {0};
        service.previewAndApply(UpdatePreviewService.Origin.MANAGE_TOOLS, null, r -> {
            assertEquals("onApplied runs before onFinished", 0, finished[0]);
            applied.add(r);
        }, () -> finished[0]++);
        return finished[0];
    }

    public void testOnFinishedRunsOnceForEveryOutcome() {
        List<CliRunner.CliResult> applied = new ArrayList<>();
        assertEquals("nothing to update", 1, finishedCount(cli(captured("up-to-date"), null, null), NEVER, applied));
        assertEquals("dry run failed", 1, finishedCount(cli(captured("no-openspec-dir"), null, null), NEVER, applied));
        assertEquals("cancelled", 1, finishedCount(cli(captured("windsurf-move"), null, null), model -> false, applied));
        assertTrue(applied.isEmpty());

        assertEquals("applied", 1, finishedCount(cli(captured("windsurf-move"), OK, null), model -> true, applied));
        assertEquals(List.of(OK), applied);

        UpdatePreviewService.UpdateCli applyCannotRun = (project, cliArgs) -> {
            String[] args = cliArgs.requested().toArray(String[]::new);
            java.util.Map<String, String> env = cliArgs.env();
            if (List.of(args).equals(DRY)) return captured("windsurf-move");
            throw new CliRunner.CliException("OpenSpec CLI command timed out after 30s");
        };
        assertEquals("apply could not run", 1, finishedCount(applyCannotRun, model -> true, new ArrayList<>()));
    }

    // ---------- A plan: Apply runs once, Cancel runs nothing more ----------

    public void testApplyRunsThePlainUpdateExactlyOnceNonInteractively() {
        AtomicReference<UpdatePreviewModel> shown = new AtomicReference<>();
        service.setSeamsForTest(cli(captured("windsurf-move"), OK, null), model -> {
            shown.set(model);
            return true;
        });
        List<CliRunner.CliResult> applied = new ArrayList<>();

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, applied::add);

        assertEquals(List.of(DRY, APPLY), callArgs());
        assertEquals(List.of(OK), applied);
        assertEquals("24 changes: 12 deletes, 12 adds", shown.get().summary());
        for (Call c : calls) {
            assertEquals("every update run is non-interactive: " + c.args(), "0", c.env().get("OPEN_SPEC_INTERACTIVE"));
        }
    }

    public void testCancelRunsNothingMore() {
        service.setSeamsForTest(cli(captured("windsurf-move"), null, null), model -> false);

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> fail("must not apply"));

        assertEquals(List.of(DRY), callArgs());
        assertTrue(raised.toString(), raised.isEmpty());
    }

    public void testTheFocusToolReachesThePreview() {
        AtomicReference<UpdatePreviewModel> shown = new AtomicReference<>();
        service.setSeamsForTest(cli(captured("stale-skills"), null, null), model -> {
            shown.set(model);
            return false;
        });

        service.previewAndApply(UpdatePreviewService.Origin.MANAGE_TOOLS, "claude", r -> {});

        assertEquals("claude", shown.get().focusTool());
        assertEquals("claude", shown.get().groups().get(0).tool());
    }

    // ---------- Legacy cleanup from the forced plan ----------

    public void testLegacyArtifactsAreOfferedFromTheForcedPlan() {
        service.setSeamsForTest(cli(captured("legacy-pending"), OK, captured("legacy-pending-force")), model -> true);

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> {});

        assertEquals(List.of(DRY, APPLY, FORCED), callArgs());
        List<String> infos = messages(NotificationType.INFORMATION);
        assertTrue(raised.toString(), infos.stream().anyMatch(m -> m.contains("listed 3 legacy file(s)")));
        assertNoForceWithoutDryRun();
    }

    public void testAFailedForcedPlanOffersNothingAndSaysWhy() {
        service.setSeamsForTest(cli(captured("legacy-pending"), OK, captured("no-openspec-dir")), model -> true);

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> {});

        assertEquals(List.of(DRY, APPLY, FORCED), callArgs());
        assertTrue(messages(NotificationType.INFORMATION).stream().noneMatch(m -> m.contains("legacy file(s)")));
        assertTrue(messages(NotificationType.WARNING).get(0).contains("legacy cleanup list couldn't be read"));
    }

    public void testAFailedApplySkipsTheCleanup() {
        CliRunner.CliResult failedApply = new CliRunner.CliResult(1, "", "EACCES");
        service.setSeamsForTest(cli(captured("legacy-pending"), failedApply, null), model -> true);
        List<CliRunner.CliResult> applied = new ArrayList<>();

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, applied::add);

        assertEquals(List.of(DRY, APPLY), callArgs());
        assertEquals(List.of(failedApply), applied);
    }

    public void testOnlyInProjectDeletesAreOffered() {
        UpdatePlanParser.Plan forced = (UpdatePlanParser.Plan) UpdatePlanParser.parse(captured("legacy-pending-force"));
        List<UpdatePlanParser.Change> withOutside = new ArrayList<>(forced.changes());
        withOutside.add(new UpdatePlanParser.Change("~/.codex/prompts/openspec-apply.md", "delete", "openspec"));
        withOutside.add(new UpdatePlanParser.Change("openspec/AGENTS.md", "update", "openspec"));

        List<String> offered = UpdatePreviewService.legacyDeletes(new UpdatePlanParser.Plan(withOutside, List.of(), List.of()));

        assertEquals(List.of(".cursor/commands/openspec-apply.md", ".cursor/commands/openspec-archive.md",
                ".cursor/commands/openspec-proposal.md"), offered);
    }

    private static UpdatePlanParser.Plan forcedPlan(java.nio.file.Path json) {
        try {
            return (UpdatePlanParser.Plan) UpdatePlanParser.parse(
                    new CliRunner.CliResult(0, java.nio.file.Files.readString(json), ""));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 2.1 tags skill-folder deletes and the marker {@code openspec}; the update manages them, so none is offered. */
    public void testSkillFoldersAndTheMarkerAreNeverOfferedAsLegacyFiles() {
        int planned = 0;
        for (String v : List.of("2.1.0", "2.1.1")) {
            for (String name : List.of("notes-beside-skill", "profile-shrink", "profile-empty", "unedited")) {
                java.nio.file.Path json = com.johnnyblabs.openspec.util.FixtureVersions.CLI
                        .resolve(v + "/update-migration/" + name + "/plan-force.json");
                UpdatePlanParser.Plan forced = forcedPlan(json);
                planned += (int) forced.changes().stream().filter(c -> c.action().equals("delete")
                        && c.tool().equals("openspec") && (UpdateMigration.inSkillFolder(c.path())
                        || c.path().equals(UpdateMigration.MARKER))).count();
                assertEquals(v + " " + name, List.of(), UpdatePreviewService.legacyDeletes(forced));
            }
        }
        assertTrue("sanity: the plans tag skill-folder deletes openspec: " + planned, planned > 20);
    }

    /** The captured legacy cleanups still offer exactly OpenSpec's own legacy deletes. */
    public void testLegacyCapturesOfferWhatTheyOfferedBefore() {
        for (String v : List.of("1.17.0", "2.0.0", "2.1.0", "2.1.1")) {
            for (String name : List.of("legacy-pending-force", "legacy-one-edited-force")) {
                java.nio.file.Path json = com.johnnyblabs.openspec.util.FixtureVersions.CLI
                        .resolve(v + "/update-dry-run/" + name + ".json");
                if (!java.nio.file.Files.exists(json)) continue;
                UpdatePlanParser.Plan forced = forcedPlan(json);
                List<String> before = forced.changes().stream().filter(c -> c.action().equals("delete")
                        && !c.outsideProject() && c.tool().equals("openspec")).map(UpdatePlanParser.Change::path).toList();
                assertFalse(v + " " + name + " sanity", before.isEmpty());
                assertEquals(v + " " + name, before, UpdatePreviewService.legacyDeletes(forced));
            }
        }
    }

    // ---------- Files at risk (update-migration captures) ----------

    private static CliRunner.CliResult migrationPlan(String version, String name) {
        String base = "/fixtures/cli/" + version + "/update-migration/" + name + "/plan";
        return new CliRunner.CliResult(Integer.parseInt(read(base + ".exit").strip()), read(base + ".json"), "");
    }

    /** Recreates a capture's project before the update in a real temp folder and points the service at it. */
    private java.nio.file.Path projectBefore(String version, String name) throws IOException {
        java.nio.file.Path dir = com.johnnyblabs.openspec.util.FixtureVersions.CLI.resolve(version + "/update-migration");
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("update-migration");
        com.intellij.openapi.util.Disposer.register(getTestRootDisposable(),
                () -> com.intellij.openapi.util.io.FileUtil.delete(root.toFile()));
        com.johnnyblabs.openspec.util.FixtureLayout.materialize(
                com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(dir.resolve(name + "/before.json")))
                        .getAsJsonObject(), root.resolve("project"), root.resolve("home"),
                com.johnnyblabs.openspec.util.FixtureLayout.LinkMode.SYMLINK_OR_COPY,
                com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(dir.resolve("texts.json")))
                        .getAsJsonObject());
        service.setProjectRootForTest(root.resolve("project"));
        service.setCliVersionForTest(version);
        return root.resolve("project");
    }

    public void testTheFilesAtRiskReachThePreviewAndHoldApply() throws IOException {
        projectBefore("2.1.1", "edited");
        List<UpdatePreviewModel> shown = new ArrayList<>();
        service.setSeamsForTest(cli(migrationPlan("2.1.1", "edited"), OK, null), model -> {
            shown.add(model);
            return false;
        });
        service.setCliVersionForTest("2.1.1");

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> {});

        assertEquals(1, shown.size());
        UpdatePreviewModel m = shown.get(0);
        assertTrue(m.migration());
        assertEquals(List.of(".claude/skills/openspec-explore/SKILL.md", ".cursor/skills/openspec-explore/SKILL.md"),
                m.atRisk().stream().map(UpdateMigration.AtRisk::path).sorted().toList());
        assertFalse("Apply waits for the backed-up confirmation", m.canApply(false));
        assertTrue(m.canApply(true));
        assertTrue(m.cancelIsDefault());
        assertEquals("cancelled: no apply", List.of(DRY), callArgs());
    }

    public void testNothingIsAtRiskWhereTwoZeroZeroKeepsTheEdits() throws IOException {
        projectBefore("2.0.0", "edited");
        List<UpdatePreviewModel> shown = new ArrayList<>();
        service.setSeamsForTest(cli(migrationPlan("2.0.0", "edited"), OK, null), model -> {
            shown.add(model);
            return false;
        });
        service.setCliVersionForTest("2.0.0");

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> {});

        assertEquals(List.of(), shown.get(0).atRisk());
        assertTrue(shown.get(0).canApply(false));
    }

    public void testTheFilesAreReadInTheBackgroundNotInTheUiStep() throws IOException {
        projectBefore("2.1.1", "edited");
        boolean[] inBackground = {false};
        List<Boolean> readInBackground = new ArrayList<>();
        service.setSeamsForTest(cli(migrationPlan("2.1.1", "edited"), OK, null), model -> false);
        service.setCliVersionForTest("2.1.1");
        service.setThreadingForTest((title, task) -> {
            inBackground[0] = true;
            try {
                task.run();
            } finally {
                inBackground[0] = false;
            }
        }, (modality, task) -> {
            boolean was = inBackground[0];
            inBackground[0] = false;
            try {
                task.run();
            } finally {
                inBackground[0] = was;
            }
        });
        service.setAtRiskReaderForTest((root, plan) -> {
            readInBackground.add(inBackground[0]);
            return UpdateMigration.atRiskFiles(root, plan);
        });

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> {});

        assertEquals(List.of(true), readInBackground);
    }

    private static CliRunner.CliResult appliedRun(String version, String name) {
        String base = "/fixtures/cli/" + version + "/update-migration/" + name + "/applied";
        return new CliRunner.CliResult(Integer.parseInt(read(base + ".exit").strip()), read(base + ".txt"),
                read(base + ".stderr"));
    }

    private List<Notification> keptNotices() {
        return raised.stream().filter(n -> UpdatePreviewService.KEPT_TITLE.equals(n.getTitle())).toList();
    }

    public void testWhatTheUpdateKeptIsReportedOnceWithItsGuidance() throws IOException {
        projectBefore("2.1.1", "edited");
        service.setSeamsForTest(cli(migrationPlan("2.1.1", "edited"), appliedRun("2.1.1", "edited"), null), model -> true);
        service.setCliVersionForTest("2.1.1");

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> {});

        List<Notification> notices = keptNotices();
        assertEquals(1, notices.size());
        String content = notices.get(0).getContent();
        assertTrue(content, content.contains(".claude/commands/opsx/explore.md"));
        assertTrue(content, content.contains("Move anything worth keeping into the skill, then delete by hand."));
        assertEquals(NotificationType.INFORMATION, notices.get(0).getType());
        assertEquals("the kept command file exists in the project, so it can be opened",
                List.of("Open explore.md"), notices.get(0).getActions().stream()
                        .map(a -> a.getTemplatePresentation().getText()).toList());
    }

    public void testReplacedEditsAreReportedAsAWarning() throws IOException {
        projectBefore("2.0.0", "shared-folder");
        service.setSeamsForTest(cli(migrationPlan("2.0.0", "shared-folder"), appliedRun("2.0.0", "shared-folder"), null),
                model -> true);
        service.setCliVersionForTest("2.0.0");

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> {});

        List<Notification> notices = keptNotices();
        assertEquals(1, notices.size());
        assertEquals(NotificationType.WARNING, notices.get(0).getType());
        assertTrue(notices.get(0).getContent().contains(".agents/skills/openspec-explore/SKILL.md"));
    }

    public void testAnUpdateThatKeptNothingRaisesNoKeptNotice() throws IOException {
        projectBefore("2.1.1", "unedited");
        service.setSeamsForTest(cli(migrationPlan("2.1.1", "unedited"), appliedRun("2.1.1", "unedited"), null), model -> true);
        service.setCliVersionForTest("2.1.1");

        service.previewAndApply(UpdatePreviewService.Origin.ACTION, null, r -> {});

        assertEquals(List.of(), keptNotices());
    }

    // ---------- The profile switch ----------

    public void testOnCli115TheProfileSwitchShowsThePreviewInsteadOfAsking() {
        TestDialogManager.setTestDialog(message -> {
            throw new AssertionError("no Yes/No question on 1.15: " + message);
        });
        service.setSeamsForTest(cli(captured("stale-skills"), OK, null), model -> true);

        getProject().getService(WorkflowProfileSwitchService.class).promptAndRunUpdateIfConfirmed("custom");

        assertEquals(List.of(DRY, APPLY), callArgs());
        assertTrue(messages(NotificationType.INFORMATION).stream().anyMatch(m -> m.contains("Skills updated")));
    }

    public void testBelowTheFloorTheProfileSwitchStillAsks() {
        CliDetectionTestSupport.fakeAvailable(detection, "1.14.3");
        List<String> asked = new ArrayList<>();
        TestDialogManager.setTestDialog(message -> {
            asked.add(message);
            return 1; // "Later"
        });
        service.setSeamsForTest(cli(null, null, null), NEVER);

        getProject().getService(WorkflowProfileSwitchService.class).promptAndRunUpdateIfConfirmed("custom");

        assertEquals(1, asked.size());
        assertTrue(callArgs().isEmpty());
    }

    private void assertNoForceWithoutDryRun() {
        for (List<String> a : callArgs()) {
            assertFalse("--force only ever with --dry-run: " + a, a.contains("--force") && !a.contains("--dry-run"));
        }
        assertEquals(Arrays.asList(DRY, APPLY, FORCED), callArgs());
    }
}
