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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueLifecycleServiceTest {

    @Mock Project project;
    @Mock OpenSpecSettings settings;
    @Mock ChangeService changeService;

    private IssueLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new IssueLifecycleService(project);
    }

    @Nested
    class NoMatchingTracker {

        @Test
        void onArchive_noTrackingMetadata_returnsNormally() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(true);

                // No matching change found — getTrackingMetadata returns null
                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());

                assertDoesNotThrow(() -> service.onArchive("nonexistent-change", "/tmp/dir"));
            }
        }

        @Test
        void onApply_noTrackingMetadata_returnsNormally() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(true);

                when(project.getService(ChangeService.class)).thenReturn(changeService);
                when(changeService.getActiveChanges()).thenReturn(List.of());

                assertDoesNotThrow(() -> service.onApply("nonexistent-change", "/tmp/dir"));
            }
        }

        @Test
        void onPropose_trackersDisabled_returnsNormally() {
            try (MockedStatic<OpenSpecSettings> settingsMock = mockStatic(OpenSpecSettings.class)) {
                settingsMock.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.isForgejoEnabled()).thenReturn(false);
                when(settings.isPlaneEnabled()).thenReturn(false);

                assertDoesNotThrow(() -> service.onPropose("change", "/tmp/dir"));
            }
        }
    }

    @Nested
    class TitleConversion {

        @Test
        void kebabToTitleCase() {
            assertEquals("Ensure Integration Testing",
                    IssueLifecycleService.toTitleCase("ensure-integration-testing"));
        }

        @Test
        void singleWord() {
            assertEquals("Fix", IssueLifecycleService.toTitleCase("fix"));
        }

        @Test
        void nullReturnsNull() {
            assertNull(IssueLifecycleService.toTitleCase(null));
        }

        @Test
        void emptyReturnsEmpty() {
            assertEquals("", IssueLifecycleService.toTitleCase(""));
        }
    }
}
