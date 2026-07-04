# Changelog Automation

## Purpose
Automate generation of plugin `changeNotes` from `CHANGELOG.md` at build time, eliminating manually-maintained inline HTML and ensuring every release ships with accurate, version-matched release notes.

## Requirements

### Requirement: Build-time changeNotes generation

The build SHALL generate the `changeNotes` field in `plugin.xml` from `CHANGELOG.md` at build time using the `org.jetbrains.changelog` Gradle plugin. The plugin SHALL parse `CHANGELOG.md`, locate the entry matching the current project version, convert the markdown content to HTML, and inject it as the `changeNotes` value. The version header SHALL be excluded from the rendered output to avoid duplication with Marketplace's own version display.

#### Scenario: Matching version entry exists
- **WHEN** `CHANGELOG.md` contains an entry matching the current project version
- **THEN** the build SHALL render that entry's content as HTML and use it as `changeNotes`

#### Scenario: No matching version entry
- **WHEN** `CHANGELOG.md` has no entry matching the current project version and no `[Unreleased]` section exists
- **THEN** the build SHALL fail with an error indicating the missing changelog entry

#### Scenario: Unreleased fallback during development
- **WHEN** `CHANGELOG.md` has no entry matching the current project version but an `[Unreleased]` section exists
- **THEN** the build SHALL use the `[Unreleased]` section content as `changeNotes`

### Requirement: Custom header format support

The changelog plugin SHALL be configured with a `headerParserRegex` matching the project's existing `## v{version} — {title}` header format. The regex SHALL extract the semver portion from headers like `## v0.2.9 — EDT Threading Compliance`.

#### Scenario: Existing header format parsed
- **WHEN** `CHANGELOG.md` contains `## v0.2.9 — EDT Threading Compliance`
- **THEN** the plugin SHALL recognize this as the entry for version `0.2.9`

#### Scenario: Header without title parsed
- **WHEN** `CHANGELOG.md` contains `## v0.3.0`
- **THEN** the plugin SHALL recognize this as the entry for version `0.3.0`

### Requirement: No inline HTML changeNotes

The `build.gradle.kts` file SHALL NOT contain a manually-maintained inline HTML string for `changeNotes`. The `changeNotes` field SHALL be assigned exclusively via a Gradle `provider {}` that delegates to the changelog plugin's `renderItem()` method.

#### Scenario: Inline HTML removed
- **WHEN** the build script is inspected
- **THEN** there SHALL be no raw HTML strings assigned to `changeNotes`

#### Scenario: Provider-based assignment
- **WHEN** `patchPluginXml` runs
- **THEN** `changeNotes` SHALL be populated by the changelog plugin's rendering pipeline

### Requirement: User-facing capability changes are disclosed

The changelog SHALL disclose changes to user-facing capabilities. When a change adds, removes, or materially alters a user-facing capability — including **retiring a capability for a supported CLI version** — it SHALL record a corresponding entry in the changelog's unreleased section, written for users. A user-facing capability that a released version delivered SHALL NOT be removed or re-gated **silently** (with no changelog entry). Internal-only refactors with no user-visible effect are exempt.

#### Scenario: Removing a shipped capability is disclosed
- **WHEN** a change removes or re-gates a user-facing capability that a released version delivered
- **THEN** the changelog's unreleased section SHALL include an entry describing the change for users, and the absence of such an entry SHALL be treated as a defect in the change

#### Scenario: Version-scoped retirement is noted with its scope
- **WHEN** a capability is retired for a supported CLI version because that version's client removed the underlying command
- **THEN** the changelog SHALL note the retirement and the version scope (which CLI line still offers it and which no longer does)

#### Scenario: Internal refactors need no capability entry
- **WHEN** a change alters implementation without adding, removing, or materially changing any user-facing capability
- **THEN** no user-facing capability changelog entry is required
