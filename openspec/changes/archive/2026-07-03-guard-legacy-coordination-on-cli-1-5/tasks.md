## 1. Version-window helper

- [x] 1.1 Add `CliVersion.below(String detected, String ceiling)` — returns `true` when `detected` is non-null, non-empty, parseable, and strictly less than `ceiling` in the existing semver-style numeric comparison; returns `false` for null/empty/`>= ceiling`. Mirror the null/empty handling of `atLeast` (a null/empty `detected` is not "below" anything → `false`), and document that boundary choice.
- [x] 1.2 Add `CliVersion.inRange(String detected, String floorInclusive, String ceilingExclusive)` implemented as `atLeast(detected, floorInclusive) && below(detected, ceilingExclusive)`, so the half-open window `[floor, ceiling)` is expressible in one call.

## 2. Bound coordination to the CLI window

- [x] 2.1 Add `COORDINATION_CLI_CEILING = "1.5.0"` next to `COORDINATION_CLI_FLOOR = "1.4.0"` in `CoordinationService`, with a comment noting 1.5.0 removed the `workspace`/`context-store`/`initiative` commands.
- [x] 2.2 Change `cliCoordinationAvailable()` to gate on `CliVersion.inRange(detectedVersion, COORDINATION_CLI_FLOOR, COORDINATION_CLI_CEILING)` (still requiring `detection != null && detection.isAvailable()`), so it is `true` only on CLI `[1.4.0, 1.5.0)` and `false` on `>= 1.5.0`.
- [x] 2.3 Confirm (no new code needed) that with `cliCoordinationAvailable()` false, `resolveWorkspaces`/`resolveContextStores`/`resolveInitiatives`, `fetchContextStoreDoctor`, `createInitiative`, `setupContextStore`, and `setupWorkspace` all skip the removed commands and take their existing fallback/guard branches (disk read for resolution; `WriteResult.fail(...)` for writes).

## 3. Tier stand-down on CLI ≥ 1.5.0

- [x] 3.1 Verify `CoordinationTier.resolve(hasState, coordinationModeActive, cli)` can no longer return Full when `cli` is false: on CLI ≥ 1.5.0 it resolves to Awareness when legacy on-disk state exists, and Hidden otherwise. Adjust the resolver only if it can currently reach Full without `cli`.
- [x] 3.2 Retire the dead coordination-mode trigger that set `coordinationModeActive` from the removed non-default (`workspace-planning`) mode, so an old mode marker can't force a non-Hidden tier on a 1.5.0 CLI. Ensure the caller of `getCoordinationData(coordinationModeActive)` derives the flag only from still-valid signals.

## 4. Retire the removed schema

- [x] 4.1 Remove `workspace-planning` from `VersionSupport.V1_2`'s `validSchemas` set so `getValidSchemas()` returns only `spec-driven`, matching CLI 1.5.0's `openspec schemas`.
- [x] 4.2 Update the `getValidSchemas()` Javadoc (currently names `workspace-planning` as natively supported) to reflect the removal, and confirm no other caller hardcodes `workspace-planning` (grep `src/main/java`, `src/test/java`).

## 5. Documentation

- [x] 5.1 Update README and `docs/feature-reference.md` to describe the coordination CLI window `[1.4.0, 1.5.0)` and the read-only/Hidden stand-down on 1.5.0+ (vendor-neutral).
- [x] 5.2 Add a CHANGELOG entry under the unreleased section describing the safety guard (coordination no longer invokes removed CLI commands on 1.5.0+).
- [x] 5.3 Update the coverage matrix `docs/openspec-support.md`: change the **Coordination layers** rows (workspace / context-store / initiative) to state the supported CLI window is `[1.4.0, 1.5.0)` — these commands are **removed in 1.5.0**, with the surface standing down to read-only/Hidden on 1.5.0+; update the **Version support** section to note the coordination commands are 1.4-only. Keep vendor-neutral.

## 6. Tests

- [x] 6.1 `CliVersion.below` tests: `below("1.4.1","1.5.0")` → true; `below("1.5.0","1.5.0")` → false; `below("1.5.1","1.5.0")` → false; `below(null,"1.5.0")` and `below("","1.5.0")` → false. Each assertion must fail if `below` is stubbed to a constant.
- [x] 6.2 `CliVersion.inRange` tests: in-window (`1.4.0`, `1.4.9`) → true; below floor (`1.3.9`) → false; at/above ceiling (`1.5.0`, `1.6.0`) → false — proving the half-open `[floor, ceiling)` boundary.
- [x] 6.3 `CoordinationService.cliCoordinationAvailable()` tests over stubbed detected versions: `1.3.0` → false, `1.4.0`/`1.4.1` → true, `1.5.0`/`1.6.0` → false. Contract-test the version boundary against captured real `openspec --version` output where the fixture harness applies.
- [x] 6.4 Tier tests: with `cli=false` and legacy on-disk state present → Awareness; with `cli=false` and no state → Hidden; assert Full is unreachable when `cli=false`. Include a case simulating a 1.5.0 detected version end-to-end through `getCoordinationData`.
- [x] 6.5 Write-action guard tests: on a simulated 1.5.0 CLI, `createInitiative`/`setupContextStore`/`setupWorkspace` return `WriteResult.fail(...)` and never invoke `CliRunner` for a removed command (assert no removed command is run).
- [x] 6.6 `VersionSupport.getValidSchemas()` test: returns exactly `{spec-driven}` and does not contain `workspace-planning`; the assertion must fail if the schema is re-added.
