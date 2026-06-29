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

    /** @param root the initiative's own directory (where its artifacts live). */
    private static InitiativeEntry initiative(String root) {
        return new InitiativeEntry("init-1", "T", "", InitiativeStatus.ACTIVE, null, List.of(), "store-1", root);
    }

    @Test
    void resolvePathBuildsRootFileName(@TempDir Path tmp) {
        InitiativeEntry init = initiative(tmp.toString());
        Path tasks = InitiativeArtifact.TASKS.resolvePath(init);
        assertNotNull(tasks);
        // Directly under root — NOT root/<id>/tasks.md.
        assertEquals(tmp.resolve("tasks.md"), tasks);
    }

    @Test
    void resolvePathNullWhenRootUnknown() {
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
        Path design = Files.writeString(tmp.resolve("design.md"), "# Design");
        assertEquals(design, InitiativeArtifact.DESIGN.resolveExistingPath(init));
    }
}
