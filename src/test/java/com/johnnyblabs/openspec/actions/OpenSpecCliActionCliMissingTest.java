package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.util.CliInstallHints;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Running a CLI action with no CLI warns with the shared install hint and both actions. */
public class OpenSpecCliActionCliMissingTest extends BasePlatformTestCase {

    public void testTheWarningNamesTheCommandAndOffersInstructionsThenSettings() {
        CliDetectionService detection = new CliDetectionService(getProject());
        CliDetectionTestSupport.fakeUnavailable(detection);
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        List<Notification> raised = new ArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable())
                .subscribe(Notifications.TOPIC, new Notifications() {
                    @Override
                    public void notify(@NotNull Notification notification) {
                        raised.add(notification);
                    }
                });
        OpenSpecCliAction action = new OpenSpecCliAction() {
            @Override
            protected String[] getCliArgs() {
                return new String[]{"foo"};
            }

            @Override
            protected String getCommandLabel() {
                return "Foo";
            }
        };
        var context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject()).build();

        action.actionPerformed(TestActionEvent.createTestEvent(action, context));

        assertEquals(1, raised.size());
        Notification n = raised.get(0);
        assertTrue(n.getContent(), n.getContent().contains("Cannot run 'Foo'"));
        assertTrue(n.getContent(), n.getContent().contains("no Node.js needed"));
        assertFalse(n.getContent(), n.getContent().contains("@fission-ai/openspec"));
        List<String> titles = new ArrayList<>();
        n.getActions().forEach(a -> titles.add(a.getTemplatePresentation().getText()));
        assertEquals(List.of(CliInstallHints.INSTALL_DOCS_LABEL, "Open Settings"), titles);
    }
}
