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
        String notice = ManageAiToolsDialog.toolSetUpNotice("Claude Code", true, false, null);

        assertTrue(notice, notice.startsWith("Claude Code is set up: OpenSpec wrote its /opsx commands to this project."));
        assertTrue(ManageAiToolsDialog.toolSetUpNotice("Claude Code", true, true, null)
                .startsWith("Claude Code is set up: OpenSpec wrote its skills to this project."));
        assertTrue(notice, notice.contains("\"Copy for Claude Code\""));
        assertFalse("with Direct API configured, no need to point at it", notice.contains("Direct API"));
    }

    public void testWithoutDirectApiItPointsToGeneratingInTheIde() {
        ManageAiToolsDialog.notifySucceeded(getProject(), "Adding Claude Code", "Claude Code", false, true, null);

        assertTrue(last().getContent(), last().getContent().endsWith("To generate inside the IDE instead, set up Direct API."));
        assertEquals(List.of(ManageAiToolsDialog.SET_UP_DIRECT_API), actionTexts(last()));
    }

    public void testWithDirectApiThereIsNoSetUpAction() {
        ManageAiToolsDialog.notifySucceeded(getProject(), "Configuring Cursor", "Cursor", true, true, null);

        assertEquals(ManageAiToolsDialog.toolSetUpNotice("Cursor", true, true, null), last().getContent());
        assertTrue(actionTexts(last()).isEmpty());
    }

    public void testUpdateStillJustSaysDone() {
        ManageAiToolsDialog.notifySucceeded(getProject(), "Updating Claude Code", null, false, true, null);

        assertEquals("Updating Claude Code — done", last().getContent());
        assertTrue(actionTexts(last()).isEmpty());
    }

    /** Add and Configure set a tool up, so they pass its name; Update covers every tool, so it doesn't. */
    public void testAddAndConfigurePassTheToolSoTheyGetTheSetUpNotice() throws Exception {
        String source = java.nio.file.Files.readString(ModalityCallScanner.mainSource("dialogs/ManageAiToolsDialog.java"));

        assertEquals("Adding and Configuring both set up the tool", 2, count(source,
                "runCliAction(() -> addArgs(project, tool.cliId()),"));
        assertEquals(2, count(source, " + tool.name(), tool.name(), btn));"));
        assertTrue(source.contains("runCliAction(com.johnnyblabs.openspec.util.CliCommands.update(), \"Updating \" + tool.name(), null, sourceButton);"));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    public void testTheSubtitleSaysTheseAreAgentsNotInIdeGeneration() {
        String v1 = ManageAiToolsDialog.subtitle(false);
        String v2 = ManageAiToolsDialog.subtitle(true);
        assertTrue(v1, v1.startsWith("AI coding agents that run OpenSpec's /opsx commands."));
        assertTrue(v2, v2.startsWith("AI coding agents that run OpenSpec's skills."));
        assertTrue(v2, v2.contains("set up Direct API in Settings"));
    }

    /** The CLI line: the detected version, or with none, the project's layout (2.x when it has a multi-tool list). */
    public void testTheLineComesFromTheCliOrTheLayout() throws Exception {
        assertFalse(ManageAiToolsDialog.cli2x("1.17.0", null));
        assertTrue(ManageAiToolsDialog.cli2x("2.0.0", null));
        assertTrue(ManageAiToolsDialog.cli2x("2.1.1", null));
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("line");
        try {
            assertFalse("an empty project reads as 1.x", ManageAiToolsDialog.cli2x(null, dir));
            java.nio.file.Files.createDirectories(dir.resolve(".agents/skills"));
            java.nio.file.Files.writeString(dir.resolve(".agents/skills/.openspec-target"), "claude\ncodex\n");
            assertTrue("a multi-tool list reads as 2.x", ManageAiToolsDialog.cli2x(null, dir));
        } finally {
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /** The CLI's setup note is passed on as it prints it: Hermes on 1.x and 2.x, GSD on 2.1, none after a machine add. */
    public void testTheCliSetupNoteIsPassedOn() {
        var hermes = com.johnnyblabs.openspec.services.AiToolRegistry.byId("hermes");
        var gsd = com.johnnyblabs.openspec.services.AiToolRegistry.byId("gsd");
        var cursor = com.johnnyblabs.openspec.services.AiToolRegistry.byId("cursor");
        var inProject = com.johnnyblabs.openspec.util.CliCommands.initInProject("hermes");
        var keeping = com.johnnyblabs.openspec.util.CliCommands.initKeepingScope("hermes");

        String on211 = com.johnnyblabs.openspec.services.AiToolRegistry.setupNoteAfter(hermes, "2.1.1", inProject);
        assertNotNull(on211);
        assertTrue(on211, on211.contains("hermes skills trust"));
        assertTrue(com.johnnyblabs.openspec.services.AiToolRegistry.setupNoteAfter(hermes, "1.17.0", inProject).contains("skills.external_dirs"));
        assertNull("not for a non-universal tool kept on the machine", com.johnnyblabs.openspec.services.AiToolRegistry.setupNoteAfter(hermes, "2.1.1", keeping));
        assertNotNull("a universal-folder tool keeps it on the machine too",
                com.johnnyblabs.openspec.services.AiToolRegistry.setupNoteAfter(gsd, "2.1.1", com.johnnyblabs.openspec.util.CliCommands.initKeepingScope("gsd")));
        assertNull(com.johnnyblabs.openspec.services.AiToolRegistry.setupNoteAfter(gsd, "2.0.0", inProject));
        assertNull(com.johnnyblabs.openspec.services.AiToolRegistry.setupNoteAfter(cursor, "2.1.1", inProject));

        String notice = ManageAiToolsDialog.toolSetUpNotice("Hermes Agent", true, true, on211);
        assertTrue(notice, notice.contains("Setup required for Hermes Agent: " + on211));
    }
}
