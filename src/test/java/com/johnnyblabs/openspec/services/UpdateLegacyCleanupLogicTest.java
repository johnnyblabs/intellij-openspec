package com.johnnyblabs.openspec.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure decision logic of the update legacy-cleanup flow:
 * set identity (dismissal memory), regeneration-loop detection, and the
 * project-root scope lock on deletable paths.
 */
class UpdateLegacyCleanupLogicTest {

    @Test
    void setKey_isOrderInsensitive() {
        String a = UpdateLegacyCleanupService.setKey(List.of("b.md", "a.md"));
        String b = UpdateLegacyCleanupService.setKey(List.of("a.md", "b.md"));
        assertEquals(a, b);
        assertNotEquals(a, UpdateLegacyCleanupService.setKey(List.of("a.md")));
    }

    @Test
    void regenerationLoop_detectedWhenRemovedFilesReturn() {
        List<String> removed = List.of(".junie/commands/opsx-apply.md", ".junie/commands/opsx-sync.md");
        assertTrue(UpdateLegacyCleanupService.isRegenerationLoop(removed, removed));
        assertTrue(UpdateLegacyCleanupService.isRegenerationLoop(removed,
                        List.of(".junie/commands/opsx-apply.md")),
                "any removed file returning means the CLI regenerates");
    }

    @Test
    void cleanOrDisjointAfterState_isNotALoop() {
        List<String> removed = List.of(".junie/commands/opsx-apply.md");
        assertFalse(UpdateLegacyCleanupService.isRegenerationLoop(removed, List.of()));
        assertFalse(UpdateLegacyCleanupService.isRegenerationLoop(removed,
                        List.of(".other-tool/commands/legacy.md")),
                "a different pending set is new information, not a loop");
    }

    @Test
    void scopeLock_discardsPathsEscapingTheProjectRoot(@TempDir Path root) {
        List<Path> kept = UpdateLegacyCleanupService.filterInsideRoot(root, List.of(
                ".junie/commands/opsx-apply.md",
                "../outside/evil.md",
                "/etc/passwd",
                "nested/../.junie/commands/opsx-sync.md"));
        assertEquals(2, kept.size(), "escaping and absolute-outside paths are discarded: " + kept);
        assertTrue(kept.stream().allMatch(p -> p.startsWith(root.toAbsolutePath().normalize())));
    }
}
