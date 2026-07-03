# Workflow Schema Context

## Purpose
Schema/version-aware resolution of the active OpenSpec mode and version axes — derived from `openspec status` / `openspec instructions` — cached and consumed by workflow surfaces as the single source of truth for mode- and version-dependent behavior, instead of inferring layout from the on-disk directory structure.
## Requirements
### Requirement: Resolved workflow schema context

The plugin SHALL derive a resolved workflow schema context from `openspec status --json` (consulting `openspec instructions --json` where artifact-level detail is needed), capturing the active schema name, `actionContext.mode`, `actionContext.sourceOfTruth`, and `actionContext.allowedEditRoots`. This context SHALL be the single source of truth that workflow surfaces consult for mode-dependent behavior.

#### Scenario: Derive context from status
- **WHEN** a change is selected and the CLI is at or above the 1.3 floor
- **THEN** the plugin SHALL parse `actionContext` from `openspec status --json` and expose the active mode, source of truth, and allowed edit roots as the resolved schema context

#### Scenario: Non-default mode is represented faithfully
- **WHEN** `actionContext.mode` is a non-default mode such as `workspace-planning`
- **THEN** the resolved context SHALL report that mode rather than defaulting to `spec-driven`

### Requirement: Independent version-axis resolution

The plugin SHALL resolve and expose two independent version axes without conflating them: the **CLI version** (floor 1.3.0, baseline 1.4.x) and the **config-format version** (`openspec/config.yaml` `version:`). Version-dependent behavior SHALL select the axis that actually governs it. Any UI that overrides a version SHALL present only values the targeted axis actually models, so that a selectable value cannot be silently ignored.

#### Scenario: CLI version gates capability availability
- **WHEN** behavior depends on whether a command or schema exists in the installed client
- **THEN** the plugin SHALL evaluate the CLI version axis (e.g. 1.3 vs 1.4), not the config-format version

#### Scenario: Config-format version is preserved for effective-version resolution
- **WHEN** the plugin resolves the effective version for self-validation
- **THEN** it SHALL continue to read the config-format `version:` field and SHALL NOT substitute the CLI version for it

#### Scenario: Version-override UI reflects the config-format axis
- **WHEN** the settings expose a config-format version override
- **THEN** the override SHALL present only values the config-format axis actually models (currently `1.2.0`), and SHALL NOT offer CLI-version-looking values (e.g. `1.3.0`/`1.4.0`) that the config-format axis does not distinguish and that would be silently ignored

### Requirement: Context caching and invalidation

The plugin SHALL cache the resolved schema context and reuse it without re-invoking the CLI until invalidated. The cache SHALL be invalidated on change-selection change and on propose, apply, and archive operations.

#### Scenario: Cache hit
- **WHEN** the resolved context has been computed for the current change selection and no invalidating operation has occurred
- **THEN** the plugin SHALL return the cached context without calling the CLI

#### Scenario: Cache invalidation
- **WHEN** the change selection changes or a propose, apply, or archive operation occurs
- **THEN** the plugin SHALL discard the cached context so the next read recomputes it

### Requirement: Built-in fallback when CLI is unavailable

When the OpenSpec CLI is unavailable or below the 1.3 floor (so `actionContext` is not available), the resolved schema context SHALL fall back to the default `spec-driven`, repo-local assumption, preserving the plugin's current behavior.

#### Scenario: CLI absent or below floor
- **WHEN** the CLI cannot be invoked or reports a version below 1.3.0
- **THEN** the resolved context SHALL report `spec-driven` / repo-local so existing surfaces behave exactly as they do today

