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
        return (project, env, args) -> {
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
        service.setSeamsForTest((project, env, args) -> {
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

        UpdatePreviewService.UpdateCli applyCannotRun = (project, env, args) -> {
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
