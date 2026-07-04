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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies the coordination CLI version window {@code [1.4.0, 1.5.0)}: coordination engages only on
 * a 1.4.x CLI, and on a CLI that removed the commands (>= 1.5.0) the surface stands down without
 * invoking a removed command or reaching the Full tier.
 *
 * <p>The {@link #perVersionBehaviorContract() per-version behavior contract} pins the FULL per-CLI
 * matrix (1.3.x / 1.4.x / 1.5.x). It is the regression guard for the 1.4 coordination write actions:
 * those actions shipped in v0.3.0/v0.3.1, were silently removed during a panel rebuild, and are
 * restored here version-gated. A test that asserts the 1.4.x write path is <em>enabled</em> (and the
 * 1.5.x path <em>disabled</em>) fails if either regresses again.
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

    // ---- Per-version behavior contract (1.3.x / 1.4.x / 1.5.x) ---------------

    /**
     * Pins the full per-CLI-version behavior contract. Each row asserts the version's expected
     * capabilities so a change that regresses a supported version's behavior fails here.
     *
     * <ul>
     *   <li><b>1.3.x</b> — below the coordination floor: no coordination, no store; no write path;
     *       both the legacy and store write actions short-circuit with guidance (never shell out).</li>
     *   <li><b>1.4.x</b> — inside the window {@code [1.4.0, 1.5.0)}: coordination reads engage and the
     *       legacy write path is ENABLED (this is the assertion that would have caught the silent
     *       removal); the 1.5 store path is disabled.</li>
     *   <li><b>1.5.x</b> — at/above the ceiling: the 1.5 store write path is enabled and the legacy
     *       coordination write path is disabled (its commands were removed in 1.5.0).</li>
     * </ul>
     */
    @Test
    void perVersionBehaviorContract() {
        // 1.3.x — below the coordination floor.
        for (String v : new String[]{"1.3.0", "1.3.9"}) {
            CoordinationService s = serviceWith(v, true);
            assertFalse(s.cliCoordinationAvailable(), v + ": coordination is a 1.4-line capability");
            assertFalse(s.cliStoreAvailable(), v + ": stores are a 1.5-line capability");
            assertFalse(CoordinationActionGating.coordinationWriteEnabled(true, s.cliCoordinationAvailable()),
                    v + ": no legacy coordination write path below the floor");
            assertFalse(CoordinationActionGating.writeEnabled(true, s.cliStoreAvailable()),
                    v + ": no store write path below the floor");
            // Below the floor every legacy write short-circuits with guidance — no shell-out.
            assertCoordinationWritesGated(s, v);
        }

        // 1.4.x — inside the coordination window; the legacy write path is ENABLED.
        for (String v : new String[]{"1.4.0", "1.4.9"}) {
            CoordinationService s = serviceWith(v, true);
            assertTrue(s.cliCoordinationAvailable(), v + ": inside [1.4.0, 1.5.0)");
            assertFalse(s.cliStoreAvailable(), v + ": store model is not yet the lead model");
            assertTrue(CoordinationActionGating.coordinationWriteEnabled(true, s.cliCoordinationAvailable()),
                    v + ": legacy coordination write path must be enabled in the window");
            assertFalse(CoordinationActionGating.writeEnabled(true, s.cliStoreAvailable()),
                    v + ": store write path is off in the 1.4 window");
            // The write methods must NOT short-circuit on a 1.4.x CLI: the gate passes, so they proceed
            // to the CLI invocation (which fails for lack of a real settings service / CLI in the unit
            // harness, yielding a non-guidance failure). A guidance message here would mean the gate
            // wrongly stood the write down — exactly the regression this contract guards.
            assertCoordinationWritesNotGated(s, v);
        }

        // 1.5.x — the store model leads; the legacy coordination write path is disabled.
        for (String v : new String[]{"1.5.0", "1.6.0"}) {
            CoordinationService s = serviceWith(v, true);
            assertFalse(s.cliCoordinationAvailable(), v + ": 1.4 commands were removed in 1.5.0");
            assertTrue(s.cliStoreAvailable(), v + ": store model leads at >= 1.5.0");
            assertFalse(CoordinationActionGating.coordinationWriteEnabled(true, s.cliCoordinationAvailable()),
                    v + ": legacy coordination write path must be off at >= 1.5.0");
            assertTrue(CoordinationActionGating.writeEnabled(true, s.cliStoreAvailable()),
                    v + ": store write path is enabled at >= 1.5.0");
            // The legacy writes short-circuit with guidance and never invoke a removed command.
            assertCoordinationWritesGated(s, v);
        }
    }

    /** Asserts all three legacy writes short-circuit with the window guidance (no shell-out). */
    private void assertCoordinationWritesGated(CoordinationService s, String version) {
        assertEquals(CoordinationService.COORDINATION_WRITE_GUIDANCE,
                s.createInitiative("demo", "Demo").message(),
                version + ": createInitiative must short-circuit outside the window");
        assertEquals(CoordinationService.COORDINATION_WRITE_GUIDANCE,
                s.setupContextStore("demo").message(),
                version + ": setupContextStore must short-circuit outside the window");
        assertEquals(CoordinationService.COORDINATION_WRITE_GUIDANCE,
                s.setupWorkspace("demo").message(),
                version + ": setupWorkspace must short-circuit outside the window");
    }

    /** Asserts the three legacy writes proceed past the gate on an in-window CLI (non-guidance fail). */
    private void assertCoordinationWritesNotGated(CoordinationService s, String version) {
        assertFalse(s.createInitiative("demo", "Demo").success(),
                version + ": no real CLI in the harness, so the write still fails");
        assertNotEquals(CoordinationService.COORDINATION_WRITE_GUIDANCE,
                s.createInitiative("demo", "Demo").message(),
                version + ": createInitiative must NOT short-circuit in the window");
        assertNotEquals(CoordinationService.COORDINATION_WRITE_GUIDANCE,
                s.setupContextStore("demo").message(),
                version + ": setupContextStore must NOT short-circuit in the window");
        assertNotEquals(CoordinationService.COORDINATION_WRITE_GUIDANCE,
                s.setupWorkspace("demo").message(),
                version + ": setupWorkspace must NOT short-circuit in the window");
    }

}
