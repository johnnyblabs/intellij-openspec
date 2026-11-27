package com.johnnyblabs.openspec.services;

import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A change's {@code retire_capabilities} marker, read as upstream's {@code readBooleanMarker} reads it
 * ({@code dist/utils/change-metadata.js}, the same from 1.8.0 through 2.2): a marker counts only when it is {@code true}
 * in metadata that passes {@code ChangeMetadataSchema} and names a schema the CLI loads. A marker present but not
 * honorable counts as not declared, with the CLI's reason where captured runs pin that reason's wording; for any other
 * failure the reason is left out, never guessed.
 */
public final class RetirementMarkers {

    static final String KEY = "retire_capabilities";
    private static final Pattern MENTIONED = Pattern.compile("^\\s*(['\"]?)" + KEY + "\\1\\s*:", Pattern.MULTILINE);
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern KEBAB = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    /** The (expected, received) pairs whose zod wording a captured run pins. */
    private static final Set<String> CAPTURED_TYPE_MESSAGES =
            Set.of("string/undefined", "boolean/string", "boolean/number", "boolean/null");

    /** What the CLI makes of the schema a marker's metadata names. */
    public enum SchemaVerdict { LOADS, UNKNOWN, CANNOT_LOAD }

    /** Resolves a schema name as the CLI does: project, then user, then built-in; the first copy found decides. */
    @FunctionalInterface
    public interface SchemaCheck {
        SchemaVerdict check(String name);
    }

    private RetirementMarkers() {
    }

    /** Reads {@code <changeDir>/.openspec.yaml}. */
    public static SpecMerge.RetirementMarker read(Path changeDir, SchemaCheck schemas) {
        String raw;
        try {
            raw = Files.readString(changeDir.resolve(".openspec.yaml"), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return SpecMerge.RetirementMarker.NONE;
        } catch (IOException e) {
            return unhonorable("the metadata file cannot be read (" + e.getMessage() + ")");
        }
        return parse(raw, schemas);
    }

    static SpecMerge.RetirementMarker parse(String raw, SchemaCheck schemas) {
        Object parsed;
        try {
            parsed = yaml().load(raw);
        } catch (RuntimeException e) {
            return MENTIONED.matcher(raw).find() ? unhonorable("the file is not valid YAML") : SpecMerge.RetirementMarker.NONE;
        }
        if (!(parsed instanceof Map<?, ?> map) || !map.containsKey(KEY)) return SpecMerge.RetirementMarker.NONE;
        Object marker = map.get(KEY);
        if (Boolean.FALSE.equals(marker)) return SpecMerge.RetirementMarker.NONE;
        // The schema's first failing field, in ChangeMetadataSchema's key order, decides; the marker's own value only
        // counts once every other field is valid, exactly as zod's first issue does.
        Failure first = firstFailure(map);
        if (first != null) {
            return first.reason() == null ? new SpecMerge.RetirementMarker(false, null) : unhonorable(first.reason());
        }
        String schema = (String) map.get("schema");
        return switch (schemas.check(schema)) {
            case LOADS -> new SpecMerge.RetirementMarker(true, null);
            case UNKNOWN -> unhonorable("schema: unknown schema '" + schema + "'");
            // An override the CLI rejects, or one the plugin can't check without a CLI: never honored, reason left out.
            case CANNOT_LOAD -> new SpecMerge.RetirementMarker(false, null);
        };
    }

    /** A failing field and the CLI's reason for it, or {@code null} reason when no capture pins its wording. */
    private record Failure(@Nullable String reason) {
    }

    /** ChangeMetadataSchema (identical 1.8.0 through 2.2.0), field by field in its declaration order. */
    private static @Nullable Failure firstFailure(Map<?, ?> map) {
        Object schema = map.get("schema");
        if (!(schema instanceof String s)) return typeFailure("schema", "string", schema, map.containsKey("schema"));
        if (s.isEmpty()) return new Failure(null);
        if (map.containsKey("created")) {
            Object created = map.get("created");
            if (!(created instanceof String c)) return typeFailure("created", "string", created, true);
            if (!DATE.matcher(c).find()) return new Failure("created: created must be YYYY-MM-DD format");
        }
        if (map.containsKey("goal")) {
            if (!(map.get("goal") instanceof String g) || g.isEmpty()) return new Failure(null);
        }
        if (map.containsKey("affected_areas")) {
            if (!(map.get("affected_areas") instanceof List<?> areas)) return new Failure(null);
            for (Object a : areas) if (!(a instanceof String as) || as.isEmpty()) return new Failure(null);
        }
        if (map.containsKey("initiative")) {
            if (!(map.get("initiative") instanceof Map<?, ?> init) || !Set.of("store", "id").containsAll(init.keySet())
                    || !(init.get("store") instanceof String st) || !KEBAB.matcher(st).find()
                    || !(init.get("id") instanceof String id) || !KEBAB.matcher(id).find()) {
                return new Failure(null);
            }
        }
        if (map.containsKey("skip_specs") && !(map.get("skip_specs") instanceof Boolean)) return new Failure(null);
        Object marker = map.get(KEY);
        if (!(marker instanceof Boolean)) return typeFailure(KEY, "boolean", marker, true);
        return null;
    }

    private static Failure typeFailure(String field, String expected, @Nullable Object value, boolean present) {
        String received = !present ? "undefined" : received(value);
        if (received == null || !CAPTURED_TYPE_MESSAGES.contains(expected + "/" + received)) return new Failure(null);
        return new Failure(field + ": Invalid input: expected " + expected + ", received " + received);
    }

    /** zod's received-type word for the values YAML 1.2's core schema yields; {@code null} for any other. */
    private static @Nullable String received(@Nullable Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "string";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Double d) return d.isNaN() || d.isInfinite() ? null : "number";
        if (value instanceof Number) return "number";
        return null;
    }

    /** Upstream's {@code unhonorable}: not declared, with the reason's control characters made safe to print. */
    private static SpecMerge.RetirementMarker unhonorable(String reason) {
        return new SpecMerge.RetirementMarker(false, reason.replaceAll("[\\u0000-\\u001f\\u007f]", "?"));
    }

    /** SnakeYAML with YAML 1.2's core schema, as the CLI's parser reads: no {@code yes}/{@code on}, no timestamps. */
    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options), new Representer(new DumperOptions()), new DumperOptions(), options,
                new CoreSchemaResolver());
    }

    /** YAML 1.2 core schema implicit tags (booleans, nulls, ints, floats) and nothing else. */
    private static final class CoreSchemaResolver extends Resolver {
        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$"), "tTfF");
            // YAML 1.2's 0o octal is left out: SnakeYAML can't construct it, and failing the whole file would misreport it.
            addImplicitResolver(Tag.INT, Pattern.compile("^(?:[-+]?[0-9]+|0x[0-9a-fA-F]+)$"), "-+0123456789");
            addImplicitResolver(Tag.FLOAT, Pattern.compile(
                    "^(?:[-+]?(?:\\.[0-9]+|[0-9]+(?:\\.[0-9]*)?)(?:[eE][-+]?[0-9]+)?|[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN))$"),
                    "-+0123456789.");
            addImplicitResolver(Tag.NULL, Pattern.compile("^(?:~|null|Null|NULL)$"), "~nN");
            addImplicitResolver(Tag.NULL, Pattern.compile("^$"), null);
        }
    }
}
