## Why

`ConfigService` uses SnakeYAML's strict `Constructor(OpenSpecConfig.class, ...)` to parse `config.yaml`. When any field's YAML structure doesn't exactly match the Java bean types — for example, if `rules` values are arrays instead of strings, or if unknown fields appear — SnakeYAML throws and the plugin shows an intrusive IDE popup: "config.yaml parse error: No suitable constructor with 2 argument found for class java.lang.String". This blocks normal usage and fires on every config reload.

## What Changes

- Replace strict `Constructor`-based YAML parsing with lenient `Map`-based parsing that manually extracts known fields
- Suppress the popup notification for parse errors — log at debug level instead
- Return a sensible default `OpenSpecConfig` on parse failure so downstream code doesn't null-check everywhere

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `validation`: Config parsing becomes lenient — unrecognized fields or type mismatches are silently ignored instead of throwing

## Impact

- `ConfigService.java` — YAML parsing rewritten from type-safe `Constructor` to generic `Map` extraction
- `OpenSpecConfig.java` — may need a static factory method from `Map`
- No dependency changes, no API changes
