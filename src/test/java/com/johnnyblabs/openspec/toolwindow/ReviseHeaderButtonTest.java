package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.actions.OpenSpecDataKeys;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.List;

/**
 * The workflow panel's header has a Revise button that runs Revise Change... for the panel's change. Headless tests use
 * a data manager that ignores components' own providers, so the button's provider is read directly; the uiSmoke suite
 * covers the real data context.
 */
public class ReviseHeaderButtonTest extends OpenSpecIntegrationTestBase {

    private WorkflowActionPanel workflowPanel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        long end = System.currentTimeMillis() + 10_000;
        while (panel.treeModelForTest() != model && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        workflowPanel = panel.workflowPanelForTest();
    }

    /** The fixture project's active change. */
    private static final String CHANGE = "test-change";

    private JButton button() throws Exception {
        workflowPanel.setActiveChange(CHANGE);
        JButton button = workflowPanel.reviseIconButtonForTest();
        long end = System.currentTimeMillis() + 10_000;
        while (!button.isEnabled() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        return button;
    }

    public void testTheButtonIsEnabledForTheChangeAndExplainsItself() throws Exception {
        JButton button = button();

        assertTrue("enabled once the panel has a change", button.isEnabled());
        assertEquals(WorkflowActionPanel.REVISE_TOOLTIP, button.getToolTipText());
        assertTrue("the button provides the panel's change", button instanceof DataProvider);
        assertEquals(CHANGE, ((DataProvider) button).getData(OpenSpecDataKeys.CHANGE_NAME.getName()));
    }

    public void testItLaunchesFromTheButtonWhoseDataNamesTheChange() throws Exception {
        JButton button = button();
        List<java.awt.Component> sources = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        workflowPanel.setLauncherForTest((action, source) -> {
            actions.add(ActionManager.getInstance().getId(action));
            sources.add(source);
        });

        button.doClick();

        assertEquals(List.of("OpenSpec.ReviseChange"), actions);
        assertSame("the data context must come from the button, which names the change", button, sources.get(0));
        assertEquals(CHANGE, ((DataProvider) sources.get(0)).getData(OpenSpecDataKeys.CHANGE_NAME.getName()));
    }

    public void testActivatingItRunsReviseChangeThroughTheActionPipeline() throws Exception {
        JButton button = button();
        List<String> ran = new ArrayList<>();
        com.intellij.openapi.application.ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
                .subscribe(AnActionListener.TOPIC, new AnActionListener() {
                    @Override
                    public void beforeActionPerformed(AnAction action, AnActionEvent event) {
                        ran.add(ActionManager.getInstance().getId(action) + "@" + event.getPlace());
                    }
                });
        // The fixture has no update workflow installed, so the action ends at its "run Update OpenSpec…" notice.
        button.doClick();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertTrue(ran.toString(), ran.contains("OpenSpec.ReviseChange@WorkflowPanel"));
    }
}
