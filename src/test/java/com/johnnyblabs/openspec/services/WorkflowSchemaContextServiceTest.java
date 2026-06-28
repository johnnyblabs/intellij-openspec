package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.model.WorkflowSchemaContext;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowSchemaContextServiceTest {

    @Mock Project project;
    @Mock CliDetectionService detection;
    @Mock ArtifactOrchestrationService orchestration;
    @Mock OpenSpecSettings settings;

    private WorkflowSchemaContextService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowSchemaContextService(project);
        lenient().when(project.getService(CliDetectionService.class)).thenReturn(detection);
        lenient().when(project.getService(ArtifactOrchestrationService.class)).thenReturn(orchestration);
    }

    @Test
    void cliBelowFloorFallsBackWithoutCallingStatus() {
        try (MockedStatic<OpenSpecSettings> s = mockStatic(OpenSpecSettings.class)) {
            s.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
            when(settings.getEffectiveVersion(project)).thenReturn("1.2.0");
            when(detection.isAvailable()).thenReturn(true);
            when(detection.getDetectedVersion()).thenReturn("1.2.99"); // below the 1.3 floor

            WorkflowSchemaContext ctx = service.getContext("c");

            assertTrue(ctx.isSpecDrivenRepoLocal());
            assertFalse(ctx.cliActionContextAvailable());
            assertEquals("1.2.0", ctx.configFormatVersion());
            verifyNoInteractions(orchestration); // never asked the CLI for status
        }
    }

    @Test
    void cliAtFloorResolvesNonDefaultModeFromStatus() {
        try (MockedStatic<OpenSpecSettings> s = mockStatic(OpenSpecSettings.class)) {
            s.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
            when(settings.getEffectiveVersion(project)).thenReturn("1.2.0");
            when(detection.isAvailable()).thenReturn(true);
            when(detection.getDetectedVersion()).thenReturn("1.4.0");

            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setSchemaName("workspace-planning");
            ChangeArtifactDag.ActionContext ac = new ChangeArtifactDag.ActionContext();
            ac.setMode("workspace-planning");
            ac.setSourceOfTruth("workspace");
            dag.setActionContext(ac);
            when(orchestration.getArtifactStatus("c")).thenReturn(dag);

            WorkflowSchemaContext ctx = service.getContext("c");

            assertEquals("workspace-planning", ctx.mode());
            assertTrue(ctx.isNonDefaultMode());
            assertTrue(ctx.cliActionContextAvailable());
            // Version axes are kept separate.
            assertEquals("1.4.0", ctx.cliVersion());
            assertEquals("1.2.0", ctx.configFormatVersion());
        }
    }

    @Test
    void atFloorButNoActionContextFallsBack() {
        try (MockedStatic<OpenSpecSettings> s = mockStatic(OpenSpecSettings.class)) {
            s.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
            when(settings.getEffectiveVersion(project)).thenReturn("1.2.0");
            when(detection.isAvailable()).thenReturn(true);
            when(detection.getDetectedVersion()).thenReturn("1.4.0");

            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setSchemaName("spec-driven"); // no actionContext block
            when(orchestration.getArtifactStatus("c")).thenReturn(dag);

            WorkflowSchemaContext ctx = service.getContext("c");

            assertTrue(ctx.isSpecDrivenRepoLocal());
            assertFalse(ctx.cliActionContextAvailable());
        }
    }

    @Test
    void cachesAndInvalidates() {
        try (MockedStatic<OpenSpecSettings> s = mockStatic(OpenSpecSettings.class)) {
            s.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
            when(settings.getEffectiveVersion(project)).thenReturn("1.2.0");
            when(detection.isAvailable()).thenReturn(false); // fallback path

            assertNull(service.getCachedContext("c"));
            WorkflowSchemaContext ctx = service.getContext("c");
            assertSame(ctx, service.getCachedContext("c"));

            service.invalidateCache("c");
            assertNull(service.getCachedContext("c"));
        }
    }
}
