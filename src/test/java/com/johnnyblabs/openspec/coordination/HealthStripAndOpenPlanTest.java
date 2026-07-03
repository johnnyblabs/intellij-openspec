package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the doctor-driven health-strip selection ({@link CoordinationService#highestActionableDiagnostic})
 * and the workset-open folder plan ({@link WorksetOpenPlan}) — both pure, IDE-free logic.
 */
class HealthStripAndOpenPlanTest {

    private static StoreEntry storeWith(String id, List<Diagnostic> diagnostics) {
        return StoreEntry.basic(id, "/fixture/" + id)
                .withDoctor(null, null, null, null, diagnostics);
    }

    // ---- T.5: health strip renders highest severity, hidden when only info --

    @Test
    void highestActionableDiagnosticPicksErrorAcrossStores() {
        List<StoreEntry> stores = List.of(
                storeWith("a", List.of(new Diagnostic("warning", "w", "warn", null, "warn fix"))),
                storeWith("b", List.of(new Diagnostic("error", "e", "boom", null, "err fix"))));
        Diagnostic top = CoordinationService.highestActionableDiagnostic(stores);
        assertNotNull(top);
        assertEquals("error", top.severity());
        assertEquals("err fix", top.fix(), "the fix is retained verbatim for the inline action");
    }

    @Test
    void healthStripHiddenWhenOnlyInfoOrHealthy() {
        List<StoreEntry> infoOnly = List.of(
                storeWith("a", List.of(new Diagnostic("info", "i", "fyi", null, null))));
        assertNull(CoordinationService.highestActionableDiagnostic(infoOnly),
                "info-only → strip hidden");

        List<StoreEntry> healthy = List.of(storeWith("a", List.of()));
        assertNull(CoordinationService.highestActionableDiagnostic(healthy),
                "no diagnostics → strip hidden");
    }

    @Test
    void warningIsActionableEvenWithoutAnError() {
        List<StoreEntry> stores = List.of(
                storeWith("a", List.of(new Diagnostic("warning", "w", "heads up", null, "w fix"))));
        Diagnostic top = CoordinationService.highestActionableDiagnostic(stores);
        assertNotNull(top);
        assertEquals("warning", top.severity());
    }

    // ---- workset open plan ---------------------------------------------------

    @Test
    void orderedPathsPreservesMemberOrderAndDropsBlanks() {
        WorksetEntry ws = new WorksetEntry("v", List.of(
                new WorksetEntry.Member("primary", "/fixture/a"),
                new WorksetEntry.Member("blank", ""),
                new WorksetEntry.Member("secondary", "/fixture/b")));
        List<String> paths = WorksetOpenPlan.orderedPaths(ws);
        assertEquals(List.of("/fixture/a", "/fixture/b"), paths);
        assertEquals(2, WorksetOpenPlan.folderCount(ws), "the confirmation count skips blank rows");
    }

    @Test
    void emptyWorksetPlansNoFolders() {
        assertTrue(WorksetOpenPlan.orderedPaths(new WorksetEntry("v", List.of())).isEmpty());
    }
}
