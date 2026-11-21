## REMOVED Requirements

### Requirement: Testable functionality is unit-tested

**Reason**: the requirement carries a scenario whose title names a vendor. Renaming it changes the requirement name, so it is removed here and re-added under the new name.

**Migration**: none. The re-added requirement keeps the same text and scenarios, and nothing else refers to the old name.

## ADDED Requirements

### Requirement: Functionality without the IDE platform is unit-tested

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

#### Scenario: Self-hosted workflow honors the same concurrency contract
- **WHEN** the self-hosted build workflow is triggered
- **THEN** it SHALL declare the same concurrency block as the GitHub `build.yml` workflow (group keyed by workflow + ref, cancel-in-progress true) so the self-hosted runner exhibits identical cancellation behavior

#### Scenario: Fixture lifecycle
- **WHEN** contract fixtures are added
- **THEN** they SHALL live under `src/test/resources/fixtures/cli/<version>/`, and their provenance (capturing version or era, capture recipe, recapturability) SHALL be recorded in a committed manifest
- **AND** fixtures for a still-supported generation SHALL keep their assertions intact until that generation's support is dropped by a spec-level change
- **AND** a fixture whose source command no longer exists SHALL be documented as pinned, in the manifest and in its consuming test
