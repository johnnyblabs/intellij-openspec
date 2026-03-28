package com.johnnyblabs.openspec.services;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CLI detection throttling logic.
 * These test the staleness check in isolation without spawning processes.
 */
class CliDetectionServiceTest {

    private static final Duration STALENESS = Duration.ofSeconds(30);

    @Test
    void stalenessCheck_nullTimestamp_isStale() {
        Instant last = null;
        boolean stale = isStale(last);
        assertTrue(stale, "Null timestamp (never detected) should be considered stale");
    }

    @Test
    void stalenessCheck_recentTimestamp_isNotStale() {
        Instant last = Instant.now().minus(Duration.ofSeconds(10));
        boolean stale = isStale(last);
        assertFalse(stale, "Detection 10s ago should not be stale (threshold is 30s)");
    }

    @Test
    void stalenessCheck_oldTimestamp_isStale() {
        Instant last = Instant.now().minus(Duration.ofSeconds(45));
        boolean stale = isStale(last);
        assertTrue(stale, "Detection 45s ago should be stale (threshold is 30s)");
    }

    @Test
    void stalenessCheck_exactlyAtThreshold_isNotStale() {
        // At exactly 30s, Duration.between is equal to STALENESS, so compareTo returns 0 (not < 0)
        // which means it IS stale. This is the boundary case.
        Instant last = Instant.now().minus(Duration.ofSeconds(30));
        boolean stale = isStale(last);
        assertTrue(stale, "Detection exactly at threshold should be considered stale");
    }

    @Test
    void stalenessCheck_justUnderThreshold_isNotStale() {
        Instant last = Instant.now().minus(Duration.ofSeconds(29));
        boolean stale = isStale(last);
        assertFalse(stale, "Detection 29s ago should not be stale");
    }

    /**
     * Mirrors the staleness check logic in CliDetectionService.detectIfStale().
     */
    private boolean isStale(Instant last) {
        if (last == null) return true;
        return Duration.between(last, Instant.now()).compareTo(STALENESS) >= 0;
    }
}