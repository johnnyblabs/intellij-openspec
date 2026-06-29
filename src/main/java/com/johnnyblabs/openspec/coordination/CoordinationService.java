package com.johnnyblabs.openspec.coordination;

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

    /** Whether the CLI is available at or above the coordination floor (1.4). */
    public boolean cliCoordinationAvailable() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable()
                && CliVersion.atLeast(detection.getDetectedVersion(), COORDINATION_CLI_FLOOR);
    }

    /**
     * Resolves all three collections and the presentation tier in one off-EDT snapshot.
     *
     * @param coordinationModeActive whether the active workflow mode is a coordination mode
     *                               (a non-default mode such as {@code workspace-planning})
     */
    public CoordinationData getCoordinationData(boolean coordinationModeActive) {
        boolean cli = cliCoordinationAvailable();
        List<WorkspaceEntry> workspaces = resolveWorkspaces();
        List<ContextStoreEntry> stores = resolveContextStores();
        List<InitiativeEntry> initiatives = resolveInitiatives();
        boolean hasState = !workspaces.isEmpty() || !stores.isEmpty() || !initiatives.isEmpty();
        CoordinationTier tier = CoordinationTier.resolve(hasState, coordinationModeActive, cli);
        return new CoordinationData(workspaces, stores, initiatives, tier, cli);
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
        JsonObject root = GSON.fromJson(json, JsonObject.class);
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
        JsonObject root = GSON.fromJson(json, JsonObject.class);
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

    @Nullable
    private static String backendLocalPath(@Nullable Object entry) {
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
        JsonObject root = GSON.fromJson(json, JsonObject.class);
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
            JsonObject root = GSON.fromJson(r.stdout(), JsonObject.class);
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
            return WriteResult.fail("OpenSpec CLI 1.4+ is required to create initiatives.");
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
            return WriteResult.fail("OpenSpec CLI 1.4+ is required to set up a context store.");
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
            return WriteResult.fail("OpenSpec CLI 1.4+ is required to set up a workspace.");
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
        } catch (CliRunner.CliException e) {
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
