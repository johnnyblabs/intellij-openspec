## MODIFIED Requirements

### Requirement: Plugin Verifier compatibility

The verify job SHALL run the IntelliJ Plugin Verifier against a verified IDE set spanning the declared compatibility range, reporting binary compatibility, deprecated API usage and experimental API usage. The set SHALL contain the minimum declared version (2024.2), the latest release of the last IntelliJ IDEA Community line (2025.2), and the latest release of every IntelliJ IDEA line after it. Early-access builds SHALL NOT be verified.

#### Scenario: All declared versions checked
- **WHEN** the verify job runs
- **THEN** it SHALL verify the plugin against the minimum declared version (2024.2), the last Community release line (2025.2), and each IntelliJ IDEA release line after it through the latest stable release

#### Scenario: A new stable release line is added to the verified set
- **WHEN** JetBrains ships a new stable IntelliJ IDEA release line
- **THEN** the verified set SHALL be extended to include it, so the newest stable IDE is always verified

#### Scenario: EAP builds are not verified in CI
- **WHEN** the verify job resolves its IDE set
- **THEN** it SHALL NOT include early-access builds

#### Scenario: Compatible result
- **WHEN** the Plugin Verifier reports "Compatible" for all checked versions
- **THEN** the verify job SHALL pass

#### Scenario: Incompatible result
- **WHEN** the Plugin Verifier reports binary incompatibilities for any checked version
- **THEN** the verify job SHALL fail, blocking the PR from merging

#### Scenario: Intermediate lines
- **WHEN** the verified set omits release lines between the floor and the last Community line
- **THEN** that MAY be done, because the plugin compiles against the floor and API breakage surfaces first in the newest releases

### Requirement: Test coverage regression floor

The build SHALL enforce a JaCoCo coverage regression floor, wired into `check`, covering the instruction, line and branch counters. Each minimum SHALL be set just below the recorded baseline, and the baseline (measured value and date) SHALL be recorded with the floor. The floor SHALL be ratcheted upward as coverage improves and SHALL NOT be lowered without a recorded justification.

#### Scenario: Regression below the floor fails the build
- **WHEN** a change reduces measured coverage for a governed counter below its configured minimum
- **THEN** `jacocoTestCoverageVerification` SHALL fail, and because it is wired into `check`, `./gradlew build` (and CI) SHALL fail

#### Scenario: Branch coverage is governed
- **WHEN** the coverage floor is evaluated
- **THEN** it SHALL include a branch-coverage minimum in addition to instruction and line

#### Scenario: The floor is not silently lowered
- **WHEN** a change lowers a coverage minimum
- **THEN** the change SHALL record a justification; lowering a floor to make an unrelated change pass is not permitted

### Requirement: Testable functionality is unit-tested

Functionality that runs without the IDE platform (parsers, resolvers, version comparisons, action gating, path and format handling) SHALL have unit tests that assert real behaviour and fail when that behaviour breaks. A change that adds or alters such functionality SHALL add or update its tests in the same change. Tests of output the plugin does not control SHALL be contract-tested against captured real output, not hand-authored shapes.

#### Scenario: New testable logic ships with tests
- **WHEN** a change adds functionality that can be unit-tested without the IDE platform
- **THEN** the change SHALL include unit tests asserting that functionality's real behavior, and each SHALL fail if the covered behavior regresses

#### Scenario: External-output parsers are contract-tested
- **WHEN** code parses output from an external tool (a CLI `--json`, an on-disk format, an API response)
- **THEN** its tests SHALL run against captured real output committed as a fixture, not against a hand-authored approximation

#### Scenario: New-generation captures are added without weakening legacy coverage
- **WHEN** contract fixtures are captured from a newer CLI generation whose output semantics differ from an already-covered, still-supported generation
- **THEN** the new captures SHALL land under the new generation's version-named directory with their own generation-specific tests, and the existing generation's fixtures and assertions SHALL be preserved verbatim

#### Scenario: Unrecapturable fixtures are pinned, not staled
- **WHEN** the current CLI no longer provides the command a committed fixture was captured from
- **THEN** the fixture SHALL be marked as pinned in the provenance manifest and its consuming test's documentation, and SHALL remain the coverage for the generation that provided the command

#### Scenario: Forgejo workflow honors the same concurrency contract
- **WHEN** the `.forgejo/workflows/build.yaml` workflow is triggered
- **THEN** it SHALL declare the same concurrency block as the GitHub `build.yml` workflow (group keyed by workflow + ref, cancel-in-progress true) so Forgejo Actions exhibits identical cancellation behavior

#### Scenario: Fixture lifecycle
- **WHEN** contract fixtures are added
- **THEN** they SHALL live under `src/test/resources/fixtures/cli/<version>/`, and their provenance (capturing version or era, capture recipe, recapturability) SHALL be recorded in a committed manifest
- **AND** fixtures for a still-supported generation SHALL keep their assertions intact until that generation's support is dropped by a spec-level change
- **AND** a fixture whose source command no longer exists SHALL be documented as pinned, in the manifest and in its consuming test

### Requirement: Single-sourced top-supported CLI version

The top-supported OpenSpec CLI version SHALL be declared in one place, the `openspecTargetVersion` property in `gradle.properties`. Every other build or CI consumer SHALL derive it from that source rather than restate it. The build SHALL enforce, wired into `check`, that a contract-fixture corpus exists for that version, and the UI-smoke workflow SHALL install the single-sourced version.

#### Scenario: Target version without a fixture corpus fails the build

- **WHEN** `openspecTargetVersion` names a version for which no captured fixture corpus (`src/test/resources/fixtures/cli/<version>/`, with a `version.txt` whose content is that version) exists
- **THEN** the `check`-wired verification SHALL fail, and because it is wired into `check`, `./gradlew build` (and CI) SHALL fail

#### Scenario: Target version with a fixture corpus passes

- **WHEN** `openspecTargetVersion` names a version whose captured fixture corpus is present and whose `version.txt` matches
- **THEN** the verification SHALL pass without touching any other build output

#### Scenario: UI-smoke workflow installs the single-sourced version

- **WHEN** the UI-smoke workflow installs the OpenSpec CLI before running the smoke journeys
- **THEN** it SHALL install the version named by `openspecTargetVersion` (read from `gradle.properties`), not a version literal maintained separately in the workflow file, so the smoke suite runs against the plugin's declared top-supported CLI

#### Scenario: The historical floor is not collapsed into the target

- **WHEN** the single-sourced target version advances to a newer generation
- **THEN** the durable version floor enforced by the cross-generation parity guard SHALL still require the previously-covered generations' corpora, so advancing the target SHALL NOT drop coverage of an older still-supported generation

#### Scenario: Target and floor stay separate
- **WHEN** the top target and the durable version floor are considered
- **THEN** they SHALL remain separate concepts; the floor is a lower bound on retained coverage, enforced by the cross-generation parity guard

### Requirement: Scheduled-for-removal API usage fails verification

The Plugin Verifier gate SHALL fail the build on IntelliJ Platform API scheduled for removal (`@Deprecated(forRemoval = true)`), in addition to the binary-compatibility failures it already fails on. Such a call becomes a runtime incompatibility once removed, and the compatibility range is open-ended.

#### Scenario: Scheduled-for-removal usage fails the build

- **WHEN** the plugin sources call an IntelliJ Platform API annotated `@Deprecated(forRemoval = true)` against any IDE in the verified range
- **THEN** `verifyPlugin` SHALL report it at a failing level, failing the build, so the usage cannot merge

#### Scenario: Plain-deprecated usage does not fail the build

- **WHEN** the plugin sources call an IntelliJ Platform API that is `@Deprecated` but not `forRemoval`, and no non-deprecated replacement exists at the plugin's compatibility floor
- **THEN** `verifyPlugin` SHALL report it without failing the build, and the usage SHALL be tracked for migration when the floor advances

#### Scenario: The default binary-compatibility failures are preserved

- **WHEN** the scheduled-for-removal gate is added to the verification step
- **THEN** the severity levels that fail the verifier by default (binary incompatibilities / compatibility problems, internal-API and override-only usage) SHALL still fail, so adding the scheduled-for-removal gate does not weaken existing verification coverage

#### Scenario: Plain deprecation
- **WHEN** API is `@Deprecated` without `forRemoval`
- **THEN** its usage SHALL remain report-only and SHALL be tracked for migration when the floor advances, not blocked, because its replacement may be unavailable at the compatibility floor
