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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class SchemaService {

    private static final Logger LOG = Logger.getInstance(SchemaService.class);
    private static final String MIN_CLI_VERSION = "1.2.0";

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
        String version = detection.getDetectedVersion();
        if (version == null || version.isEmpty()) {
            return false;
        }
        return compareVersions(version, MIN_CLI_VERSION) >= 0;
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

    /**
     * Compares two semantic version strings (e.g., "1.2.0" vs "1.1.0").
     *
     * @return negative if a < b, 0 if equal, positive if a > b
     */
    static int compareVersions(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int maxLen = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < maxLen; i++) {
            int aNum = i < aParts.length ? parseVersionPart(aParts[i]) : 0;
            int bNum = i < bParts.length ? parseVersionPart(bParts[i]) : 0;
            if (aNum != bNum) {
                return Integer.compare(aNum, bNum);
            }
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            // Strip non-numeric suffixes like "0-beta"
            String numeric = part.replaceAll("[^0-9].*", "");
            return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
