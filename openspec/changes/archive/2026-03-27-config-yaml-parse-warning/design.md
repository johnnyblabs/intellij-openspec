## Context

`ConfigService.reload()` uses `new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()))` to parse `config.yaml` into a Java bean. SnakeYAML's `Constructor` requires an exact type match for every field — if the YAML has a list where the bean expects a `String`, or an unknown field, parsing throws a `MarkedYAMLException` which triggers an IDE notification popup on every config reload.

## Goals / Non-Goals

**Goals:**
- Eliminate the parse error popup for valid-but-evolving config.yaml files
- Make config parsing resilient to unknown fields and type mismatches
- Preserve all existing downstream behavior (getSchema, getVersion, getProfile, etc.)

**Non-Goals:**
- Migrating away from SnakeYAML entirely
- Validating config structure (that's `BuiltInValidator`'s job)
- Supporting new config fields

## Decisions

### Parse as generic Map, extract known fields

Replace `Constructor(OpenSpecConfig.class, ...)` with default `new Yaml()` which returns `Map<String, Object>`. Extract known fields (`schema`, `version`, `profile`, `context`, `rules`) with safe casts. Unknown fields and type mismatches are silently ignored.

**Why not keep Constructor with try/catch?** The `Constructor` fails on the first type mismatch and discards the entire config. Generic parsing lets us extract the fields that *do* match while ignoring the rest.

### Downgrade notification to debug log

The current `OpenSpecNotifier.notify(..., WARNING)` popup is disruptive for what is effectively a schema-evolution issue. Move to `LOG.debug()` since `BuiltInValidator` already reports config issues through the proper validation channel.

### Return empty config on total failure, not null

If YAML parsing fails entirely (malformed YAML), return `new OpenSpecConfig()` with defaults instead of `null`. This eliminates null-checks scattered across callers.

## Risks / Trade-offs

- **Silent field drops**: If a user misspells a field name, the parser won't warn them. Mitigation: `BuiltInValidator` already covers required-field validation.
- **Type coercion**: `profile` could theoretically be a string instead of a map in some config. Mitigation: safe-cast with fallback to empty map.
