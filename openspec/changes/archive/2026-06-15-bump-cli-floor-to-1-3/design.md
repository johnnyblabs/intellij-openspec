## Context

The CLI floor was bumped from 1.2.0 to 1.3.0 in commit `5e83163` ("Raise minimum supported OpenSpec CLI to 1.3.0"). That commit landed without an OpenSpec change proposal and left three downstream surfaces in inconsistent states:

- `SchemaService.MIN_CLI_VERSION` = `"1.3.0"` ✅ aligned
- `README.md` "Minimum supported CLI" = 1.3.0 ✅ aligned
- `plugin-core` `Old-CLI startup notification` requirement = "older than 1.3.0" ✅ aligned
- `schema-management` `CLI version guard` requirement = "1.2.0 or later" ❌ stale

The misalignment is a tripwire — the next contributor who reads `schema-management` will see 1.2.0 and write code or tests pinned to that, opening a behavior gap. This change exists to close that gap and to retroactively put the 1.3.0 floor decision through the proposal workflow so subsequent floor bumps have an audit trail.

The Settings version-override combo was expanded to `["", "1.0.0", "1.1.0", "1.2.0", "1.3.0", "1.4.0"]` in commit `f2303ba` as part of the Junie-review fallout. Pre-floor entries (`1.0.0`, `1.1.0`, `1.2.0`) are inconsistent with the floor we're formalizing and don't produce any behaviorally distinct fallback today (all route to V1_2 via `fromString`'s default branch). Trimming them aligns the UI with the floor.

## Goals / Non-Goals

**Goals:**
- Bring `schema-management`'s `CLI version guard` requirement to 1.3.0 so all CLI-version-floor statements across the spec layer agree.
- Document the "1.3.x supported floor + 1.4.x recommended" model in the proposal so future floor decisions reference a stated policy, not implicit shipped behavior.
- Trim version-override combo presets to floor-relevant values (`["", "1.3.0", "1.4.0"]`).

**Non-Goals:**
- Bumping the floor to 1.4.0. Deferred to a future change once enough of the install base has moved past 1.3.x. The earlier `bump-cli-floor-to-1-4` proposal (a tracker entry) was closed pending that condition.
- Resolving the `VersionSupport.V1_X` axis ambiguity (config-format axis vs CLI-era axis). That discussion belongs with the future 1.4 work — surfacing it here would inflate this change beyond a retro-docs scope.
- Adding a `V1_4` placeholder enum entry. Same reason — comes with the V1_X axis decision.
- Removing `MIN_CLI_VERSION` literal in favor of `VersionSupport.allVersions()[0]` or similar. Worth doing eventually so future floor bumps are a one-line constant change; out of scope here.

## Decisions

### D1. Floor floor at 1.3.0; document 1.4.x as recommended

`SchemaService.MIN_CLI_VERSION` stays `"1.3.0"`. The `schema-management` delta spec says 1.3.0. The README already says "recommended CLI is 1.4.x"; no change there. Plugin features that require 1.4-specific CLI behavior (e.g., interactive `openspec config profile` picker invoked by D3 of the archived `profile-ui-cli-alignment` change) continue to handle 1.3.x gracefully via the existing fallback paths — that's not a regression introduced here, it's the state the plugin already ships.

**Alternative considered**: bump to 1.4.0 right now. Rejected — user feedback indicates 1.3.x is still a real install population. Bumping prematurely just generates support tickets without unlocking proportional plugin features.

### D2. Trim version-override combo to `["", "1.3.0", "1.4.0"]`

The pre-floor entries (`1.0.0`, `1.1.0`, `1.2.0`) were carry-over from earlier `V1_X` enum entries that no longer exist. They route to `V1_2` via `fromString`'s fallback and produce identical fallback behavior, so removing them changes nothing functionally — just stops implying they're meaningful choices. Combo stays `setEditable(true)` for legacy values; presets are convenience, not constraint.

**Alternative considered**: trim to `["", "1.3.0"]` (single supported value). Rejected — `1.4.x` is the recommended version and surfaces in the README; the combo should let users mirror their declared `config.yaml` `version:` against it.

## Risks / Trade-offs

- **Risk**: A user with `version: 1.2.0` in their `openspec/config.yaml` opens Settings and doesn't see `1.2.0` as a combo preset. → **Mitigation**: combo stays editable; the persisted value still renders when the panel loads; the `plugin-core` floor notification already explains that they're on a below-floor version. The combo is for what's *worth picking going forward*, not a museum of supported values.
- **Trade-off**: Filing a proposal for what amounts to one stale-spec fix + one combo line is heavy. Worth it because (a) the 1.3.0 floor never went through a proposal and the audit trail matters when the next bump lands, (b) bundling the combo trim keeps the "1.2.x is below floor" decision coherent across the spec layer and the UI.

## Migration Plan

1. Land this change in the plugin.
2. Plugin update reaches users via JetBrains Marketplace (CI-driven, `v*` tag push).
3. Users with persisted combo values of `1.0.0`/`1.1.0`/`1.2.0` see the value render in the editable combo on first Settings open; the dropdown no longer offers those presets. No data migration; the persisted value is still usable.
4. Rollback: revert the change commit; CI publishes the prior plugin version on next tag. No data migration concerns.

## Open Questions

- None. This is a scoped retro-docs + UI trim.