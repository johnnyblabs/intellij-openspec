package com.johnnyblabs.openspec.actions;

import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Continue's delivery routing. Direct API generates in the IDE; the clipboard and editor tab hand the continue workflow
 * to the user's AI tool, naming the change.
 */
class OpenSpecContinueActionTest {

    @Test
    void onlyDirectApiGeneratesInTheIde() {
        assertTrue(OpenSpecContinueAction.routesToDirectApi(DeliveryMode.DIRECT_API));
        assertFalse(OpenSpecContinueAction.routesToDirectApi(DeliveryMode.CLIPBOARD));
        assertFalse(OpenSpecContinueAction.routesToDirectApi(DeliveryMode.EDITOR_TAB));
    }

    @Test
    void continueHasItsOwnUpstreamSkill() {
        assertEquals("continue", WorkflowInvocation.CONTINUE);
        assertEquals("openspec-continue-change", WorkflowInvocation.CONTINUE_SKILL);
    }

    @Test
    void theHandoffIsTheToolsContinueInvocationForTheChange() {
        WorkflowInvocation.Found found = new WorkflowInvocation.Found(null, "/opsx:continue");
        assertEquals("/opsx:continue alpha", OpenSpecContinueAction.handoff(found, "alpha"));
    }
}
