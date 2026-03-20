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
class OpenSpecUpdateActionTest {

    @Mock Project project;
    @Mock AnActionEvent event;
    @Mock CliDetectionService detection;

    private OpenSpecUpdateAction action;
    private Presentation presentation;

    @BeforeEach
    void setUp() {
        action = new OpenSpecUpdateAction();
        presentation = new Presentation();
        lenient().when(event.getPresentation()).thenReturn(presentation);
        lenient().when(event.getProject()).thenReturn(project);
    }

    @Nested
    class CliArgsAndLabel {

        @Test
        void cliArgs_returnsUpdate() {
            assertArrayEquals(new String[]{"update"}, action.getCliArgs());
        }

        @Test
        void commandLabel_returnsUpdate() {
            assertEquals("update", action.getCommandLabel());
        }
    }

    @Nested
    class UpdateEnablement {

        @Test
        void disabledAndHidden_whenProjectIsNull() {
            when(event.getProject()).thenReturn(null);

            action.update(event);

            assertFalse(presentation.isEnabled());
            assertFalse(presentation.isVisible());
        }

        @Test
        void disabledAndHidden_whenNotOpenSpecProject() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(false);

                action.update(event);

                assertFalse(presentation.isEnabled());
                assertFalse(presentation.isVisible());
            }
        }

        @Test
        void disabled_whenCliUnavailable() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(CliDetectionService.class)).thenReturn(detection);
                when(detection.isAvailable()).thenReturn(false);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertFalse(presentation.isEnabled());
                assertEquals("Install OpenSpec CLI to use this action",
                        presentation.getDescription());
            }
        }

        @Test
        void disabled_whenCliDetectionServiceIsNull() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(CliDetectionService.class)).thenReturn(null);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertFalse(presentation.isEnabled());
                assertEquals("Install OpenSpec CLI to use this action",
                        presentation.getDescription());
            }
        }

        @Test
        void enabled_whenCliAvailableAndOpenSpecProject() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
                when(project.getService(CliDetectionService.class)).thenReturn(detection);
                when(detection.isAvailable()).thenReturn(true);

                action.update(event);

                assertTrue(presentation.isVisible());
                assertTrue(presentation.isEnabled());
                assertEquals("Refresh agent instruction files via openspec update",
                        presentation.getDescription());
            }
        }
    }
}
