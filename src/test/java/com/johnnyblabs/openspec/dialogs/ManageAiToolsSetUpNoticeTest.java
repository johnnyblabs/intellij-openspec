package com.johnnyblabs.openspec.dialogs;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.ModalityCallScanner;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Setting up a tool in Manage AI Tools says what it did: OpenSpec wrote the agent's commands and prompts are copied for
 * it. Without Direct API it also points to generating inside the IDE, with a "Set up Direct API…" action.
 */
public class ManageAiToolsSetUpNoticeTest extends BasePlatformTestCase {

    private final List<Notification> raised = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        raised.clear();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
    }

    private Notification last() {
        assertFalse("a notification was raised", raised.isEmpty());
        return raised.get(raised.size() - 1);
    }

    private static List<String> actionTexts(Notification n) {
        return n.getActions().stream().map(a -> a.getTemplatePresentation().getText()).toList();
    }

    public void testTheNoticeSaysWhatSettingUpTheAgentDid() {
        String notice = ManageAiToolsDialog.toolSetUpNotice("Claude Code", true);

        assertTrue(notice, notice.startsWith("Claude Code is set up: OpenSpec wrote its /opsx commands to this project."));
        assertTrue(notice, notice.contains("\"Copy for Claude Code\""));
        assertFalse("with Direct API configured, no need to point at it", notice.contains("Direct API"));
    }

    public void testWithoutDirectApiItPointsToGeneratingInTheIde() {
        ManageAiToolsDialog.notifySucceeded(getProject(), "Adding Claude Code", "Claude Code", false);

        assertTrue(last().getContent(), last().getContent().endsWith("To generate inside the IDE instead, set up Direct API."));
        assertEquals(List.of(ManageAiToolsDialog.SET_UP_DIRECT_API), actionTexts(last()));
    }

    public void testWithDirectApiThereIsNoSetUpAction() {
        ManageAiToolsDialog.notifySucceeded(getProject(), "Configuring Cursor", "Cursor", true);

        assertEquals(ManageAiToolsDialog.toolSetUpNotice("Cursor", true), last().getContent());
        assertTrue(actionTexts(last()).isEmpty());
    }

    public void testUpdateStillJustSaysDone() {
        ManageAiToolsDialog.notifySucceeded(getProject(), "Updating Claude Code", null, false);

        assertEquals("Updating Claude Code — done", last().getContent());
        assertTrue(actionTexts(last()).isEmpty());
    }

    /** Add and Configure set a tool up, so they pass its name; Update covers every tool, so it doesn't. */
    public void testAddAndConfigurePassTheToolSoTheyGetTheSetUpNotice() throws Exception {
        String source = java.nio.file.Files.readString(ModalityCallScanner.mainSource("dialogs/ManageAiToolsDialog.java"));

        assertEquals("Adding and Configuring both set up the tool", 2, count(source,
                "\"init --tools \" + tool.cliId(),"));
        assertEquals(2, count(source, " + tool.name(), tool.name(), btn));"));
        assertTrue(source.contains("runCliAction(\"update\", \"Updating \" + tool.name(), null, sourceButton);"));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    public void testTheSubtitleSaysTheseAreAgentsNotInIdeGeneration() {
        assertTrue(ManageAiToolsDialog.SUBTITLE, ManageAiToolsDialog.SUBTITLE.startsWith("AI coding agents that run OpenSpec's /opsx commands."));
        assertTrue(ManageAiToolsDialog.SUBTITLE, ManageAiToolsDialog.SUBTITLE.contains("set up Direct API in Settings"));
    }
}
