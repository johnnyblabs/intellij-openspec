## 1. Lenient Config Parsing

- [x] 1.1 Replace `Constructor(OpenSpecConfig.class, ...)` with default `new Yaml()` returning `Map<String, Object>` in `ConfigService.reload()`
- [x] 1.2 Add static factory `OpenSpecConfig.fromMap(Map<String, Object>)` that extracts known fields with safe casts and defaults
- [x] 1.3 Return `new OpenSpecConfig()` on total parse failure instead of setting `config = null`
- [x] 1.4 Downgrade parse error notification to `LOG.debug()` — remove `OpenSpecNotifier.notify()` call for parse errors

## 2. Testing

- [x] 2.1 Test: config with unknown fields parses without error and extracts known fields
- [x] 2.2 Test: config with type mismatch (e.g., array where string expected) skips mismatched field and uses default
- [x] 2.3 Test: malformed YAML returns default empty config
- [x] 2.4 Test: valid config still parses all fields correctly (regression)
