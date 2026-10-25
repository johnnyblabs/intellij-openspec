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
import com.johnnyblabs.openspec.services.ExplorePromptService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.toolwindow.ExplorePanel;
import com.johnnyblabs.openspec.toolwindow.ExplorePanelService;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the real Explore panel's Send button through {@link ExploreContextAction}'s Direct API path. Every way a
 * request can end must leave the input enabled.
 */
public class ExploreDirectApiPanelTest extends BasePlatformTestCase {

    private static final String TOOL_WINDOW_ID = "OpenSpec";
    private static final String TOPIC = "why caching";
    private static final String SENTINEL = "clipboard-sentinel";

    private final List<Notification> raised = new ArrayList<>();
    private ExplorePanelService panelService;
    private ExplorePanel panel;
    private String savedProvider;

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
        CopyPasteManager.getInstance().setContents(new StringSelection(SENTINEL));

        panelService = getProject().getService(ExplorePanelService.class);
        panel = new ExplorePanel(getProject());
        panelService.register(panel);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ExploreContextAction.setRawRequestForTest(null);
            panelService.register(null);
            panel.dispose();
            OpenSpecSettings.getInstance(getProject()).setAiProvider(savedProvider);
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

    public void testACancelInsideTheRequestPropagatesFromRun() {
        Task.Backgroundable task = ExploreContextAction.newExploreTask(getProject(), request(prompt -> {
            throw new ProcessCanceledException();
        }), panelService, "prompt", TOPIC);

        assertThrows(ProcessCanceledException.class, () -> task.run(new EmptyProgressIndicator()));
    }

    public void testACancelDuringTheCallWinsOverALateResponse() {
        Task.Backgroundable task = ExploreContextAction.newExploreTask(getProject(),
                request(prompt -> "late response"), panelService, "prompt", TOPIC);
        ProgressIndicator indicator = new EmptyProgressIndicator();
        indicator.cancel();

        assertThrows(ProcessCanceledException.class, () -> task.run(indicator));
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
