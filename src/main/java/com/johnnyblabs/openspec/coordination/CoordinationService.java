package com.johnnyblabs.openspec.coordination;

import com.johnnyblabs.openspec.util.CliJson;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Resolves the three OpenSpec 1.4 coordination collections — workspaces, context stores, and
 * initiatives — preferring the CLI's JSON output and falling back to reading the global data
 * dir directly when the CLI is unavailable or below the coordination floor.
 *
 * <p>All resolution methods may spawn an external process or read disk and therefore
 * <b>MUST NOT be called on the EDT</b>. The service returns data only; it never touches UI.
 *
 * @see CoordinationPaths for the on-disk layout
 * @see CoordinationTier for how the presentation tier is derived
 */
@Service(Service.Level.PROJECT)
public final class CoordinationService {
    private static final Logger LOG = Logger.getInstance(CoordinationService.class);
    private static final Gson GSON = new Gson();

    /**
     * The CLI version at which the coordination commands ({@code workspace}, {@code context-store},
     * {@code initiative}) exist. Below this, the CLI cannot serve the collections and the
     * built-in on-disk fallback is used (Awareness tier at most).
     */
    public static final String COORDINATION_CLI_FLOOR = "1.4.0";

    /**
     * The CLI version at which the coordination commands were <b>removed</b>. OpenSpec CLI 1.5.0
     * replaced {@code workspace} / {@code context-store} / {@code initiative} with the {@code store}
     * / {@code workset} model, so those subcommands no longer exist. Coordination is therefore
     * served by the CLI only in the half-open window {@code [COORDINATION_CLI_FLOOR, this)}; on
     * {@code >= this} the plugin must not invoke the removed commands.
     */
    public static final String COORDINATION_CLI_CEILING = "1.5.0";

    /**
     * The CLI version at which the {@code store} / {@code workset} model exists. At or above this
     * floor the plugin prefers the CLI's JSON for stores and worksets; below it (or when the CLI is
     * absent) it reads the on-disk {@code stores/registry.yaml} and {@code worksets/worksets.yaml}
     * directly and presents whatever exists read-only. This floor is evaluated here via
     * {@link CliVersion#atLeast}; it is deliberately <b>not</b> modeled on {@code VersionSupport}
     * (the pinned config-format axis).
     */
    public static final String STORE_CLI_FLOOR = "1.5.0";

    private final Project project;
    private volatile @Nullable CoordinationPaths pathsOverride;

    public CoordinationService(Project project) {
        this.project = project;
    }

    @TestOnly
    public void setPathsForTest(@Nullable CoordinationPaths paths) {
        this.pathsOverride = paths;
    }

    private CoordinationPaths paths() {
        CoordinationPaths override = pathsOverride;
        return override != null ? override : CoordinationPaths.resolve();
    }

    /**
     * Whether the CLI can serve the coordination commands — i.e. it is available and its version is
     * in the window {@code [1.4.0, 1.5.0)}. Returns {@code false} on CLI {@code >= 1.5.0}, where the
     * {@code workspace} / {@code context-store} / {@code initiative} commands were removed, so the
     * plugin falls back to reading on-disk state (Awareness) and never invokes a removed command.
     */
    public boolean cliCoordinationAvailable() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable()
                && CliVersion.inRange(detection.getDetectedVersion(),
                        COORDINATION_CLI_FLOOR, COORDINATION_CLI_CEILING);
    }

    /**
     * Whether the detected CLI is at or above the coordination ceiling ({@code >= 1.5.0}) — a
     * version that has removed the coordination commands. Used to stand the surface down (no Full
     * tier, and no mode-forced Awareness) even if a stale non-default-mode marker is present.
     */
    private boolean cliAtOrAboveCoordinationCeiling() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable()
                && CliVersion.atLeast(detection.getDetectedVersion(), COORDINATION_CLI_CEILING);
    }

    /**
     * Whether the CLI can serve the {@code store} / {@code workset} commands — i.e. it is available
     * and its version is at or above {@link #STORE_CLI_FLOOR} ({@code 1.5.0}). When true, stores and
     * worksets are the canonical lead model and any legacy 1.4 state on disk is demoted. The gate is
     * derived solely from the detected CLI version and never consults the config-format version axis.
     */
    public boolean cliStoreAvailable() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable()
                && CliVersion.atLeast(detection.getDetectedVersion(), STORE_CLI_FLOOR);
    }

    /**
     * Resolves all three collections and the presentation tier in one off-EDT snapshot.
     *
     * @param coordinationModeActive whether the active workflow mode is a coordination mode
     *                               (a non-default mode such as {@code workspace-planning})
     */
    public CoordinationData getCoordinationData(boolean coordinationModeActive) {
        boolean cli = cliCoordinationAvailable();
        boolean storeCli = cliStoreAvailable();
        List<WorkspaceEntry> workspaces = resolveWorkspaces();
        List<ContextStoreEntry> contextStores = resolveContextStores();
        List<InitiativeEntry> initiatives = resolveInitiatives();
        List<StoreEntry> stores = resolveStores();
        List<WorksetEntry> worksets = resolveWorksets();
        // Enrich stores with lazy doctor health off the EDT so the panel can render badges and the
        // retained diagnostic fix text. A failed/absent doctor leaves the entry unchanged.
        if (storeCli && !stores.isEmpty()) {
            List<StoreEntry> enriched = new ArrayList<>(stores.size());
            for (StoreEntry s : stores) {
                enriched.add(fetchStoreDoctor(s));
            }
            stores = enriched;
        }
        boolean legacyState = !workspaces.isEmpty() || !contextStores.isEmpty() || !initiatives.isEmpty();
        boolean newState = !stores.isEmpty() || !worksets.isEmpty();
        boolean hasState = legacyState || newState;
        // On a CLI >= 1.5.0 the coordination commands (and the non-default coordination mode that
        // used to trigger them) are gone. A lingering mode marker must not force a non-Hidden tier:
        // the surface stands down to Awareness only when real on-disk state exists, else Hidden.
        boolean effectiveMode = coordinationModeActive && !cliAtOrAboveCoordinationCeiling();
        CoordinationTier tier = CoordinationTier.resolve(hasState, effectiveMode, cli);
        return new CoordinationData(workspaces, contextStores, initiatives, stores, worksets,
                tier, cli, storeCli, storeCli, legacyState);
    }

    // ---- Workspaces ----------------------------------------------------------

    public List<WorkspaceEntry> resolveWorkspaces() {
        if (cliCoordinationAvailable()) {
            String json = runListJson("workspace", "list");
            if (json != null) {
                try {
                    return parseWorkspaces(json);
                } catch (Exception e) {
                    LOG.warn("Failed to parse workspace list JSON; falling back to disk", e);
                }
            }
        }
        return readWorkspacesFromDisk(paths());
    }

    static List<WorkspaceEntry> parseWorkspaces(String json) {
        List<WorkspaceEntry> result = new ArrayList<>();
        JsonObject root = GSON.fromJson(CliJson.extractJsonPayload(json), JsonObject.class);
        if (root == null) return result;
        JsonArray arr = arrayOf(root, "workspaces");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String name = stringOf(o, "name");
            String path = stringOf(o, "path", "workspaceRoot", "root", "workspace_root");
            if (name == null && path == null) continue;
            Boolean resolved = boolOf(o, "resolvesLocally", "resolves", "resolved");
            boolean resolvesLocally = resolved != null
                    ? resolved
                    : (path != null && Files.exists(Path.of(path)));
            result.add(new WorkspaceEntry(name != null ? name : path, path, resolvesLocally));
        }
        return result;
    }

    static List<WorkspaceEntry> readWorkspacesFromDisk(CoordinationPaths paths) {
        // Managed workspaces exist as subdirectories of the workspaces dir; the registry.yaml
        // may additionally map names to external linked paths (and can be empty). Union both,
        // preferring an explicit registry path over the managed directory.
        java.util.LinkedHashMap<String, String> byName = new java.util.LinkedHashMap<>();
        Path dir = paths.managedWorkspacesDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> children = Files.list(dir)) {
                children.filter(Files::isDirectory)
                        .forEach(d -> byName.put(d.getFileName().toString(), d.toString()));
            } catch (Exception e) {
                LOG.debug("Failed to scan managed workspaces dir: " + dir, e);
            }
        }
        Map<String, Object> registry = readYamlMap(paths.workspaceRegistryFile());
        if (registry != null && registry.get("workspaces") instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() != null) {
                    byName.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }
        List<WorkspaceEntry> result = new ArrayList<>();
        for (Map.Entry<String, String> e : byName.entrySet()) {
            String path = e.getValue();
            boolean resolves = path != null && Files.exists(Path.of(path));
            result.add(new WorkspaceEntry(e.getKey(), path, resolves));
        }
        return result;
    }

    // ---- Context stores ------------------------------------------------------

    public List<ContextStoreEntry> resolveContextStores() {
        if (cliCoordinationAvailable()) {
            String json = runListJson("context-store", "list");
            if (json != null) {
                try {
                    return parseContextStores(json);
                } catch (Exception e) {
                    LOG.warn("Failed to parse context-store list JSON; falling back to disk", e);
                }
            }
        }
        return readContextStoresFromDisk(paths());
    }

    static List<ContextStoreEntry> parseContextStores(String json) {
        List<ContextStoreEntry> result = new ArrayList<>();
        JsonObject root = GSON.fromJson(CliJson.extractJsonPayload(json), JsonObject.class);
        if (root == null) return result;
        JsonArray arr = arrayOf(root, "context_stores", "contextStores", "stores");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String id = stringOf(o, "id");
            String storeRoot = stringOf(o, "root", "storeRoot", "store_root");
            String metadataPath = stringOf(o, "metadataPath", "metadata_path");
            if (id == null && storeRoot == null) continue;
            result.add(ContextStoreEntry.basic(id != null ? id : storeRoot, storeRoot, metadataPath));
        }
        return result;
    }

    static List<ContextStoreEntry> readContextStoresFromDisk(CoordinationPaths paths) {
        List<ContextStoreEntry> result = new ArrayList<>();
        Map<String, Object> registry = readYamlMap(paths.contextStoreRegistryFile());
        if (registry == null) return result;
        Object stores = registry.get("stores");
        if (stores instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String id = String.valueOf(e.getKey());
                String storeRoot = backendLocalPath(e.getValue());
                result.add(ContextStoreEntry.basic(id, storeRoot, null));
            }
        }
        return result;
    }

    /**
     * Extracts the {@code backend.local_path} (or {@code localPath}) from a registry entry. Shared
     * by both the context-store registry reader and the 1.5 store registry reader — the two files
     * are byte-identical in shape ({@code stores: {<id>: {backend: {type, local_path}}}}), so this
     * single parser serves both and must not be forked.
     */
    @Nullable
    static String backendLocalPath(@Nullable Object entry) {
        if (entry instanceof Map<?, ?> m) {
            Object backend = m.get("backend");
            if (backend instanceof Map<?, ?> b) {
                Object local = b.get("localPath");
                if (local == null) local = b.get("local_path");
                if (local != null) return String.valueOf(local);
            }
        }
        return null;
    }

    // ---- Stores (OpenSpec 1.5) ----------------------------------------------

    /**
     * Resolves the 1.5 stores: CLI-first when {@link #cliStoreAvailable()} (parsing
     * {@code store list --json}), falling back to reading {@code stores/registry.yaml} on a parse
     * failure or below the floor. Never throws — a failure degrades to the on-disk fallback.
     */
    public List<StoreEntry> resolveStores() {
        if (cliStoreAvailable()) {
            String json = runListJson("store", "list");
            if (json != null) {
                try {
                    return parseStores(json);
                } catch (Exception e) {
                    LOG.warn("Failed to parse store list JSON; falling back to disk", e);
                }
            }
        }
        return readStoresFromDisk(paths());
    }

    static List<StoreEntry> parseStores(String json) {
        List<StoreEntry> result = new ArrayList<>();
        JsonObject root = GSON.fromJson(CliJson.extractJsonPayload(json), JsonObject.class);
        if (root == null) return result;
        JsonArray arr = arrayOf(root, "stores");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String id = stringOf(o, "id");
            String storeRoot = stringOf(o, "root", "storeRoot", "store_root");
            if (id == null && storeRoot == null) continue;
            result.add(StoreEntry.basic(id != null ? id : storeRoot, storeRoot));
        }
        return result;
    }

    /**
     * Parses {@code store doctor --json} into fully-enriched {@link StoreEntry} values. Reads
     * {@code metadata.{present,valid}}, {@code git.is_repository}, and {@code openspec_root.healthy}
     * from their exact nested keys, and retains each per-store diagnostic (with its {@code fix}).
     * Every {@code git} subfield is treated as nullable — a non-git store leaves them null and must
     * never NPE.
     */
    static List<StoreEntry> parseStoreDoctor(String json) {
        List<StoreEntry> result = new ArrayList<>();
        JsonObject root = GSON.fromJson(CliJson.extractJsonPayload(json), JsonObject.class);
        if (root == null) return result;
        JsonArray arr = arrayOf(root, "stores", "context_stores");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String id = stringOf(o, "id");
            String storeRoot = stringOf(o, "root", "storeRoot", "store_root");
            if (id == null && storeRoot == null) continue;
            Boolean present = null, valid = null, git = null, healthy = null;
            if (o.get("metadata") != null && o.get("metadata").isJsonObject()) {
                JsonObject md = o.getAsJsonObject("metadata");
                present = boolOf(md, "present");
                valid = boolOf(md, "valid");
            }
            if (o.get("git") != null && o.get("git").isJsonObject()) {
                git = boolOf(o.getAsJsonObject("git"), "is_repository", "isRepository");
            }
            if (o.get("openspec_root") != null && o.get("openspec_root").isJsonObject()) {
                healthy = boolOf(o.getAsJsonObject("openspec_root"), "healthy");
            } else if (o.get("openspecRoot") != null && o.get("openspecRoot").isJsonObject()) {
                healthy = boolOf(o.getAsJsonObject("openspecRoot"), "healthy");
            }
            List<Diagnostic> diagnostics = parseDiagnostics(o);
            result.add(StoreEntry.basic(id != null ? id : storeRoot, storeRoot)
                    .withDoctor(present, valid, git, healthy, diagnostics));
        }
        return result;
    }

    static List<StoreEntry> readStoresFromDisk(CoordinationPaths paths) {
        List<StoreEntry> result = new ArrayList<>();
        Map<String, Object> registry = readYamlMap(paths.storeRegistryFile());
        if (registry == null) return result;
        Object stores = registry.get("stores");
        if (stores instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String id = String.valueOf(e.getKey());
                // Reuse the shared backend-local-path parser — the store registry is byte-identical
                // in shape to the context-store registry.
                String storeRoot = backendLocalPath(e.getValue());
                result.add(StoreEntry.basic(id, storeRoot));
            }
        }
        return result;
    }

    /**
     * Lazily enriches a store with {@code store doctor} health detail and diagnostics. Runs
     * {@code store doctor <id> --json} off the EDT. Returns the entry unchanged when the CLI is
     * unavailable, the id is null, or the lookup fails — it never throws.
     */
    public StoreEntry fetchStoreDoctor(StoreEntry entry) {
        if (!cliStoreAvailable() || entry.id() == null) return entry;
        try {
            CliRunner.CliResult r = CliRunner.run(project, "store", "doctor", entry.id(), "--json");
            if (!r.isSuccess()) return entry;
            for (StoreEntry parsed : parseStoreDoctor(r.stdout())) {
                if (entry.id().equals(parsed.id())) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            LOG.debug("store doctor failed for " + entry.id(), e);
        }
        return entry;
    }

    // ---- Worksets (OpenSpec 1.5) --------------------------------------------

    /**
     * Resolves the 1.5 worksets: CLI-first when {@link #cliStoreAvailable()} (parsing
     * {@code workset list --json}), falling back to reading {@code worksets/worksets.yaml} on a
     * parse failure or below the floor. Never throws.
     */
    public List<WorksetEntry> resolveWorksets() {
        if (cliStoreAvailable()) {
            String json = runListJson("workset", "list");
            if (json != null) {
                try {
                    return parseWorksets(json);
                } catch (Exception e) {
                    LOG.warn("Failed to parse workset list JSON; falling back to disk", e);
                }
            }
        }
        return readWorksetsFromDisk(paths());
    }

    static List<WorksetEntry> parseWorksets(String json) {
        List<WorksetEntry> result = new ArrayList<>();
        JsonObject root = GSON.fromJson(CliJson.extractJsonPayload(json), JsonObject.class);
        if (root == null) return result;
        JsonArray arr = arrayOf(root, "worksets");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String name = stringOf(o, "name");
            if (name == null) continue;
            List<WorksetEntry.Member> members = new ArrayList<>();
            JsonArray memberArr = arrayOf(o, "members");
            for (JsonElement me : memberArr) {
                if (!me.isJsonObject()) continue;
                JsonObject mo = me.getAsJsonObject();
                String mName = stringOf(mo, "name");
                String mPath = stringOf(mo, "path");
                if (mName == null && mPath == null) continue;
                members.add(new WorksetEntry.Member(mName != null ? mName : mPath, mPath));
            }
            result.add(new WorksetEntry(name, members));
        }
        return result;
    }

    static List<WorksetEntry> readWorksetsFromDisk(CoordinationPaths paths) {
        List<WorksetEntry> result = new ArrayList<>();
        Map<String, Object> registry = readYamlMap(paths.worksetsFile());
        if (registry == null) return result;
        Object worksets = registry.get("worksets");
        if (worksets instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String name = String.valueOf(e.getKey());
                List<WorksetEntry.Member> members = new ArrayList<>();
                if (e.getValue() instanceof Map<?, ?> body && body.get("members") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> mm) {
                            String mName = mm.get("name") != null ? String.valueOf(mm.get("name")) : null;
                            String mPath = mm.get("path") != null ? String.valueOf(mm.get("path")) : null;
                            if (mName == null && mPath == null) continue;
                            members.add(new WorksetEntry.Member(mName != null ? mName : mPath, mPath));
                        }
                    }
                }
                result.add(new WorksetEntry(name, members));
            }
        }
        return result;
    }

    // ---- Diagnostic envelope + root canonicalization ------------------------

    /** Parses the uniform 1.5 diagnostic envelope {@code status: [{severity, code, message, target, fix}]}. */
    static List<Diagnostic> parseDiagnostics(@Nullable JsonObject obj) {
        List<Diagnostic> result = new ArrayList<>();
        JsonArray arr = arrayOf(obj, "status");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            result.add(new Diagnostic(
                    stringOf(o, "severity"),
                    stringOf(o, "code"),
                    stringOf(o, "message"),
                    stringOf(o, "target"),
                    stringOf(o, "fix")));
        }
        return result;
    }

    /**
     * Canonicalizes a path for equality comparison: {@code toRealPath()} first (resolving symlinks
     * and Windows short paths, matching what the CLI records), falling back to
     * {@code toAbsolutePath().normalize()} when the path does not exist or real-path resolution
     * throws.
     */
    static Path canonicalize(Path p) {
        if (p == null) return null;
        try {
            return p.toRealPath();
        } catch (Exception e) {
            return p.toAbsolutePath().normalize();
        }
    }

    /**
     * Returns the store whose (canonicalized) root matches the (canonicalized) project root, or
     * null when none matches. Both sides are canonicalized so a symlinked, non-normalized, or
     * short-path root still matches — raw string comparison would miss those.
     */
    @Nullable
    static StoreEntry storeMatchingRoot(List<StoreEntry> stores, @Nullable Path projectRoot) {
        if (projectRoot == null) return null;
        Path canonProject = canonicalize(projectRoot);
        for (StoreEntry s : stores) {
            if (s.root() == null) continue;
            Path canonStore = canonicalize(Path.of(s.root()));
            if (canonStore != null && canonStore.equals(canonProject)) {
                return s;
            }
        }
        return null;
    }

    /** Instance convenience: the store matching this project's base path, or null. */
    @Nullable
    public StoreEntry storeMatchingProjectRoot(List<StoreEntry> stores) {
        String base = project.getBasePath();
        return base != null ? storeMatchingRoot(stores, Path.of(base)) : null;
    }

    // ---- Initiatives ---------------------------------------------------------

    public List<InitiativeEntry> resolveInitiatives() {
        if (cliCoordinationAvailable()) {
            String json = runListJson("initiative", "list");
            if (json != null) {
                try {
                    return parseInitiatives(json);
                } catch (Exception e) {
                    LOG.warn("Failed to parse initiative list JSON; falling back to disk", e);
                }
            }
        }
        return readInitiativesFromDisk(paths());
    }

    static List<InitiativeEntry> parseInitiatives(String json) {
        List<InitiativeEntry> result = new ArrayList<>();
        JsonObject root = GSON.fromJson(CliJson.extractJsonPayload(json), JsonObject.class);
        if (root == null) return result;
        // The CLI emits a flat top-level `initiatives` array across all stores; each entry
        // carries its own `root` (the initiative directory) and `store` (the store id).
        JsonArray arr = arrayOf(root, "initiatives");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String id = stringOf(o, "id");
            if (id == null) continue;
            String title = orEmpty(stringOf(o, "title"));
            String summary = orEmpty(stringOf(o, "summary"));
            InitiativeStatus status = InitiativeStatus.fromString(stringOf(o, "status"));
            String created = stringOf(o, "created");
            String store = stringOf(o, "store", "store_id");
            String initiativeRoot = stringOf(o, "root");
            List<String> owners = new ArrayList<>();
            JsonArray ownersArr = arrayOf(o, "owners");
            for (JsonElement oe : ownersArr) {
                if (oe.isJsonPrimitive()) owners.add(oe.getAsString());
            }
            result.add(new InitiativeEntry(id, title, summary, status, created, owners, store, initiativeRoot));
        }
        return result;
    }

    static List<InitiativeEntry> readInitiativesFromDisk(CoordinationPaths paths) {
        List<InitiativeEntry> result = new ArrayList<>();
        for (ContextStoreEntry store : readContextStoresFromDisk(paths)) {
            if (store.root() == null) continue;
            // Initiatives live under <storeRoot>/initiatives/<id>/.
            Path initiativesDir = Path.of(store.root()).resolve("initiatives");
            if (!Files.isDirectory(initiativesDir)) continue;
            try (Stream<Path> children = Files.list(initiativesDir)) {
                children.filter(Files::isDirectory).forEach(dir -> {
                    Path meta = dir.resolve(InitiativeArtifact.METADATA.fileName());
                    if (Files.isRegularFile(meta)) {
                        InitiativeEntry entry = readInitiativeYaml(meta, store.id(), dir.toString());
                        if (entry != null) result.add(entry);
                    }
                });
            } catch (Exception e) {
                LOG.debug("Failed to scan context store for initiatives: " + initiativesDir, e);
            }
        }
        return result;
    }

    @Nullable
    private static InitiativeEntry readInitiativeYaml(Path metadataFile, @Nullable String store, String initiativeRoot) {
        Map<String, Object> map = readYamlMap(metadataFile);
        if (map == null) return null;
        String id = map.get("id") != null ? String.valueOf(map.get("id"))
                : metadataFile.getParent().getFileName().toString();
        String title = map.get("title") != null ? String.valueOf(map.get("title")) : "";
        String summary = map.get("summary") != null ? String.valueOf(map.get("summary")) : "";
        InitiativeStatus status = InitiativeStatus.fromString(
                map.get("status") != null ? String.valueOf(map.get("status")) : null);
        String created = map.get("created") != null ? String.valueOf(map.get("created")) : null;
        List<String> owners = new ArrayList<>();
        if (map.get("owners") instanceof List<?> list) {
            for (Object o : list) owners.add(String.valueOf(o));
        }
        return new InitiativeEntry(id, title, summary, status, created, owners, store, initiativeRoot);
    }

    // ---- Doctor (lazy) -------------------------------------------------------

    /**
     * Enriches a context store with {@code doctor} health detail. Returns the entry unchanged
     * when the CLI is unavailable or the lookup fails. Off-EDT.
     */
    public ContextStoreEntry fetchContextStoreDoctor(ContextStoreEntry entry) {
        if (!cliCoordinationAvailable() || entry.id() == null) return entry;
        try {
            CliRunner.CliResult r = CliRunner.run(project, "context-store", "doctor", entry.id(), "--json");
            if (!r.isSuccess()) return entry;
            JsonObject root = GSON.fromJson(CliJson.extractJsonPayload(r.stdout()), JsonObject.class);
            JsonArray stores = arrayOf(root, "context_stores", "stores");
            for (JsonElement el : stores) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!entry.id().equals(stringOf(o, "id"))) continue;
                Boolean present = null, valid = null, git = null;
                if (o.get("metadata") != null && o.get("metadata").isJsonObject()) {
                    JsonObject md = o.getAsJsonObject("metadata");
                    present = boolOf(md, "present");
                    valid = boolOf(md, "valid");
                }
                if (o.get("git") != null && o.get("git").isJsonObject()) {
                    git = boolOf(o.getAsJsonObject("git"), "isRepository", "is_repository");
                }
                return entry.withDoctor(present, valid, git);
            }
        } catch (Exception e) {
            LOG.debug("context-store doctor failed for " + entry.id(), e);
        }
        return entry;
    }

    // ---- Write actions (Full tier) ------------------------------------------

    /** Outcome of a coordination write action. */
    public record WriteResult(boolean success, String message, @Nullable String createdPath) {
        public static WriteResult ok(String message, @Nullable String path) {
            return new WriteResult(true, message, path);
        }

        public static WriteResult fail(String message) {
            return new WriteResult(false, message, null);
        }
    }

    /**
     * Creates an initiative via {@code openspec initiative create <id> --title <title>}.
     * Off-EDT. On success, returns the path to the created {@code initiative.yaml} when it
     * can be located so the caller can open it.
     */
    public WriteResult createInitiative(String id, String title) {
        if (!cliCoordinationAvailable()) {
            return WriteResult.fail("Creating initiatives requires an OpenSpec CLI in the 1.4.x line "
                    + "(the coordination commands were removed in 1.5.0).");
        }
        try {
            CliRunner.CliResult r = CliRunner.run(project, "initiative", "create", id, "--title", title);
            if (!r.isSuccess()) {
                return WriteResult.fail(stderrOrStdout(r));
            }
            String createdPath = findInitiativeMetadataPath(id);
            return WriteResult.ok("Created initiative '" + id + "'.", createdPath);
        } catch (CliRunner.CliException e) {
            return WriteResult.fail("Failed to create initiative: " + e.getMessage());
        }
    }

    /** Sets up/registers a context store via {@code openspec context-store setup [id]}. Off-EDT. */
    public WriteResult setupContextStore(@Nullable String id) {
        if (!cliCoordinationAvailable()) {
            return WriteResult.fail("Setting up a context store requires an OpenSpec CLI in the 1.4.x "
                    + "line (the coordination commands were removed in 1.5.0).");
        }
        try {
            CliRunner.CliResult r = (id == null || id.isBlank())
                    ? CliRunner.run(project, "context-store", "setup")
                    : CliRunner.run(project, "context-store", "setup", id);
            return r.isSuccess()
                    ? WriteResult.ok("Set up context store.", null)
                    : WriteResult.fail(stderrOrStdout(r));
        } catch (CliRunner.CliException e) {
            return WriteResult.fail("Failed to set up context store: " + e.getMessage());
        }
    }

    /** Sets up a workspace via {@code openspec workspace setup --name <name>}. Off-EDT. */
    public WriteResult setupWorkspace(String name) {
        if (!cliCoordinationAvailable()) {
            return WriteResult.fail("Setting up a workspace requires an OpenSpec CLI in the 1.4.x line "
                    + "(the coordination commands were removed in 1.5.0).");
        }
        try {
            CliRunner.CliResult r = CliRunner.run(project,
                    "workspace", "setup", "--name", name, "--no-interactive");
            return r.isSuccess()
                    ? WriteResult.ok("Set up workspace '" + name + "'.", null)
                    : WriteResult.fail(stderrOrStdout(r));
        } catch (CliRunner.CliException e) {
            return WriteResult.fail("Failed to set up workspace: " + e.getMessage());
        }
    }

    @Nullable
    private String findInitiativeMetadataPath(String id) {
        for (InitiativeEntry initiative : resolveInitiatives()) {
            if (id.equals(initiative.id())) {
                Path p = InitiativeArtifact.METADATA.resolveExistingPath(initiative);
                return p != null ? p.toString() : null;
            }
        }
        return null;
    }

    // ---- helpers -------------------------------------------------------------

    @Nullable
    private String runListJson(String command, String sub) {
        try {
            CliRunner.CliResult r = CliRunner.run(project, command, sub, "--json");
            if (r.isSuccess()) {
                return r.stdout();
            }
            LOG.warn("openspec " + command + " " + sub + " --json failed: " + r.stderr());
        } catch (Exception e) {
            // Any failure (CLI error, missing service, unexpected runtime error) degrades to the
            // on-disk fallback rather than throwing into the caller — the read surface is beta-guarded.
            LOG.warn("openspec " + command + " " + sub + " --json errored", e);
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYamlMap(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            String content = Files.readString(file);
            Object loaded = new Yaml().load(content);
            return loaded instanceof Map ? (Map<String, Object>) loaded : null;
        } catch (Exception e) {
            LOG.debug("Failed to read coordination YAML: " + file, e);
            return null;
        }
    }

    private static JsonArray arrayOf(@Nullable JsonObject obj, String... keys) {
        if (obj != null) {
            for (String key : keys) {
                JsonElement el = obj.get(key);
                if (el != null && el.isJsonArray()) {
                    return el.getAsJsonArray();
                }
            }
        }
        return new JsonArray();
    }

    @Nullable
    private static String stringOf(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement el = obj.get(key);
            if (el != null && el.isJsonPrimitive()) {
                String s = el.getAsString();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    @Nullable
    private static Boolean boolOf(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement el = obj.get(key);
            if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
                return el.getAsBoolean();
            }
        }
        return null;
    }

    private static String orEmpty(@Nullable String s) {
        return s != null ? s : "";
    }

    private static String stderrOrStdout(CliRunner.CliResult r) {
        String err = r.stderr() != null ? r.stderr().trim() : "";
        if (!err.isEmpty()) return err;
        String out = r.stdout() != null ? r.stdout().trim() : "";
        return out.isEmpty() ? "OpenSpec CLI command failed." : out;
    }
}
