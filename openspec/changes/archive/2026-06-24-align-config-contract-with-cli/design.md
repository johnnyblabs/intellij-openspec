## Context

The plugin's `BuiltInValidator.validateConfig` was written against an assumed contract: "every OpenSpec project has a config.yaml; it declares schema + version + profile; if any of those are missing, surface it loudly." That assumption was reasonable when the plugin shipped — early OpenSpec CLI versions did emit a config.yaml with those fields. But upstream evolved: the current CLI treats config.yaml as **augmentation**, not a precondition. Empirical confirmation (run inside this repo):

```
$ mv openspec/config.yaml /tmp/  # temporarily hide it
$ openspec validate --all
✓ spec/validation
✓ spec/spec-sync
…
Totals: 32 passed, 0 failed (32 items)
```

Upstream reads `openspec/config.yaml` from exactly three call sites (`utils/change-utils.js`, `utils/change-metadata.js`, `core/artifact-graph/instruction-loader.js`) and every one of them is null-safe. The CLI's own `project-config.js` literally comments `// No config is OK` and returns `null`.

The plugin, meanwhile, fires four issues against a config-less project today:

| Rule code | Severity | Trigger |
|---|---|---|
| `config-missing` | ERROR | config.yaml absent |
| `config-version-required` | WARNING | `version:` field absent |
| `config-field-required` | ERROR | `VersionSupport.V1_2.requiredConfigFields` lists `version` and the field is absent |
| `config-profile-recommended` | WARNING | `profile:` field empty |

None of these have an upstream counterpart. Same pattern as the just-archived `add-renamed-delta-section-support`: the plugin was stricter than upstream on something upstream considers optional, and that strictness produced false-positive surface noise. The fix is to relax the validator, not to demand users add fields that upstream doesn't read.

## Plugin vs upstream — full config-field contract comparison

| Field | Upstream Zod schema | Upstream behavior when absent | Plugin reads it? | Plugin behavior when absent (current) | Plugin behavior when absent (this change) |
|---|---|---|---|---|---|
| `(file: openspec/config.yaml)` | n/a — file is read with `existsSync` check, returns `null` if absent | Callers fall back to defaults: `schema → 'spec-driven'`, no context blob, no rules | yes — every config field is read here | ERROR `config-missing`, short-circuits the rest of validation | Skipped silently; no issue raised (matches upstream "No config is OK") |
| `schema:` | `z.string().min(1)` — required when config.yaml exists | If file absent, callers fall back to `'spec-driven'` | yes — `OpenSpecConfig.fromMap` reads it; `ScaffoldingService` consults it for templates | ERROR `config-schema-required` if missing or empty (after passing the config-missing gate) | **Unchanged** — keep ERROR `config-schema-required` when config.yaml exists but `schema:` is missing. Upstream's Zod requires it; the plugin should too, but only conditionally on the file being present. |
| `context:` | `z.string().optional()`, max 50KB | No project-context blob injected into instruction templates | yes — `ExploreContextService` interpolates into AI prompts | No issue (already optional in the validator) | **Unchanged** — already aligned. |
| `rules:` | `z.record(z.string(), z.array(z.string())).optional()` — keyed by artifact ID | No additional per-artifact rules appended to skill instructions | yes — `SpecTreeModel` displays them; not enforced by the plugin's own validator | No issue (already optional) | **Unchanged** — already aligned. |
| `version:` | **not in the schema** — stripped by `z.core.$strip` | n/a — upstream never sees it | yes — `OpenSpecConfig.fromMap`, `OpenSpecSettings.getEffectiveVersion`, `VersionSupport.fromString` | WARNING `config-version-required` if absent; ERROR `config-field-required` because `V1_2.requiredConfigFields` lists it; WARNING `config-version-unknown` if value not in `VersionSupport.allVersions()` | **All three dropped.** Plugin-internal field with no upstream contract; the runtime-resolution path already defaults to `V1_2` when null. |
| `profile:` | **not in the schema** — stripped by `z.core.$strip` | n/a — upstream never sees it | yes — `SpecTreeModel` displays it; `ExploreContextService` reads description for AI prompts | WARNING `config-profile-recommended` if empty | **Dropped.** Plugin-side display field; no functional dependency. |

After this change, the validator fires exactly **two** rules tied to `openspec/config.yaml`:

| Rule code | Severity | Trigger (post-change) | Upstream-aligned? |
|---|---|---|---|
| `config-schema-required` | ERROR | config.yaml EXISTS but `schema:` is missing/empty | ✅ matches Zod's `schema: z.string().min(1)` |
| `config-schema-invalid` | WARNING | `schema:` is set but not in `SchemaService.getKnownSchemaNames()` (CLI-runtime-driven, includes user forks) | ✅ matches upstream's fuzzy-match suggestion in `suggestSchemas` |

Everything else upstream considers optional, the plugin now considers optional too.

## Plugin vs upstream — feature-level comparison (broader context)

For reference and to document the durable architectural rule the project keeps re-discovering:

| Concern | Upstream CLI | Plugin (today, post this change) | Notes |
|---|---|---|---|
| **Delta-spec sections** | 4 types: ADDED, MODIFIED, REMOVED, RENAMED | 4 types (RENAMED added in the last change) | Fixed in `add-renamed-delta-section-support`. |
| **Schema names** | Built-in + user forks via `openspec schema fork`, fuzzy-matched on miss | CLI-runtime-driven via `SchemaService.getKnownSchemaNames()`; built-in fallback when CLI < 1.3 | Fixed in `schema-validation-cli-runtime-driven`. |
| **CLI floor** | npm-managed; current 1.4.1 | 1.3.0 floor + 1.4.x recommended | Fixed in `bump-cli-floor-to-1-3`. |
| **Apply order for delta operations** | RENAMED → REMOVED → MODIFIED → ADDED (per `specs-apply.js`) | Same order (realigned in the last change) | Fixed in `add-renamed-delta-section-support`. |
| **`openspec/config.yaml` required?** | No — "No config is OK" | **No (this change)** — was ERROR `config-missing` before | This change. |
| **`version:` field required?** | No — Zod strips it | **No (this change)** — was ERROR `config-field-required` before | This change. |
| **`profile:` field recommended?** | n/a — upstream doesn't read it | **No (this change)** — was WARNING before | This change. |

The architectural rule, written out plainly: **anything upstream OpenSpec accepts, the plugin should accept; anything upstream supports as optional, the plugin should support as optional.** The plugin can add CONVENIENCE (better UI, faster scaffolding, IDE inspections) but should never add STRICTNESS on contracts upstream itself defines.

## Goals / Non-Goals

**Goals:**
- Stop the plugin from emitting false-positive issues on OpenSpec projects that upstream considers valid.
- Preserve the one validation rule that genuinely mirrors upstream's Zod schema (`schema:` required when the file exists).
- Codify the "plugin should not be more restrictive than upstream" principle in the `validation` capability spec for future contributors.

**Non-Goals:**
- Refactor `OpenSpecSettings.getEffectiveVersion` to stop consulting the config-file `version:` field. The Settings-side resolution is still functionally correct (returns `null` → `VersionSupport.fromString(null)` → V1_2); just stops being a *required* read. Deeper refactor (derive effective version from CLI runtime detection) is its own change.
- Decide the `VersionSupport.V1_X` config-format-vs-CLI-era axis ambiguity. That belongs with the next CLI floor bump.
- Touch the upstream-managed `openspec-*` skill files. Per CLAUDE.md, those get regenerated by `openspec update`; nothing here should land in those.

## Decisions

### D1. Skip — don't error — when config.yaml is absent

`BuiltInValidator.validateConfig` early-returns at L248 with ERROR `config-missing`. Replace with: if `config == null`, return `new ValidationResult(true, List.of(), "built-in")` — no issues, passed=true. This mirrors `readProjectConfig()`'s "No config is OK" return-null behavior at the validator surface.

**Alternative considered**: emit `config-missing` as INFO (informational, not WARNING) so users still see a hint that `openspec init` would create a default config. Rejected — INFO is not currently a severity in `ValidationIssue.Severity`, and adding it just for one rule is over-engineering. If the hint matters, surface it from a separate onboarding/welcome flow, not the validator.

### D2. Shrink `VersionSupport.V1_2.requiredConfigFields` from `{schema, version}` to `{schema}`

The `version:` field is plugin-internal-only — upstream Zod strips it. Removing it from the required-fields set drops the `config-field-required` ERROR. The plugin's `VersionSupport.fromString(null)` already defaults to V1_2; the only thing that previously elevated null-version to an ERROR was this single-element list.

**Alternative considered**: keep `version` in the required set but downgrade the rule's severity. Rejected — that just trades one false-positive for a softer false-positive. The root issue is that `version:` isn't a contract upstream defines, so requiring it (at any severity) is a categorical error.

### D3. Drop `config-version-required` and `config-version-unknown` WARNINGs

`config-version-required` fires when `version:` is absent. After D2 it's the only thing left signaling the absence. Drop it for the same reason as D2.

`config-version-unknown` fires when `version:` is set but not in `VersionSupport.allVersions()` (today just `["1.2.0"]`). This is a useful local check IF the user sets the field — but with D2/D3 the field is genuinely optional. Keep the WARNING only if a value is set; if absent, no issue. This is the existing else-if behavior at L274 — it's untouched by this change; the change just removes the if-branch (L271-273) so the absent-version path becomes a no-op instead of a warning.

### D4. Drop `config-profile-recommended` WARNING

Upstream doesn't read `profile:` at all. The plugin uses it for tree-view display and AI-prompt context, both null-safe. The "should have a profile field" warning has no upstream counterpart and surfaces noise on any project that didn't customize. Drop entirely.

### D5. Keep `config-schema-required` and `config-schema-invalid`

These match upstream's contract. `schema:` is `z.string().min(1)` — Zod will reject an empty schema when the file exists. `config-schema-invalid` mirrors upstream's `suggestSchemas` flow (fuzzy match + suggestion). Both stay as-is.

## Risks / Trade-offs

- **Risk**: a user who has an *intentionally incomplete* config.yaml (e.g. removed `version:` while testing migration) loses the hint that the field is missing. → **Mitigation**: the field was never an upstream-defined contract; the hint was misleading anyway. If we want to keep a "your config is missing fields that drive plugin features" hint, surface it from the Settings panel or a Welcome screen, not the validator.
- **Risk**: future contributors look at the simplified validator and assume `version:` was never a thing. → **Mitigation**: the design doc (this file) and the proposal explicitly capture the divergence and the decision to converge. The `OpenSpecConfig.fromMap` still reads `version:`, so it's discoverable via grep.
- **Trade-off**: this is the third change in a row tackling "plugin too strict relative to upstream." Each individual fix is small. The pattern argues for a one-time documentation push (a CLAUDE.md section, perhaps) codifying the rule for all future config/feature work. This change includes a one-paragraph addition to the `validation` capability spec's preamble to that effect.

## Migration Plan

1. Land this change in the plugin.
2. Plugin update reaches users via the standard release pipeline.
3. Users with complete config.yaml files see no change.
4. Users with sparse config.yaml files (or no config.yaml at all) stop seeing config-related validation noise. No data migration required — no fields are added or removed in any stored format.
5. Rollback: revert the change commit; CI publishes the prior plugin version on next tag. No state changes to undo.

## Open Questions

- Should this change also remove the unused-anyway "required config fields per VersionSupport" indirection entirely (i.e. drop `requiredConfigFields` from `VersionSupport`)? The set will be `{schema}` after this change, and the only consumer is the validator loop at L282-293. The loop could be replaced with a single inline `schema`-presence check. Argument for: cleaner. Argument against: keeping the indirection leaves a hook for future per-version required fields if the V1_X axis decision adds them. **Defaulting to keeping the indirection** — the cost is small; the optionality has value if a future config-format era genuinely does mandate new fields.
