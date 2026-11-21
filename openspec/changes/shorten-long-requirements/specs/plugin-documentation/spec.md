## MODIFIED Requirements

### Requirement: Workflow profiles documentation page is published

The `Workflow-Profiles.md` documentation SHALL be committed to `main` at `scripts/docs/wiki/Workflow-Profiles.md`, so the plugin's documentation links resolve to a live page. Only that wiki content MAY enumerate specific workflow names, since the plugin code intentionally does not.

#### Scenario: Docs URL resolves
- **WHEN** the user clicks "About profiles…" in the status bar widget popup or "Read the full guide" in the Settings panel ContextHelpLabel
- **THEN** the link SHALL open a live, fully rendered documentation page (HTTP 200) and not return 404

#### Scenario: Page covers the three-way semantic split
- **WHEN** a user reads the workflow profiles documentation page
- **THEN** the page SHALL distinguish between schema (e.g., `spec-driven`), project profile (config.yaml `profile:` block name/description/language metadata), and workflow profile (global CLI config — `core` or a custom workflow set)

#### Scenario: Page covers the two-step profile change process
- **WHEN** a user reads the workflow profiles documentation page
- **THEN** the page SHALL describe the two-step OpenSpec profile change process: first run `openspec config profile <preset>` (or use Customize workflows… to launch the interactive picker) to switch the workflow set, then run `openspec update` to install the corresponding skills for the user's AI tools

#### Scenario: Page reflects D2 — combo only lists CLI presets
- **WHEN** the page describes the Settings panel workflow profile combo
- **THEN** the page SHALL accurately reflect that the combo lists only CLI-accepted presets (default and `core`) and that the path to a non-preset workflow set is via the "Customize workflows…" button — the page SHALL NOT describe `custom` as a third combo entry the user can pick directly

### Requirement: OpenSpec client coverage matrix is published

The project SHALL publish and maintain a coverage matrix at `docs/openspec-support.md` that maps OpenSpec client capabilities to the plugin's support status, annotated with the minimum CLI version each requires. It SHALL distinguish supported, partial, divergent, planned and plugin-original capabilities. Support mechanisms (delegated, built-in or indirect) SHALL reflect the verified behaviour of the code they describe; a mismatch is a documentation defect.

#### Scenario: Matrix reflects client coverage with version annotations
- **WHEN** a user reads `docs/openspec-support.md`
- **THEN** it SHALL present OpenSpec client capabilities grouped by area, each with a support status and a CLI-version annotation
- **AND** it SHALL state the minimum supported CLI version and that behavior degrades gracefully below it

#### Scenario: Mechanism classification matches code behavior
- **WHEN** the matrix classifies a capability's support mechanism (delegated / built-in / indirect)
- **THEN** that classification SHALL match how the plugin actually implements the capability, verified against the code path rather than assumed

#### Scenario: README links to the coverage matrix
- **WHEN** a user reads the README
- **THEN** the README SHALL link to the OpenSpec client coverage matrix

#### Scenario: Coverage matrix stays vendor-neutral on the public mirror
- **WHEN** the coverage matrix or its accompanying change artifacts are published to the public mirror
- **THEN** they SHALL reference only public identifiers (OpenSpec change names, CHANGELOG versions)
- **AND** they SHALL NOT contain internal tracker identifiers or environment-specific references

#### Scenario: Support contract
- **WHEN** the matrix states the plugin's CLI-version support
- **THEN** it SHALL give the minimum supported version, the baseline, and the runtime version-awareness

### Requirement: Every document declares a maintenance class

Every tracked documentation file (each doc under `docs/`, and the top-level README, CHANGELOG and CONTRIBUTING) SHALL declare a maintenance class near the top: Living (updated with every relevant change), Snapshot (reviewed on a stated cadence), Reference (updated only when the described thing changes), or Retired (kept for history, not maintained).

#### Scenario: Each doc carries a recognized maintenance label
- **WHEN** any tracked documentation file is read
- **THEN** it SHALL contain a `Maintenance:` label naming exactly one of the four classes (Living, Snapshot, Reference, Retired)

#### Scenario: A doc without a maintenance label is a hygiene failure
- **WHEN** a documentation-hygiene check scans the tracked docs
- **THEN** it SHALL fail if any doc lacks a `Maintenance:` label or uses a class outside the defined set

### Requirement: Per-CLI-version feature-delta analysis grounds support decisions

Before or as part of adding support for an OpenSpec CLI version, the plugin SHALL produce a per-version feature-delta and plugin-impact analysis under `docs/cli-versions/`, cataloguing the features introduced, modified, deprecated and removed relative to the prior supported version. Support decisions for that version SHALL reference the analysis, and the `docs/cli-versions/` index SHALL list each analysis.

#### Scenario: Analysis precedes support for a CLI version
- **WHEN** the plugin adds or changes support for an OpenSpec CLI version
- **THEN** a per-version feature-delta analysis for that version SHALL exist under `docs/cli-versions/`, and the implementing change SHALL reference it

#### Scenario: Upstream claims are cited; unverified claims are quarantined
- **WHEN** the analysis states a fact about the CLI's behavior in a version
- **THEN** that fact SHALL carry a citation to upstream OpenSpec documentation, and any claim not verifiable upstream SHALL appear only under an "open questions / to verify" section, not as an asserted fact

#### Scenario: Each feature carries a supportability and UI assessment
- **WHEN** a feature appears in the analysis
- **THEN** it SHALL record whether the plugin supports it, how (built-in / delegated / read-only / n-a), whether a plugin UI component exists or is warranted, and a consumer how-to

#### Scenario: Decisions reference the analysis
- **WHEN** a plugin change decides to build, defer, or gate support for a CLI-version feature
- **THEN** the decision SHALL be traceable to the per-version analysis rather than to assumption

#### Scenario: Upstream citations
- **WHEN** a claim about CLI behaviour is recorded
- **THEN** it SHALL cite Fission's upstream documentation
- **AND** a claim that cannot be verified upstream SHALL be quarantined in an open-questions section, not asserted as fact

#### Scenario: Per-feature impact
- **WHEN** a feature is recorded
- **THEN** the analysis SHALL give its mechanics, whether the plugin can and should surface it (including whether a UI component exists or is warranted), and a consumer how-to through the plugin or the CLI

#### Scenario: Currency and production
- **WHEN** the plugin's understanding of a version changes
- **THEN** the analysis SHALL be updated and carry a maintenance label
- **AND** it SHALL be produced through an OpenSpec explore pass over the upstream documentation
