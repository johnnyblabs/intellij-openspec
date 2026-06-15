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
        void presetListContainsOnlyCliAcceptedPresets() {
            // Combo only offers presets the CLI accepts as switch targets. As of
            // CLI 1.3.1, that's "" (default) and "core". "custom" is rejected by
            // `openspec config profile custom` and must not appear here.
            assertEquals(
                    java.util.List.of("", "core"),
                    OpenSpecSettingsPanel.WORKFLOW_PROFILE_PRESETS);
        }

        @Test
        void presetListDoesNotContainCustom() {
            // D2: "custom" is no longer a switchable preset — the CLI rejects
            // `openspec config profile custom`. Persisted legacy "custom" values
            // surface as orphan entries via the renderer, never via this list.
            assertFalse(OpenSpecSettingsPanel.WORKFLOW_PROFILE_PRESETS.contains("custom"));
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
        void customPreset_isOrphanInCurrentVersion_rendersWithNotFoundSuffix() {
            // After D2, "custom" is not in WORKFLOW_PROFILE_PRESETS — any persisted
            // legacy "custom" value reaches the renderer with isOrphan=true.
            assertEquals(
                    "custom (not found in CLI)",
                    OpenSpecSettingsPanel.renderWorkflowProfileItem("custom", true));
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

    @Nested
    class IsOrphanValue {

        @Test
        void nullValue_returnsFalse() {
            assertFalse(OpenSpecSettingsPanel.isOrphanValue(null));
        }

        @Test
        void emptyValue_returnsFalse() {
            // Empty string is the "use CLI's active profile" sentinel, not an orphan.
            assertFalse(OpenSpecSettingsPanel.isOrphanValue(""));
        }

        @Test
        void knownPreset_returnsFalse() {
            assertFalse(OpenSpecSettingsPanel.isOrphanValue("core"));
        }

        @Test
        void legacyCustom_returnsTrue() {
            // Persisted "custom" from plugin v0.2.10 is now orphan per D2.
            assertTrue(OpenSpecSettingsPanel.isOrphanValue("custom"));
        }

        @Test
        void arbitraryUnknownValue_returnsTrue() {
            assertTrue(OpenSpecSettingsPanel.isOrphanValue("spec-driven"));
            assertTrue(OpenSpecSettingsPanel.isOrphanValue("whatever-future-preset"));
        }
    }
}
