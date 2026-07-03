package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Host-independent CRLF-vs-LF parse parity for the captured store/workset fixtures. Each captured
 * fixture is parsed with LF endings, again with every {@code \n} rewritten to {@code \r\n}, and once
 * more with a trailing lone {@code \r}; all forms MUST yield an equal model. This guards the parsers
 * against CRLF corruption <b>without</b> re-implementing the upstream escaping fix — the fixtures are
 * the real captured shapes, only their line endings are varied.
 *
 * <p>{@link StoreEntry}/{@link WorksetEntry} are records, so model equality is structural.
 */
class CrlfLfParseParityTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.5.0/" + name;
        try (InputStream is = CrlfLfParseParityTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            // Normalize to LF first so the LF baseline is deterministic regardless of how git
            // checked the fixture out on this host.
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String crlf(String lf) {
        return lf.replace("\n", "\r\n");
    }

    private static String trailingCr(String lf) {
        // A lone carriage return appended at the very end of the payload (the classic CRLF-corruption
        // tail) — must not corrupt the parse.
        return lf + "\r";
    }

    // ---- 3.1 + 3.2: store list ----------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"store-list.json"})
    void storeListParsesIdenticallyAcrossLineEndings(String name) {
        String lf = fixture(name);
        // Sanity: the LF baseline really has no CR, so the CRLF/trailing-CR variants differ from it.
        assertFalse(lf.contains("\r"), "LF baseline must contain no carriage returns");

        List<StoreEntry> base = CoordinationService.parseStores(lf);
        assertEquals(base, CoordinationService.parseStores(crlf(lf)), "CRLF must yield an equal model");
        assertEquals(base, CoordinationService.parseStores(trailingCr(lf)),
                "a trailing lone CR must yield an equal model");
        // Guard against a vacuous test: the baseline actually parsed something.
        assertFalse(base.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"store-doctor.json", "store-doctor-diagnostic.json"})
    void storeDoctorParsesIdenticallyAcrossLineEndings(String name) {
        String lf = fixture(name);
        List<StoreEntry> base = CoordinationService.parseStoreDoctor(lf);
        assertEquals(base, CoordinationService.parseStoreDoctor(crlf(lf)));
        assertEquals(base, CoordinationService.parseStoreDoctor(trailingCr(lf)));
        assertFalse(base.isEmpty());
        // Doctor detail (including retained diagnostics with their fix text) must survive CRLF too —
        // record equality already covers this, but assert the non-triviality explicitly.
        assertFalse(base.get(0).diagnostics() == null);
    }

    // ---- 3.1 + 3.2: workset list --------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"workset-list.json"})
    void worksetListParsesIdenticallyAcrossLineEndings(String name) {
        String lf = fixture(name);
        List<WorksetEntry> base = CoordinationService.parseWorksets(lf);
        assertEquals(base, CoordinationService.parseWorksets(crlf(lf)));
        assertEquals(base, CoordinationService.parseWorksets(trailingCr(lf)));
        assertFalse(base.isEmpty());
        assertFalse(base.get(0).members().isEmpty(), "member list must survive CRLF rewriting");
    }

    // ---- 3.1: on-disk YAML registries (disk fallback path) ------------------

    @ParameterizedTest
    @ValueSource(strings = {"stores-registry.yaml"})
    void diskStoreRegistryParsesIdenticallyAcrossLineEndings(String name, @TempDir Path tmp) throws Exception {
        String lf = fixture(name);
        assertEquals(readStoresWith(tmp.resolve("lf"), lf),
                readStoresWith(tmp.resolve("crlf"), crlf(lf)),
                "the disk store registry reader must be CRLF-insensitive");
        assertEquals(readStoresWith(tmp.resolve("lf2"), lf),
                readStoresWith(tmp.resolve("cr"), trailingCr(lf)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"worksets.yaml"})
    void diskWorksetFileParsesIdenticallyAcrossLineEndings(String name, @TempDir Path tmp) throws Exception {
        String lf = fixture(name);
        assertEquals(readWorksetsWith(tmp.resolve("lf"), lf),
                readWorksetsWith(tmp.resolve("crlf"), crlf(lf)));
        assertEquals(readWorksetsWith(tmp.resolve("lf2"), lf),
                readWorksetsWith(tmp.resolve("cr"), trailingCr(lf)));
    }

    private static List<StoreEntry> readStoresWith(Path dataHome, String registryContent) throws Exception {
        CoordinationPaths p = pathsFor(dataHome);
        Files.createDirectories(p.storesDir());
        Files.writeString(p.storeRegistryFile(), registryContent);
        List<StoreEntry> stores = CoordinationService.readStoresFromDisk(p);
        assertFalse(stores.isEmpty(), "registry must parse to at least one store");
        return stores;
    }

    private static List<WorksetEntry> readWorksetsWith(Path dataHome, String worksetsContent) throws Exception {
        CoordinationPaths p = pathsFor(dataHome);
        Files.createDirectories(p.worksetsDir());
        Files.writeString(p.worksetsFile(), worksetsContent);
        List<WorksetEntry> worksets = CoordinationService.readWorksetsFromDisk(p);
        assertFalse(worksets.isEmpty(), "worksets file must parse to at least one workset");
        return worksets;
    }

    private static CoordinationPaths pathsFor(Path xdgDataHome) {
        Function<String, String> env = key -> "XDG_DATA_HOME".equals(key) ? xdgDataHome.toString() : null;
        return CoordinationPaths.resolve(env, false, "/home/test");
    }
}
