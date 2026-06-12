package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@code OpenSpecProjectService.StartupDetection.execute} routing of CLI-state
 * to the appropriate notifier method. The decision logic itself is the simple chain:
 * not-an-OpenSpec-project → no-op; no-CLI → cliMissing; below-floor → cliBelowFloor;
 * at-or-above-floor → no notification.
 *
 * <p>Comparison semantics for the floor check live in {@link com.johnnyblabs.openspec.util.CliVersion}
 * and are covered exhaustively by {@code CliVersionAtLeastTest}; this test just pins the
 * "which notifier method was called for this CLI state" routing decision.
 */
@ExtendWith(MockitoExtension.class)
class CliFloorNotificationTest {

    @Mock Project project;
    @Mock CliDetectionService cliDetection;
    @Mock AiToolDetectionService aiDetection;
    @Mock Continuation<? super Unit> continuation;

    private OpenSpecProjectService.StartupDetection startup() {
        return new OpenSpecProjectService.StartupDetection();
    }

    private void wireServices() {
        when(project.getService(CliDetectionService.class)).thenReturn(cliDetection);
        when(project.getService(AiToolDetectionService.class)).thenReturn(aiDetection);
    }

    @Test
    void notAnOpenSpecProject_noNotificationFires() {
        try (MockedStatic<OpenSpecFileUtil> file = mockStatic(OpenSpecFileUtil.class);
             MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            file.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(false);

            startup().execute(project, continuation);

            notif.verifyNoInteractions();
        }
    }

    @Test
    void cliMissing_firesCliMissingNotification() {
        try (MockedStatic<OpenSpecFileUtil> file = mockStatic(OpenSpecFileUtil.class);
             MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            file.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
            wireServices();
            when(cliDetection.isAvailable()).thenReturn(false);

            startup().execute(project, continuation);

            notif.verify(() -> OpenSpecNotifier.cliMissing(project));
            notif.verify(() -> OpenSpecNotifier.cliBelowFloor(any(Project.class), any(String.class)), never());
        }
    }

    @Test
    void cliBelowFloor_1_2_0_firesFloorNotification() {
        try (MockedStatic<OpenSpecFileUtil> file = mockStatic(OpenSpecFileUtil.class);
             MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            file.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
            wireServices();
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.2.0");

            startup().execute(project, continuation);

            notif.verify(() -> OpenSpecNotifier.cliBelowFloor(eq(project), eq("1.2.0")));
            notif.verify(() -> OpenSpecNotifier.cliMissing(any(Project.class)), never());
        }
    }

    @Test
    void cliBelowFloor_1_0_0_firesFloorNotification() {
        try (MockedStatic<OpenSpecFileUtil> file = mockStatic(OpenSpecFileUtil.class);
             MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            file.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
            wireServices();
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.0.0");

            startup().execute(project, continuation);

            notif.verify(() -> OpenSpecNotifier.cliBelowFloor(eq(project), eq("1.0.0")));
        }
    }

    @Test
    void cliAtFloor_1_3_0_firesNoNotification() {
        try (MockedStatic<OpenSpecFileUtil> file = mockStatic(OpenSpecFileUtil.class);
             MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            file.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
            wireServices();
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.3.0");

            startup().execute(project, continuation);

            notif.verify(() -> OpenSpecNotifier.cliMissing(any(Project.class)), never());
            notif.verify(() -> OpenSpecNotifier.cliBelowFloor(any(Project.class), any(String.class)), never());
        }
    }

    @Test
    void cliAboveFloor_1_4_x_firesNoNotification() {
        try (MockedStatic<OpenSpecFileUtil> file = mockStatic(OpenSpecFileUtil.class);
             MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            file.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
            wireServices();
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn("1.4.1");

            startup().execute(project, continuation);

            notif.verify(() -> OpenSpecNotifier.cliMissing(any(Project.class)), never());
            notif.verify(() -> OpenSpecNotifier.cliBelowFloor(any(Project.class), any(String.class)), never());
        }
    }

    @Test
    void cliVersionNull_treatsAsBelowFloor_firesFloorNotificationWithUnknownLabel() {
        try (MockedStatic<OpenSpecFileUtil> file = mockStatic(OpenSpecFileUtil.class);
             MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            file.when(() -> OpenSpecFileUtil.isOpenSpecProject(project)).thenReturn(true);
            wireServices();
            when(cliDetection.isAvailable()).thenReturn(true);
            when(cliDetection.getDetectedVersion()).thenReturn(null);

            startup().execute(project, continuation);

            // Null version: CliVersion.atLeast returns false → floor notification fires with "unknown" label
            notif.verify(() -> OpenSpecNotifier.cliBelowFloor(eq(project), eq("unknown")));
        }
    }
}
