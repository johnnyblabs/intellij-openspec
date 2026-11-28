package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ask the Agent hands the request, as typed and unquoted, to the router skill spelled for the user's tool, in each
 * delivery mode; with no router installed it says how to get one; and it is offered only from CLI 2.2.0.
 */
public class OpenSpecAskAgentActionTest extends BasePlatformTestCase {

    private static final String REQUEST = "verify add-auth, then archive it";

    private final List<Notification> raised = new ArrayList<>();
    private String savedDelivery;
    private String savedPreferredTool;

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
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        savedDelivery = settings.getPreferredDeliveryMethod();
        savedPreferredTool = settings.getPreferredTool();
        CopyPasteManager.getInstance().setContents(new StringSelection("clipboard-sentinel"));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecAskAgentAction.setRequestForTest(null);
            FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
            OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
            settings.setPreferredDeliveryMethod(savedDelivery);
            settings.setPreferredTool(savedPreferredTool);
            deleteUnderBase(".claude");
            deleteUnderBase(".agents");
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private void installRouter(String skillsDir) throws Exception {
        Path skill = Path.of(getProject().getBasePath(), skillsDir, "skills", "openspec", "SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "Run /openspec.\n");
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(skill);
    }

    public void testTheRequestGoesToTheRouterUnquotedOnTheClipboard() throws Exception {
        installRouter(".claude");
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("CLIPBOARD");
        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Claude Code");

        OpenSpecAskAgentAction.ask(getProject(), REQUEST);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("/openspec " + REQUEST, clipboard());
        assertTrue(contents().toString(), contents().stream().anyMatch(c -> c.contains("Paste it into Claude Code")));
    }

    public void testCodexGetsItsOwnSpelling() throws Exception {
        installRouter(".agents");
        Files.createDirectories(Path.of(getProject().getBasePath(), ".codex"));
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("CLIPBOARD");
        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Codex");

        OpenSpecAskAgentAction.ask(getProject(), REQUEST);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("$openspec " + REQUEST, clipboard());
        deleteUnderBase(".codex");
    }

    public void testTheEditorTabHoldsTheHandoff() throws Exception {
        installRouter(".claude");
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("EDITOR_TAB");
        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Claude Code");

        OpenSpecAskAgentAction.ask(getProject(), REQUEST);
        PlatformTestUtil.waitWithEventsDispatching("the handoff was not opened",
                () -> FileEditorManager.getInstance(getProject()).getOpenFiles().length > 0, 10);

        VirtualFile[] open = FileEditorManager.getInstance(getProject()).getOpenFiles();
        assertEquals(OpenSpecAskAgentAction.EDITOR_TAB_FILE_NAME, open[0].getName());
        assertEquals("/openspec " + REQUEST, ((LightVirtualFile) open[0]).getContent().toString());
        assertEquals("nothing copied", "clipboard-sentinel", clipboard());
    }

    /** Direct API can't run a skill: the handoff is copied, with the note saying why; no model is called. */
    public void testDirectApiCopiesWithTheNote() throws Exception {
        installRouter(".claude");
        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Claude Code");
        WorkflowInvocation.Found found = WorkflowInvocation.find(getProject(), WorkflowInvocation.ROUTER,
                WorkflowInvocation.ROUTER_SKILL);
        assertNotNull(found);

        WorkflowHandoffDelivery.deliver(getProject(), OpenSpecAskAgentAction.TITLE, OpenSpecAskAgentAction.EDITOR_TAB_FILE_NAME,
                OpenSpecAskAgentAction.DIRECT_API_NOTE, found, OpenSpecAskAgentAction.handoff(found, REQUEST),
                DeliveryMode.DIRECT_API);

        assertEquals("/openspec " + REQUEST, clipboard());
        assertTrue(contents().stream().anyMatch(c -> c.endsWith(OpenSpecAskAgentAction.DIRECT_API_NOTE)));
    }

    public void testNoRouterSaysHowToGetOne() throws Exception {
        OpenSpecAskAgentAction.ask(getProject(), REQUEST);

        assertEquals("clipboard-sentinel", clipboard());
        assertTrue(raised.stream().anyMatch(n -> n.getType() == NotificationType.WARNING
                && n.getContent().equals(OpenSpecAskAgentAction.NOT_INSTALLED_NOTICE)));
        assertTrue(OpenSpecAskAgentAction.NOT_INSTALLED_NOTICE.contains("openspec update"));
    }

    public void testOfferedFromCli22OrWithNoCli() {
        assertFalse(OpenSpecAskAgentAction.shown("2.1.1"));
        assertFalse(OpenSpecAskAgentAction.shown("1.17.0"));
        assertTrue(OpenSpecAskAgentAction.shown("2.2.0"));
        assertTrue("a version, not text: 2.10 is after 2.2", OpenSpecAskAgentAction.shown("2.10.0"));
        assertTrue("no CLI detected: the lookup on click decides", OpenSpecAskAgentAction.shown(null));
    }

    public void testACancelledOrBlankRequestDoesNothing() throws Exception {
        installRouter(".claude");
        OpenSpecAskAgentAction.setRequestForTest(p -> "   ");
        com.intellij.openapi.actionSystem.AnActionEvent event = com.intellij.testFramework.TestActionEvent.createTestEvent(
                com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(getProject()));
        new OpenSpecAskAgentAction().actionPerformed(event);
        assertEquals("clipboard-sentinel", clipboard());
        assertTrue(raised.isEmpty());
    }

    private static String clipboard() throws Exception {
        return (String) CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    }

    private List<String> contents() {
        List<String> out = new ArrayList<>();
        for (Notification n : raised) out.add(n.getContent());
        return out;
    }

    private void deleteUnderBase(String relative) throws Exception {
        Path root = Path.of(getProject().getBasePath(), relative);
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
