package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Explore tab sits right after Browse, before Console and Coordination, however it's created. */
public class ExploreTabPositionTest extends OpenSpecIntegrationTestBase {

    private ContentManager contents;
    private String savedProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        contents = ContentFactory.getInstance().createContentManager(true, getProject());
        Disposer.register(getTestRootDisposable(), contents);
        savedProvider = OpenSpecSettings.getInstance(getProject()).getState().aiProvider;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecSettings.getInstance(getProject()).getState().aiProvider = savedProvider;
            AiCredentialStore.markHasApiKey(AiProvider.CLAUDE, false);
            contents.removeAllContents(true);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private List<String> tabs() {
        List<String> names = new ArrayList<>();
        for (Content c : contents.getContents()) names.add(c.getDisplayName());
        return names;
    }

    private Content tab(String name) {
        return ContentFactory.getInstance().createContent(new JPanel(), name, false);
    }

    public void testWithTheToolWindowTheTabsReadBrowseExploreConsole() {
        OpenSpecSettings.getInstance(getProject()).getState().aiProvider = "CLAUDE";
        AiCredentialStore.markHasApiKey(AiProvider.CLAUDE, true);
        ToolWindow toolWindow = mock(ToolWindow.class);
        when(toolWindow.getContentManager()).thenReturn(contents);
        when(toolWindow.getDisposable()).thenReturn(getTestRootDisposable());

        OpenSpecToolWindowFactory.createNormalContent(getProject(), toolWindow);
        OpenSpecToolWindowPanel browse = (OpenSpecToolWindowPanel) contents.findContent("Browse").getComponent();
        javax.swing.tree.TreeModel initial = browse.treeModelForTest();

        assertEquals(List.of("Browse", "Explore", "Console"), tabs().subList(0, 3));
        // Let the Browse panel finish loading, so nothing it schedules lands after teardown disposes it.
        long end = System.currentTimeMillis() + 10_000;
        while (browse.treeModelForTest() == initial && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    public void testCreatedLaterItGoesRightAfterBrowse() {
        for (String name : List.of("Browse", "Console", "Coordination")) contents.addContent(tab(name));

        ExplorePanelService.addExploreTab(contents, tab("Explore"));

        assertEquals(List.of("Browse", "Explore", "Console", "Coordination"), tabs());
    }

    public void testWithoutBrowseItIsAppended() {
        contents.addContent(tab("Get Started"));

        ExplorePanelService.addExploreTab(contents, tab("Explore"));

        assertEquals(List.of("Get Started", "Explore"), tabs());
    }
}
