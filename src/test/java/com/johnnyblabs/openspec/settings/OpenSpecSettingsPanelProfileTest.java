package com.johnnyblabs.openspec.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the workflow-profile combo helpers in {@link OpenSpecSettingsPanel}.
 * Focuses on the pure-function display logic and preset list — the full UI behavior
 * (combo enable/disable on CLI availability, orphan insertion) is covered by manual
 * sandbox verification (task 9.3, 9.7).
 */
class OpenSpecSettingsPanelProfileTest {

    @Nested
    class WorkflowProfilePresets {

        @Test
        void presetListContainsExpectedValues() {
            assertEquals(
                    java.util.List.of("", "core", "custom"),
                    OpenSpecSettingsPanel.WORKFLOW_PROFILE_PRESETS);
        }

        @Test
        void presetListDoesNotContainSpecDriven() {
            // "spec-driven" is a SCHEMA, not a workflow profile — confirm it never
            // sneaks back into the preset list.
            assertFalse(OpenSpecSettingsPanel.WORKFLOW_PROFILE_PRESETS.contains("spec-driven"));
        }
    }

    @Nested
    class RenderWorkflowProfileItem {

        @Test
        void emptyPreset_rendersAsDefault() {
            assertEquals(
                    "(default — uses CLI's active profile)",
                    OpenSpecSettingsPanel.renderWorkflowProfileItem("", false));
        }

        @Test
        void nullPreset_rendersAsDefault() {
            assertEquals(
                    "(default — uses CLI's active profile)",
                    OpenSpecSettingsPanel.renderWorkflowProfileItem(null, false));
        }

        @Test
        void corePreset_rendersWithFiveEssentials() {
            String result = OpenSpecSettingsPanel.renderWorkflowProfileItem("core", false);
            assertTrue(result.startsWith("core — "), "expected to start with 'core — ': " + result);
            assertTrue(result.contains("propose"));
            assertTrue(result.contains("explore"));
            assertTrue(result.contains("apply"));
            assertTrue(result.contains("sync"));
            assertTrue(result.contains("archive"));
        }

        @Test
        void customPreset_rendersWithSelectedWorkflowsHint() {
            assertEquals(
                    "custom — your selected workflows",
                    OpenSpecSettingsPanel.renderWorkflowProfileItem("custom", false));
        }

        @Test
        void orphanValue_rendersWithNotFoundSuffix() {
            assertEquals(
                    "spec-driven (not found in CLI)",
                    OpenSpecSettingsPanel.renderWorkflowProfileItem("spec-driven", true));
        }

        @Test
        void orphanFlagOverridesPresetMatching() {
            // If somehow a known preset is marked orphan (shouldn't happen),
            // the orphan suffix wins for honesty.
            assertEquals(
                    "core (not found in CLI)",
                    OpenSpecSettingsPanel.renderWorkflowProfileItem("core", true));
        }
    }
}
