package com.johnnyblabs.openspec.services;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The workflows a custom profile adds to the core set, which the empty workflow panel points at. */
class WorkflowsBeyondCoreTest {

    @Test
    void aCustomProfileListsItsWorkflowsBeyondCore_inItsOrder() {
        Set<String> active = WorkflowProfileService.ordered("propose", "explore", "new", "continue", "apply", "verify");
        assertEquals(List.of("new", "continue", "verify"),
                List.copyOf(WorkflowProfileService.beyondCore("custom", active, WorkflowProfileService.CURRENT_CORE)));
    }

    @Test
    void theCoreProfileOrACustomOneWithOnlyCoreWorkflowsHasNone() {
        assertEquals(Set.of(), WorkflowProfileService.beyondCore("core", WorkflowProfileService.ordered("propose", "new"),
                WorkflowProfileService.CURRENT_CORE));
        assertEquals(Set.of(), WorkflowProfileService.beyondCore("custom", WorkflowProfileService.CURRENT_CORE,
                WorkflowProfileService.CURRENT_CORE));
    }

    /** The core set follows the CLI: update is core from 1.4, so it is beyond core on 1.3. */
    @Test
    void theCoreSetIsTheResolvedClis() {
        Set<String> active = WorkflowProfileService.ordered("propose", "explore", "apply", "archive", "update");
        assertEquals(Set.of("update"), WorkflowProfileService.beyondCore("custom", active,
                WorkflowProfileService.coreWorkflows("1.3.0")));
    }
}
