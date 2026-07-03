## Why

OpenSpec CLI 1.5.0 **removed** the `workspace`, `context-store`, and `initiative` commands — the entire 1.4 coordination model — replacing it with the early-beta `store`/`workset` model. The plugin's coordination surface (shipped in the Phase 3 `coordination-surfaces` change) was built against those now-removed commands and gates itself with a single lower bound: `CoordinationService.cliCoordinationAvailable()` returns `true` for **any** CLI at or above `COORDINATION_CLI_FLOOR` (`1.4.0`). It has no upper bound, so on a 1.5.0 CLI the plugin still:

- shells out to `openspec workspace list`, `openspec context-store list`, `openspec initiative list` (and the matching `doctor`/`create`/`setup` subcommands) — all of which no longer exist and fail; and
- resolves the presentation to the **Full** tier, offering write actions (create-initiative, setup-context-store, setup-workspace) that silently fail because their commands are gone.

This is latent breakage: a user who upgrades their CLI to 1.5.0 gets a coordination surface that errors on refresh and dangles dead write actions. The plugin's governing rule is that it **mirrors the OpenSpec client only** — it must not keep a removed model live. This change makes the coordination surface engage strictly within the CLI window where its commands actually exist, and stand down safely everywhere else.

This is a **safety-only** narrowing. It introduces no reading or UI for the new `store`/`workset` model — that is deliberately deferred (see Impact).

## What Changes

- **Introduce a version window for coordination.** The 1.4 coordination commands exist only in CLI `[1.4.0, 1.5.0)`. Add an upper-bound companion to `CliVersion.atLeast` — a `below(detected, ceiling)` helper (and an `inRange(detected, floorInclusive, ceilingExclusive)` convenience built from `atLeast` + `below`) — so "this feature was removed in 1.5.0" becomes expressible. Today only `atLeast` exists, which cannot express an upper bound. Add a `COORDINATION_CLI_CEILING` constant (`1.5.0`) alongside the existing `COORDINATION_CLI_FLOOR` (`1.4.0`).
- **Gate `cliCoordinationAvailable()` on the window.** It returns `true` only when the detected CLI is in `[1.4.0, 1.5.0)`. On CLI ≥ 1.5.0 it returns `false`, so `getCoordinationData`, the three `resolve*` methods, `fetchContextStoreDoctor`, and the write actions never invoke a removed command; resolution falls through to the read-only on-disk path.
- **Force the tier away from Full on CLI ≥ 1.5.0.** With `cliCoordinationAvailable()` false, `CoordinationTier.resolve(...)` can no longer land on Full. If legacy 1.4 coordination state still exists on disk, the surface shows read-only **Awareness** (so a user isn't surprised by state vanishing); with no legacy state it stays **Hidden**. No write action that would fail against 1.5.0 is ever offered.
- **Retire the stale `workspace-planning` schema.** CLI 1.5.0's `openspec schemas` lists only `spec-driven`. Remove `workspace-planning` from `VersionSupport.getValidSchemas()` so the plugin no longer advertises a schema the current CLI doesn't recognize, and retire the dead coordination-mode trigger that keyed off that removed non-default mode when deriving `coordinationModeActive`.
- **Leave the global CLI floor at 1.3.0.** This change does not raise the plugin-wide minimum; it only bounds the coordination sub-feature. A 1.5.0 user keeps every non-coordination capability.

## Capabilities

### New Capabilities
<!-- None. This change narrows an existing capability rather than adding one. -->

### Modified Capabilities
- `coordination-surfaces`: bound the coordination layer to the CLI window `[1.4.0, 1.5.0)` where its commands exist. On CLI ≥ 1.5.0 the plugin stops invoking the removed `workspace`/`context-store`/`initiative` commands, cannot resolve to the Full tier (Hidden, or read-only Awareness when legacy on-disk state exists), and no longer advertises the removed `workspace-planning` schema.

## Impact

- **Code:** `CliVersion` gains `below` / `inRange`; `CoordinationService` gains a `COORDINATION_CLI_CEILING` and window-checks `cliCoordinationAvailable()`; `VersionSupport.getValidSchemas()` drops `workspace-planning`; the coordination-mode trigger feeding `getCoordinationData(coordinationModeActive)` is retired. No new services, tool-window tabs, or write actions.
- **CLI contract:** the 1.4 coordination command surface (`{workspace,context-store,initiative} list|doctor|create|setup --json`) is now invoked **only** on CLI `[1.4.0, 1.5.0)`. On 1.5.0+ the plugin never calls it. Reading of the new `store`/`workset` surface is **not** added here.
- **Behavior:** users on CLI 1.4.x are unaffected (full coordination surface as before). Users on 1.5.0+ no longer see failing refreshes or dead write actions; they see Hidden, or read-only Awareness if legacy 1.4 state is still on disk. Users below 1.4 are unaffected (already Awareness-at-most).
- **Out of scope (tracked by other changes — referenced, not implemented here):** reading/UI for the new `store`/`workset` model (`add-store-workset-read-surface`); write actions for it (`add-store-workset-write-actions`); the cross-platform CI matrix (`add-cross-platform-ci-matrix`). The CRLF/telemetry parsing fix already shipped separately and is not part of this change.
- **Platform compatibility:** unchanged — continues to support IntelliJ IDEA 2024.2 and later. All CLI/IO stays off the EDT.
- **Docs:** README, CHANGELOG, and the feature reference updated to describe the coordination CLI window and the 1.5.0 stand-down behavior (vendor-neutral).
- **Tracker:** the linked tracker entry.
