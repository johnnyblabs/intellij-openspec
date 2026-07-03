package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Host-independent table over {@link CoordinationPaths#resolve(Function, boolean, String)}, driving
 * the {@code windows=true}/{@code false} legs of the parameterized resolver from a single JVM so the
 * Windows branch is exercised even when the build host is macOS/Linux.
 *
 * <p>The resolver builds {@link Path} values with {@code Path.of}, which uses the host filesystem's
 * separator. On a non-Windows JVM the appended segments join with {@code /}; a real Windows JVM joins
 * with {@code \}. These tests therefore assert the <b>structure</b> host-independently via {@link Path}
 * equality and name-element checks (separator-agnostic), plus that the {@code %LOCALAPPDATA%} prefix —
 * which already contains backslashes — is preserved verbatim. The full backslash-separator guarantee
 * is asserted in an {@code @EnabledOnOs(WINDOWS)} case that runs on the GitHub Windows matrix leg.
 *
 * <p><b>Windows data-dir capture (task 7):</b> the Windows branch is asserted here against the
 * documented {@code %LOCALAPPDATA%\openspec} layout via the {@code windows=true} override. Confirming
 * that shape against a real {@code store register --json} {@code registry.path} on a Windows host is a
 * manual follow-up (see the change's {@code tasks.md} section 7) — it needs a Windows machine and is
 * not fakeable from a captured Unix run.
 */
class CoordinationPathsCrossPlatformTest {

    private static final String LOCAL_APP_DATA = "C:\\Users\\Me\\AppData\\Local";

    private static Function<String, String> env(Map<String, String> m) {
        return m::get;
    }

    // ---- 2.1: Windows + LOCALAPPDATA → <LOCALAPPDATA>\openspec (backslash prefix) ----

    @Test
    void windowsWithLocalAppDataResolvesUnderOpenspec() {
        Map<String, String> env = new HashMap<>();
        env.put("LOCALAPPDATA", LOCAL_APP_DATA);
        CoordinationPaths p = CoordinationPaths.resolve(env(env), true, "C:\\Users\\Me");

        // Windows branch selected LOCALAPPDATA (not the Unix ~/.local/share fallback) and appended
        // the global data dir name. Path equality is host-independent (both sides built the same way).
        assertEquals(Path.of(LOCAL_APP_DATA, CoordinationPaths.GLOBAL_DATA_DIR_NAME), p.globalDataDir());
        // The %LOCALAPPDATA% prefix, including its backslashes, is preserved verbatim in the string.
        assertTrue(p.globalDataDir().toString().startsWith(LOCAL_APP_DATA),
                "LOCALAPPDATA prefix (with backslashes) must survive resolution");
        // Last element is the global data dir name regardless of separator style.
        assertEquals(CoordinationPaths.GLOBAL_DATA_DIR_NAME, p.globalDataDir().getFileName().toString());
    }

    @Test
    void windowsStoreRegistryPathHasExpectedSegments() {
        Map<String, String> env = new HashMap<>();
        env.put("LOCALAPPDATA", LOCAL_APP_DATA);
        CoordinationPaths p = CoordinationPaths.resolve(env(env), true, "C:\\Users\\Me");

        Path reg = p.storeRegistryFile();
        assertEquals(Path.of(LOCAL_APP_DATA,
                        CoordinationPaths.GLOBAL_DATA_DIR_NAME,
                        CoordinationPaths.STORES_DIR_NAME,
                        CoordinationPaths.REGISTRY_FILE_NAME),
                reg);
        // Separator-agnostic tail check: .../openspec/stores/registry.yaml.
        int n = reg.getNameCount();
        assertEquals(CoordinationPaths.REGISTRY_FILE_NAME, reg.getName(n - 1).toString());
        assertEquals(CoordinationPaths.STORES_DIR_NAME, reg.getName(n - 2).toString());
        assertEquals(CoordinationPaths.GLOBAL_DATA_DIR_NAME, reg.getName(n - 3).toString());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsSeparatorsAreBackslashesOnWindowsHost() {
        // Runs only on the GitHub Windows matrix leg: on a real Windows JVM Path.of uses the native
        // backslash separator throughout, so the resolved registry path is fully backslash-delimited.
        Map<String, String> env = new HashMap<>();
        env.put("LOCALAPPDATA", LOCAL_APP_DATA);
        CoordinationPaths p = CoordinationPaths.resolve(env(env), true, "C:\\Users\\Me");

        String reg = p.storeRegistryFile().toString();
        assertTrue(reg.endsWith("openspec\\stores\\registry.yaml"),
                "Windows resolution must use backslash separators: " + reg);
        assertFalse(reg.contains("/"), "no forward slashes on a Windows host: " + reg);
    }

    // ---- 2.2: XDG_DATA_HOME precedence even when windows=true ----

    @Test
    void xdgOverridesWindowsBranch() {
        Map<String, String> env = new HashMap<>();
        env.put("XDG_DATA_HOME", "/xdg/data");
        env.put("LOCALAPPDATA", LOCAL_APP_DATA);
        CoordinationPaths p = CoordinationPaths.resolve(env(env), true, "C:\\Users\\Me");

        // XDG wins over the %LOCALAPPDATA% branch even with the Windows flag set.
        assertEquals(Path.of("/xdg/data", CoordinationPaths.GLOBAL_DATA_DIR_NAME), p.globalDataDir());
        assertFalse(p.globalDataDir().toString().startsWith(LOCAL_APP_DATA),
                "XDG_DATA_HOME must take precedence over LOCALAPPDATA");
    }

    @Test
    void blankXdgIsIgnoredSoWindowsBranchStillApplies() {
        Map<String, String> env = new HashMap<>();
        env.put("XDG_DATA_HOME", "   ");
        env.put("LOCALAPPDATA", LOCAL_APP_DATA);
        CoordinationPaths p = CoordinationPaths.resolve(env(env), true, "C:\\Users\\Me");

        assertEquals(Path.of(LOCAL_APP_DATA, CoordinationPaths.GLOBAL_DATA_DIR_NAME), p.globalDataDir());
    }

    // ---- 2.3: null-home fallback → Unix ~/.local/share/openspec shape ----

    @Test
    void nullHomeFallsBackToUnixShape() {
        // Neither XDG nor the Windows branch applies (windows=false, no LOCALAPPDATA): a null home
        // resolves relative to "." with the Unix ~/.local/share/openspec layout.
        CoordinationPaths p = CoordinationPaths.resolve(env(new HashMap<>()), false, null);
        assertEquals(Path.of(".", ".local", "share", CoordinationPaths.GLOBAL_DATA_DIR_NAME),
                p.globalDataDir());
    }

    @Test
    void realHomeUsesUnixShare() {
        CoordinationPaths p = CoordinationPaths.resolve(env(new HashMap<>()), false, "/home/test");
        assertEquals(Path.of("/home/test", ".local", "share", CoordinationPaths.GLOBAL_DATA_DIR_NAME),
                p.globalDataDir());
    }

    @Test
    void windowsWithoutLocalAppDataFallsBackToUnixShape() {
        // windows=true but LOCALAPPDATA unset → the Windows branch is skipped and the home fallback wins.
        CoordinationPaths p = CoordinationPaths.resolve(env(new HashMap<>()), true, "/home/test");
        assertEquals(Path.of("/home/test", ".local", "share", CoordinationPaths.GLOBAL_DATA_DIR_NAME),
                p.globalDataDir());
    }

    // ---- 2.4: stores/ and worksets/ resolve under the data dir on BOTH legs ----

    @Test
    void storesAndWorksetsResolveUnderDataDirOnWindowsLeg() {
        Map<String, String> env = new HashMap<>();
        env.put("LOCALAPPDATA", LOCAL_APP_DATA);
        CoordinationPaths p = CoordinationPaths.resolve(env(env), true, "C:\\Users\\Me");
        assertChildrenOfDataDir(p);
    }

    @Test
    void storesAndWorksetsResolveUnderDataDirOnUnixLeg() {
        CoordinationPaths p = CoordinationPaths.resolve(env(new HashMap<>()), false, "/home/test");
        assertChildrenOfDataDir(p);
    }

    private static void assertChildrenOfDataDir(CoordinationPaths p) {
        Path data = p.globalDataDir();
        assertEquals(data.resolve(CoordinationPaths.STORES_DIR_NAME), p.storesDir());
        assertEquals(data.resolve(CoordinationPaths.WORKSETS_DIR_NAME), p.worksetsDir());
        assertEquals(data, p.storesDir().getParent());
        assertEquals(data, p.worksetsDir().getParent());
        assertEquals(CoordinationPaths.REGISTRY_FILE_NAME, p.storeRegistryFile().getFileName().toString());
        assertEquals(CoordinationPaths.WORKSETS_FILE_NAME, p.worksetsFile().getFileName().toString());
    }
}
