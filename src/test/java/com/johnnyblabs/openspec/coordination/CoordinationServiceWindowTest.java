package com.johnnyblabs.openspec.coordination;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies the coordination CLI version window {@code [1.4.0, 1.5.0)}: coordination engages only on
 * a 1.4.x CLI, and on a CLI that removed the commands (>= 1.5.0) the surface stands down without
 * invoking a removed command or reaching the Full tier.
 */
@ExtendWith(MockitoExtension.class)
class CoordinationServiceWindowTest {

    @Mock Project project;
    @Mock CliDetectionService detection;

    private CoordinationService serviceWith(String version, boolean available) {
        CoordinationService service = new CoordinationService(project);
        lenient().when(project.getService(CliDetectionService.class)).thenReturn(detection);
        lenient().when(detection.isAvailable()).thenReturn(available);
        lenient().when(detection.getDetectedVersion()).thenReturn(version);
        return service;
    }

    // ---- 6.3: cliCoordinationAvailable() window boundary ---------------------

    @Test
    void coordinationAvailableOnlyInsideTheWindow() {
        assertFalse(serviceWith("1.3.0", true).cliCoordinationAvailable(), "below floor");
        assertTrue(serviceWith("1.4.0", true).cliCoordinationAvailable(), "floor is inclusive");
        assertTrue(serviceWith("1.4.1", true).cliCoordinationAvailable());
        assertFalse(serviceWith("1.5.0", true).cliCoordinationAvailable(), "ceiling is exclusive — removed in 1.5.0");
        assertFalse(serviceWith("1.6.0", true).cliCoordinationAvailable());
    }

    @Test
    void coordinationUnavailableWhenCliMissing() {
        assertFalse(serviceWith("1.4.1", false).cliCoordinationAvailable(), "not available");
    }

    // ---- 6.4: stand-down on CLI >= 1.5.0 -------------------------------------

    @Test
    void onCli150ModeMarkerDoesNotForceNonHiddenTier(@TempDir Path emptyHome) {
        // Empty data dir → no on-disk coordination state. On a 1.5.0 CLI a lingering non-default
        // mode marker must NOT elevate the tier: no state → Hidden, and never Full (cli unavailable).
        CoordinationService service = serviceWith("1.5.0", true);
        service.setPathsForTest(CoordinationPaths.resolve(k -> null, false, emptyHome.toString()));

        CoordinationData data = service.getCoordinationData(/* coordinationModeActive */ true);

        assertFalse(data.sourcedFromCli(), "coordination CLI is unavailable at >= 1.5.0");
        assertEquals(CoordinationTier.HIDDEN, data.tier(),
                "a stale mode marker must not force a non-Hidden tier on a 1.5.0 CLI");
        assertFalse(data.tier().allowsWriteActions(), "Full tier must be unreachable at >= 1.5.0");
    }

    // ---- 6.5: write actions are guarded on CLI >= 1.5.0 (no removed command run) --

    @Test
    void writeActionsFailWithoutInvokingRemovedCommandsOnCli150() {
        // detection is stubbed at 1.5.0 and NO ProcessRunner is configured; if a write reached
        // CliRunner it would try to launch a real process. The guard short-circuits first, so each
        // returns a failure and never invokes a removed command.
        CoordinationService service = serviceWith("1.5.0", true);

        CoordinationService.WriteResult init = service.createInitiative("id", "Title");
        CoordinationService.WriteResult store = service.setupContextStore("id");
        CoordinationService.WriteResult ws = service.setupWorkspace("name");

        assertFalse(init.success(), "createInitiative must fail on a 1.5.0 CLI");
        assertFalse(store.success(), "setupContextStore must fail on a 1.5.0 CLI");
        assertFalse(ws.success(), "setupWorkspace must fail on a 1.5.0 CLI");
    }
}
