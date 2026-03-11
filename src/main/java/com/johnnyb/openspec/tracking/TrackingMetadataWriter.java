package com.johnnyb.openspec.tracking;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads and updates the tracking block in .openspec.yaml
 * without disturbing other fields.
 */
public final class TrackingMetadataWriter {

    private static final Logger LOG = Logger.getInstance(TrackingMetadataWriter.class);

    private TrackingMetadataWriter() {}

    @SuppressWarnings("unchecked")
    public static void writeForgejoRef(Path changeDir, int issueNumber, String issueUrl) throws IOException {
        Path yamlPath = changeDir.resolve(".openspec.yaml");
        Map<String, Object> data = readYaml(yamlPath);

        Map<String, Object> tracking = (Map<String, Object>) data.computeIfAbsent("tracking", k -> new LinkedHashMap<>());
        Map<String, Object> forgejo = new LinkedHashMap<>();
        forgejo.put("issueNumber", issueNumber);
        forgejo.put("issueUrl", issueUrl);
        tracking.put("forgejo", forgejo);

        writeYaml(yamlPath, data);
    }

    @SuppressWarnings("unchecked")
    public static void writePlaneRef(Path changeDir, String workItemId, String workItemUrl) throws IOException {
        Path yamlPath = changeDir.resolve(".openspec.yaml");
        Map<String, Object> data = readYaml(yamlPath);

        Map<String, Object> tracking = (Map<String, Object>) data.computeIfAbsent("tracking", k -> new LinkedHashMap<>());
        Map<String, Object> plane = new LinkedHashMap<>();
        plane.put("workItemId", workItemId);
        plane.put("workItemUrl", workItemUrl);
        tracking.put("plane", plane);

        writeYaml(yamlPath, data);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYaml(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        String content = Files.readString(path);
        Yaml yaml = new Yaml(new LoaderOptions());
        Object loaded = yaml.load(content);
        if (loaded instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) loaded);
        }
        return new LinkedHashMap<>();
    }

    private static void writeYaml(Path path, Map<String, Object> data) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        Files.writeString(path, yaml.dump(data));
    }
}
