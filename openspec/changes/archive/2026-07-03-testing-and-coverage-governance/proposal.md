## Why

Testing discipline and coverage are enforced today only by convention (the OpenSpec `tasks` rule, the CLAUDE.md contract-test note) and a JaCoCo floor that no spec governs. Two gaps:

1. **No spec requires that testable functionality be unit-tested.** Logic that runs without the IDE platform — parsers, resolvers, version math, action gating — can ship untested, and nothing capability-level says otherwise. (This is the same class of gap that let a version-gated behavior regress without a test.)
2. **The coverage floor has no declared baseline or ratchet policy in a spec.** `build.gradle.kts` carries a floor "just below current," but the current number was never recorded, so "just below current" drifts and the floor can silently erode.

This change makes both explicit: a coverage **baseline** is measured and recorded, the floor is set just below it with a stated ratchet policy, and testable functionality carries an explicit unit-test requirement.

## What Changes

- **Establish and record the coverage baseline.** Measured 2026-07-03: **INSTRUCTION 32.6%, LINE 30.7%, BRANCH 29.1%, METHOD 37.0%.** `build.gradle.kts` floors are set just below the baseline (INSTRUCTION `0.32`, LINE `0.30`, **new** BRANCH `0.28`) and the baseline is documented inline. Any regression below the floor fails `check` (hence CI).
- **`ci` gains a "Test coverage regression floor" requirement** — the build SHALL enforce a JaCoCo coverage floor set just below the recorded baseline across at least instruction, line, and branch counters; the floor SHALL be ratcheted upward as coverage improves and SHALL NOT be lowered without recorded justification; a change that drops coverage below the floor SHALL fail the build.
- **`ci` gains a "Testable functionality is unit-tested" requirement** — functionality exercisable without the IDE platform SHALL have unit tests asserting real behavior (each test fails if the covered behavior breaks); a change adding such functionality SHALL add the tests in the same change. This lifts the existing convention to a governed requirement.

## Capabilities

### Modified Capabilities
- `ci`: adds the coverage-baseline/ratchet floor requirement and the testable-functionality unit-test requirement.

## Impact

- **Build:** `build.gradle.kts` floors tightened to the recorded baseline (INSTRUCTION 0.31→0.32, LINE 0.29→0.30, add BRANCH 0.28); baseline documented inline. Current coverage clears all three.
- **Spec:** two ADDED requirements in `ci`.
- **Enforcement:** the coverage floor is already wired into `check`/CI; the unit-test requirement is enforced by review + the floor + the per-change `tasks` rule. No new machinery.
- **No runtime behavior change.**
