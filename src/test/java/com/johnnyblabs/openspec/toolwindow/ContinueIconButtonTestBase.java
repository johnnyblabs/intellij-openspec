package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.components.JBLabel;
import com.johnnyblabs.openspec.actions.OpenSpecDataKeys;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A workflow panel showing one active change with a captured status, for the Continue button tests. */
public abstract class ContinueIconButtonTestBase extends OpenSpecIntegrationTestBase {

    static final String CHANGE = "test-change";
    WorkflowActionPanel workflowPanel;

    /** The captured status the panel shows for the change. */
    abstract String statusFixture();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(List.of(new Change(CHANGE, "/tmp/" + CHANGE)));
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());
        PanelStatusTestSupport.feedCapturedStatus(getProject(), CHANGE, getTestRootDisposable(), statusFixture(),
                artifactId -> null);
        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        long end = System.currentTimeMillis() + 10_000;
        while (panel.treeModelForTest() != model && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        workflowPanel = panel.workflowPanelForTest();
        workflowPanel.setActiveChange(CHANGE);
        JButton revise = workflowPanel.reviseIconButtonForTest();
        end = System.currentTimeMillis() + 10_000;
        while (!revise.isEnabled() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertTrue("sanity: the panel shows the change", revise.isEnabled());
    }
}
