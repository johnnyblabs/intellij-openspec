package com.johnnyblabs.openspec.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.CliVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class SchemaService {

    private static final Logger LOG = Logger.getInstance(SchemaService.class);
    private static final String MIN_CLI_VERSION = "1.3.0";

    private final Project project;
    private List<SchemaInfo> cachedSchemas;

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
     * Forks an existing schema by calling {@code openspec schema fork <source> <name>}.
     *
     * @return the path to the forked schema file, or null on failure
     */
    public String forkSchema(String source, String name) {
        try {
            CliRunner.CliResult result = CliRunner.run(project, "schema", "fork", source, name);
            if (!result.isSuccess()) {
                LOG.warn("openspec schema fork failed: " + result.stderr());
                return null;
            }
            clearCache();
            return result.stdout().trim();
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to fork schema", e);
            return null;
        }
    }

    /**
     * Creates a new schema by calling {@code openspec schema init <name>}.
     *
     * @return the path to the new schema file, or null on failure
     */
    public String initSchema(String name) {
        try {
            CliRunner.CliResult result = CliRunner.run(project, "schema", "init", name);
            if (!result.isSuccess()) {
                LOG.warn("openspec schema init failed: " + result.stderr());
                return null;
            }
            clearCache();
            return result.stdout().trim();
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to init schema", e);
            return null;
        }
    }

    /**
     * Checks whether the detected CLI version supports schema commands.
     */
    public boolean isSchemaSupported() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null || !detection.isAvailable()) {
            return false;
        }
        return CliVersion.atLeast(detection.getDetectedVersion(), MIN_CLI_VERSION);
    }

    /**
     * Clears the cached schema list so the next call to {@link #listSchemas()} fetches fresh data.
     */
    public void clearCache() {
        cachedSchemas = null;
    }

    // --- Internal helpers ---

    static List<SchemaInfo> parseSchemaList(String json) {
        List<SchemaInfo> schemas = new ArrayList<>();
        try {
            Gson gson = new Gson();
            JsonArray array = gson.fromJson(json, JsonArray.class);
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                String name = obj.has("name") ? obj.get("name").getAsString() : "";
                String description = obj.has("description") ? obj.get("description").getAsString() : "";
                boolean isBuiltIn = obj.has("isBuiltIn") && obj.get("isBuiltIn").getAsBoolean();
                List<String> artifactIds = new ArrayList<>();
                if (obj.has("artifactIds") && obj.get("artifactIds").isJsonArray()) {
                    for (JsonElement aid : obj.getAsJsonArray("artifactIds")) {
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
