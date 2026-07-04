## 1. Restore the 1.4 coordination write methods

- [x] 1.1 Add a `WriteResult.ok(String message, @Nullable String createdPath)` factory to the current `CoordinationService.WriteResult` (the 1.5 record) so a legacy success can carry an optional created path.
- [x] 1.2 Recover `createInitiative(id, title)`, `setupContextStore(@Nullable id)`, and `setupWorkspace(name)` from the released v0.3.0 `CoordinationService`, re-expressed on the current `WriteResult`. Each runs off the EDT and delegates to `initiative create` / `context-store setup` / `workspace setup`.
- [x] 1.3 Gate each on `cliCoordinationAvailable()` (the `[1.4.0, 1.5.0)` window). Outside the window, short-circuit with `WriteResult.fail(COORDINATION_WRITE_GUIDANCE)` and never shell out. The guidance message is accurate for both out-of-window cases and never tells an in-window (1.4.x) user they need 1.5.
- [x] 1.4 Surface the CLI's error output on in-window failure (via a `stderrOrStdout` helper), per the spec; harden each method to degrade to a fail result on any exception so a pooled-thread write never crashes the thread.

## 2. Re-plumb the three IDE actions

- [x] 2.1 Add `CoordinationActionGating.coordinationWriteEnabled(boolean fullTier, boolean cliCoordinationAvailable)` = `fullTier && cliCoordinationAvailable`, sibling to the existing store `writeEnabled`.
- [x] 2.2 Add `NewInitiativeAction`, `SetUpContextStoreAction`, `SetUpWorkspaceAction` as `AnAction`s in `CoordinationPanel`, wired into the same `ActionToolbar` action group (so they also appear in the tree context menu).
- [x] 2.3 Gate visibility on the legacy surface (`!storeModel`) and enablement on the cached `coordinationWriteEnabled` flag, so the actions are enabled only on a 1.4.x CLI and hidden on a 1.5 CLI (self-retiring). `update()` reads cached state only; the CLI call runs off the EDT; refresh via `invokeLater`.
- [x] 2.4 Compute `coordinationWriteEnabled` in `applyTier` from `coordinationWriteEnabled(data.tier().allowsWriteActions(), data.sourcedFromCli())` (`sourcedFromCli()` is exactly `cliCoordinationAvailable()`).

## 3. Fix the misleading status message

- [x] 3.1 Replace the legacy-branch status text ("Write actions require the OpenSpec 1.5 store/workset model.") with tier-accurate text: in-window → create/set-up actions enabled (and note they retire on a 1.5 upgrade); below `1.4.0` → the actions require an OpenSpec CLI in the 1.4.x line. Never tell a 1.4.x user they need 1.5.

## 4. Reconcile the coordination-surfaces spec

- [x] 4.1 MODIFY *Tiered coordination presentation*: remove the false "SHALL NOT resolve to Full at ≥1.5.0" clause and its scenario; Full is reachable in the 1.4 window (legacy writes) OR at ≥1.5.0 (store writes). Resolves the contradiction with `store-workset-actions`.
- [x] 4.2 MODIFY *Coordination write actions*: describe the restored, version-gated, self-retiring 1.4 actions (New Initiative / Set Up Context Store / Set Up Workspace) and the accurate out-of-window guidance.
- [x] 4.3 ADD *CLI-version behavior contract*: per-CLI-version behavior (1.3.x / 1.4.x / 1.5.x) is explicit and enforced by per-version tests; a change altering a supported version's behavior must update the contract and tests.

## 5. Per-version enforcement tests

- [x] 5.1 Extend `CoordinationServiceWindowTest` with a per-version matrix (Mockito `Project` + `CliDetectionService`).
- [x] 5.2 `1.3.x` (1.3.0, 1.3.9): no coordination, no store; no write path; legacy writes short-circuit with guidance.
- [x] 5.3 `1.4.x` (1.4.0, 1.4.9): `cliCoordinationAvailable()` true; `coordinationWriteEnabled(true, true)` true; `createInitiative`/`setupContextStore`/`setupWorkspace` do NOT short-circuit (non-guidance failure) — the assertion that would have caught the regression.
- [x] 5.4 `1.5.x` (1.5.0, 1.6.0): store write path enabled; legacy coordination write path disabled; legacy writes short-circuit without invoking a removed command.
- [x] 5.5 Each assertion fails if the covered version's behavior regresses (no vacuous asserts).

## 6. Documentation

- [x] 6.1 `docs/feature-reference.md`: Full 1.4 line = read + initiative-artifact nav + create/set-up write actions (note they retire on a 1.5 upgrade); Full 1.5 line = store/workset read+write.
- [x] 6.2 `docs/openspec-support.md`: keep the set-up/create action (Full tier) on the `1.4.x` rows; state the per-version contract in the Version-support block and mark it the single source of truth.
- [x] 6.3 `docs/getting-started-cli-companion.md` + `docs/marketplace-page.md`: Coordination wording reflects 1.4.x = view + create/manage (in-window), 1.5.x = stores/worksets.

## 7. CHANGELOG

- [x] 7.1 Add an Unreleased entry disclosing that the 1.4 coordination write actions are available in `[1.4.0, 1.5.0)` and retire automatically on a 1.5 CLI — user-facing and honest.

## 8. Process rule

- [x] 8.1 Add a "version-support fidelity" rule to CONTRIBUTING (parallel to documentation-fidelity): any change touching CLI-version-gated behavior MUST update the per-version contract + per-version tests.

## 9. Verify

- [x] 9.1 `./gradlew build` GREEN (tests + JaCoCo floor).
- [x] 9.2 `openspec validate cli-version-support-contract --strict` passes.
