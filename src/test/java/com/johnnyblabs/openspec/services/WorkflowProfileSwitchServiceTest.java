package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowProfileSwitchServiceTest {

    @Mock Project project;
    @Mock CliDetectionService detection;
    @Mock OpenSpecSettings settings;
    @Mock WorkflowProfileService wps;

    private WorkflowProfileSwitchService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowProfileSwitchService(project);
    }

    @Nested
    class SwitchProfile {

        @Test
        void cliSuccess_returnsSwitchedAndPersists() {
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            when(project.getService(WorkflowProfileService.class)).thenReturn(wps);
            when(detection.isAvailable()).thenReturn(true);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class);
                 MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                cli.when(() -> CliRunner.run(project, "config", "profile", "custom"))
                        .thenReturn(new CliRunner.CliResult(0, "ok", ""));

                WorkflowProfileSwitchService.SwitchResult result = service.switchProfile("custom");

                assertEquals(WorkflowProfileSwitchService.Outcome.SWITCHED, result.outcome());
                verify(settings).setProfile("custom");
                verify(wps).refresh();
            }
        }

        @Test
        void cliFailure_returnsFailureAndDoesNotPersist() {
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            when(detection.isAvailable()).thenReturn(true);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class);
                 MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class);
                 MockedStatic<OpenSpecNotifier> notifier = mockStatic(OpenSpecNotifier.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                cli.when(() -> CliRunner.run(project, "config", "profile", "bogus"))
                        .thenReturn(new CliRunner.CliResult(1, "", "Unknown profile"));

                WorkflowProfileSwitchService.SwitchResult result = service.switchProfile("bogus");

                assertEquals(WorkflowProfileSwitchService.Outcome.CLI_FAILURE, result.outcome());
                assertEquals("Unknown profile", result.error());
                verify(settings, never()).setProfile(anyString());
                notifier.verify(() -> OpenSpecNotifier.warn(eq(project), eq("Profile Switch"), anyString()));
            }
        }

        @Test
        void cliException_returnsFailureAndDoesNotPersist() {
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            when(detection.isAvailable()).thenReturn(true);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class);
                 MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class);
                 MockedStatic<OpenSpecNotifier> notifier = mockStatic(OpenSpecNotifier.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                cli.when(() -> CliRunner.run(project, "config", "profile", "custom"))
                        .thenThrow(new CliRunner.CliException("CLI crashed"));

                WorkflowProfileSwitchService.SwitchResult result = service.switchProfile("custom");

                assertEquals(WorkflowProfileSwitchService.Outcome.CLI_FAILURE, result.outcome());
                verify(settings, never()).setProfile(anyString());
            }
        }

        @Test
        void cliUnavailable_persistsLocallyAndReturnsCliUnavailable() {
            when(project.getService(CliDetectionService.class)).thenReturn(detection);
            when(project.getService(WorkflowProfileService.class)).thenReturn(wps);
            when(detection.isAvailable()).thenReturn(false);

            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class);
                 MockedStatic<OpenSpecNotifier> notifier = mockStatic(OpenSpecNotifier.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);

                WorkflowProfileSwitchService.SwitchResult result = service.switchProfile("custom");

                assertEquals(WorkflowProfileSwitchService.Outcome.CLI_UNAVAILABLE, result.outcome());
                verify(settings).setProfile("custom");
                verify(wps).refresh();
                notifier.verify(() -> OpenSpecNotifier.info(eq(project), eq("Profile"), anyString()));
            }
        }

        @Test
        void detectionServiceNull_treatedAsCliUnavailable() {
            when(project.getService(CliDetectionService.class)).thenReturn(null);
            when(project.getService(WorkflowProfileService.class)).thenReturn(wps);

            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class);
                 MockedStatic<OpenSpecNotifier> notifier = mockStatic(OpenSpecNotifier.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);

                WorkflowProfileSwitchService.SwitchResult result = service.switchProfile("custom");

                assertEquals(WorkflowProfileSwitchService.Outcome.CLI_UNAVAILABLE, result.outcome());
                verify(settings).setProfile("custom");
            }
        }
    }

    @Nested
    class PromptAndRunUpdate {

        @Test
        void yesChoice_invokesRunUpdate() {
            WorkflowProfileSwitchService spy = spy(service);
            doNothing().when(spy).runUpdate();

            try (MockedStatic<Messages> messages = mockStatic(Messages.class)) {
                messages.when(() -> Messages.showYesNoDialog(
                        any(Project.class), anyString(), anyString(), anyString(), anyString(), nullable(Icon.class)))
                        .thenReturn(Messages.YES);

                spy.promptAndRunUpdateIfConfirmed("custom");

                verify(spy).runUpdate();
            }
        }

        @Test
        void laterChoice_doesNotInvokeRunUpdate() {
            WorkflowProfileSwitchService spy = spy(service);

            try (MockedStatic<Messages> messages = mockStatic(Messages.class)) {
                messages.when(() -> Messages.showYesNoDialog(
                        any(Project.class), anyString(), anyString(), anyString(), anyString(), nullable(Icon.class)))
                        .thenReturn(Messages.NO);

                spy.promptAndRunUpdateIfConfirmed("custom");

                verify(spy, never()).runUpdate();
            }
        }
    }
}
