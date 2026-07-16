package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests that parse <b>real</b> OpenSpec CLI JSON captured as fixtures under
 * {@code src/test/resources/fixtures/cli/coordination-*.json}. These verify the
 * {@code CoordinationService} parsers against the actual CLI output shape (field names,
 * nesting), not a hand-written approximation.
 *
 * <p><b>These fixtures are permanently pinned (1.4-generation).</b> The {@code workspace},
 * {@code context-store}, and {@code initiative} commands were removed upstream at CLI 1.5.0,
 * so the captures can never be refreshed; they remain the only parse coverage for the
 * still-supported 1.4.x line. See {@code fixtures/cli/README.md} (provenance manifest).
 * Delete them only when 1.4.x support is dropped by a spec-level change.
 */
class CoordinationContractTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = CoordinationContractTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    @Test
    void parsesRealContextStoreList() {
        List<ContextStoreEntry> stores =
                CoordinationService.parseContextStores(fixture("coordination-context-store-list.json"));
        assertEquals(1, stores.size());
        assertEquals("teststore", stores.get(0).id());
        assertEquals("/fixture/context-stores/teststore", stores.get(0).root());
    }

    @Test
    void parsesRealInitiativeListFlatArrayWithOwnRoot() {
        List<InitiativeEntry> initiatives =
                CoordinationService.parseInitiatives(fixture("coordination-initiative-list.json"));
        assertEquals(1, initiatives.size());
        InitiativeEntry init = initiatives.get(0);
        assertEquals("test-init", init.id());
        assertEquals("Test Initiative", init.title());
        assertEquals("A test", init.summary());
        assertEquals(InitiativeStatus.EXPLORING, init.status());
        assertEquals("2026-06-29", init.created());
        assertEquals("teststore", init.store());
        // `root` is the initiative's OWN directory — artifacts resolve directly under it.
        assertEquals("/fixture/context-stores/teststore/initiatives/test-init", init.root());
    }

    @Test
    void initiativeArtifactPathUsesRootDirectlyNotNestedId() {
        InitiativeEntry init =
                CoordinationService.parseInitiatives(fixture("coordination-initiative-list.json")).get(0);
        // Regression guard: must be <root>/tasks.md, NOT <root>/<id>/tasks.md.
        // Compare Path to Path — Path.toString() is separator-dependent (backslashes on Windows).
        assertEquals(java.nio.file.Path.of("/fixture/context-stores/teststore/initiatives/test-init", "tasks.md"),
                InitiativeArtifact.TASKS.resolvePath(init));
    }

    @Test
    void parsesRealWorkspaceList() {
        List<WorkspaceEntry> workspaces =
                CoordinationService.parseWorkspaces(fixture("coordination-workspace-list.json"));
        assertEquals(1, workspaces.size());
        assertEquals("testws", workspaces.get(0).name());
        assertNotNull(workspaces.get(0).path());
        assertTrue(workspaces.get(0).path().contains("workspaces/testws"));
    }

    @Test
    void doctorFixtureHasExpectedHealthShape() {
        // The doctor JSON uses the `context_stores` array key with metadata.{present,valid}
        // and git.is_repository — the exact shape fetchContextStoreDoctor parses.
        String doctor = fixture("coordination-context-store-doctor.json");
        assertTrue(doctor.contains("\"context_stores\""));
        assertTrue(doctor.contains("\"present\": true"));
        assertTrue(doctor.contains("\"is_repository\""));
    }
}
