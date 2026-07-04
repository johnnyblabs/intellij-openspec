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
}
