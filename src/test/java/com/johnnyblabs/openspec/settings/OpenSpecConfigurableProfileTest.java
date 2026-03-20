package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the profile switch CLI delegation logic in OpenSpecConfigurable.
 * Uses reflection to test the private applyProfileChange method directly,
 * since testing through apply() would require full UI initialization.
 */
@ExtendWith(MockitoExtension.class)
class OpenSpecConfigurableProfileTest {

    @Mock Project project;
    @Mock CliDetectionService detection;
    @Mock OpenSpecSettings settings;
    @Mock OpenSpecSettingsPanel panel;

    @Nested
    class ProfileSwitchWithCli {

        @Test
        void cliSuccess_persistsNewProfile() throws Exception {
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            when(detection.isAvailable()).thenReturn(true);

            CliRunner.CliResult success = new CliRunner.CliResult(0, "Profile switched", "");

            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "profile", "tdd"))
                        .thenReturn(success);

                invokeApplyProfileChange("tdd", "spec-driven");

                verify(settings).setProfile("tdd");
                verify(panel).refreshConfigProfileSection();
            }
        }

        @Test
        void cliFailure_revertsToOldProfile() throws Exception {
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            when(detection.isAvailable()).thenReturn(true);

            CliRunner.CliResult failure = new CliRunner.CliResult(1, "", "Unknown profile: invalid");

            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class);
                 MockedStatic<com.johnnyblabs.openspec.util.OpenSpecNotifier> notifier =
                         mockStatic(com.johnnyblabs.openspec.util.OpenSpecNotifier.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "profile", "invalid"))
                        .thenReturn(failure);

                invokeApplyProfileChange("invalid", "spec-driven");

                verify(settings, never()).setProfile(anyString());
                verify(panel).setProfile("spec-driven");
            }
        }

        @Test
        void cliException_revertsToOldProfile() throws Exception {
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            when(detection.isAvailable()).thenReturn(true);

            try (MockedStatic<CliRunner> cliStatic = mockStatic(CliRunner.class);
                 MockedStatic<com.johnnyblabs.openspec.util.OpenSpecNotifier> notifier =
                         mockStatic(com.johnnyblabs.openspec.util.OpenSpecNotifier.class)) {
                cliStatic.when(() -> CliRunner.run(project, "config", "profile", "bad"))
                        .thenThrow(new CliRunner.CliException("CLI crashed"));

                invokeApplyProfileChange("bad", "spec-driven");

                verify(settings, never()).setProfile(anyString());
                verify(panel).setProfile("spec-driven");
            }
        }
    }

    @Nested
    class ProfileSwitchWithoutCli {

        @Test
        void cliUnavailable_persistsLocally() throws Exception {
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            when(detection.isAvailable()).thenReturn(false);

            try (MockedStatic<com.johnnyblabs.openspec.util.OpenSpecNotifier> notifier =
                         mockStatic(com.johnnyblabs.openspec.util.OpenSpecNotifier.class)) {
                invokeApplyProfileChange("tdd", "spec-driven");

                verify(settings).setProfile("tdd");
            }
        }

        @Test
        void cliDetectionServiceNull_persistsLocally() throws Exception {
            when(project.getService(CliDetectionService.class)).thenReturn(null);

            try (MockedStatic<com.johnnyblabs.openspec.util.OpenSpecNotifier> notifier =
                         mockStatic(com.johnnyblabs.openspec.util.OpenSpecNotifier.class)) {
                invokeApplyProfileChange("tdd", "spec-driven");

                verify(settings).setProfile("tdd");
            }
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
