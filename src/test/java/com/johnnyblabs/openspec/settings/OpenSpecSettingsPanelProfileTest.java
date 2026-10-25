package com.johnnyblabs.openspec.settings;

import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        void corePreset_rendersWithGenericHint() {
            // D7: the renderer no longer enumerates workflow names — that's
            // plugin-side hardcoded knowledge that rots against CLI version changes.
            // The hint stays category-level only ("essentials only"); specific
            // workflow names live in the docs (Workflow-Profiles.md).
            String result = OpenSpecSettingsPanel.renderWorkflowProfileItem("core", false);
            assertEquals("core — essentials only", result);
            assertFalse(result.contains("propose"));
            assertFalse(result.contains("explore"));
            assertFalse(result.contains("apply"));
            assertFalse(result.contains("sync"));
            assertFalse(result.contains("archive"));
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

    /**
     * The Config Profile section's data resolution ({@code computeConfigProfileView}) — the rewire
     * from the never-real {@code config profile --json} to {@link WorkflowProfileService}
     * (which reads {@code config list --json}). Tests the pure seam directly; the Swing rendering
     * ({@code applyConfigProfileView}) stays under manual/sandbox verification like the rest of the panel.
     */
    @Nested
    class ConfigProfileSection {

        @Test
        void rendersActiveProfileAndWorkflowsFromService() {
            CliDetectionService detection = mock(CliDetectionService.class);
            when(detection.isAvailable()).thenReturn(true);
            WorkflowProfileService service = mock(WorkflowProfileService.class);
            when(service.getActiveProfileName()).thenReturn("core");
            // The core set on a current CLI: six workflows, including `update`.
            when(service.getActiveWorkflows()).thenReturn(new LinkedHashSet<>(
                    List.of("propose", "explore", "apply", "update", "sync", "archive")));

            // Pass a DISTINCT local name ("" = the default combo value) so asserting displayName=="core"
            // proves the name came from the CLI-resolved service, not echoed from the local param —
            // the section must show the resolved active profile, not the persisted combo selection.
            OpenSpecSettingsPanel.ConfigProfileView view =
                    OpenSpecSettingsPanel.computeConfigProfileView(detection, service, "");

            assertFalse(view.fallback(), "CLI available → not a fallback view");
            assertEquals("core", view.displayName(), "name comes from the service, not the local param");
            assertEquals(List.of("propose", "explore", "apply", "update", "sync", "archive"),
                    view.workflows());
            // Proves the section actually consults WorkflowProfileService (config list --json),
            // not the dead `config profile --json` path a regression would restore.
            verify(service).refresh();
            verify(service).getActiveProfileName();
            verify(service).getActiveWorkflows();
        }

        @Test
        void fallbackWhenCliUnavailable_doesNotTouchService() {
            CliDetectionService detection = mock(CliDetectionService.class);
            when(detection.isAvailable()).thenReturn(false);
            WorkflowProfileService service = mock(WorkflowProfileService.class);

            OpenSpecSettingsPanel.ConfigProfileView view =
                    OpenSpecSettingsPanel.computeConfigProfileView(detection, service, "myprofile");

            assertTrue(view.fallback());
            assertEquals("myprofile", view.displayName(), "fallback carries the locally-stored name");
            assertTrue(view.workflows().isEmpty());
            verifyNoInteractions(service);
        }

        @Test
        void fallbackWhenDetectionNull_doesNotTouchService() {
            WorkflowProfileService service = mock(WorkflowProfileService.class);
            OpenSpecSettingsPanel.ConfigProfileView view =
                    OpenSpecSettingsPanel.computeConfigProfileView(null, service, "");
            assertTrue(view.fallback());
            verifyNoInteractions(service);
        }

        @Test
        void descriptionRowIsGone() {
            // The invented profile "description" must stay removed — OpenSpec emits none. The view
            // record has no description component (compile-time), and the Swing field is deleted;
            // lock the field so it can't be reintroduced.
            assertThrows(NoSuchFieldException.class,
                    () -> OpenSpecSettingsPanel.class.getDeclaredField("profileDescriptionLabel"));
        }
    }
}
