## Context

The override lives in three places:
- `OpenSpecSettings.State.versionOverride` (persisted).
- `getEffectiveVersion`: the override wins if non-empty, otherwise `config.yaml` `version:`, otherwise the 1.2.0 default.
- The editable `versionCombo` in `OpenSpecSettingsPanel`, wired through `OpenSpecConfigurable`.

Six callers use the effective version:
- `ChangeService`, `BuiltInValidator` and `SpecTreeModel` resolve it through `VersionSupport.fromString`, which maps every string to `V1_2`.
- `WorkflowSchemaContextService` carries it as the context's config-format version.
- `ExploreContextService` refers to it in a comment.

## Goals / Non-Goals

**Goals:** no control that does nothing; no stored value that applies invisibly; the config-format axis and its readers intact.

**Non-Goals:**
- Removing `VersionSupport`, the `version:` reader, or the persisted field.
- Changing what `openspec/config.yaml` scaffolding writes.

## Decisions

### D1. Remove the control, keep the field

The combo, `VERSION_OVERRIDE_PRESETS`, and the panel's `getVersionOverride`/`setVersionOverride` go, along with the Configurable's handling. `State.versionOverride` stays as a persisted field, marked as no longer read, so an existing settings file loads without warnings. `OpenSpecSettings.getVersionOverride`/`setVersionOverride` go if they have no callers left.

### D2. `getEffectiveVersion` ignores a stored override

With no UI, a stored override would apply forever and be invisible. Today any value resolves to `V1_2` anyway, but the raw string still reaches the schema context, and a future second format would make it matter. So `getEffectiveVersion` reads only `config.yaml`'s `version:`, else returns `null` as it does today, which every caller already resolves to the 1.2.0 format through `VersionSupport.fromString`. That's the fallback behaviour the project guidance asks to keep, unchanged apart from the dropped override layer.

## Risks / Trade-offs

- **Someone who typed a custom override loses it.** → It had no effect on behaviour (everything resolves to `V1_2`). The CHANGELOG says the control was removed because it did nothing.
- **A future second config format** will need the control back. → The axis, field and reader are kept for exactly that; reintroducing a combo is small.

## Migration Plan

Settings files keep their `versionOverride` entry, ignored. To roll back, revert the commit.
