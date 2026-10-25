package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.ExplorePromptService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.toolwindow.ExplorePanel;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Explore delivered to an agent, by clipboard or editor tab, through {@link ExploreContextAction#runExplore}: the agent
 * prompt (no Direct API preamble), a balloon that names the instructions' source, and no file written to disk.
 */
public class ExploreAgentDeliveryTest extends BasePlatformTestCase {

    private static final String TOPIC = "why caching";
    private static final Path OLD_TEMP_FILE = Path.of(System.getProperty("java.io.tmpdir"), "openspec-explore-prompt.md");

    private final List<Notification> raised = new ArrayList<>();
    private String savedDelivery;
    private String savedPreferredTool;

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
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        savedDelivery = settings.getPreferredDeliveryMethod();
        savedPreferredTool = settings.getPreferredTool();
        CopyPasteManager.getInstance().setContents(new StringSelection("clipboard-sentinel"));
        // The previous implementation left this behind; a stale copy must not make the no-write check pass or fail.
        Files.deleteIfExists(OLD_TEMP_FILE);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ExploreContextAction.setTaskRunnerForTest(null);
            FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
            OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
            settings.setPreferredDeliveryMethod(savedDelivery);
            settings.setPreferredTool(savedPreferredTool);
            deleteUnderBase(".cursor");
            deleteUnderBase(".claude");
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testClipboardBuildsThePromptInABackgroundTaskAndCopiesOnlyOnSuccess() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("CLIPBOARD");
        List<Task.Backgroundable> started = new ArrayList<>();
        ExploreContextAction.setTaskRunnerForTest(started::add);

        ExploreContextAction.runExplore(getProject(), TOPIC);

        assertEquals("the prompt is built in a background task, not on the EDT", 1, started.size());
        assertEquals("nothing is copied before the task runs", "clipboard-sentinel", clipboard());
        started.get(0).run(new EmptyProgressIndicator());
        assertEquals("nothing is copied from the background thread", "clipboard-sentinel", clipboard());
        started.get(0).onSuccess();
        assertEquals(agentPrompt(), clipboard());
    }

    public void testEditorTabBuildsThePromptInABackgroundTaskAndOpensOnlyOnSuccess() {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("EDITOR_TAB");
        List<Task.Backgroundable> started = new ArrayList<>();
        ExploreContextAction.setTaskRunnerForTest(started::add);

        ExploreContextAction.runExplore(getProject(), TOPIC);
        started.get(0).run(new EmptyProgressIndicator());
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(1, started.size());
        assertEquals("no tab before the task succeeds", 0, FileEditorManager.getInstance(getProject()).getOpenFiles().length);
        started.get(0).onSuccess();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals(1, FileEditorManager.getInstance(getProject()).getOpenFiles().length);
    }

    public void testClipboardCopiesTheAgentPromptAndSaysTheDefaultWasUsed() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("CLIPBOARD");

        ExploreContextAction.runExplore(getProject(), TOPIC);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(agentPrompt(), clipboard());
        assertFalse(clipboard().contains(ExplorePromptService.DIRECT_API_PREAMBLE));
        assertEquals(contents().toString(), 1, raised.size());
        assertTrue(raised.get(0).getContent(), raised.get(0).getContent().contains(ExplorePanel.BUILT_IN_DEFAULT_NOTE));
    }

    public void testClipboardNamesTheGeneratedFileItUsed() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("CLIPBOARD");
        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Cursor");
        Path target = Path.of(getProject().getBasePath(), ".cursor/commands/opsx-explore.md");
        Files.createDirectories(target.getParent());
        Files.copy(Path.of("src/test/resources/fixtures/cli/1.15.0/explore/cursor-command.md"), target);
        // Claude comes before Cursor in the registry and is detected, so only the Settings preference picks Cursor.
        Path claude = Path.of(getProject().getBasePath(), ".claude/skills/openspec-explore/SKILL.md");
        Files.createDirectories(claude.getParent());
        Files.copy(Path.of("src/test/resources/fixtures/cli/1.15.0/explore/claude-skill-both.md"), claude);

        ExploreContextAction.runExplore(getProject(), TOPIC);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertTrue("the Cursor command's handoff was copied", clipboard().contains("`/opsx-propose`"));
        assertFalse("not Claude's skill", clipboard().contains("`/opsx:propose`"));
        String balloon = raised.get(0).getContent();
        assertTrue(balloon, balloon.contains(".cursor/commands/opsx-explore.md"));
        assertFalse(balloon, balloon.contains("built-in default"));
    }

    public void testEditorTabOpensAnInMemoryFileWithTheAgentPrompt() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("EDITOR_TAB");

        ExploreContextAction.runExplore(getProject(), TOPIC);
        PlatformTestUtil.waitWithEventsDispatching("the prompt was not opened",
                () -> FileEditorManager.getInstance(getProject()).getOpenFiles().length > 0, 10);

        VirtualFile[] open = FileEditorManager.getInstance(getProject()).getOpenFiles();
        assertEquals(1, open.length);
        assertInstanceOf(open[0], LightVirtualFile.class);
        assertEquals(ExploreContextAction.EDITOR_TAB_FILE_NAME, open[0].getName());
        assertFalse(open[0].isInLocalFileSystem());
        assertEquals(agentPrompt(), ((LightVirtualFile) open[0]).getContent().toString());
        assertFalse("nothing is written to the temp directory", Files.exists(OLD_TEMP_FILE));
        assertTrue(contents().toString(), contents().stream().anyMatch(c -> c.contains(ExplorePanel.BUILT_IN_DEFAULT_NOTE)));
    }

    private String agentPrompt() {
        return getProject().getService(ExplorePromptService.class).buildPrompt(TOPIC);
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
