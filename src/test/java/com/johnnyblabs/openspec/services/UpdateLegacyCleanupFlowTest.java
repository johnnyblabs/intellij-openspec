package com.johnnyblabs.openspec.services;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Flow tests for the update legacy-cleanup outcome: a pending migration block raises the
 * actionable review notification instead of bare success; clean output changes nothing;
 * dismissal and regeneration memory suppress by file-set identity and re-offer on change.
 */
public class UpdateLegacyCleanupFlowTest extends OpenSpecIntegrationTestBase {

    private final List<Notification> raised = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        raised.clear();
        getProject().getMessageBus().connect(getTestRootDisposable())
                .subscribe(Notifications.TOPIC, new Notifications() {
                    @Override
                    public void notify(@NotNull Notification notification) {
                        raised.add(notification);
                    }
                });
        PropertiesComponent state = PropertiesComponent.getInstance(getProject());
        state.unsetValue(UpdateLegacyCleanupService.DISMISSED_SET_KEY);
        state.unsetValue(UpdateLegacyCleanupService.REGENERATING_SET_KEY);
    }

    private static String loadFixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = UpdateLegacyCleanupFlowTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private UpdateLegacyCleanupService service() {
        return getProject().getService(UpdateLegacyCleanupService.class);
    }

    private boolean cleanupNoticeRaised() {
        return raised.stream().anyMatch(n -> n.getContent().contains("legacy file(s)"));
    }

    public void testPendingOutputRaisesActionableNotice() {
        service().handleUpdateResult(loadFixture("update-legacy-pending.txt"));
        assertTrue("Pending migration block must raise the review notice", cleanupNoticeRaised());
        Notification notice = raised.stream().filter(n -> n.getContent().contains("legacy file(s)"))
                .findFirst().orElseThrow();
        assertFalse("Notice must be actionable", notice.getActions().isEmpty());
        assertTrue("Notice must state the never-force guarantee",
                notice.getContent().contains("never runs --force"));
    }

    public void testCleanOutputRaisesNothing() {
        service().handleUpdateResult(loadFixture("update-clean.txt"));
        assertFalse("Clean output must not raise the cleanup notice", cleanupNoticeRaised());
    }

    public void testPending16OutputRaisesActionableNotice() {
        // 1.6-generation capture: same block, new "Migrated: custom profile" preamble.
        service().handleUpdateResult(loadFixture("1.6.0/update-legacy-pending.txt"));
        assertTrue("1.6 pending migration block must raise the review notice", cleanupNoticeRaised());
    }

    public void testClean16OutputRaisesNothing() {
        service().handleUpdateResult(loadFixture("1.6.0/update-clean.txt"));
        assertFalse("1.6 clean output (Migrated: preamble, no block) must not raise the notice",
                cleanupNoticeRaised());
    }

    public void testDismissedSetIsSuppressed() {
        String fixture = loadFixture("update-legacy-pending.txt");
        List<String> pending = com.johnnyblabs.openspec.util.UpdateOutputParser.parseLegacyCleanup(fixture);
        PropertiesComponent.getInstance(getProject())
                .setValue(UpdateLegacyCleanupService.DISMISSED_SET_KEY, UpdateLegacyCleanupService.setKey(pending));

        service().handleUpdateResult(fixture);

        assertFalse("Dismissed unchanged set must stay quiet", cleanupNoticeRaised());
    }

    public void testChangedSetReopensAfterDismissal() {
        List<String> fourFiles = com.johnnyblabs.openspec.util.UpdateOutputParser
                .parseLegacyCleanup(loadFixture("update-legacy-pending.txt"));
        PropertiesComponent.getInstance(getProject())
                .setValue(UpdateLegacyCleanupService.DISMISSED_SET_KEY, UpdateLegacyCleanupService.setKey(fourFiles));

        // The regenerated fixture reports five files — a different set re-opens the flow.
        service().handleUpdateResult(loadFixture("update-legacy-pending-regenerated.txt"));

        assertTrue("A changed pending set must re-raise the notice", cleanupNoticeRaised());
    }

    public void testRegeneratingSetIsSuppressed() {
        String fixture = loadFixture("update-legacy-pending-regenerated.txt");
        List<String> pending = com.johnnyblabs.openspec.util.UpdateOutputParser.parseLegacyCleanup(fixture);
        PropertiesComponent.getInstance(getProject())
                .setValue(UpdateLegacyCleanupService.REGENERATING_SET_KEY, UpdateLegacyCleanupService.setKey(pending));

        service().handleUpdateResult(fixture);

        assertFalse("A set recorded as regenerating must never re-nag", cleanupNoticeRaised());
    }

    public void test1_14RewordedBlockRaisesTheNoticeWithoutClaimingSafety() {
        // CLI 1.14.0 reworded the guidance line; before this fix the notice never appeared on 1.14.x.
        service().handleUpdateResult(loadFixture("1.14.3/update-legacy-pending.txt"));
        assertTrue("1.14 cleanup block must raise the review notice", cleanupNoticeRaised());
        Notification notice = raised.stream().filter(n -> n.getContent().contains("legacy file(s)"))
                .findFirst().orElseThrow();
        assertTrue(notice.getContent(), notice.getContent().contains("listed 3 legacy file(s)"));
        assertFalse("the CLI no longer says the files are safe to remove",
                notice.getContent().contains("safe to remove"));
        assertTrue(notice.getContent().contains("never runs --force"));
    }

    private void verifyWith(com.johnnyblabs.openspec.util.CliRunner.CliResult rerun) throws Exception {
        UpdateLegacyCleanupService service = service();
        service.setUpdateCliForTest((p, args) -> rerun);
        try {
            service.verifyCleanup(List.of(".cursor/commands/openspec-apply.md"));
            long deadline = System.currentTimeMillis() + 10_000;
            while (raised.isEmpty() && System.currentTimeMillis() < deadline) {
                com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(20);
            }
        } finally {
            service.setUpdateCliForTest(null);
        }
    }

    public void testFailedVerificationIsNotReportedClean() throws Exception {
        // Real 1.14.3 `update` failure (exit 1, empty stdout) as the re-run: used to say "a clean state".
        verifyWith(com.johnnyblabs.openspec.util.CliFixtures.error("update-fail"));
        assertFalse("must not claim a clean state", raised.stream().anyMatch(n -> n.getContent().contains("clean state")));
        assertTrue("must say the cleanup couldn't be verified",
                raised.stream().anyMatch(n -> n.getContent().contains("couldn't be verified")));
    }

    public void testSuccessfulCleanVerificationStillReportsClean() throws Exception {
        verifyWith(new com.johnnyblabs.openspec.util.CliRunner.CliResult(0, loadFixture("update-clean.txt"), ""));
        assertTrue(raised.stream().anyMatch(n -> n.getContent().contains("clean state")));
    }
}
