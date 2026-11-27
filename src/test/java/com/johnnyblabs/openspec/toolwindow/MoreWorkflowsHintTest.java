package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.components.JBLabel;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.services.WorkflowProfileService;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With no active change the workflow panel shows its "No changes yet" card. On a custom profile with workflows beyond
 * core, the real refresh, not a test seam, shows the card's hint naming them.
 */
public class MoreWorkflowsHintTest extends OpenSpecIntegrationTestBase {

    public void testAProjectWithNoChangesOnACustomProfileShowsTheHint() throws Exception {
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(List.of());
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());
        WorkflowProfileService profile = mock(WorkflowProfileService.class);
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        when(profile.getActiveProfileName()).thenReturn("custom");
        when(profile.workflowsBeyondCore()).thenReturn(new LinkedHashSet<>(List.of("continue", "verify")));
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());

        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        JBLabel hint = panel.workflowPanelForTest().moreWorkflowsLabelForTest();
        long end = System.currentTimeMillis() + 10_000;
        while (!hint.isVisible() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }

        assertTrue("the empty card's hint shows for a custom profile's extra workflows", hint.isVisible());
        assertEquals("Also in your profile: continue, verify", hint.getToolTipText());
    }

    public void testAProjectWithNoChangesOnTheCoreProfileShowsNoHint() throws Exception {
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(List.of());
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());
        WorkflowProfileService profile = mock(WorkflowProfileService.class);
        when(profile.getActiveProfileName()).thenReturn("core");
        when(profile.workflowsBeyondCore()).thenReturn(Set.of());
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());

        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        long end = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }

        assertFalse(panel.workflowPanelForTest().moreWorkflowsLabelForTest().isVisible());
    }
}
