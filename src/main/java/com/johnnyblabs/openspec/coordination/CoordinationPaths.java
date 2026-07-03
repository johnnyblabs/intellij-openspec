package com.johnnyblabs.openspec.coordination;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Resolves the OpenSpec global data dir and the locations of the coordination collections
 * underneath it, mirroring the CLI's {@code getGlobalDataDir}:
 *
 * <ul>
 *   <li>{@code $XDG_DATA_HOME/openspec} when {@code XDG_DATA_HOME} is set (all platforms);</li>
 *   <li>otherwise {@code %LOCALAPPDATA%\openspec} on Windows;</li>
 *   <li>otherwise {@code ~/.local/share/openspec} on Unix/macOS.</li>
 * </ul>
 *
 * <p>Workspaces live under {@code workspaces/} and context stores under
 * {@code context-stores/}, each with a {@code registry.yaml}; initiatives are nested within
 * a context store at {@code <store>/<id>/}. This is the on-disk source the built-in fallback
 * reads when the CLI is unavailable.
 *
 * <p>The resolver is parameterized over an env lookup and platform/home so it can be
 * exercised in tests with a fixture data dir (via {@code XDG_DATA_HOME}).
 */
public final class CoordinationPaths {

    public static final String GLOBAL_DATA_DIR_NAME = "openspec";
    public static final String MANAGED_WORKSPACES_DIR_NAME = "workspaces";
    public static final String CONTEXT_STORES_DIR_NAME = "context-stores";
    public static final String REGISTRY_FILE_NAME = "registry.yaml";

    // OpenSpec 1.5 store/workset layout, resolved under the same global data dir. The store
    // registry file is byte-identical in shape to the context-store registry — only the
    // directory name differs (see the shared backend-local-path parser in CoordinationService).
    public static final String STORES_DIR_NAME = "stores";
    public static final String WORKSETS_DIR_NAME = "worksets";
    public static final String WORKSETS_FILE_NAME = "worksets.yaml";

    private final Path globalDataDir;

    private CoordinationPaths(Path globalDataDir) {
        this.globalDataDir = globalDataDir;
    }

    /** Resolves using the real process environment and host platform. */
    public static CoordinationPaths resolve() {
        return resolve(System::getenv, SystemInfo.isWindows, System.getProperty("user.home"));
    }

    /** Test-friendly resolver: explicit env lookup, platform flag, and home dir. */
    public static CoordinationPaths resolve(Function<String, String> env, boolean windows, @Nullable String home) {
        String xdg = env.apply("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return new CoordinationPaths(Path.of(xdg, GLOBAL_DATA_DIR_NAME));
        }
        if (windows) {
            String localAppData = env.apply("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return new CoordinationPaths(Path.of(localAppData, GLOBAL_DATA_DIR_NAME));
            }
        }
        String resolvedHome = home != null ? home : ".";
        return new CoordinationPaths(Path.of(resolvedHome, ".local", "share", GLOBAL_DATA_DIR_NAME));
    }

    public Path globalDataDir() {
        return globalDataDir;
    }

    public Path managedWorkspacesDir() {
        return globalDataDir.resolve(MANAGED_WORKSPACES_DIR_NAME);
    }

    public Path workspaceRegistryFile() {
        return managedWorkspacesDir().resolve(REGISTRY_FILE_NAME);
    }

    public Path contextStoresDir() {
        return globalDataDir.resolve(CONTEXT_STORES_DIR_NAME);
    }

    public Path contextStoreRegistryFile() {
        return contextStoresDir().resolve(REGISTRY_FILE_NAME);
    }

    // ---- OpenSpec 1.5 stores & worksets --------------------------------------

    public Path storesDir() {
        return globalDataDir.resolve(STORES_DIR_NAME);
    }

    public Path storeRegistryFile() {
        return storesDir().resolve(REGISTRY_FILE_NAME);
    }

    public Path worksetsDir() {
        return globalDataDir.resolve(WORKSETS_DIR_NAME);
    }

    public Path worksetsFile() {
        return worksetsDir().resolve(WORKSETS_FILE_NAME);
    }
}
