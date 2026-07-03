package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinationServiceParsingTest {

    // ---- CLI JSON parsing ----------------------------------------------------

    @Test
    void parsesWorkspaceListJson() {
        String json = """
                {"workspaces":[
                  {"name":"alpha","path":"/tmp/does-not-exist-alpha","resolvesLocally":true},
                  {"name":"beta","path":"/tmp/does-not-exist-beta"}
                ],"status":[]}
                """;
        List<WorkspaceEntry> result = CoordinationService.parseWorkspaces(json);
        assertEquals(2, result.size());
        assertEquals("alpha", result.get(0).name());
        assertTrue(result.get(0).resolvesLocally());
        // No explicit flag and a non-existent path → resolvesLocally derived as false.
        assertFalse(result.get(1).resolvesLocally());
    }

    @Test
    void parsesListJsonDespiteLeadingTelemetryNotice() {
        // Regression: on its first run the CLI prepends a telemetry notice to stdout, which
        // previously broke GSON parsing and silently dropped the panel to its disk fallback.
        String json = """
                Note: OpenSpec collects anonymous usage stats. Opt out: OPENSPEC_TELEMETRY=0
                {"workspaces":[{"name":"alpha","path":"/tmp/does-not-exist-alpha","resolvesLocally":true}],"status":[]}
                """;
        List<WorkspaceEntry> result = CoordinationService.parseWorkspaces(json);
        assertEquals(1, result.size());
        assertEquals("alpha", result.get(0).name());
    }

    @Test
    void parsesContextStoreListJsonWithSnakeCaseKeys() {
        String json = """
                {"context_stores":[
                  {"id":"store-1","root":"/data/store-1","metadata_path":"/data/store-1/.openspec-store.yaml"}
                ],"status":[]}
                """;
        List<ContextStoreEntry> result = CoordinationService.parseContextStores(json);
        assertEquals(1, result.size());
        assertEquals("store-1", result.get(0).id());
        assertEquals("/data/store-1", result.get(0).root());
        assertEquals("/data/store-1/.openspec-store.yaml", result.get(0).metadataPath());
    }

    @Test
    void parsesInitiativeListJsonAndStatus() {
        // Mirrors the real CLI: a flat `initiatives` array where each entry carries its own
        // `root` (the initiative directory) and `store` (the store id).
        String json = """
                {"context_store":null,"context_stores":[],
                 "initiatives":[
                   {"id":"init-1","title":"First","summary":"s","status":"active","created":"2026-06-01",
                    "owners":["jb"],"store":"store-1","root":"/data/store-1/initiatives/init-1"},
                   {"id":"init-2","title":"Second","status":"frozen","store":"store-1",
                    "root":"/data/store-1/initiatives/init-2"}
                 ],"status":[]}
                """;
        List<InitiativeEntry> result = CoordinationService.parseInitiatives(json);
        assertEquals(2, result.size());
        assertEquals(InitiativeStatus.ACTIVE, result.get(0).status());
        assertEquals("store-1", result.get(0).store());
        assertEquals("/data/store-1/initiatives/init-1", result.get(0).root());
        assertEquals(List.of("jb"), result.get(0).owners());
        // Unrecognized status from a future CLI is tolerated, not fatal.
        assertEquals(InitiativeStatus.UNKNOWN, result.get(1).status());
    }

    @Test
    void emptyArraysProduceEmptyLists() {
        assertTrue(CoordinationService.parseWorkspaces("{\"workspaces\":[]}").isEmpty());
        assertTrue(CoordinationService.parseContextStores("{\"context_stores\":[]}").isEmpty());
        assertTrue(CoordinationService.parseInitiatives("{\"initiatives\":[]}").isEmpty());
    }

    // ---- On-disk fallback ----------------------------------------------------

    @Test
    void readsWorkspacesFromDiskRegistry(@TempDir Path tmp) throws Exception {
        CoordinationPaths paths = paths(tmp);
        Path resolvedDir = Files.createDirectories(tmp.resolve("repo-here"));
        Files.createDirectories(paths.managedWorkspacesDir());
        Files.writeString(paths.workspaceRegistryFile(), """
                version: 1
                workspaces:
                  alpha: %s
                  beta: /nope/missing
                """.formatted(resolvedDir.toString()));

        List<WorkspaceEntry> result = CoordinationService.readWorkspacesFromDisk(paths);
        assertEquals(2, result.size());
        WorkspaceEntry alpha = result.stream().filter(w -> w.name().equals("alpha")).findFirst().orElseThrow();
        assertTrue(alpha.resolvesLocally());
        WorkspaceEntry beta = result.stream().filter(w -> w.name().equals("beta")).findFirst().orElseThrow();
        assertFalse(beta.resolvesLocally());
    }

    @Test
    void readsInitiativesFromDiskViaContextStoreBackend(@TempDir Path tmp) throws Exception {
        CoordinationPaths paths = paths(tmp);
        // A context store whose git backend local_path points at the store root; initiatives
        // live under <storeRoot>/initiatives/<id>/ (matching the real on-disk layout).
        Path storeRoot = Files.createDirectories(tmp.resolve("store-root"));
        Files.createDirectories(paths.contextStoresDir());
        Files.writeString(paths.contextStoreRegistryFile(), """
                version: 1
                stores:
                  store-1:
                    backend:
                      type: git
                      local_path: %s
                """.formatted(storeRoot.toString()));

        Path initiativeDir = Files.createDirectories(storeRoot.resolve("initiatives").resolve("init-1"));
        Files.writeString(initiativeDir.resolve("initiative.yaml"), """
                version: 1
                id: init-1
                title: Disk Initiative
                summary: read from yaml
                status: exploring
                created: "2026-06-02"
                owners:
                  - jb
                """);

        List<InitiativeEntry> result = CoordinationService.readInitiativesFromDisk(paths);
        assertEquals(1, result.size());
        InitiativeEntry init = result.get(0);
        assertEquals("init-1", init.id());
        assertEquals("Disk Initiative", init.title());
        assertEquals(InitiativeStatus.EXPLORING, init.status());
        assertEquals("store-1", init.store());
        assertEquals(initiativeDir.toString(), init.root());
        // Artifact paths resolve directly under the initiative root.
        assertEquals(initiativeDir.resolve("design.md"),
                InitiativeArtifact.DESIGN.resolvePath(init));
    }

    @Test
    void missingGlobalDataDirYieldsEmptyNotError(@TempDir Path tmp) {
        CoordinationPaths paths = paths(tmp.resolve("nonexistent"));
        assertTrue(CoordinationService.readWorkspacesFromDisk(paths).isEmpty());
        assertTrue(CoordinationService.readContextStoresFromDisk(paths).isEmpty());
        assertTrue(CoordinationService.readInitiativesFromDisk(paths).isEmpty());
    }

    private static CoordinationPaths paths(Path xdgDataHome) {
        Map<String, String> env = Map.of("XDG_DATA_HOME", xdgDataHome.toString());
        return CoordinationPaths.resolve(env::get, false, "/home/test");
    }

    @Test
    void coordinationPathsResolvesXdgThenPlatformFallbacks() {
        CoordinationPaths xdg = CoordinationPaths.resolve(k -> "XDG".equals(k) ? null
                : "XDG_DATA_HOME".equals(k) ? "/x" : null, false, "/home/u");
        assertEquals(Path.of("/x", "openspec"), xdg.globalDataDir());

        CoordinationPaths unix = CoordinationPaths.resolve(k -> null, false, "/home/u");
        assertEquals(Path.of("/home/u", ".local", "share", "openspec"), unix.globalDataDir());

        CoordinationPaths win = CoordinationPaths.resolve(
                k -> "LOCALAPPDATA".equals(k) ? "C:\\Users\\u\\AppData\\Local" : null, true, "C:\\Users\\u");
        assertNotNull(win.globalDataDir());
        assertTrue(win.globalDataDir().toString().contains("openspec"));
    }
}
