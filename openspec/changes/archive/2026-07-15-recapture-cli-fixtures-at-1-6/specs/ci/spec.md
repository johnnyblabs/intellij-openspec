## MODIFIED Requirements

### Requirement: Testable functionality is unit-tested

Functionality that can be exercised without the running IDE platform — parsers, resolvers, version comparisons, action-gating logic, path/format handling, and similar pure logic — SHALL have unit tests that assert real behavior, such that each test fails if the behavior it covers is broken. A change that adds or materially alters such functionality SHALL add or update the corresponding tests in the same change. Tests that parse output the plugin does not control SHALL be contract-tested against captured real output rather than hand-authored shapes.

Contract fixtures SHALL follow a per-generation lifecycle: new captures SHALL be organized under CLI-version-named directories (`src/test/resources/fixtures/cli/<version>/`), and fixture provenance — the capturing CLI version or best-evidence era, the capture recipe, and whether the fixture is recapturable — SHALL be recorded in a committed manifest. Fixtures covering a still-supported CLI generation SHALL be retained with their assertions intact (not re-pointed at a newer generation's captures or deleted) until that generation's support is dropped by a spec-level change. A fixture whose source command no longer exists in the current CLI SHALL be documented as pinned in the manifest and in its consuming test rather than left to stale silently.

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
