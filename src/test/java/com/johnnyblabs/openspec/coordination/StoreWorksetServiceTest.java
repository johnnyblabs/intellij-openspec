package com.johnnyblabs.openspec.coordination;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * Service-level tests for the read-only OpenSpec 1.5 store/workset surface: on-disk fallback,
 * version gating at the {@code 1.5.0} floor, root canonicalization, legacy demotion, and the
 * beta-guarded degradation to disk on a malformed CLI payload.
 */
@ExtendWith(MockitoExtension.class)
class StoreWorksetServiceTest {

    @Mock Project project;
    @Mock CliDetectionService detection;

    private CoordinationService serviceWith(String version, boolean available) {
        CoordinationService service = new CoordinationService(project);
        lenient().when(project.getService(CliDetectionService.class)).thenReturn(detection);
        lenient().when(detection.isAvailable()).thenReturn(available);
        lenient().when(detection.getDetectedVersion()).thenReturn(version);
        return service;
    }

    private static CoordinationPaths paths(Path xdgDataHome) {
        Map<String, String> env = Map.of("XDG_DATA_HOME", xdgDataHome.toString());
        return CoordinationPaths.resolve(env::get, false, "/home/test");
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.5.0/" + name;
        try (InputStream is = StoreWorksetServiceTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- T.6: on-disk fallback ----------------------------------------------

    @Test
    void readsStoresFromDiskRegistry(@TempDir Path tmp) throws Exception {
        CoordinationPaths p = paths(tmp);
        Files.createDirectories(p.storesDir());
        // Contract-test against the CAPTURED on-disk registry shape (sanitized paths).
        Files.writeString(p.storeRegistryFile(), fixture("stores-registry.yaml"));

        List<StoreEntry> stores = CoordinationService.readStoresFromDisk(p);
        assertEquals(2, stores.size());
        StoreEntry git = stores.stream().filter(s -> s.id().equals("gitstore")).findFirst().orElseThrow();
        assertEquals("/fixture/gitstore", git.root());
        StoreEntry nogit = stores.stream().filter(s -> s.id().equals("nogitstore")).findFirst().orElseThrow();
        assertEquals("/fixture/nogitstore", nogit.root());
    }

    @Test
    void readsWorksetsFromDiskFile(@TempDir Path tmp) throws Exception {
        CoordinationPaths p = paths(tmp);
        Files.createDirectories(p.worksetsDir());
        Files.writeString(p.worksetsFile(), fixture("worksets.yaml"));

        List<WorksetEntry> worksets = CoordinationService.readWorksetsFromDisk(p);
        assertEquals(1, worksets.size());
        WorksetEntry ws = worksets.get(0);
        assertEquals("myview", ws.name());
        assertEquals(2, ws.members().size());
        assertEquals("gitstore", ws.members().get(0).name());
        assertEquals("/fixture/gitstore", ws.members().get(0).path());
    }

    @Test
    void missingStoreAndWorksetDirsYieldEmptyNotError(@TempDir Path tmp) {
        CoordinationPaths p = paths(tmp.resolve("nonexistent"));
        assertTrue(CoordinationService.readStoresFromDisk(p).isEmpty());
        assertTrue(CoordinationService.readWorksetsFromDisk(p).isEmpty());
    }

    // ---- T.7: version gating at the 1.5.0 floor ------------------------------

    @Test
    void cliStoreAvailableOnlyAtOrAboveFloor() {
        assertFalse(serviceWith("1.4.9", true).cliStoreAvailable(), "below floor");
        assertTrue(serviceWith("1.5.0", true).cliStoreAvailable(), "floor is inclusive");
        assertTrue(serviceWith("1.6.0", true).cliStoreAvailable());
        assertFalse(serviceWith("1.5.0", false).cliStoreAvailable(), "CLI not available");
    }

    @Test
    void belowFloorResolvesFromDiskNotCli(@TempDir Path tmp) throws Exception {
        // The floor is derived purely from the detected CLI version via CliVersion.atLeast — the
        // config-format axis (VersionSupport, pinned at 1.2.0) is NOT consulted. Below the floor the
        // resolve methods must read the on-disk registry rather than invoking the CLI.
        CoordinationPaths p = paths(tmp);
        Files.createDirectories(p.storesDir());
        Files.writeString(p.storeRegistryFile(), fixture("stores-registry.yaml"));
        Files.createDirectories(p.worksetsDir());
        Files.writeString(p.worksetsFile(), fixture("worksets.yaml"));

        CoordinationService service = serviceWith("1.4.9", true);
        service.setPathsForTest(p);

        assertFalse(service.cliStoreAvailable(), "gate must be false below the 1.5.0 floor");
        List<StoreEntry> stores = service.resolveStores();
        assertEquals(2, stores.size(), "below floor → disk registry, no CLI");
        assertEquals(1, service.resolveWorksets().size());
    }

    // ---- T.8: root canonicalization -----------------------------------------

    @Test
    void canonicalizationMatchesEquivalentRootsAndRejectsDifferent(@TempDir Path tmp) throws Exception {
        Path real = Files.createDirectories(tmp.resolve("project"));
        Path other = Files.createDirectories(tmp.resolve("elsewhere"));
        Files.createDirectories(real.resolve("sub"));

        // Same location expressed with a trailing "." and a non-normalized ".." segment (all real
        // segments exist, so both sides resolve via toRealPath).
        Path nonNormalized = real.resolve(".").resolve("sub").resolve("..");
        StoreEntry match = CoordinationService.storeMatchingRoot(
                List.of(StoreEntry.basic("s", nonNormalized.toString())), real);
        assertNotNull(match, "roots differing only by normalization must match after canonicalization");
        assertEquals("s", match.id());

        // A symlink to the real project root must also match (toRealPath resolves it).
        Path link = tmp.resolve("link");
        Files.createSymbolicLink(link, real);
        StoreEntry linkMatch = CoordinationService.storeMatchingRoot(
                List.of(StoreEntry.basic("linked", link.toString())), real);
        assertNotNull(linkMatch, "a symlinked store root must match the real project root");

        // A genuinely different root must not match.
        assertNull(CoordinationService.storeMatchingRoot(
                List.of(StoreEntry.basic("nope", other.toString())), real));
    }

    @Test
    void canonicalizeFallsBackForNonExistentPath(@TempDir Path tmp) {
        // toRealPath throws for a non-existent path → fall back to toAbsolutePath().normalize().
        // Anchor under a real absolute base: a bare "/no/such/dir" is not absolute on
        // Windows (no drive), so toAbsolutePath() would prepend the drive and diverge.
        Path missing = tmp.resolve("no/such/dir/./x/..");
        Path canon = CoordinationService.canonicalize(missing);
        assertEquals(tmp.resolve("no/such/dir").toAbsolutePath().normalize(), canon);
    }

    // ---- T.9: legacy demotion (no migration) --------------------------------

    @Test
    void storesAreLeadModelWithLegacyDemotedWhenBothExist(@TempDir Path tmp) throws Exception {
        CoordinationPaths p = paths(tmp);
        // New 1.5 state on disk.
        Files.createDirectories(p.storesDir());
        Files.writeString(p.storeRegistryFile(), fixture("stores-registry.yaml"));
        Files.createDirectories(p.worksetsDir());
        Files.writeString(p.worksetsFile(), fixture("worksets.yaml"));
        // Legacy 1.4 state on disk (a context store registry).
        Files.createDirectories(p.contextStoresDir());
        Files.writeString(p.contextStoreRegistryFile(), """
                version: 1
                stores:
                  legacy-store:
                    backend:
                      type: git
                      local_path: /fixture/legacy-store
                """);

        CoordinationService service = serviceWith("1.5.0", true);
        service.setPathsForTest(p);
        CoordinationData data = service.getCoordinationData(false);

        assertTrue(data.storesAreLeadModel(), "CLI >= 1.5.0 → stores/worksets lead");
        assertTrue(data.legacyStateExists(), "legacy context-store state on disk → legacy group present");
        assertFalse(data.stores().isEmpty(), "stores resolved from disk fallback");
        assertFalse(data.worksets().isEmpty());
        assertFalse(data.contextStores().isEmpty(), "legacy state reflected (read-only), never migrated");
    }

    @Test
    void noLegacyGroupWhenOnlyStoreStateExists(@TempDir Path tmp) throws Exception {
        CoordinationPaths p = paths(tmp);
        Files.createDirectories(p.storesDir());
        Files.writeString(p.storeRegistryFile(), fixture("stores-registry.yaml"));

        CoordinationService service = serviceWith("1.5.0", true);
        service.setPathsForTest(p);
        CoordinationData data = service.getCoordinationData(false);

        assertTrue(data.storesAreLeadModel());
        assertFalse(data.legacyStateExists(), "no legacy state on disk → no legacy group");
        assertFalse(data.stores().isEmpty());
    }

    // ---- T.10: beta-guarded degradation --------------------------------------

    @Test
    void malformedPayloadThrowsSoTheResolveFallbackIsLoadBearing() {
        // Proves the try/catch in resolveStores/resolveWorksets is doing real work: the parser
        // itself throws on a malformed CLI payload (it is not silently tolerant).
        assertThrows(RuntimeException.class,
                () -> CoordinationService.parseStores("{\"stores\":[{\"id\":"));
        assertThrows(RuntimeException.class,
                () -> CoordinationService.parseWorksets("{\"worksets\":[{\"name\":"));
    }

    @Test
    void brokenCliDegradesToDiskWithoutThrowing(@TempDir Path tmp) throws Exception {
        // At CLI >= 1.5.0 the store/workset CLI path is attempted; with no real process available it
        // errors internally. resolveStores/resolveWorksets must degrade to the on-disk registry and
        // never throw into the caller (beta guard).
        CoordinationPaths p = paths(tmp);
        Files.createDirectories(p.storesDir());
        Files.writeString(p.storeRegistryFile(), fixture("stores-registry.yaml"));
        Files.createDirectories(p.worksetsDir());
        Files.writeString(p.worksetsFile(), fixture("worksets.yaml"));

        CoordinationService service = serviceWith("1.5.0", true);
        service.setPathsForTest(p);

        List<StoreEntry> stores = assertDoesNotThrow(service::resolveStores);
        List<WorksetEntry> worksets = assertDoesNotThrow(service::resolveWorksets);
        assertEquals(2, stores.size(), "degrades to the on-disk registry");
        assertEquals(1, worksets.size());
        assertNull(stores.get(0).metadataPresent(), "disk fallback carries no doctor detail");
    }
}
