package com.johnnyb.openspec.tracking;

import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeMetadata;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveSyncServiceTest {

    @Mock Project project;
    @Mock OpenSpecSettings settings;
    @Mock ChangeService changeService;
    @Mock ForgejoService forgejoService;
    @Mock PlaneService planeService;

    private ArchiveSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new ArchiveSyncService(project);
    }

    private Change makeChangeWithTracking(String name, int forgejoIssue, String planeId) {
        Change change = new Change(name, "/tmp/" + name);
        ChangeMetadata metadata = new ChangeMetadata();
        ChangeMetadata.TrackingMetadata tracking = new ChangeMetadata.TrackingMetadata();

        if (forgejoIssue > 0) {
            ChangeMetadata.ForgejoRef ref = new ChangeMetadata.ForgejoRef();
            ref.setIssueNumber(forgejoIssue);
            ref.setIssueUrl("http://forgejo/issue/" + forgejoIssue);
            tracking.setForgejo(ref);
        }
        if (planeId != null) {
            ChangeMetadata.PlaneRef ref = new ChangeMetadata.PlaneRef();
            ref.setWorkItemId(planeId);
            ref.setWorkItemUrl("http://plane/item/" + planeId);
            tracking.setPlane(ref);
        }

        metadata.setTracking(tracking);
        change.setMetadata(metadata);
        return change;
    }

    @Nested
    class ArchiveSuccessSyncSuccess {

        @Test
        void bothTrackersUpdateSuccessfully() throws Exception {
            Change change = makeChangeWithTracking("test-change", 42, "plane-123");

            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(true);
                when(settings.isPlaneEnabled()).thenReturn(true);
                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());
                when(changeService.getArchivedChanges()).thenReturn(List.of(change));
                when(project.getService(ForgejoService.class)).thenReturn(forgejoService);
                when(project.getService(PlaneService.class)).thenReturn(planeService);
                when(forgejoService.isIssueClosed(42)).thenReturn(false);
                when(planeService.isWorkItemInState("plane-123", "Done")).thenReturn(false);

                ArchiveSyncService.SyncResult result = syncService.sync("test-change");

                assertTrue(result.isSuccess());
                assertEquals(ArchiveSyncService.SyncState.SUCCESS, result.state());
                verify(forgejoService).addComment(42, "Change archived");
                verify(forgejoService).updateIssue(42, "closed", List.of("done"));
                verify(planeService).updateWorkItemState("plane-123", "Done");
            }
        }
    }

    @Nested
    class ArchiveSuccessSyncFailureRetry {

        @Test
        void forgejoFailsPlaneSucceeds_partialFailure() throws Exception {
            Change change = makeChangeWithTracking("test-change", 42, "plane-123");

            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(true);
                when(settings.isPlaneEnabled()).thenReturn(true);
                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());
                when(changeService.getArchivedChanges()).thenReturn(List.of(change));
                when(project.getService(ForgejoService.class)).thenReturn(forgejoService);
                when(project.getService(PlaneService.class)).thenReturn(planeService);
                when(forgejoService.isIssueClosed(42)).thenThrow(new IOException("Network error"));
                when(planeService.isWorkItemInState("plane-123", "Done")).thenReturn(false);

                ArchiveSyncService.SyncResult result = syncService.sync("test-change");

                assertEquals(ArchiveSyncService.SyncState.PARTIAL_FAILURE, result.state());
                assertTrue(result.isRetryable());
                assertTrue(result.message().contains("Forgejo"));
            }
        }

        @Test
        void retrySucceedsAfterFailure() throws Exception {
            Change change = makeChangeWithTracking("test-change", 42, "plane-123");

            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(true);
                when(settings.isPlaneEnabled()).thenReturn(true);
                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());
                when(changeService.getArchivedChanges()).thenReturn(List.of(change));
                when(project.getService(ForgejoService.class)).thenReturn(forgejoService);
                when(project.getService(PlaneService.class)).thenReturn(planeService);
                // Both already done on retry
                when(forgejoService.isIssueClosed(42)).thenReturn(true);
                when(planeService.isWorkItemInState("plane-123", "Done")).thenReturn(true);

                ArchiveSyncService.SyncResult result = syncService.sync("test-change");

                assertTrue(result.isSuccess());
                // Should NOT call update methods since already in terminal state
                verify(forgejoService, never()).addComment(anyInt(), anyString());
                verify(planeService, never()).updateWorkItemState(anyString(), anyString());
            }
        }
    }

    @Nested
    class IdempotentSync {

        @Test
        void repeatedSyncDoesNotDuplicateForgejoClose() throws Exception {
            Change change = makeChangeWithTracking("test-change", 42, null);

            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(true);
                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());
                when(changeService.getArchivedChanges()).thenReturn(List.of(change));
                when(project.getService(ForgejoService.class)).thenReturn(forgejoService);
                when(forgejoService.isIssueClosed(42)).thenReturn(true);

                ArchiveSyncService.SyncResult result = syncService.sync("test-change");

                assertTrue(result.isSuccess());
                verify(forgejoService, never()).addComment(anyInt(), anyString());
                verify(forgejoService, never()).updateIssue(anyInt(), anyString(), anyList());
            }
        }

        @Test
        void repeatedSyncDoesNotDuplicatePlaneClose() throws Exception {
            Change change = makeChangeWithTracking("test-change", 0, "plane-123");

            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isPlaneEnabled()).thenReturn(true);
                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());
                when(changeService.getArchivedChanges()).thenReturn(List.of(change));
                when(project.getService(PlaneService.class)).thenReturn(planeService);
                when(planeService.isWorkItemInState("plane-123", "Done")).thenReturn(true);

                ArchiveSyncService.SyncResult result = syncService.sync("test-change");

                assertTrue(result.isSuccess());
                verify(planeService, never()).updateWorkItemState(anyString(), anyString());
            }
        }
    }

    @Nested
    class MissingTrackingMetadata {

        @Test
        void noTrackingMetadata_returnsSkipped() {
            Change change = new Change("test-change", "/tmp/test-change");
            change.setMetadata(new ChangeMetadata());

            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(true);
                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());
                when(changeService.getArchivedChanges()).thenReturn(List.of(change));

                ArchiveSyncService.SyncResult result = syncService.sync("test-change");

                assertEquals(ArchiveSyncService.SyncState.SKIPPED, result.state());
            }
        }

        @Test
        void changeNotFound_returnsSkipped() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(true);
                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());
                when(changeService.getArchivedChanges()).thenReturn(List.of());

                ArchiveSyncService.SyncResult result = syncService.sync("nonexistent");

                assertEquals(ArchiveSyncService.SyncState.SKIPPED, result.state());
            }
        }

        @Test
        void trackersDisabled_returnsSkipped() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(false);

                ArchiveSyncService.SyncResult result = syncService.sync("test-change");

                assertEquals(ArchiveSyncService.SyncState.SKIPPED, result.state());
            }
        }
    }
}
