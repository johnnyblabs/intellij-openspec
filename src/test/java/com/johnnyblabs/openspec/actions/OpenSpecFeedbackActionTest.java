package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
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
class OpenSpecFeedbackActionTest {

    @Mock Project project;
    @Mock AnActionEvent event;
    @Mock CliDetectionService detection;

    private OpenSpecFeedbackAction action;
    private Presentation presentation;

    @BeforeEach
    void setUp() {
        action = new OpenSpecFeedbackAction();
        presentation = new Presentation();
        lenient().when(event.getPresentation()).thenReturn(presentation);
        lenient().when(event.getProject()).thenReturn(project);
    }

    @Nested
    class CliArgConstruction {

        @Test
        void messageOnly() {
            assertArrayEquals(new String[]{"feedback", "Great tool"},
                    OpenSpecFeedbackAction.buildCliArgs("Great tool", ""));
        }

        @Test
        void nullOrBlankBody_omitsBodyFlag() {
            assertArrayEquals(new String[]{"feedback", "msg"},
                    OpenSpecFeedbackAction.buildCliArgs("msg", null));
            assertArrayEquals(new String[]{"feedback", "msg"},
                    OpenSpecFeedbackAction.buildCliArgs("msg", "   "));
        }

        @Test
        void bodyAppendedWithFlag() {
            assertArrayEquals(new String[]{"feedback", "msg", "--body", "longer details"},
                    OpenSpecFeedbackAction.buildCliArgs("msg", "longer details"));
        }
    }

    @Nested
    class VisibilityGating {

        @Test
        void hidden_whenProjectIsNull() {
            when(event.getProject()).thenReturn(null);

            action.update(event);

            assertFalse(presentation.isVisible());
            assertFalse(presentation.isEnabled());
        }

        @Test
        void hidden_whenNotAnOpenSpecProject() {
            try (MockedStatic<OpenSpecFileUtil> util = mockStatic(OpenSpecFileUtil.class)) {
                util.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(false);

                action.update(event);

                assertFalse(presentation.isVisible());
            }
        }

        @Test
        void hidden_whenCliNotDetected() {
            try (MockedStatic<OpenSpecFileUtil> util = mockStatic(OpenSpecFileUtil.class)) {
                util.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(CliDetectionService.class)).thenReturn(detection);
                when(detection.isAvailable()).thenReturn(false);

                action.update(event);

                // The spec requires hidden (not merely disabled) without a CLI —
                // feedback has no built-in fallback to degrade to.
                assertFalse(presentation.isVisible());
            }
        }

        @Test
        void hidden_whenDetectionServiceMissing() {
            try (MockedStatic<OpenSpecFileUtil> util = mockStatic(OpenSpecFileUtil.class)) {
                util.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(CliDetectionService.class)).thenReturn(null);

                action.update(event);

                assertFalse(presentation.isVisible());
            }
        }

        @Test
        void visibleAndEnabled_whenCliAvailable() {
            try (MockedStatic<OpenSpecFileUtil> util = mockStatic(OpenSpecFileUtil.class)) {
                util.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(CliDetectionService.class)).thenReturn(detection);
                when(detection.isAvailable()).thenReturn(true);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertTrue(presentation.isEnabled());
            }
        }
    }
}
