package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.WorkflowProfileSwitchService;
import com.johnnyblabs.openspec.services.WorkflowProfileSwitchService.Outcome;
import com.johnnyblabs.openspec.services.WorkflowProfileSwitchService.SwitchResult;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.mockito.Mockito.*;

/**
 * Tests for OpenSpecConfigurable.applyProfileChange — verifies it delegates to
 * {@link WorkflowProfileSwitchService} and reacts to the outcome correctly.
 * The service's own CLI delegation logic is tested in
 * {@link com.johnnyblabs.openspec.services.WorkflowProfileSwitchServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class OpenSpecConfigurableProfileTest {

    @Mock Project project;
    @Mock OpenSpecSettings settings;
    @Mock OpenSpecSettingsPanel panel;
    @Mock WorkflowProfileSwitchService switchService;

    @Nested
    class ProfileSwitchDelegation {

        @Test
        void switchedOutcome_refreshesConfigProfileSectionAndPrompts() throws Exception {
            when(project.getService(WorkflowProfileSwitchService.class)).thenReturn(switchService);
            when(switchService.switchProfile("custom")).thenReturn(new SwitchResult(Outcome.SWITCHED, null));

            invokeApplyProfileChange("custom", "core");

            verify(switchService).switchProfile("custom");
            verify(panel).refreshConfigProfileSection();
            verify(switchService).promptAndRunUpdateIfConfirmed("custom");
            verify(panel, never()).setProfile(anyString());
        }

        @Test
        void cliFailureOutcome_revertsPanelToOldProfileAndWarns() throws Exception {
            when(project.getService(WorkflowProfileSwitchService.class)).thenReturn(switchService);
            when(switchService.switchProfile("custom"))
                    .thenReturn(new SwitchResult(Outcome.CLI_FAILURE, "Unknown profile"));

            try (MockedStatic<OpenSpecNotifier> notifier = mockStatic(OpenSpecNotifier.class)) {
                invokeApplyProfileChange("custom", "core");

                verify(panel).setProfile("core");
                verify(panel, never()).refreshConfigProfileSection();
                verify(switchService, never()).promptAndRunUpdateIfConfirmed(anyString());
                notifier.verify(() -> OpenSpecNotifier.warn(eq(project), eq("Profile Switch"),
                        contains("Unknown profile")));
            }
        }

        @Test
        void cliUnavailableOutcome_refreshesConfigProfileSectionWithoutPrompt() throws Exception {
            when(project.getService(WorkflowProfileSwitchService.class)).thenReturn(switchService);
            when(switchService.switchProfile("custom"))
                    .thenReturn(new SwitchResult(Outcome.CLI_UNAVAILABLE, null));

            invokeApplyProfileChange("custom", "core");

            verify(panel).refreshConfigProfileSection();
            verify(switchService, never()).promptAndRunUpdateIfConfirmed(anyString());
            verify(panel, never()).setProfile(anyString());
        }

        @Test
        void switchServiceUnavailable_persistsLocallyAsFallback() throws Exception {
            when(project.getService(WorkflowProfileSwitchService.class)).thenReturn(null);

            invokeApplyProfileChange("custom", "core");

            verify(settings).setProfile("custom");
        }
    }

    /**
     * Uses reflection to invoke the private applyProfileChange method.
     */
    private void invokeApplyProfileChange(String newProfile, String oldProfile) throws Exception {
        OpenSpecConfigurable configurable = new OpenSpecConfigurable(project);

        // Inject the panel field via reflection
        java.lang.reflect.Field panelField = OpenSpecConfigurable.class.getDeclaredField("panel");
        panelField.setAccessible(true);
        panelField.set(configurable, panel);

        // Invoke applyProfileChange
        Method method = OpenSpecConfigurable.class.getDeclaredMethod(
                "applyProfileChange", OpenSpecSettings.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(configurable, settings, newProfile, oldProfile);
    }
}
