package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InitiativeArtifactTest {

    private static InitiativeEntry initiative(String storeRoot) {
        return new InitiativeEntry("init-1", "T", "", InitiativeStatus.ACTIVE, null, List.of(), storeRoot);
    }

    @Test
    void resolvePathBuildsStoreRootIdFileName(@TempDir Path tmp) {
        InitiativeEntry init = initiative(tmp.toString());
        Path tasks = InitiativeArtifact.TASKS.resolvePath(init);
        assertNotNull(tasks);
        assertEquals(tmp.resolve("init-1").resolve("tasks.md"), tasks);
    }

    @Test
    void resolvePathNullWhenStoreRootUnknown() {
        InitiativeEntry init = initiative(null);
        assertNull(InitiativeArtifact.TASKS.resolvePath(init));
    }

    @Test
    void resolveExistingPathReturnsNullForMissingArtifact(@TempDir Path tmp) {
        InitiativeEntry init = initiative(tmp.toString());
        // Nothing created on disk yet → "not created".
        assertNull(InitiativeArtifact.DESIGN.resolveExistingPath(init));
    }

    @Test
    void resolveExistingPathReturnsPathWhenArtifactExists(@TempDir Path tmp) throws Exception {
        InitiativeEntry init = initiative(tmp.toString());
        Path dir = Files.createDirectories(tmp.resolve("init-1"));
        Path design = Files.writeString(dir.resolve("design.md"), "# Design");
        assertEquals(design, InitiativeArtifact.DESIGN.resolveExistingPath(init));
    }
}
