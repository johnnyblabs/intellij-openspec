## Why

The plugin's built-in validator codifies a stricter contract for `openspec/config.yaml` than the upstream OpenSpec CLI does. Specifically:

- Upstream treats `openspec/config.yaml` as **optional** — its own source has the comment `// No config is OK` and every consumer falls through to defaults when the file is absent.
- The plugin treats it as **required** — `BuiltInValidator.validateConfig` emits an ERROR `config-missing` and short-circuits the rest of the config check.
- Upstream's Zod schema accepts only `{schema, context, rules}` and silently strips unknown keys. It has no concept of a `version:` field.
- The plugin reads `version:` as a plugin-internal field and emits `config-version-required` (WARNING) + `config-field-required` (ERROR) when it's missing.

This is the same architectural anti-pattern the prior `add-renamed-delta-section-support` and `schema-validation-cli-runtime-driven` changes resolved on other surfaces: **the plugin should not be more restrictive than upstream OpenSpec on contracts upstream defines.** A user can initialize an OpenSpec project with the CLI and have it work end-to-end — but opening that same project in the plugin produces ERRORs and WARNINGs that signal "this project is broken" when it isn't. The plugin's own validator should treat upstream-optional things as optional.

## What Changes

In `BuiltInValidator.validateConfig`, relax the validator to match upstream's "augmentation, not requirement" model:

- **Drop the `config-missing` ERROR** when `openspec/config.yaml` is absent. Return a clean `ValidationResult` instead, mirroring upstream's `readProjectConfig() → null // No config is OK` semantics.
- **Drop `config-version-required`** (WARNING when `version:` is absent). Upstream doesn't know about `version:`; the plugin's `VersionSupport.fromString(null)` already falls through to `V1_2` (the default) without behavioral break.
- **Drop `config-field-required`** for the `version` field (was ERROR when `VersionSupport.V1_2.requiredConfigFields` listed `version`). Remove `version` from that set so only `schema` remains required.
- **Drop `config-profile-recommended`** (WARNING when `profile:` is empty). Upstream doesn't read `profile:`; it's a plugin-side display field with no upstream contract behind it.
- **Keep `config-schema-required`** (ERROR when `schema:` is missing AND config.yaml exists). Upstream's Zod schema requires `schema` when config.yaml is present, so the plugin should too — but only when the file is present.
- **Keep `config-schema-invalid`** (WARNING when schema name isn't recognized). Already CLI-runtime-driven via `SchemaService.getKnownSchemaNames`.

Result: the only ERROR the validator can fire about `openspec/config.yaml` is "schema field missing when the file exists." Everything else upstream considers optional, the plugin now considers optional.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `validation`: the "Config validation" requirement loosens to match upstream's optional-config semantics. The "config.yaml must exist" rule becomes "config.yaml may exist; when it does, it must declare a recognized schema." The `version:` field becomes plugin-internal-not-required.

## Impact

- **User-visible**: a user opening an OpenSpec project initialized with only `openspec init` (no config.yaml customization) no longer sees `config-missing` / `config-version-required` / `config-field-required` / `config-profile-recommended` issues on first load. The plugin matches upstream's "this is a valid OpenSpec project" verdict.
- **Code**: ~30-line change in `BuiltInValidator.validateConfig`, one-line change in `VersionSupport.V1_2` (shrink `requiredConfigFields` from `{schema, version}` to `{schema}`).
- **Specs**: delta under `validation` (MODIFIED on "Config validation" requirement; new scenarios for the absent-config and absent-version paths).
- **Tests**: existing `BuiltInValidatorRulesTest` covers structural delta-spec rules; new tests needed for the relaxed config-validation path. Add `configMissing_isClean()`, `configWithoutVersion_isClean()`, `configWithoutProfile_isClean()`, `configWithoutSchema_isError()` (the one rule that stays).
- **Migration**: zero. Projects that currently HAVE a complete config.yaml continue to validate clean. Projects missing fields just stop seeing the warnings/errors. No data shape changes, no settings migration.
- **Compatibility**: pure relaxation — no rule that previously passed now fails. Reverse is true for `config-missing` and friends: rules that previously failed now pass.
- **Trackers**: this change is a candidate for tracker mirroring on archive; no existing tracker entries.
- **Deferred / out of scope**:
  - **`OpenSpecSettings.getEffectiveVersion` refactor**: the plugin's runtime-version-resolution path still consults `config.yaml`'s `version:` field as a fallback. This change makes the *validator* tolerant of `version:` being absent, but the settings-resolution path still has the indirection. A deeper refactor would derive effective version from CLI runtime detection (already available via `SchemaService`) instead of the config field. Tracked as future work.
  - **`VersionSupport.V1_X` axis ambiguity**: the prior `bump-cli-floor-to-1-3` archive flagged "is `V1_X` a config-format axis or a CLI-era axis?" as unresolved. This change doesn't decide that question; it just stops requiring the `version:` field at the validator surface. The axis decision belongs with the next CLI-floor bump.
  - **`profile:` field handling**: this change drops the "profile recommended" WARNING but doesn't change how `SpecTreeModel` or `ExploreContextService` render/consume the profile data. Those code paths are already null-safe.
