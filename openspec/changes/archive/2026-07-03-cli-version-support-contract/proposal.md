## Why

The 1.4 coordination **write actions** — New Initiative, Set Up Context Store, and Set Up Workspace — **shipped and were advertised** in plugin v0.3.0 and v0.3.1, both published to the Marketplace. They were then **silently removed** while the Coordination panel was rebuilt around the 1.5 store/workset toolbar, with **no spec delta and no CHANGELOG note**. A user on a 1.4.x CLI who reads the docs (which still describe these actions) finds a Coordination tab that lists their initiatives/context-stores/workspaces read-only, with no way to create or set them up — a regression against a released, documented capability.

The 1.4 CLI **has** these commands (`initiative create`, `context-store setup`, `workspace setup`). A read-only IDE on a 1.4.x CLI therefore **diverges from the client** — the opposite of the plugin's governing rule that it mirrors the OpenSpec client. The 1.4.x user base is large and slow to migrate, so honoring backward-compat matters.

This change **restores** the three write actions, **version-gated** to the coordination window `[1.4.0, 1.5.0)` so they auto-retire the moment a user upgrades to a 1.5 CLI (which removed the commands). Critically, it also **engrains enforcement**: a per-CLI-version behavior contract plus per-version tests, so that silently deleting or mis-gating a version-gated capability fails the build instead of shipping unnoticed.

## What Changes

- **Restore the three coordination write methods** on `CoordinationService` — `createInitiative`, `setupContextStore`, `setupWorkspace` — recovered from the released v0.3.0 tag and re-expressed on the current `WriteResult` type. Each is gated on `cliCoordinationAvailable()` (the `[1.4.0, 1.5.0)` window); outside the window it short-circuits with an accurate guidance message and never shells out (it never tells a 1.4.x user they need 1.5).
- **Re-plumb three IDE actions** into the Coordination panel's `ActionToolbar` and tree context menu — New Initiative, Set Up Context Store, Set Up Workspace — mirroring the store actions. They are visible on the legacy (pre-1.5) surface and enabled only within the window; on a 1.5 CLI the store-lead surface hides them, so they self-retire.
- **Add a gating sibling** `CoordinationActionGating.coordinationWriteEnabled(fullTier, cliCoordinationAvailable)` = `fullTier && cliCoordinationAvailable`, the `[1.4.0, 1.5.0)` gate, alongside the existing store gate `writeEnabled = fullTier && storesAreLeadModel`. The two are mutually exclusive by CLI version.
- **Fix the misleading status message** that told every legacy user "Write actions require the OpenSpec 1.5 store/workset model." It now reads accurately per tier: within the window the create/set-up actions are enabled; below `1.4.0` the CLI is required. It never tells a 1.4.x user they need 1.5.
- **Reconcile the spec.** Remove the false "SHALL NOT resolve to Full at ≥1.5.0" clause and scenario from *Tiered coordination presentation* (Full is reachable in the 1.4 window OR at ≥1.5.0 via the store model — this resolves the contradiction with `store-workset-actions`); clarify *Coordination write actions* to describe the restored, self-retiring 1.4 actions; and add a *CLI-version behavior contract* requirement enforced by per-version tests.
- **Add per-version enforcement tests** covering the full 1.3.x / 1.4.x / 1.5.x matrix, including the assertion that would have caught this regression: on a 1.4.x CLI the coordination write path is enabled and the write methods do not short-circuit.

## Capabilities

### New Capabilities
<!-- None. This change restores an existing capability and adds enforcement; it does not introduce a new user-facing capability. -->

### Modified Capabilities
- `coordination-surfaces`: restore the version-gated 1.4 coordination write actions (create initiative / set up context store / set up workspace) within `[1.4.0, 1.5.0)`; reconcile the tiered-presentation requirement so Full is reachable in both coordination regimes (1.4 window and ≥1.5.0 store model); and add a CLI-version behavior contract enforced by per-version tests.

## Impact

- **Code:** `CoordinationService` regains `createInitiative` / `setupContextStore` / `setupWorkspace` (window-gated) plus a `WriteResult.ok(...)` factory and a `stderrOrStdout` helper; `CoordinationActionGating` gains `coordinationWriteEnabled`; `CoordinationPanel` gains three `AnAction`s wired into the toolbar and context menu, a cached `coordinationWriteEnabled` flag, and an accurate status message (and renames the generic write dispatcher `runStoreWrite → runWrite`). No new services or tool-window tabs.
- **CLI contract:** the 1.4 write commands (`initiative create`, `context-store setup`, `workspace setup`) are invoked **only** on a CLI in `[1.4.0, 1.5.0)`. On `≥1.5.0` they are never invoked (and the actions are hidden). Reading behavior is unchanged.
- **Behavior:** users on a 1.4.x CLI regain the create/set-up actions that shipped in v0.3.0/v0.3.1. Users on `≥1.5.0` see the store/workset write actions as before and never see the retired 1.4 actions. Users below `1.4.0` see read-only Awareness with accurate guidance.
- **Testing:** a per-version behavior contract test pins 1.3.x / 1.4.x / 1.5.x. The 1.4.x row asserts the coordination write path is enabled and the write methods proceed past the gate (the regression guard); the 1.5.x row asserts they short-circuit without invoking a removed command.
- **Process:** CONTRIBUTING gains a "version-support fidelity" rule — any change touching CLI-version-gated behavior must update the per-version contract and per-version tests.
- **Docs:** the feature reference, coverage matrix, CLI-companion guide, and marketplace page describe the restored 1.4 write actions and their self-retirement on a 1.5 upgrade; the CHANGELOG discloses the restoration honestly.
- **Platform compatibility:** unchanged — IntelliJ IDEA 2024.2+. All CLI/IO stays off the EDT; `update()` reads cached state only.
- **Tracker:** the linked tracker entry.
