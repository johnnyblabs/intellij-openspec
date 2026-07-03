package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests that parse <b>real</b> OpenSpec 1.5.0 CLI JSON captured as fixtures under
 * {@code src/test/resources/fixtures/cli/1.5.0/}. These verify the {@code store} / {@code workset}
 * parsers against the actual CLI output shape (field names, nesting), not a hand-written
 * approximation.
 *
 * <p>Capture recipe: under an isolated {@code XDG_DATA_HOME}/{@code HOME} with
 * {@code OPENSPEC_TELEMETRY=0}, run {@code store setup <id> --path ...} (and {@code --no-init-git}
 * for a non-git store), then capture {@code store list --json}, {@code store doctor --json},
 * {@code workset create ...}, and {@code workset list --json}; sanitize absolute paths to
 * {@code /fixture/...}. The diagnostic fixture comes from a real {@code store doctor} run against a
 * store whose metadata was removed. If the CLI output format changes, re-capture and fix failures.
 */
class StoreWorksetContractTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.5.0/" + name;
        try (InputStream is = StoreWorksetContractTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    // ---- T.2: store list -----------------------------------------------------

    @Test
    void parsesRealStoreList() {
        List<StoreEntry> stores = CoordinationService.parseStores(fixture("store-list.json"));
        assertEquals(2, stores.size());
        StoreEntry git = stores.get(0);
        assertEquals("gitstore", git.id());
        assertEquals("/fixture/gitstore", git.root());
        StoreEntry nogit = stores.get(1);
        assertEquals("nogitstore", nogit.id());
        assertEquals("/fixture/nogitstore", nogit.root());
        // Basic (list-tier) entries carry no doctor detail yet.
        assertNull(git.metadataPresent());
        assertNull(git.gitRepository());
    }

    @Test
    void emptyStoreArrayYieldsEmptyList() {
        assertTrue(CoordinationService.parseStores("{\"stores\":[],\"status\":[]}").isEmpty());
    }

    // ---- T.3: store doctor ---------------------------------------------------

    @Test
    void parsesRealStoreDoctorHealthFromExactNestedKeys() {
        List<StoreEntry> stores = CoordinationService.parseStoreDoctor(fixture("store-doctor.json"));
        assertEquals(2, stores.size());

        StoreEntry git = stores.stream().filter(s -> s.id().equals("gitstore")).findFirst().orElseThrow();
        // metadata.{present,valid}, git.is_repository, openspec_root.healthy — the exact nested keys.
        assertEquals(Boolean.TRUE, git.metadataPresent());
        assertEquals(Boolean.TRUE, git.metadataValid());
        assertEquals(Boolean.TRUE, git.gitRepository());
        assertEquals(Boolean.TRUE, git.openspecRootHealthy());
    }

    @Test
    void nonGitStoreParsesWithoutNpeAndReadsIsRepositoryFalse() {
        List<StoreEntry> stores = CoordinationService.parseStoreDoctor(fixture("store-doctor.json"));
        StoreEntry nogit = stores.stream().filter(s -> s.id().equals("nogitstore")).findFirst().orElseThrow();
        // The real 1.5.0 shape for a non-git store: git.is_repository is false and the OTHER git
        // subfields (has_commits, has_remote, origin_url) are null. Parsing must not NPE on those
        // nulls, and git health reads as "not a repository" (false), never throwing.
        assertEquals(Boolean.FALSE, nogit.gitRepository());
        assertEquals(Boolean.TRUE, nogit.metadataPresent());
        assertEquals(Boolean.TRUE, nogit.openspecRootHealthy());
    }

    // ---- T.4: workset list ---------------------------------------------------

    @Test
    void parsesRealWorksetListWithMembers() {
        List<WorksetEntry> worksets = CoordinationService.parseWorksets(fixture("workset-list.json"));
        assertEquals(1, worksets.size());
        WorksetEntry ws = worksets.get(0);
        assertEquals("myview", ws.name());
        assertEquals(2, ws.members().size());
        assertEquals("gitstore", ws.members().get(0).name());
        assertEquals("/fixture/gitstore", ws.members().get(0).path());
        assertEquals("nogitstore", ws.members().get(1).name());
        assertEquals("/fixture/nogitstore", ws.members().get(1).path());
    }

    @Test
    void emptyWorksetArrayYieldsEmptyList() {
        assertTrue(CoordinationService.parseWorksets("{\"worksets\":[],\"status\":[]}").isEmpty());
    }

    // ---- T.5: diagnostic envelope --------------------------------------------

    @Test
    void retainsDiagnosticFixSuggestionFromRealDoctorOutput() {
        List<StoreEntry> stores = CoordinationService.parseStoreDoctor(fixture("store-doctor-diagnostic.json"));
        assertEquals(1, stores.size());
        StoreEntry broken = stores.get(0);
        // The store's metadata was removed → a store_metadata_missing diagnostic with a ready-made fix.
        assertEquals(Boolean.FALSE, broken.metadataPresent());
        Diagnostic metaMissing = broken.diagnostics().stream()
                .filter(d -> "store_metadata_missing".equals(d.code()))
                .findFirst().orElseThrow();
        assertEquals("error", metaMissing.severity());
        assertEquals("store.metadata", metaMissing.target());
        assertNotNull(metaMissing.fix());
        assertTrue(metaMissing.fix().contains("rerun store register"),
                "the ready-made fix string must be retained verbatim on the parsed model");
    }

    @Test
    void diagnosticWithoutFixHasNullFix() {
        List<StoreEntry> stores = CoordinationService.parseStoreDoctor(fixture("store-doctor-diagnostic.json"));
        Diagnostic configMissing = stores.get(0).diagnostics().stream()
                .filter(d -> "openspec_config_missing".equals(d.code()))
                .findFirst().orElseThrow();
        // This diagnostic carries no fix key — it must parse as null, not empty-string or throw.
        assertNull(configMissing.fix());
        assertFalse(configMissing.message().isEmpty());
    }
}
