package com.johnnyblabs.openspec.root;

import com.johnnyblabs.openspec.coordination.CoordinationPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The resolver cases the CLI captures don't reach (a symlinked registry path, a store kept at {@code specs/}, a store
 * whose {@code specs} is a file, releases before stores, default-store values the CLI won't write), on real temp
 * directories. The captured cases live in
 * {@link StoreRootContractTest}.
 */
class RootResolverTest {

    @TempDir
    Path tmp;
    Path w;

    @BeforeEach
    void setUp() throws IOException {
        w = tmp.toRealPath();
    }

    @Test
    void aStoreWhoseSpecsPathIsAFileIsUnhealthy() throws IOException {
        Path store = store("demo");
        Files.delete(store.resolve("openspec/specs"));
        Files.writeString(store.resolve("openspec/specs"), "x");
        register("demo", store);
        pointer("app", "demo");

        assertEquals("unhealthy_store_root", resolve("app", "2.1.1").problem().code());
    }

    @Test
    void aStoreReachedThroughASymlinkResolvesToItsRealPath() throws IOException {
        Path store = store("demo");
        Path link = w.resolve("link-to-demo");
        try {
            Files.createSymbolicLink(link, store);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symlinks unavailable here: " + e);
        }
        register("demo", link);
        pointer("app", "demo");

        RootResolution r = resolve("app", "2.1.1");

        assertEquals(store, r.root(), "canonicalized as the CLI does");
        assertEquals(RootResolution.Source.DECLARED, r.source());
    }

    @Test
    void aStoreKeptAtSpecsIsNotAPlanningShape() throws IOException {
        // The recommended ~/openspec/<id> layout with a store whose id is "specs" must not make ~ a root.
        Files.createDirectories(w.resolve("home/openspec/specs/.openspec-store"));
        Files.writeString(w.resolve("home/openspec/specs/.openspec-store/store.yaml"), "version: 1\nid: specs\n");
        Files.createDirectories(w.resolve("home/project"));

        assertFalse(resolve("home/project", "2.1.1").resolved());
    }

    @Test
    void beforeStoresAPointerConfigIsAPlainLocalRoot() throws IOException {
        Path store = store("demo");
        register("demo", store);
        pointer("app", "demo");

        RootResolution r = resolve("app", "1.4.1");

        assertEquals(w.resolve("app"), r.root());
        assertEquals(RootResolution.Source.NEAREST, r.source());
        assertNull(r.warning());
    }

    @Test
    void anUnreadableReleaseHonorsNoStoreSource() throws IOException {
        Path store = store("demo");
        register("demo", store);
        pointer("app", "demo");

        assertEquals(w.resolve("app"), resolve("app", null).root());
    }

    @Test
    void anEmptyOrNonStringDefaultStoreIsUnset() throws IOException {
        register("demo", store("demo"));
        Files.createDirectories(w.resolve("noroot"));
        // An empty default is captured; null and non-string values the CLI can't be made to write are covered here.
        for (String value : new String[]{"null", "{}"}) {
            writeGlobalConfig("{\"defaultStore\": " + value + "}");
            assertFalse(resolve("noroot", "2.1.1").resolved(), value);
            assertNull(resolve("noroot", "2.1.1").problem(), value);
        }
        writeGlobalConfig("not json");
        assertNull(resolve("noroot", "2.1.1").problem());
    }

    @Test
    void theGlobalConfigFollowsUpstreamsDirectoryRules() {
        assertEquals(Path.of("/x/openspec/config.json"),
                GlobalConfigPaths.configFile(Map.of("XDG_CONFIG_HOME", "/x")::get, true, "/h"));
        assertEquals(Path.of("C:/Users/u/AppData/Roaming/openspec/config.json"),
                GlobalConfigPaths.configFile(Map.of("APPDATA", "C:/Users/u/AppData/Roaming")::get, true, "/h"));
        assertEquals(Path.of("/h/AppData/Roaming/openspec/config.json"),
                GlobalConfigPaths.configFile(k -> null, true, "/h"));
        assertEquals(Path.of("/h/.config/openspec/config.json"),
                GlobalConfigPaths.configFile(k -> null, false, "/h"));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------------

    private RootResolution resolve(String cwd, String release) {
        return RootResolver.resolve(w.resolve(cwd), new RootResolver.Inputs(w.resolve("config/openspec/config.json"),
                CoordinationPaths.resolve(Map.of("XDG_DATA_HOME", w.resolve("data").toString())::get, false, null),
                release));
    }

    private Path store(String id) throws IOException {
        Path store = w.resolve("stores").resolve(id);
        Files.createDirectories(store.resolve(".openspec-store"));
        Files.writeString(store.resolve(".openspec-store/store.yaml"), "version: 1\nid: " + id + "\n");
        Files.createDirectories(store.resolve("openspec/specs"));
        Files.writeString(store.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        return store;
    }

    private void register(String id, Path root) throws IOException {
        Path registry = w.resolve("data/openspec/stores/registry.yaml");
        Files.createDirectories(registry.getParent());
        String existing = Files.exists(registry) ? Files.readString(registry) : "version: 1\nstores:\n";
        Files.writeString(registry, existing + "  " + id + ":\n    backend:\n      type: git\n      local_path: "
                + root.toString().replace('\\', '/') + "\n");
    }

    private void pointer(String dir, String id) throws IOException {
        Files.createDirectories(w.resolve(dir).resolve("openspec"));
        Files.writeString(w.resolve(dir).resolve("openspec/config.yaml"), "store: " + id + "\n");
    }

    private void defaultStore(String id) throws IOException {
        writeGlobalConfig("{\"defaultStore\": \"" + id + "\"}");
    }

    private void writeGlobalConfig(String json) throws IOException {
        Path config = w.resolve("config/openspec/config.json");
        Files.createDirectories(config.getParent());
        Files.writeString(config, json);
    }
}
