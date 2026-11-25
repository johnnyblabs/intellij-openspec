package com.johnnyblabs.openspec.root;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.coordination.CoordinationPaths;
import com.johnnyblabs.openspec.coordination.CoordinationService;
import com.johnnyblabs.openspec.coordination.StoreEntry;
import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves a project's OpenSpec root from files on disk, the way the CLI's {@code resolveOpenSpecRoot} does
 * ({@code dist/core/root-selection.js}, unchanged from 1.16.0 through 2.2.0; the order is the same since 1.7.0):
 * <ol>
 *   <li>Walk up from the start directory to the nearest {@code openspec/} that qualifies: one with a planning shape
 *       ({@code specs/} or {@code changes/} as a directory that isn't itself a store) or a config file.</li>
 *   <li>A planning shape is the root ({@code nearest}); a {@code store:} pointer beside it is ignored with a
 *       warning. Otherwise a malformed pointer is a problem, a valid one resolves the store ({@code declared}), and
 *       no pointer leaves the config-only directory as the root ({@code nearest}).</li>
 *   <li>With nothing qualifying, the global {@code defaultStore} resolves ({@code global_default}).</li>
 *   <li>Otherwise there is no root. The CLI's "stores registered but no root" error is not a problem here: the
 *       plugin offers to set the project up instead.</li>
 * </ol>
 * A store resolves only when it is registered, its {@code .openspec-store/store.yaml} names the same id, and its
 * {@code openspec/} directory is healthy. Pointers apply from CLI 1.5.0 and the default store from 1.7.0, judged
 * against the release the plugin follows.
 *
 * <p>Pure: no IntelliJ APIs, no CLI. Contract-tested against captured {@code context --json} runs.
 */
public final class RootResolver {

    public static final String OPENSPEC_DIR = "openspec";
    /** A {@code store:} pointer in a project config applies from here. */
    public static final String STORE_POINTER = "1.5.0";
    /** The global {@code defaultStore} applies from here. */
    public static final String DEFAULT_STORE = "1.7.0";

    static final String STORE_METADATA = ".openspec-store/store.yaml";

    /** Where the machine-wide inputs live, and the release whose rules apply. */
    public record Inputs(Path globalConfigFile, CoordinationPaths dataPaths, @Nullable String followedRelease) {
        public static Inputs host(@Nullable String followedRelease) {
            return new Inputs(GlobalConfigPaths.configFile(), CoordinationPaths.resolve(), followedRelease);
        }
    }

    private RootResolver() {
    }

    public static RootResolution resolve(Path start, Inputs inputs) {
        boolean pointers = has(inputs.followedRelease(), STORE_POINTER);
        Path nearest = findQualifying(canonical(start));
        if (nearest != null) {
            return resolveNearest(nearest, inputs, pointers);
        }
        if (has(inputs.followedRelease(), DEFAULT_STORE)) {
            String defaultStore = readDefaultStore(inputs.globalConfigFile());
            if (defaultStore != null) {
                return resolveDefault(defaultStore, inputs);
            }
        }
        return RootResolution.none();
    }

    // ---- The walk -------------------------------------------------------------------------------------------------

    /** The nearest directory, from {@code start} upward, whose {@code openspec/} qualifies. */
    static @Nullable Path findQualifying(Path start) {
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path openspec = dir.resolve(OPENSPEC_DIR);
            if (Files.isDirectory(openspec) && (hasPlanningShape(openspec) || configFile(dir) != null)) {
                return dir;
            }
        }
        return null;
    }

    /** {@code specs/} or {@code changes/} is a directory, and not a store kept at that path. */
    static boolean hasPlanningShape(Path openspecDir) {
        return isPlanningDir(openspecDir.resolve("specs")) || isPlanningDir(openspecDir.resolve("changes"));
    }

    private static boolean isPlanningDir(Path dir) {
        return Files.isDirectory(dir) && !Files.exists(dir.resolve(STORE_METADATA));
    }

    static @Nullable Path configFile(Path projectRoot) {
        Path yaml = projectRoot.resolve(OPENSPEC_DIR).resolve("config.yaml");
        if (Files.exists(yaml)) return yaml;
        Path yml = projectRoot.resolve(OPENSPEC_DIR).resolve("config.yml");
        return Files.exists(yml) ? yml : null;
    }

    private static RootResolution resolveNearest(Path nearest, Inputs inputs, boolean pointers) {
        Pointer pointer = pointers ? readPointer(configFile(nearest)) : Pointer.NONE;
        if (hasPlanningShape(nearest.resolve(OPENSPEC_DIR))) {
            String warning = pointer.value() == null ? null
                    : "Warning: " + pointer.file() + " declares store '" + pointer.value()
                    + "', but this directory is a real OpenSpec root; the declaration is ignored.";
            return new RootResolution(nearest, RootResolution.Source.NEAREST, null, null, warning);
        }
        if (pointer.malformed() != null) {
            boolean unparseable = "unparseable".equals(pointer.malformed());
            return RootResolution.problem(new RootResolution.Problem("invalid_store_pointer",
                    "Invalid store declaration in " + pointer.file() + ": "
                            + (unparseable ? "the config file could not be read as YAML"
                            : "the store key must be a single store id string") + ".",
                    unparseable ? "Fix the YAML syntax in " + pointer.file() + "."
                            : "Edit " + pointer.file() + " so the store key is a registered store id, or remove it."));
        }
        if (pointer.value() == null) {
            return new RootResolution(nearest, RootResolution.Source.NEAREST, null, null, null);
        }
        return resolveStore(pointer.value(), RootResolution.Source.DECLARED, inputs, pointer.file());
    }

    private static RootResolution resolveDefault(String id, Inputs inputs) {
        return resolveStore(id, RootResolution.Source.GLOBAL_DEFAULT, inputs, null);
    }

    // ---- Stores ---------------------------------------------------------------------------------------------------

    private static RootResolution resolveStore(String id, RootResolution.Source source, Inputs inputs,
                                               @Nullable Path declaredIn) {
        RootResolution.Problem idProblem = storeIdProblem(id);
        if (idProblem != null) return RootResolution.problem(origin(idProblem, id, declaredIn));
        List<StoreEntry> entries = CoordinationService.readStoresFromDisk(inputs.dataPaths());
        StoreEntry entry = entries.stream().filter(e -> id.equals(e.id())).findFirst().orElse(null);
        if (entry == null || entry.root() == null) {
            boolean none = entries.isEmpty();
            String message = none ? "Unknown store '" + id + "'. No stores are registered."
                    : "Unknown store '" + id + "'. Registered stores: "
                    + entries.stream().map(StoreEntry::id).collect(Collectors.joining(", ")) + ".";
            // Formatted, so the CLI's own wording is a message, not an argument being built. Upstream reshapes the fix
            // for a declared pointer only for an unknown store, and for the global default for both codes.
            String register = String.format("Register the store (openspec store register <path> --id %s)", id);
            String fix;
            if (declaredIn == null) {
                fix = register + " or clear the stale global default (openspec config unset defaultStore).";
            } else if (none) {
                fix = String.format("Run openspec store setup %s or openspec store register <path> first.", id);
            } else {
                fix = register + " or edit " + declaredIn + " to name a registered store.";
            }
            return RootResolution.problem(origin(new RootResolution.Problem(
                    none ? "no_registered_stores" : "unknown_store", message, fix), id, declaredIn));
        }
        Path storeRoot;
        try {
            storeRoot = Path.of(entry.root());
        } catch (InvalidPathException e) {
            return RootResolution.problem(origin(unhealthy(id, entry.root(), "Store root is not a valid path."),
                    id, declaredIn));
        }
        if (Files.exists(storeRoot) && !Files.isDirectory(storeRoot)) {
            // Upstream fails reading the metadata through a file and reports the raw error, with no origin prefix.
            return RootResolution.problem(new RootResolution.Problem("context_failed",
                    "ENOTDIR: not a directory, open '" + storeRoot.resolve(STORE_METADATA) + "'", null));
        }
        RootResolution.Problem problem = inspectStore(id, storeRoot);
        if (problem != null) {
            return RootResolution.problem(origin(problem, id, declaredIn));
        }
        return new RootResolution(canonical(storeRoot), source, id, null, null);
    }

    /** The CLI prefixes a store problem with where the store was named. */
    private static RootResolution.Problem origin(RootResolution.Problem p, String id, @Nullable Path declaredIn) {
        String prefix = declaredIn != null ? "Declared in " + declaredIn + ": " : "Global defaultStore '" + id + "': ";
        return new RootResolution.Problem(p.code(), prefix + p.message(), p.fix());
    }

    static final String KEBAB_ID_FIX = "Use kebab-case with lowercase letters, numbers, and single hyphen separators.";
    private static final java.util.regex.Pattern KEBAB_ID = java.util.regex.Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    /** Upstream's {@code validateStoreId}: a folder-style name first, then kebab-case. */
    static @Nullable RootResolution.Problem storeIdProblem(String id) {
        String problem;
        if (id.isEmpty()) problem = "Store id must not be empty";
        else if (id.equals(".") || id.equals("..")) problem = "Store id must not be '" + id + "'";
        else if (id.contains("/") || id.contains("\\")) problem = "Store id must not contain path separators";
        else if (!KEBAB_ID.matcher(id).matches()) {
            problem = "Store id must be kebab-case with lowercase letters, numbers, and single hyphen separators";
        } else {
            return null;
        }
        return new RootResolution.Problem("invalid_store_id", problem, KEBAB_ID_FIX);
    }

    /**
     * Identity first, then root health, as upstream's {@code inspectRegisteredStore} orders them. The metadata must
     * match upstream's strict schema ({@code version: 1}, a string {@code id}, an optional non-empty {@code remote},
     * nothing else) and carry a valid id.
     */
    static @Nullable RootResolution.Problem inspectStore(String id, Path storeRoot) {
        Path metadata = storeRoot.resolve(STORE_METADATA);
        if (!Files.isRegularFile(metadata)) {
            return new RootResolution.Problem("store_identity_mismatch",
                    "Store '" + id + "' is missing identity metadata at " + metadata + ". " + doctorFix(id),
                    doctorFix(id));
        }
        Object raw;
        try {
            raw = new Yaml().load(Files.readString(metadata));
        } catch (Exception e) {
            return invalidMetadata(String.valueOf(e.getMessage()));
        }
        String schema = metadataSchemaProblem(raw);
        if (schema != null) return invalidMetadata(schema);
        String actual = String.valueOf(((Map<?, ?>) raw).get("id"));
        RootResolution.Problem idProblem = storeIdProblem(actual);
        if (idProblem != null) return idProblem;
        if (!id.equals(actual)) {
            return new RootResolution.Problem("store_identity_mismatch",
                    "Store '" + id + "' metadata id '" + actual + "' does not match its registered id. " + doctorFix(id),
                    doctorFix(id));
        }
        String health = healthProblem(storeRoot);
        return health == null ? null : unhealthy(id, storeRoot.toString(), health);
    }

    private static RootResolution.Problem invalidMetadata(String detail) {
        return new RootResolution.Problem("invalid_store_metadata", "Invalid store metadata state: " + detail,
                "Repair .openspec-store/store.yaml.");
    }

    /** The first way {@code raw} breaks upstream's metadata schema, in its wording; {@code null} when it fits. */
    static @Nullable String metadataSchemaProblem(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return "root: Invalid input: expected object";
        for (Object key : map.keySet()) {
            if (!"version".equals(key) && !"id".equals(key) && !"remote".equals(key)) {
                return "root: Unrecognized key: \"" + key + "\"";
            }
        }
        if (!Integer.valueOf(1).equals(map.get("version"))) return "version: Invalid input: expected 1";
        if (!(map.get("id") instanceof String)) return "id: Invalid input: expected string";
        Object remote = map.get("remote");
        if (remote != null && (!(remote instanceof String r) || r.isEmpty())) {
            return "remote: Invalid input: expected a non-empty string";
        }
        return null;
    }

    private static RootResolution.Problem unhealthy(String id, String root, String detail) {
        return new RootResolution.Problem("unhealthy_store_root",
                "Store '" + id + "' does not have a healthy OpenSpec root at " + root + ": " + detail + " "
                        + doctorFix(id), doctorFix(id));
    }

    /** Upstream's {@code inspectOpenSpecRoot}: the root and {@code openspec/} are directories, a config file is
     * present, and {@code specs/} or {@code changes/}, when present, are directories. */
    static @Nullable String healthProblem(Path storeRoot) {
        if (!Files.exists(storeRoot)) return "Store root does not exist.";
        if (!Files.isDirectory(storeRoot)) return "Store root is not a directory.";
        Path openspec = storeRoot.resolve(OPENSPEC_DIR);
        if (!Files.exists(openspec)) return "Missing openspec/ directory.";
        if (!Files.isDirectory(openspec)) return "openspec/ exists but is not a directory.";
        Path yaml = openspec.resolve("config.yaml");
        Path yml = openspec.resolve("config.yml");
        if (!Files.isRegularFile(yaml) && !Files.isRegularFile(yml)) {
            return Files.exists(yaml) || Files.exists(yml) ? "OpenSpec config path exists but is not a file."
                    : "Missing openspec/config.yaml or openspec/config.yml.";
        }
        for (String sub : new String[]{"specs", "changes"}) {
            Path p = openspec.resolve(sub);
            if (Files.exists(p) && !Files.isDirectory(p)) return "openspec/" + sub + " exists but is not a directory.";
        }
        return null;
    }

    private static String doctorFix(String id) {
        return "Run openspec store doctor " + id + " to inspect it.";
    }

    // ---- Pointer and global config --------------------------------------------------------------------------------

    /** A project config's {@code store:} key, read as upstream's {@code readStorePointer} does. */
    record Pointer(@Nullable Path file, @Nullable String value, @Nullable String malformed) {
        static final Pointer NONE = new Pointer(null, null, null);
    }

    static Pointer readPointer(@Nullable Path config) {
        if (config == null) return Pointer.NONE;
        Object raw;
        try {
            raw = new Yaml().load(Files.readString(config));
        } catch (Exception e) {
            return new Pointer(config, null, "unparseable");
        }
        if (!(raw instanceof Map<?, ?> map)) return new Pointer(config, null, null);
        Object value = map.get("store");
        if (value == null && !map.containsKey("store")) return new Pointer(config, null, null);
        if (value instanceof String s) return new Pointer(config, s, null);
        return new Pointer(config, null, "non_string");
    }

    /** The global config's {@code defaultStore}, or {@code null} when unset, empty or unreadable. */
    static @Nullable String readDefaultStore(Path globalConfig) {
        if (!Files.isRegularFile(globalConfig)) return null;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(globalConfig));
            if (!parsed.isJsonObject()) return null;
            JsonObject obj = parsed.getAsJsonObject();
            JsonElement value = obj.get("defaultStore");
            if (value == null || !value.isJsonPrimitive()) return null;
            String id = value.getAsString();
            return id.isEmpty() ? null : id;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---- Helpers --------------------------------------------------------------------------------------------------

    /** The real path, as the CLI canonicalizes; the normalized absolute path when it doesn't exist. */
    public static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | SecurityException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static boolean has(@Nullable String release, String since) {
        return release != null && CliVersion.atLeast(release, since);
    }
}
