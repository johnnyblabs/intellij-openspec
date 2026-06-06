package com.johnnyblabs.openspec.statusbar;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenSpecProfileStatusBarWidgetTest {

    @Nested
    class FormatLabel {

        @Test
        void coreProfile_omitsCount() {
            assertEquals("OpenSpec: core",
                    OpenSpecProfileStatusBarWidget.formatLabel("core", 5, true));
        }

        @Test
        void customProfile_includesWorkflowCount() {
            assertEquals("OpenSpec: custom · 8 workflows",
                    OpenSpecProfileStatusBarWidget.formatLabel("custom", 8, true));
        }

        @Test
        void cliUnavailable_appendsFallbackIndicator() {
            assertEquals("OpenSpec: core (fallback)",
                    OpenSpecProfileStatusBarWidget.formatLabel("core", 5, false));
        }

        @Test
        void cliUnavailable_takesPrecedenceOverCustomFormat() {
            // Even if profile name says custom, fallback formatting wins when CLI is gone
            assertEquals("OpenSpec: custom (fallback)",
                    OpenSpecProfileStatusBarWidget.formatLabel("custom", 8, false));
        }
    }

    @Nested
    class FactoryAvailability {

        @Mock
        Project project;

        @Test
        void isAvailable_falseOnNonOpenSpecProject() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(false);

                OpenSpecProfileStatusBarWidgetFactory factory = new OpenSpecProfileStatusBarWidgetFactory();
                assertFalse(factory.isAvailable(project));
            }
        }

        @Test
        void isAvailable_trueOnOpenSpecProject() {
            try (MockedStatic<OpenSpecFileUtil> fileUtil = mockStatic(OpenSpecFileUtil.class)) {
                fileUtil.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);

                OpenSpecProfileStatusBarWidgetFactory factory = new OpenSpecProfileStatusBarWidgetFactory();
                assertTrue(factory.isAvailable(project));
            }
        }

        @Test
        void factoryId_matchesWidgetId() {
            OpenSpecProfileStatusBarWidgetFactory factory = new OpenSpecProfileStatusBarWidgetFactory();
            assertEquals(OpenSpecProfileStatusBarWidget.ID, factory.getId());
        }
    }
}
