package com.johnnyblabs.openspec.util;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

/** The real CLI-not-found notification, captured from the message bus: its text and its two actions, in order. */
public class CliMissingNotificationTest extends BasePlatformTestCase {

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
    }

    public void testCliMissingNamesTheRoutesAndOffersInstructionsThenSettings() {
        OpenSpecNotifier.cliMissing(getProject());

        assertEquals(1, raised.size());
        Notification n = raised.get(0);
        assertTrue(n.getContent(), n.getContent().contains("no Node.js needed"));
        assertFalse("the balloon names no npm command: " + n.getContent(), n.getContent().contains("@fission-ai/openspec"));
        assertEquals(List.of(CliInstallHints.INSTALL_DOCS_LABEL, "Open Settings"), actionTitles(n));
    }

    public void testTheInstructionsActionOpensUpstreamsInstallationPageAndExpires() {
        Notification n = new Notification(OpenSpecNotifier.GROUP_SYSTEM, "content", NotificationType.WARNING);
        var action = OpenSpecNotifier.installInstructionsAction();
        var context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject()).build();

        try (MockedStatic<BrowserUtil> browser = mockStatic(BrowserUtil.class)) {
            action.actionPerformed(TestActionEvent.createTestEvent(action, context), n);
            browser.verify(() -> BrowserUtil.browse(CliInstallHints.INSTALL_DOCS_URL));
        }
        assertTrue(n.isExpired());
    }

    static List<String> actionTitles(Notification n) {
        List<String> titles = new ArrayList<>();
        for (AnAction a : n.getActions()) {
            titles.add(a.getTemplatePresentation().getText());
        }
        return titles;
    }
}
