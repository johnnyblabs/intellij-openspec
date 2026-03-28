package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.DirectApiService;
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
class OpenSpecFfActionTest {

    @Mock Project project;
    @Mock AnActionEvent event;
    @Mock DirectApiService apiService;
    @Mock WorkflowProfileService profileService;

    private OpenSpecFfAction action;
    private Presentation presentation;

    @BeforeEach
    void setUp() {
        action = new OpenSpecFfAction();
        presentation = new Presentation();
        presentation.setEnabled(true);
        presentation.setVisible(true);
        lenient().when(event.getPresentation()).thenReturn(presentation);
        lenient().when(event.getProject()).thenReturn(project);
    }

    @Nested
    class UpdateEnablement {

        @Test
        void disabled_whenDirectApiNotConfigured() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(WorkflowProfileService.class)).thenReturn(profileService);
                when(profileService.isWorkflowEnabled("ff")).thenReturn(true);
                when(project.getService(DirectApiService.class)).thenReturn(apiService);
                when(apiService.isConfigured()).thenReturn(false);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertFalse(presentation.isEnabled());
                assertEquals("Requires AI provider. Configure in Settings \u2192 Tools \u2192 OpenSpec.",
                        presentation.getDescription());
            }
        }

        @Test
        void disabled_whenDirectApiServiceIsNull() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(WorkflowProfileService.class)).thenReturn(profileService);
                when(profileService.isWorkflowEnabled("ff")).thenReturn(true);
                when(project.getService(DirectApiService.class)).thenReturn(null);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertFalse(presentation.isEnabled());
            }
        }

        @Test
        void enabled_whenDirectApiConfigured() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(WorkflowProfileService.class)).thenReturn(profileService);
                when(profileService.isWorkflowEnabled("ff")).thenReturn(true);
                when(project.getService(DirectApiService.class)).thenReturn(apiService);
                when(apiService.isConfigured()).thenReturn(true);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertTrue(presentation.isEnabled());
            }
        }

        @Test
        void disabled_whenProfileDisablesFF() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(WorkflowProfileService.class)).thenReturn(profileService);
                when(profileService.isWorkflowEnabled("ff")).thenReturn(false);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertFalse(presentation.isEnabled());
            }
        }
    }
}