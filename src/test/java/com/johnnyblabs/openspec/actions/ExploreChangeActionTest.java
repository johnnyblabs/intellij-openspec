package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.dialogs.ExploreTopicDialog;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** "Explore this change..." shows only for a change, follows the explore workflow, and explores that change. */
public class ExploreChangeActionTest extends BasePlatformTestCase {

    private WorkflowProfileService profile;
    private String savedDelivery;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("openspec/config.yaml", "schema: spec-driven\n"); // an OpenSpec project
        profile = mock(WorkflowProfileService.class);
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());
        savedDelivery = OpenSpecSettings.getInstance(getProject()).getPreferredDeliveryMethod();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ExploreContextAction.setTopicDialogFactoryForTest(null);
            OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod(savedDelivery);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private static AnAction action() {
        return ActionManager.getInstance().getAction("OpenSpec.ExploreChange");
    }

    private AnActionEvent event(String changeName) {
        SimpleDataContext.Builder data = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject());
        if (changeName != null) data.add(OpenSpecDataKeys.CHANGE_NAME, changeName);
        return TestActionEvent.createTestEvent(action(), data.build());
    }

    public void testShownAndEnabledForAChange() {
        AnActionEvent e = event("add-auth");
        action().update(e);

        assertTrue(e.getPresentation().isVisible());
        assertTrue(e.getPresentation().isEnabled());
    }

    public void testHiddenWithoutAChange() {
        AnActionEvent e = event(null);
        action().update(e);

        assertFalse("nothing to explore without a change", e.getPresentation().isVisible());
    }

    public void testDisabledWhenTheProfileExcludesExplore() {
        when(profile.isWorkflowEnabled("explore")).thenReturn(false);
        AnActionEvent e = event("add-auth");
        action().update(e);

        assertFalse(e.getPresentation().isEnabled());
    }

    public void testItIsGatedOnTheExploreWorkflow() {
        assertEquals("explore", ((OpenSpecBaseAction) action()).getWorkflowId());
    }

    public void testItExploresThatChange() {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("CLIPBOARD");
        List<String> prefilled = new ArrayList<>();
        ExploreContextAction.setTopicDialogFactoryForTest((project, topic) -> new ExploreTopicDialog(project, topic) {
            @Override
            public boolean showAndGet() {
                prefilled.add(getTopic());
                return false; // cancel, so nothing is delivered
            }
        });

        action().actionPerformed(event("add-auth"));

        assertEquals(List.of("add-auth"), prefilled);
    }
}
