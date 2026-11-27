package com.johnnyblabs.openspec.services;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A change's {@code retire_capabilities} marker, read as upstream's {@code readBooleanMarker} reads it
 * ({@code dist/utils/change-metadata.js}): a marker counts only when it is {@code true} in valid metadata whose schema
 * the CLI knows; one that is present but can't be honored counts as not declared, with the reason the CLI gives.
 */
public final class RetirementMarkers {

    static final String KEY = "retire_capabilities";
    /** Upstream's anchored mention check, so a comment naming the key doesn't count. */
    private static final Pattern MENTIONED = Pattern.compile("^\\s*(['\"]?)" + KEY + "\\1\\s*:", Pattern.MULTILINE);

    private RetirementMarkers() {
    }

    /** Reads {@code <changeDir>/.openspec.yaml}; {@code knownSchema} says whether the CLI knows a schema name. */
    public static SpecMerge.RetirementMarker read(Path changeDir, Predicate<String> knownSchema) {
        String raw;
        try {
            raw = Files.readString(changeDir.resolve(".openspec.yaml"), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return SpecMerge.RetirementMarker.NONE;
        } catch (IOException e) {
            return unhonorable("the metadata file cannot be read (" + e.getMessage() + ")");
        }
        return parse(raw, knownSchema);
    }

    static SpecMerge.RetirementMarker parse(String raw, Predicate<String> knownSchema) {
        Object parsed;
        try {
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(raw);
        } catch (RuntimeException e) {
            return MENTIONED.matcher(raw).find() ? unhonorable("the file is not valid YAML") : SpecMerge.RetirementMarker.NONE;
        }
        if (!(parsed instanceof Map<?, ?> map) || !map.containsKey(KEY)) return SpecMerge.RetirementMarker.NONE;
        Object value = map.get(KEY);
        if (Boolean.FALSE.equals(value)) return SpecMerge.RetirementMarker.NONE;
        if (!(value instanceof Boolean)) {
            if (value instanceof String) return unhonorable(KEY + ": Invalid input: expected boolean, received string");
            return SpecMerge.RetirementMarker.NONE;
        }
        Object schema = map.get("schema");
        if (!(schema instanceof String name) || !knownSchema.test(name)) {
            return unhonorable("schema: unknown schema '" + schema + "'");
        }
        return new SpecMerge.RetirementMarker(true, null);
    }

    /** Upstream's {@code unhonorable}: not declared, with the reason's control characters made safe to print. */
    private static SpecMerge.RetirementMarker unhonorable(String reason) {
        return new SpecMerge.RetirementMarker(false, reason.replaceAll("[\\u0000-\\u001f\\u007f]", "?"));
    }
}
