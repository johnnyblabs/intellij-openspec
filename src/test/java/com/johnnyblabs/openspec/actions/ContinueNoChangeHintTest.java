package com.johnnyblabs.openspec.actions;

import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.WorkflowProfileService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** With no change to continue, Continue points to Propose, and to New Change only when the profile has it. */
public class ContinueNoChangeHintTest extends BasePlatformTestCase {

    private void profileWith(boolean hasNew) {
        WorkflowProfileService profile = mock(WorkflowProfileService.class);
        when(profile.isWorkflowEnabled(anyString())).thenAnswer(inv -> hasNew || !"new".equals(inv.getArgument(0)));
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());
    }

    public void testOnCoreItNamesProposeOnly() {
        profileWith(false);
        assertEquals("Create a change first with Propose.", OpenSpecContinueAction.noChangeHint(getProject()));
    }

    public void testWithNewInTheProfileItNamesNewChangeToo() {
        profileWith(true);
        assertEquals("Create a change first with Propose or New Change.", OpenSpecContinueAction.noChangeHint(getProject()));
    }
}
