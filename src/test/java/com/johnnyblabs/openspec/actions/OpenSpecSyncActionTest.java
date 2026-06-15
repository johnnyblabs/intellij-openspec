package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenSpecSyncActionTest {

    @Mock Project project;
    @Mock AnActionEvent event;
    @Mock WorkflowProfileService profileService;

    private OpenSpecSyncAction action;
    private Presentation presentation;

    @BeforeEach
    void setUp() {
        action = new OpenSpecSyncAction();
        presentation = new Presentation();
        presentation.setEnabled(true);
        presentation.setVisible(true);
        lenient().when(event.getPresentation()).thenReturn(presentation);
        lenient().when(event.getProject()).thenReturn(project);
    }

    @Nested
    class UpdateEnablement {

        @Test
        void enabled_inOpenSpecProject() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertTrue(presentation.isEnabled());
            }
        }

        @Test
        void disabled_whenNotOpenSpecProject() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(false);

                action.update(event);

                assertFalse(presentation.isVisible());
                assertFalse(presentation.isEnabled());
            }
        }

        /**
         * Architectural regression test: sync is a view/diff utility, not workflow-gated.
         * A profile that disables the "sync" workflow string MUST NOT disable this action.
         */
        @Test
        void enabled_evenWhenSyncWorkflowDisabledInProfile() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                // Even if the profile says "sync" workflow is disabled, the action stays enabled.
                lenient().when(project.getService(WorkflowProfileService.class)).thenReturn(profileService);
                lenient().when(profileService.isWorkflowEnabled("sync")).thenReturn(false);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertTrue(presentation.isEnabled(),
                        "Sync action must not be gated by workflow profile (it's a view/diff utility)");
                // Profile service should NEVER be consulted for sync — base getWorkflowId() returns null,
                // so the gating block is skipped entirely.
                verify(profileService, never()).isWorkflowEnabled(anyString());
            }
        }
    }
}
