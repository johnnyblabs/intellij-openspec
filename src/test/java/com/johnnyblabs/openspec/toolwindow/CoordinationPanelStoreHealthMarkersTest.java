package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.coordination.StoreEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the store-row health-marker presentation ({@link CoordinationPanel#storeHealthMarkers}):
 * markers derive solely from the doctor-reported flags on the entry. The healthy-empty state a
 * CLI 1.6+ fresh store reports (healthy openspec-root, planning directories absent) must render
 * with no unhealthy/error marker — see the healthy-empty doctor contract test in
 * {@code StoreWorksetContractTest} for the fixture-level half of this behavior.
 */
class CoordinationPanelStoreHealthMarkersTest {

    private static StoreEntry entry(Boolean metadataPresent, Boolean metadataValid,
                                    Boolean git, Boolean rootHealthy) {
        return StoreEntry.basic("s", "/fixture/s")
                .withDoctor(metadataPresent, metadataValid, git, rootHealthy, List.of());
    }

    @Test
    void healthyEmptyStoreGetsNoErrorMarker() {
        // The 1.6 fresh/config-only store: doctor says healthy:true, planning dirs present:false.
        // The entry carries openspecRootHealthy == TRUE and must get no error marker at all.
        Map<String, Boolean> markers = CoordinationPanel.storeHealthMarkers(
                entry(true, true, false, true));
        assertFalse(markers.containsKey("unhealthy openspec-root"));
        assertFalse(markers.containsValue(true), "a healthy-empty store must render with no error marker");
        assertEquals(Map.of("not a git repo", false), markers);
    }

    @Test
    void unhealthyRootStillGetsErrorMarker() {
        Map<String, Boolean> markers = CoordinationPanel.storeHealthMarkers(
                entry(true, true, true, false));
        assertEquals(Boolean.TRUE, markers.get("unhealthy openspec-root"));
    }

    @Test
    void unknownHealthGetsNoMarker() {
        // List-tier entries (doctor not yet run) leave health null — unknown is not unhealthy.
        Map<String, Boolean> markers = CoordinationPanel.storeHealthMarkers(
                StoreEntry.basic("s", "/fixture/s"));
        assertTrue(markers.isEmpty());
    }

    @Test
    void metadataIssueStillGetsErrorMarker() {
        Map<String, Boolean> markers = CoordinationPanel.storeHealthMarkers(
                entry(true, false, null, true));
        assertEquals(Boolean.TRUE, markers.get("metadata issue"));
    }
}
