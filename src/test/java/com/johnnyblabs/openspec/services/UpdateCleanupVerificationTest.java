package com.johnnyblabs.openspec.services;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.util.CliRunner;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Legacy-cleanup verification. On CLI 1.15.0+ it must write nothing: only {@code update --dry-run} runs, never a
 * plain {@code update}. From 1.17 a plain update removes unedited legacy files unasked and still prints them as "to
 * remove". Every CLI call is answered by exact argv from a real 1.17.0 capture, and an unmapped call fails the test.
 */
public class UpdateCleanupVerificationTest extends BasePlatformTestCase {

    private static final List<String> DRY = List.of("update", "--dry-run", "--json");
    private static final List<String> FORCED = List.of("update", "--dry-run", "--force", "--json");

    private record Call(List<String> args, Map<String, String> env) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final List<List<String>> plainCalls = new ArrayList<>();
    private final List<Notification> raised = new ArrayList<>();
    private CliDetectionService detection;
    private UpdatePreviewService preview;
    private UpdateLegacyCleanupService cleanup;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        calls.clear();
        plainCalls.clear();
        raised.clear();
        detection = new CliDetectionService(getProject());
        CliDetectionTestSupport.fakeAvailable(detection, "1.17.0");
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        preview = new UpdatePreviewService(getProject());
        ServiceContainerUtil.replaceService(getProject(), UpdatePreviewService.class, preview, getTestRootDisposable());
        cleanup = getProject().getService(UpdateLegacyCleanupService.class);
        // The real-update path records and fails loudly, so a 1.15+ test can't take it unseen.
        cleanup.setUpdateCliForTest((p, args) -> {
            plainCalls.add(List.of(args));
            throw new AssertionError("verification ran openspec " + String.join(" ", args));
        });
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
            cleanup.setUpdateCliForTest(null);
        } finally {
            super.tearDown();
        }
    }

    private static String read(String path) {
        try (InputStream is = UpdateCleanupVerificationTest.class.getResourceAsStream(path)) {
            return is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The real run captured as {@code fixtures/cli/<versionedCase>.{json,exit,stderr}}. */
    private static CliRunner.CliResult captured(String versionedCase) {
        String base = "/fixtures/cli/" + versionedCase;
        String exit = read(base + ".exit").strip();
        assertFalse("fixture missing: " + base, exit.isEmpty());
        return new CliRunner.CliResult(Integer.parseInt(exit), read(base + ".json"), read(base + ".stderr"));
    }

    private static CliRunner.CliResult plan117(String name) {
        return captured("1.17.0/update-dry-run/" + name);
    }

    /** Answers the read-only runs by exact argv and records each call; anything else fails. */
    private void answer(Map<List<String>, Object> byArgs) {
        preview.setSeamsForTest((project, env, args) -> {
            List<String> argv = Arrays.asList(args);
            calls.add(new Call(argv, env));
            Object answer = byArgs.get(argv);
            if (answer instanceof CliRunner.CliException e) throw e;
            if (answer == null) throw new AssertionError("unexpected CLI call: " + argv);
            return (CliRunner.CliResult) answer;
        }, model -> false);
    }

    private List<List<String>> argvs() {
        return calls.stream().map(Call::args).toList();
    }

    private void assertReadOnly() {
        assertFalse("verification must run a CLI command", calls.isEmpty());
        assertTrue("never a plain update: " + argvs(), calls.stream().allMatch(c -> c.args().contains("--dry-run")));
        assertTrue("every run is non-interactive",
                calls.stream().allMatch(c -> c.env().equals(UpdatePreviewService.NON_INTERACTIVE)));
        assertTrue("the real-update path must not run: " + plainCalls, plainCalls.isEmpty());
    }

    private List<String> forcedDeletes() {
        var forced = (com.johnnyblabs.openspec.util.UpdatePlanParser.Plan)
                com.johnnyblabs.openspec.util.UpdatePlanParser.parse(plan117("legacy-one-edited-force"));
        return UpdatePreviewService.legacyDeletes(forced);
    }

    private Notification only() {
        assertEquals("one notice: " + raised.stream().map(Notification::getContent).toList(), 1, raised.size());
        return raised.get(0);
    }

    // --- the read-only check itself ---

    public void testNoLegacyNoteMeansNothingPendingAndRunsOnlyTheDryRun() {
        answer(Map.of(DRY, plan117("legacy-unedited")));
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertReadOnly();
        assertEquals(List.of(DRY), argvs());
        assertTrue(v.verified());
        assertEquals(List.of(), v.after());
        assertTrue("the plan still changes files, so Update is offered", v.updateRemains());
    }

    public void testARemainingNoteTakesThePendingSetFromTheForcedPlan() {
        answer(Map.of(DRY, plan117("legacy-one-edited"), FORCED, plan117("legacy-one-edited-force")));
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertReadOnly();
        assertEquals("the forced plan is read only when the note remains", List.of(DRY, FORCED), argvs());
        List<String> expected = forcedDeletes();
        assertEquals(4, expected.size());
        assertEquals(expected, v.after());
        assertFalse("files are still pending, so this isn't the Update offer", v.updateRemains());
    }

    public void testAnUpToDatePlanIsCleanWithNoUpdateOffer() {
        answer(Map.of(DRY, plan117("up-to-date")));
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertReadOnly();
        assertTrue(v.verified());
        assertFalse(v.updateRemains());
    }

    public void testAFailedDryRunIsUnverified() {
        answer(Map.of(DRY, plan117("no-openspec-dir")));
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertReadOnly();
        assertFalse(v.verified());
        assertEquals(List.of(DRY), argvs());
    }

    public void testADryRunThatCannotStartIsUnverified() {
        answer(Map.of(DRY, new CliRunner.CliException("openspec timed out")));
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertFalse(v.verified());
        assertTrue(v.unverifiedReason(), v.unverifiedReason().contains("timed out"));
    }

    public void testAFailedForcedDryRunIsUnverified() {
        answer(Map.of(DRY, plan117("legacy-one-edited"), FORCED, plan117("no-openspec-dir")));
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertReadOnly();
        assertFalse("a forced plan that failed is never read as nothing pending", v.verified());
    }

    public void testBelow115TheVerificationStillReRunsUpdate() {
        CliDetectionTestSupport.fakeAvailable(detection, "1.14.3");
        preview.setSeamsForTest((project, env, args) -> {
            throw new AssertionError("no dry run below 1.15: " + Arrays.toString(args));
        }, model -> false);
        cleanup.setUpdateCliForTest((p, args) -> {
            plainCalls.add(List.of(args));
            return new CliRunner.CliResult(0, read("/fixtures/cli/update-clean.txt"), "");
        });
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertEquals(List.of(List.of("update")), plainCalls);
        assertTrue(v.verified());
        assertFalse("the re-run already updated, so there is nothing to offer", v.updateRemains());
    }

    // --- what the user is told ---

    public void testACleanReadOnlyCheckWithChangesOffersUpdate() {
        answer(Map.of(DRY, plan117("legacy-unedited")));
        cleanup.reportVerification(List.of(".claude/commands/openspec/apply.md"), cleanup.runVerification());
        Notification notice = only();
        assertTrue(notice.getContent(), notice.getContent().contains("no legacy files left"));
        assertEquals(List.of(UpdateLegacyCleanupService.UPDATE_ACTION_TEXT),
                notice.getActions().stream().map(a -> a.getTemplatePresentation().getText()).toList());
        assertNotNull("the offered action is registered",
                ActionManager.getInstance().getAction(UpdateLegacyCleanupService.UPDATE_ACTION_ID));
    }

    public void testACleanCheckWithNothingLeftOffersNoUpdate() {
        answer(Map.of(DRY, plan117("up-to-date")));
        cleanup.reportVerification(List.of(".claude/commands/openspec/apply.md"), cleanup.runVerification());
        Notification notice = only();
        assertTrue(notice.getContent(), notice.getContent().contains("clean state"));
        assertTrue(notice.getActions().isEmpty());
    }

    public void testStillPendingIsReportedWithTheForcedPlanCount() {
        answer(Map.of(DRY, plan117("legacy-one-edited"), FORCED, plan117("legacy-one-edited-force")));
        cleanup.reportVerification(List.of("something/else.md"), cleanup.runVerification());
        Notification notice = only();
        assertTrue(notice.getContent(), notice.getContent().contains("still reports " + forcedDeletes().size()
                + " pending file(s)"));
        assertTrue(notice.getActions().isEmpty());
    }

    public void testAReadOnlyCheckIsNeverARegenerationLoop() {
        // Nothing ran that could regenerate files, so files still listed were kept or failed to delete, not recreated.
        answer(Map.of(DRY, plan117("legacy-one-edited"), FORCED, plan117("legacy-one-edited-force")));
        List<String> removed = forcedDeletes();
        cleanup.reportVerification(removed, cleanup.runVerification());
        Notification notice = only();
        assertFalse(notice.getContent(), notice.getContent().contains("regenerated"));
        assertTrue(notice.getContent(), notice.getContent().contains("still reports " + removed.size()));
        assertNull("a kept file must not be suppressed as regenerating",
                PropertiesComponent.getInstance(getProject()).getValue(UpdateLegacyCleanupService.REGENERATING_SET_KEY));
    }

    public void testANoteWithNothingListedIsNotReportedComplete() {
        // The earlier-release capture has deletes, but none from OpenSpec's legacy cleanup.
        answer(Map.of(DRY, plan117("legacy-one-edited"), FORCED, plan117("earlier-release")));
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertTrue(v.noteRemains());
        assertEquals(List.of(), v.after());
        cleanup.reportVerification(List.of("a.md"), v);
        Notification notice = only();
        assertTrue(notice.getContent(), notice.getContent().contains("still reports legacy files"));
        assertFalse(notice.getContent().contains("complete —"));
        assertTrue(notice.getActions().isEmpty());
    }

    public void testAPlanWithFailuresStillOffersUpdateInsteadOfACleanState() {
        answer(Map.of(DRY, plan117("read-only-failure")));
        UpdateLegacyCleanupService.Verification v = cleanup.runVerification();
        assertTrue(v.verified());
        assertTrue("failures are update work the preview shows", v.updateRemains());
        cleanup.reportVerification(List.of("a.md"), v);
        assertFalse(only().getContent().contains("clean state"));
    }

    public void testOnlyOpenSpecsOwnCleanupCountsAsLegacyFiles() {
        var parse = (java.util.function.Function<String, com.johnnyblabs.openspec.util.UpdatePlanParser.Plan>) name ->
                (com.johnnyblabs.openspec.util.UpdatePlanParser.Plan)
                        com.johnnyblabs.openspec.util.UpdatePlanParser.parse(plan117(name));
        assertEquals("the sweep's and the tool move's deletes are not legacy files", List.of(),
                UpdatePreviewService.legacyDeletes(parse.apply("earlier-release")));
        assertEquals(List.of(".claude/commands/openspec/apply.md", ".claude/commands/openspec/archive.md",
                        ".claude/commands/openspec/proposal.md", "openspec/AGENTS.md"),
                UpdatePreviewService.legacyDeletes(parse.apply("legacy-one-edited-force")).stream().sorted().toList());
    }

    public void testAnUnverifiedReadOnlyCheckIsNeverReportedClean() {
        answer(Map.of(DRY, plan117("no-openspec-dir")));
        cleanup.reportVerification(List.of("a.md"), cleanup.runVerification());
        Notification notice = only();
        assertTrue(notice.getContent(), notice.getContent().contains("couldn't be verified"));
        assertFalse(notice.getContent().contains("clean state"));
        assertFalse(notice.getContent().contains("complete"));
    }
}
