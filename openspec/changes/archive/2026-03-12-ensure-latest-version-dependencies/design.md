## Context

The proposal for `ensure-latest-version-dependencies` asks the plugin to stay compliant and compatible with the latest library versions, including any required migration work. In this codebase, dependency updates are cross-cutting because they affect build tooling, IntelliJ platform compatibility, test execution, and runtime behavior.

Current baseline and constraints:
- Java runtime and toolchain are pinned to Java 21.
- Gradle wrapper is on `8.13`.
- IntelliJ platform target is `2024.2` with `sinceBuild = 242`.
- Key direct dependencies include Gson, JUnit (Jupiter plus Vintage), and Mockito.

Stakeholders:
- Plugin users expecting stability on IntelliJ IDEA 2024.2+.
- Maintainers responsible for build reproducibility and update cadence.
- CI/release owners who need deterministic verification outcomes.

### Target Version Plan (Selected)

- `org.jetbrains.intellij.platform.settings`: `2.11.0` -> `2.13.0`
- `com.google.code.gson:gson`: `2.10.1` -> `2.13.2`
- `org.junit:junit-bom`: `5.10.0` -> `6.0.3`
- `org.mockito:mockito-core`: `5.11.0` -> `5.23.0`
- `org.mockito:mockito-junit-jupiter`: `5.11.0` -> `5.23.0`

Compatibility notes:
- Keep Java toolchain at 21.
- Keep IntelliJ platform target at `2024.2` for this change.
- Keep Gradle wrapper at `8.13` unless upgrade is required to satisfy dependency/tooling compatibility gates.

## Goals / Non-Goals

**Goals:**
- Establish a repeatable strategy to keep dependencies at latest stable compatible versions.
- Ensure compatibility with Java 21 and IntelliJ IDEA 2024.2+ during and after updates.
- Include all required code and test migrations in the same change when upgrades introduce API or behavior changes.
- Enforce verification gates (build, tests, plugin verification) before merge.

**Non-Goals:**
- Automatically adopting every newest release without compatibility checks.
- Expanding feature scope unrelated to dependency migration.
- Raising minimum Java or IntelliJ baseline unless explicitly approved by follow-up change.
- Introducing new runtime frameworks solely for dependency management.

## Decisions

### Decision 1: Define "latest" as latest stable compatible

**Choice:**
Use the newest stable versions that remain compatible with Java 21, IntelliJ platform `2024.2`, and existing plugin constraints.

**Rationale:**
- Satisfies proposal intent while preventing known incompatibility churn from bleeding-edge versions.
- Keeps compatibility contract with current users.

**Alternatives considered:**
- Always use absolute newest immediately: rejected due to high breakage risk.
- Keep current pins indefinitely: rejected because it conflicts with the proposal goal.

### Decision 2: Apply upgrades in phased risk tiers

**Choice:**
Upgrade in ordered phases: (1) build/tooling, (2) test stack, (3) runtime libraries, with validation after each phase.

**Rationale:**
- Improves failure isolation and reduces rollback blast radius.
- Makes migration scope and ownership clearer.

**Alternatives considered:**
- Single bulk upgrade: rejected because troubleshooting becomes difficult.
- Strict one-dependency-at-a-time across many changes: rejected as too slow for coordinated compatibility updates.

### Decision 3: Keep explicit dependency version pinning

**Choice:**
Maintain explicit version pins in Gradle files and avoid dynamic ranges.

**Rationale:**
- Preserves deterministic local and CI builds.
- Prevents unreviewed transitive drift.

**Alternatives considered:**
- Dynamic ranges (`+`): rejected due to non-determinism.
- Version catalog migration as part of this change: deferred to keep scope focused.

### Decision 4: Treat migration as mandatory completion criteria

**Choice:**
If upgrades introduce compile, runtime, or test incompatibilities, migrations SHALL be completed before this change is considered done.

**Rationale:**
- The proposal explicitly includes migration work.
- Avoids merging unstable version bumps.

**Alternatives considered:**
- Defer migration fixes: rejected due to stability risk.
- Ignore test-only breakages: rejected because tests are part of compatibility guarantees.

### Decision 5: Enforce verification gates before merge

**Choice:**
Require successful build, test suite, and IntelliJ plugin verification for upgraded dependencies.

**Rationale:**
- IntelliJ plugins are sensitive to API/platform coupling.
- Existing verification configuration provides actionable compatibility checks.

**Alternatives considered:**
- Compile-only validation: rejected as insufficient.
- Manual spot checks only: rejected for low repeatability.

## Risks / Trade-offs

- [Stable-compatible upgrades still introduce regressions] -> Mitigation: phase upgrades and run focused regression tests after each phase.
- [IntelliJ or Gradle tooling upgrade requires broader migration than planned] -> Mitigation: isolate tooling first and gate before proceeding to runtime changes.
- [Transitive dependency conflicts appear after updates] -> Mitigation: inspect dependency graph and pin conflicting transitive versions explicitly.
- [Validation time increases in CI] -> Mitigation: run phase-level checks during development and full verification at merge gate.
- [Rollback complexity after mixed upgrades] -> Mitigation: keep commits aligned to upgrade phases for clean reversions.

## Migration Plan

1. Inventory direct dependencies and critical transitives in Gradle configuration.
2. Determine target versions under the "latest stable compatible" policy.
3. Upgrade build and tooling dependencies first; run build and plugin verification.
4. Upgrade test dependencies; migrate failing tests and rerun full tests.
5. Upgrade runtime dependencies; migrate production code as required.
6. Resolve transitive conflicts and rerun full verification gates.
7. Document final versions and any migration notes in change artifacts.

Rollback strategy:
- Revert the latest phase if unresolved failures remain.
- If tooling updates are unstable, roll back tooling while keeping compatible library updates for a follow-up cycle.

## Open Questions

- Should this change include a Gradle wrapper update beyond the current `8.13` baseline?
- Is JUnit Vintage still required, or can the test stack be standardized to Jupiter-only?
- Should verification include additional IDE versions beyond the 2024.2 baseline?
- Are license/security scans required as blocking checks for this dependency change?
- What ongoing cadence should be adopted to keep dependencies current after this update?
