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
    class FormatActiveItem {

        @Test
        void corePreset_omitsCount_andYourWorkflowSetQualifier() {
            // Core is a fixed preset — no per-user variation to qualify.
            assertEquals("● core  (active)",
                    OpenSpecProfileStatusBarWidget.formatActiveItem("core", 5));
        }

        @Test
        void customProfile_addsYourWorkflowSetQualifier() {
            // D4: "custom" gets the "(your workflow set)" qualifier to defuse the
            // asymmetry against the Settings combo, which no longer offers "custom"
            // as a switchable entry. The user sees "custom" as the active label
            // because the CLI reports it when workflows diverge from any named
            // preset — not because they picked it.
            assertEquals("● custom (your workflow set) · 8 workflows  (active)",
                    OpenSpecProfileStatusBarWidget.formatActiveItem("custom", 8));
        }
    }

    @Nested
    class StaticDiscoveryCue {

        @Test
        void cue_doesNotEnumerateWorkflowNames() {
            // D4 / D7 principle: no plugin-side enumeration of specific workflows.
            // The cue stays category-level only; workflow names live in the docs.
            String cue = OpenSpecProfileStatusBarWidget.STATIC_DISCOVERY_CUE;
            assertFalse(cue.contains("propose"));
            assertFalse(cue.contains("explore"));
            assertFalse(cue.contains("apply"));
            assertFalse(cue.contains("sync"));
            assertFalse(cue.contains("archive"));
            assertFalse(cue.contains("verify"));
            assertFalse(cue.contains("ff"));
            assertFalse(cue.contains("continue"));
            assertFalse(cue.contains("bulk-archive"));
            assertFalse(cue.contains("onboard"));
            assertFalse(cue.contains("new"));
        }

        @Test
        void cue_pointsAtCliPicker() {
            // The cue's job is to direct the user to the CLI's interactive picker.
            // Until D3 ships a "Customize workflows…" button, the cue uses the
            // terminal-direct command.
            String cue = OpenSpecProfileStatusBarWidget.STATIC_DISCOVERY_CUE;
            assertTrue(cue.contains("openspec config profile"));
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
