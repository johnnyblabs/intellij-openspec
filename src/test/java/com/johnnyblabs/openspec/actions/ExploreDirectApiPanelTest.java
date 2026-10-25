package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.services.ExploreContextService;
import com.johnnyblabs.openspec.services.ExplorePromptService;
import com.johnnyblabs.openspec.util.CliRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.johnnyblabs.openspec.services.ExplorePromptService.Instructions;
import com.johnnyblabs.openspec.services.ExplorePromptService.Prompt;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.toolwindow.ExplorePanel;
import com.johnnyblabs.openspec.toolwindow.ExplorePanelService;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives the real Explore panel's Send button through {@link ExploreContextAction}'s Direct API path. Every way a
 * request can end must leave the input enabled.
 */
public class ExploreDirectApiPanelTest extends BasePlatformTestCase {

    private static final String TOOL_WINDOW_ID = "OpenSpec";
    private static final String TOPIC = "why caching";
    private static final String SENTINEL = "clipboard-sentinel";
    private static final Supplier<Prompt> BUILT = () -> new Prompt("prompt", new Instructions("instructions", null));

    private final List<Notification> raised = new ArrayList<>();
    private ExplorePanelService panelService;
    private ExplorePanel panel;
    private String savedProvider;
    private String savedPreferredTool;
    private WorkflowProfileService profile;

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
        // A light test has no OpenSpec tool window, and getAndActivate() finds the panel through it.
        ToolWindowManager.getInstance(getProject()).registerToolWindow(RegisterToolWindowTask.notClosable(TOOL_WINDOW_ID));
        savedProvider = OpenSpecSettings.getInstance(getProject()).getAiProvider();
        savedPreferredTool = OpenSpecSettings.getInstance(getProject()).getPreferredTool();
        // The real profile service asks the machine's CLI; the tests decide whether explore is enabled.
        profile = mock(WorkflowProfileService.class);
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());
        CopyPasteManager.getInstance().setContents(new StringSelection(SENTINEL));

        panelService = getProject().getService(ExplorePanelService.class);
        panel = new ExplorePanel(getProject());
        panelService.register(panel);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ExploreContextAction.setRawRequestForTest(null);
            ExploreContextAction.setTaskRunnerForTest(null);
            panelService.register(null);
            panel.dispose();
            OpenSpecSettings.getInstance(getProject()).setAiProvider(savedProvider);
            OpenSpecSettings.getInstance(getProject()).setPreferredTool(savedPreferredTool);
            deleteUnderBase(".cursor");
            ToolWindowManager.getInstance(getProject()).unregisterToolWindow(TOOL_WINDOW_ID);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testUnconfiguredProviderShowsTheNoticeInThePanelAndGivesTheInputBack() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setAiProvider("none");

        send();

        assertTrue("the input is usable again", panel.isInputEnabledForTest());
        String response = panel.responseTextForTest();
        assertTrue(response, response.contains("No Direct API provider is configured"));
        assertTrue(response, response.contains("copied to the"));
        assertTrue(response, response.contains("Settings"));
        assertEquals("the prompt was copied", expectedPrompt(), clipboard());
        assertTrue("no balloon while the panel says it: " + contents(), raised.isEmpty());
    }

    public void testUnconfiguredProviderWithoutAPanelShowsOneBalloon() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setAiProvider("none");
        panelService.register(null);

        ExploreContextAction.runExploreDirect(getProject(), TOPIC);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(contents().toString(), 1, raised.size());
        assertTrue(raised.get(0).getContent(), raised.get(0).getContent().contains("No Direct API provider is configured"));
        assertEquals(expectedPrompt(), clipboard());
    }

    public void testAnUnexpectedFailureIsShownAndGivesTheInputBack() {
        ExploreContextAction.setRawRequestForTest(request(prompt -> {
            throw new IllegalStateException("boom");
        }));

        send();

        assertTrue(panel.isInputEnabledForTest());
        String response = panel.responseTextForTest();
        assertTrue(response, response.contains("Explore failed"));
        assertTrue(response, response.contains("boom"));
    }

    public void testACancelledRequestSaysSoAndGivesTheInputBack() {
        ExploreContextAction.setRawRequestForTest(request(prompt -> {
            throw new ProcessCanceledException();
        }));

        send();

        assertTrue(panel.isInputEnabledForTest());
        String response = panel.responseTextForTest();
        assertTrue(response, response.contains(ExploreContextAction.CANCELLED_NOTICE));
        assertFalse(response, response.contains("Explore failed"));
    }

    public void testAProviderErrorIsShownAndGivesTheInputBack() {
        ExploreContextAction.setRawRequestForTest(request(prompt -> {
            throw new AiApiException("rate limited");
        }));

        send();

        assertTrue(panel.isInputEnabledForTest());
        assertTrue(panel.responseTextForTest(), panel.responseTextForTest().contains("rate limited"));
        assertEquals(contents().toString(), 1, raised.size());
        assertTrue(raised.get(0).getContent(), raised.get(0).getContent().startsWith("API error:"));
    }

    public void testASuccessfulResponseIsShown() {
        ExploreContextAction.setRawRequestForTest(request(prompt -> "**hello there**"));

        send();

        assertTrue(panel.isInputEnabledForTest());
        assertTrue(panel.responseTextForTest(), panel.responseTextForTest().contains("hello there"));
        assertTrue(contents().toString(), raised.isEmpty());
    }

    public void testDirectApiSendsThePreambleAndNamesTheInstructionsFile() throws Exception {
        writeUnderBase(".cursor/commands/opsx-explore.md", "cursor-command.md");
        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Cursor");
        AtomicReference<String> sent = new AtomicReference<>();
        ExploreContextAction.setRawRequestForTest(request(prompt -> {
            sent.set(prompt);
            return "ok";
        }));

        send();

        assertTrue(sent.get().substring(0, 60), sent.get().startsWith(ExplorePromptService.DIRECT_API_PREAMBLE));
        assertTrue("the Cursor command's own handoff was sent", sent.get().contains("`/opsx-propose`"));
        assertEquals("Instructions: .cursor/commands/opsx-explore.md", panel.sourceTextForTest());
    }

    public void testTheBuiltInDefaultIsNamedWithHowToGetTheGeneratedInstructions() {
        ExploreContextAction.setRawRequestForTest(request(prompt -> "ok"));

        send();

        String source = panel.sourceTextForTest();
        assertTrue(source, source.contains("built-in default"));
        assertTrue(source, source.contains("Update OpenSpec"));
    }

    public void testTheBuiltInDefaultIsNamedOnTheNoProviderNoticeToo() {
        OpenSpecSettings.getInstance(getProject()).setAiProvider("none");

        send();

        assertTrue(panel.sourceTextForTest(), panel.sourceTextForTest().contains("built-in default"));
    }

    public void testSendDoesNothingButSaySoWhenExploreIsNotInTheProfile() throws Exception {
        when(profile.isWorkflowEnabled("explore")).thenReturn(false);
        AtomicInteger requests = new AtomicInteger();
        ExploreContextAction.setRawRequestForTest(request(prompt -> {
            requests.incrementAndGet();
            return "ok";
        }));

        send();

        assertEquals(0, requests.get());
        assertTrue(panel.responseTextForTest(), panel.responseTextForTest().contains("Explore isn't in your workflow profile"));
        assertTrue(panel.isInputEnabledForTest());
        assertEquals("nothing was copied", SENTINEL, clipboard());
        assertTrue(contents().toString(), raised.isEmpty());
    }

    public void testSendGoesThroughWhenExploreIsInTheProfile() {
        AtomicInteger requests = new AtomicInteger();
        ExploreContextAction.setRawRequestForTest(request(prompt -> {
            requests.incrementAndGet();
            return "ok";
        }));

        send();

        assertEquals(1, requests.get());
    }

    public void testACancelInsideTheRequestPropagatesFromRun() {
        Task.Backgroundable task = ExploreContextAction.newExploreTask(getProject(), request(prompt -> {
            throw new ProcessCanceledException();
        }), panelService, BUILT, TOPIC);

        assertThrows(ProcessCanceledException.class, () -> task.run(new EmptyProgressIndicator()));
    }

    public void testACancelDuringTheCallWinsOverALateResponse() {
        Task.Backgroundable task = ExploreContextAction.newExploreTask(getProject(),
                request(prompt -> "late response"), panelService, BUILT, TOPIC);
        ProgressIndicator indicator = new EmptyProgressIndicator();
        indicator.cancel();

        assertThrows(ProcessCanceledException.class, () -> task.run(indicator));
    }

    public void testSendStartsOneTaskThatBuildsAndSends() {
        AtomicInteger requests = new AtomicInteger();
        ExploreContextAction.setRawRequestForTest(request(prompt -> {
            requests.incrementAndGet();
            return "ok";
        }));
        List<Task.Backgroundable> started = new ArrayList<>();
        ExploreContextAction.setTaskRunnerForTest(started::add);

        send();

        assertEquals("the prompt is built inside the request task, not on the EDT", 1, started.size());
        assertEquals(0, requests.get());
        assertFalse("Thinking... covers the build", panel.isInputEnabledForTest());
        started.get(0).run(new EmptyProgressIndicator());
        assertEquals(1, requests.get());
    }

    public void testTheNoProviderFallbackBuildsInABackgroundTask() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setAiProvider("none");
        List<Task.Backgroundable> started = new ArrayList<>();
        ExploreContextAction.setTaskRunnerForTest(started::add);

        send();

        assertEquals(1, started.size());
        assertEquals("nothing is copied before the task succeeds", SENTINEL, clipboard());
        Task.Backgroundable task = started.get(0);
        task.run(new EmptyProgressIndicator());
        task.onSuccess();
        task.onFinished();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals(expectedPrompt(), clipboard());
        assertTrue("the input is usable again", panel.isInputEnabledForTest());
    }

    public void testAMissingPromptServiceGivesTheInputBack() {
        panel.showLoading(TOPIC);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertFalse(panel.isInputEnabledForTest());

        ExploreContextAction.runExploreDirect(getProject(), null, TOPIC);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertTrue("the input is usable again", panel.isInputEnabledForTest());
    }

    public void testThePromptIsBuiltOffTheEdtAndDeliveredOnlyOnSuccess() throws Exception {
        // A spec makes context assembly ask the CLI for the capability list.
        Path spec = Path.of(getProject().getBasePath(), "openspec/specs/billing/spec.md");
        Files.createDirectories(spec.getParent());
        Files.writeString(spec, "# billing\n");
        List<Boolean> onEdt = new java.util.concurrent.CopyOnWriteArrayList<>();
        ExploreContextService context = getProject().getService(ExploreContextService.class);
        context.setCliForTest(new ExploreContextService.Cli() {
            @Override
            public String version() {
                return "1.15.0";
            }

            @Override
            public CliRunner.CliResult run(String... args) {
                onEdt.add(ApplicationManager.getApplication().isDispatchThread());
                return new CliRunner.CliResult(1, "", "");
            }
        });
        try {
            AtomicReference<Prompt> delivered = new AtomicReference<>();
            Task.Backgroundable task = ExploreContextAction.newBuildTask(getProject(),
                    getProject().getService(ExplorePromptService.class), panelService, TOPIC,
                    ExplorePromptService.Delivery.AGENT, delivered::set);

            PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(
                    () -> task.run(new EmptyProgressIndicator())), 60_000);

            assertEquals("the CLI ran, and only off the EDT", List.of(false), onEdt);
            assertNull("nothing is delivered from the background thread", delivered.get());
            task.onSuccess();
            assertNotNull(delivered.get());
            assertTrue(delivered.get().text(), delivered.get().text().contains(TOPIC));
        } finally {
            context.setCliForTest(null);
            deleteUnderBase("openspec");
        }
    }

    public void testABuildFailureInTheDirectApiTaskIsShownAndGivesTheInputBack() {
        AtomicInteger requests = new AtomicInteger();
        Task.Backgroundable task = ExploreContextAction.newExploreTask(getProject(), request(prompt -> {
            requests.incrementAndGet();
            return "ok";
        }), panelService, () -> {
            throw new IllegalStateException("context unavailable");
        }, TOPIC);
        panel.showLoading(TOPIC);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        task.run(new EmptyProgressIndicator());
        task.onFinished();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("nothing is sent without a prompt", 0, requests.get());
        assertTrue(panel.responseTextForTest(), panel.responseTextForTest().contains("context unavailable"));
        assertTrue("the input is usable again", panel.isInputEnabledForTest());
    }

    public void testCancellingThePromptBuildSaysSoAndGivesTheInputBack() {
        AtomicReference<Prompt> delivered = new AtomicReference<>();
        Task.Backgroundable task = ExploreContextAction.newBuildTask(getProject(),
                getProject().getService(ExplorePromptService.class), panelService, TOPIC,
                ExplorePromptService.Delivery.AGENT, delivered::set);
        panel.showLoading(TOPIC);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertFalse(panel.isInputEnabledForTest());

        task.onCancel();
        task.onFinished();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertNull("a cancelled build delivers nothing", delivered.get());
        assertTrue(panel.responseTextForTest(), panel.responseTextForTest().contains(ExploreContextAction.CANCELLED_NOTICE));
        assertTrue("the input is usable again", panel.isInputEnabledForTest());
    }

    public void testAPromptBuildFailureIsShownInThePanelAndGivesTheInputBack() {
        Task.Backgroundable task = ExploreContextAction.newBuildTask(getProject(),
                getProject().getService(ExplorePromptService.class), panelService, TOPIC,
                ExplorePromptService.Delivery.AGENT, prompt -> fail("nothing to deliver"));
        panel.showLoading(TOPIC);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        task.onThrowable(new IllegalStateException("walk failed"));
        task.onFinished();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertTrue(panel.responseTextForTest(), panel.responseTextForTest().contains("walk failed"));
        assertTrue("the input is usable again", panel.isInputEnabledForTest());
    }

    private void writeUnderBase(String relative, String fixture) throws Exception {
        Path target = Path.of(getProject().getBasePath(), relative);
        Files.createDirectories(target.getParent());
        Files.copy(Path.of("src/test/resources/fixtures/cli/1.15.0/explore", fixture), target);
    }

    /** The light project is shared between tests, so a tool directory one test creates would change the next. */
    private void deleteUnderBase(String relative) throws Exception {
        Path root = Path.of(getProject().getBasePath(), relative);
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
        // Services that read through the VFS (ChangeService, ConfigService) cached what was there; a later test in
        // the same light project must not see the deleted files.
        com.intellij.openapi.vfs.VirtualFile base = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(getProject().getBasePath());
        if (base != null) com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, base);
    }

    private void send() {
        panel.setInputTextForTest(TOPIC);
        panel.sendButtonForTest().doClick();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private String expectedPrompt() {
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

    private interface Send {
        String send(String prompt) throws AiApiException;
    }

    private static ExploreContextAction.RawRequest request(Send send) {
        return new ExploreContextAction.RawRequest() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public String send(String prompt) throws AiApiException {
                return send.send(prompt);
            }
        };
    }
}
