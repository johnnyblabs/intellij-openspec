package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.ModalityCallScanner;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Apply's delivery: every mode delivers something. Direct API hands the apply workflow to the user's AI tool, as Revise
 * and Explore do, and the editor tab opens an unsaved file rather than one in the temp directory.
 */
class ApplyDeliveryTest {

    @Test
    void theApplyWorkflowIsUpstreamsApplyChange() {
        assertEquals("apply", WorkflowInvocation.APPLY);
        assertEquals("openspec-apply-change", WorkflowInvocation.APPLY_SKILL);
    }

    @Test
    void directApiHandsTheApplyWorkflowToTheUserInsteadOfWarning() throws IOException {
        String code = Files.readString(ModalityCallScanner.mainSource("toolwindow/WorkflowActionPanel.java"));
        assertTrue(ModalityCallScanner.codeContains(code, "WorkflowInvocation.APPLY_SKILL"),
                "Direct API must hand off the apply workflow");
        assertTrue(ModalityCallScanner.codeContains(code, "WorkflowHandoffDelivery.deliver("),
                "Direct API must deliver through the shared handoff");
        assertFalse(ModalityCallScanner.codeContains(code, "Apply delivers a prompt for your AI tool"),
                "the Direct API branch must not just warn and deliver nothing");
    }

    @Test
    void theEditorTabNeverWritesThePromptToTheTempDirectory() throws IOException {
        String code = Files.readString(ModalityCallScanner.mainSource("toolwindow/WorkflowActionPanel.java"));
        assertFalse(ModalityCallScanner.codeContains(code, "java.io.tmpdir"),
                "the Apply prompt is an unsaved LightVirtualFile, like Explore and Revise");
    }
}
