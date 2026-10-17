package com.johnnyblabs.openspec.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.model.SchemaResolution;
import com.johnnyblabs.openspec.model.SchemaValidationReport;
import com.johnnyblabs.openspec.util.CliJson;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.CliVersion;
import com.johnnyblabs.openspec.version.VersionSupport;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service(Service.Level.PROJECT)
public final class SchemaService {

    private static final Logger LOG = Logger.getInstance(SchemaService.class);
    private static final String MIN_CLI_VERSION = "1.3.0";

    private final Project project;
    private List<SchemaInfo> cachedSchemas;
    private Map<String, SchemaResolution> cachedResolutions;

    public SchemaService(Project project) {
        this.project = project;
    }

    /**
     * Lists available schemas by calling {@code openspec schemas --json}.
     * Results are cached until invalidated by fork/init or explicit refresh.
     */
    public List<SchemaInfo> listSchemas() {
        if (cachedSchemas != null) {
            return cachedSchemas;
        }

        if (!isSchemaSupported()) {
            return Collections.emptyList();
        }

        try {
            CliRunner.CliResult result = CliRunner.run(project, "schemas", "--json");
            if (!result.isSuccess()) {
                LOG.warn("openspec schemas --json failed: " + result.stderr());
                return Collections.emptyList();
            }

            cachedSchemas = parseSchemaList(result.stdout());
            return cachedSchemas;
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to list schemas", e);
            return Collections.emptyList();
        }
    }

    /**
     * Forks an existing schema by calling {@code openspec schema fork <source> <name> --json}.
     *
     * @return the new schema's {@code schema.yaml} (or its directory when that file is absent), read from the
     *         CLI's reported {@code destinationPath}; {@code null} on failure or when the output carries no path
     */
    public String forkSchema(String source, String name) {
        try {
            CliRunner.CliResult result = CliRunner.run(project, forkArgs(source, name).toArray(new String[0]));
            if (!result.isSuccess()) {
                LOG.warn("openspec schema fork failed: " + result.stderr());
                return null;
            }
            clearCache();
            return schemaFileIn(schemaDirFromJson(result.stdout(), "destinationPath"));
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to fork schema", e);
            return null;
        }
    }

    /**
     * Creates a new schema by calling {@code openspec schema init <name> --json --no-default}, with the
     * description and artifact selection the user chose.
     *
     * @return the new schema's {@code schema.yaml} (or its directory when that file is absent), read from the
     *         CLI's reported {@code path}; {@code null} on failure or when the output carries no path
     */
    public String initSchema(String name, String description, List<String> artifacts) {
        try {
            CliRunner.CliResult result = CliRunner.run(project,
                    initArgs(name, description, artifacts).toArray(new String[0]));
            if (!result.isSuccess()) {
                LOG.warn("openspec schema init failed: " + result.stderr());
                return null;
            }
            clearCache();
            return schemaFileIn(schemaDirFromJson(result.stdout(), "path"));
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to init schema", e);
            return null;
        }
    }

    /** CLI arguments for {@code schema fork}; {@code --json} so the destination is read structurally. */
    static List<String> forkArgs(String source, String name) {
        return List.of("schema", "fork", source, name, "--json");
    }

    /**
     * CLI arguments for {@code schema init}. {@code --no-default} is always passed: the plugin runs the CLI without
     * a terminal, so it can't answer the CLI's "set as default?" question. {@code --description} is passed only when
     * non-blank; {@code --artifacts} carries the selection as a comma list when there is one.
     */
    static List<String> initArgs(String name, String description, List<String> artifacts) {
        List<String> args = new ArrayList<>(List.of("schema", "init", name, "--json", "--no-default"));
        if (description != null && !description.isBlank()) {
            args.add("--description");
            args.add(description.trim());
        }
        if (artifacts != null && !artifacts.isEmpty()) {
            args.add("--artifacts");
            args.add(String.join(",", artifacts));
        }
        return args;
    }

    /**
     * The schema directory a {@code schema fork --json} / {@code schema init --json} response reports under
     * {@code key} ({@code destinationPath} / {@code path}), or {@code null} when the output is not a JSON object or
     * lacks a non-blank string at that key.
     */
    static String schemaDirFromJson(String stdout, String key) {
        try {
            JsonElement parsed = JsonParser.parseString(CliJson.extractJsonPayload(stdout == null ? "" : stdout));
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject obj = parsed.getAsJsonObject();
            if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
                return null;
            }
            String dir = obj.get(key).getAsString();
            return dir.isBlank() ? null : dir;
        } catch (RuntimeException e) {
            LOG.warn("Could not read '" + key + "' from schema command output", e);
            return null;
        }
    }

    /** {@code <dir>/schema.yaml} when that file exists, otherwise {@code dir} itself; {@code null} passes through. */
    static String schemaFileIn(String dir) {
        if (dir == null) {
            return null;
        }
        Path schemaFile = Path.of(dir, "schema.yaml");
        return Files.isRegularFile(schemaFile) ? schemaFile.toString() : dir;
    }

    /**
     * Validates a schema by calling {@code openspec schema validate <name> --json}.
     * All three commands used by the schema tooling surface ({@code schema validate},
     * {@code schema which}, {@code templates}) exist from the {@value #MIN_CLI_VERSION}
     * floor onward (verified empirically on CLI 1.3.1), so they sit behind the same
     * {@link #isSchemaSupported()} guard as fork/init with no extra version gate.
     *
     * @return the parsed report; {@link SchemaValidationReport#isCliFailure()} is true
     *         (carrying stderr) when the CLI call itself failed
     */
    public SchemaValidationReport validateSchema(String name) {
        try {
            CliRunner.CliResult result = CliRunner.run(project, "schema", "validate", name, "--json");
            SchemaValidationReport report = parseSchemaValidation(result.stdout());
            if (report != null) {
                return report;
            }
            // Non-zero exit alone is not a CLI failure — an invalid schema also exits
            // non-zero but still prints the JSON report. Only unparseable output is.
            LOG.warn("openspec schema validate produced no parseable JSON: " + result.stderr());
            return SchemaValidationReport.cliFailure(name, result.stderr());
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to validate schema " + name, e);
            return SchemaValidationReport.cliFailure(name, e.getMessage());
        }
    }

    /**
     * Resolution provenance for one schema name, from the batch resolved on the listing's
     * cache lifecycle (invalidated by fork/init/{@link #clearCache()}, not per-selection).
     *
     * @return the resolution, or null when provenance is unavailable for the name —
     *         callers omit the origin display rather than blocking the listing
     */
    public SchemaResolution getSchemaResolution(String name) {
        if (cachedResolutions == null) {
            cachedResolutions = resolveAllSchemaOrigins();
        }
        return cachedResolutions.get(name);
    }

    private Map<String, SchemaResolution> resolveAllSchemaOrigins() {
        Map<String, SchemaResolution> resolutions = new HashMap<>();
        if (!isSchemaSupported()) {
            return resolutions;
        }
        for (SchemaInfo info : listSchemas()) {
            String name = info.name();
            if (name == null || name.isEmpty()) continue;
            try {
                CliRunner.CliResult result = CliRunner.run(project, "schema", "which", name, "--json");
                SchemaResolution resolution = parseSchemaResolution(result.stdout());
                if (resolution != null) {
                    resolutions.put(name, resolution);
                }
            } catch (CliRunner.CliException e) {
                LOG.warn("Failed to resolve schema origin for " + name, e);
            }
        }
        return resolutions;
    }

    /**
     * Resolves a schema's artifact templates via {@code openspec templates --schema <name> --json}.
     *
     * @return artifact id → resolved template path in CLI order, or null when the CLI
     *         call fails; entries with no usable path are omitted
     */
    public Map<String, String> resolveTemplates(String name) {
        try {
            CliRunner.CliResult result = CliRunner.run(project, "templates", "--schema", name, "--json");
            if (!result.isSuccess()) {
                LOG.warn("openspec templates failed: " + result.stderr());
                return null;
            }
            return parseTemplatePaths(result.stdout());
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to resolve templates for schema " + name, e);
            return null;
        }
    }

    /**
     * Checks whether the detected CLI version supports schema commands.
     */
    /**
     * Seeds the schema list from captured {@code schemas --json} output, with no resolution provenance, so a headless
     * test can drive the settings panel without spawning the CLI (the panel loads on a pooled thread, where a
     * thread-local static mock of the CLI runner would not apply).
     */
    @TestOnly
    public void seedSchemaListForTest(String schemasJson) {
        cachedSchemas = parseSchemaList(schemasJson);
        cachedResolutions = Map.of();
    }

    public boolean isSchemaSupported() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null || !detection.isAvailable()) {
            return false;
        }
        return CliVersion.atLeast(detection.getDetectedVersion(), MIN_CLI_VERSION);
    }

    /**
     * Returns the union of (a) the built-in schema names from {@link VersionSupport#V1_2}
     * (the synchronous, always-available fallback) and (b) the CLI-runtime list from
     * {@link #listSchemas()} when the CLI is available and supports schema management.
     *
     * <p>This is the canonical "is this schema name recognized" check for validators.
     * Built-ins are always included so the plugin still validates sensibly when the CLI
     * is unavailable or below the {@value #MIN_CLI_VERSION} floor; when the CLI is
     * available, custom-forked schemas (e.g., the user ran {@code openspec schema fork
     * spec-driven my-team-flow}) ride along automatically.
     *
     * <p>Cache invalidation: this method calls {@link #listSchemas()}, which caches via
     * the existing {@code cachedSchemas} field. {@link #clearCache()} (already invoked
     * from {@link #forkSchema} and {@link #initSchema}) is the invalidation point. The
     * union is recomputed on each call from the (possibly cached) list — cheap enough
     * to not warrant its own cache.
     *
     * @return an unmodifiable {@link Set} of schema names recognized by either the
     *         built-in fallback or the live CLI; never null, always contains at least
     *         the built-in set
     */
    public Set<String> getKnownSchemaNames() {
        Set<String> names = new HashSet<>(VersionSupport.V1_2.getValidSchemas());
        if (isSchemaSupported()) {
            for (SchemaInfo info : listSchemas()) {
                String name = info.name();
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }

    /**
     * Clears the cached schema list (and the provenance batch resolved alongside it) so
     * the next call to {@link #listSchemas()} / {@link #getSchemaResolution} fetches fresh data.
     */
    public void clearCache() {
        cachedSchemas = null;
        cachedResolutions = null;
    }

    // --- Internal helpers ---

    /**
     * Parses {@code schema validate --json} output: {@code {name, path, valid, issues:
     * [{level, path, message}]}}. Returns null when the payload is not that shape.
     */
    static SchemaValidationReport parseSchemaValidation(String raw) {
        try {
            JsonObject obj = new Gson().fromJson(CliJson.extractJsonPayload(raw), JsonObject.class);
            if (obj == null || !obj.has("valid")) {
                return null;
            }
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            boolean valid = obj.get("valid").getAsBoolean();
            List<SchemaValidationReport.Issue> issues = new ArrayList<>();
            if (obj.has("issues") && obj.get("issues").isJsonArray()) {
                for (JsonElement element : obj.getAsJsonArray("issues")) {
                    JsonObject issue = element.getAsJsonObject();
                    issues.add(new SchemaValidationReport.Issue(
                            issue.has("level") ? issue.get("level").getAsString() : "error",
                            issue.has("path") ? issue.get("path").getAsString() : "",
                            issue.has("message") ? issue.get("message").getAsString() : ""));
                }
            }
            return new SchemaValidationReport(name, valid, issues, null);
        } catch (Exception e) {
            LOG.warn("Failed to parse schema validate JSON", e);
            return null;
        }
    }

    /**
     * Parses {@code schema which --json} output: {@code {name, source, path, shadows:
     * [{source, path}]}}. Returns null when the payload is not that shape.
     */
    static SchemaResolution parseSchemaResolution(String raw) {
        try {
            JsonObject obj = new Gson().fromJson(CliJson.extractJsonPayload(raw), JsonObject.class);
            if (obj == null || !obj.has("source")) {
                return null;
            }
            List<String> shadowedSources = new ArrayList<>();
            if (obj.has("shadows") && obj.get("shadows").isJsonArray()) {
                for (JsonElement element : obj.getAsJsonArray("shadows")) {
                    JsonObject shadow = element.getAsJsonObject();
                    if (shadow.has("source")) {
                        shadowedSources.add(shadow.get("source").getAsString());
                    }
                }
            }
            return new SchemaResolution(
                    obj.has("name") ? obj.get("name").getAsString() : "",
                    obj.get("source").getAsString(),
                    obj.has("path") ? obj.get("path").getAsString() : "",
                    shadowedSources);
        } catch (Exception e) {
            LOG.warn("Failed to parse schema which JSON", e);
            return null;
        }
    }

    /**
     * Parses {@code templates --json} output: {@code {<artifactId>: {path, source}, ...}}.
     * Entries without a usable {@code path} are omitted (the caller reports them as
     * unavailable rather than failing the whole map).
     */
    static Map<String, String> parseTemplatePaths(String raw) {
        Map<String, String> paths = new LinkedHashMap<>();
        try {
            JsonObject obj = new Gson().fromJson(CliJson.extractJsonPayload(raw), JsonObject.class);
            if (obj == null) {
                return paths;
            }
            for (String artifactId : obj.keySet()) {
                JsonElement element = obj.get(artifactId);
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                if (entry.has("path") && !entry.get("path").isJsonNull()) {
                    String path = entry.get("path").getAsString();
                    if (!path.isEmpty()) {
                        paths.put(artifactId, path);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse templates JSON", e);
        }
        return paths;
    }

    static List<SchemaInfo> parseSchemaList(String json) {
        List<SchemaInfo> schemas = new ArrayList<>();
        try {
            Gson gson = new Gson();
            JsonArray array = gson.fromJson(json, JsonArray.class);
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                String name = obj.has("name") ? obj.get("name").getAsString() : "";
                String description = obj.has("description") ? obj.get("description").getAsString() : "";
                // Real `openspec schemas --json` reports `source` ("package" = built-in shipped with the
                // CLI, "project" = a local fork) and `artifacts` — NOT the `isBuiltIn`/`artifactIds` keys
                // this parser previously assumed (which no CLI version emits, so isBuiltIn was always
                // false and artifactIds always empty). Read the real keys.
                boolean isBuiltIn = obj.has("source") && "package".equals(obj.get("source").getAsString());
                List<String> artifactIds = new ArrayList<>();
                if (obj.has("artifacts") && obj.get("artifacts").isJsonArray()) {
                    for (JsonElement aid : obj.getAsJsonArray("artifacts")) {
                        artifactIds.add(aid.getAsString());
                    }
                }
                schemas.add(new SchemaInfo(name, description, isBuiltIn, artifactIds));
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse schema list JSON", e);
        }
        return schemas;
    }

}
